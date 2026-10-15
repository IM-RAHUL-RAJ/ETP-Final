import {
  WebSocketGateway,
  WebSocketServer,
  OnGatewayConnection,
  OnGatewayDisconnect,
} from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
import { Logger, Injectable } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { frontendOrigin } from '../../config/configuration';

// WebSocket Gateway for real-time session invalidation

@Injectable()
@WebSocketGateway({
  namespace: '/session',
  cors: {
    // Evaluated per handshake, after the .env has been loaded: only the web app may connect.
    origin: (_origin: string | undefined, callback: (error: Error | null, allow?: boolean | string) => void) =>
      callback(null, frontendOrigin()),
    methods: ['GET', 'POST'],
    credentials: true,
  },
})
export class SessionGateway implements OnGatewayConnection, OnGatewayDisconnect {
  @WebSocketServer() server: Server;
  private readonly logger = new Logger(SessionGateway.name);

  // Maps userId -> Set of socket IDs for that user
  private userSessions = new Map<string, Set<string>>();

  constructor(private jwtService: JwtService) {}

  // Extract userId from JWT token in query params or auth header
  
  private extractUserIdFromToken(token: string | string[] | undefined): string | null {
    if (!token) return null;

    // Handle array of tokens (from query param array)
    const tokenString = Array.isArray(token) ? token[0] : token;
    if (!tokenString) return null;

    try {
      // Remove 'Bearer ' prefix if present
      const cleanToken = tokenString.replace(/^Bearer\s+/i, '');
      const payload: any = this.jwtService.verify(cleanToken);
      return payload.sub; // 'sub' contains userId
    } catch (error) {
      this.logger.warn(`[SESSION_GATEWAY] Invalid token: ${error.message}`);
      return null;
    }
  }

  async handleConnection(socket: Socket) {
    const token = socket.handshake.auth.token || socket.handshake.query.token;
    const userId = this.extractUserIdFromToken(token);

    if (!userId) {
      this.logger.warn('[SESSION_GATEWAY] Connection rejected - invalid JWT');
      socket.disconnect(true);
      return;
    }

    // Track this socket for the user
    if (!this.userSessions.has(userId)) {
      this.userSessions.set(userId, new Set());
    }
    this.userSessions.get(userId)!.add(socket.id);

    
    socket.join(`user:${userId}`);

    this.logger.log(
      `[SESSION_GATEWAY] User ${userId} connected. Total sockets: ${this.userSessions.get(userId)!.size}`
    );
  }

  async handleDisconnect(socket: Socket) {
    // Find which user this socket belonged to
    for (const [userId, socketIds] of this.userSessions.entries()) {
      if (socketIds.has(socket.id)) {
        socketIds.delete(socket.id);
        if (socketIds.size === 0) {
          this.userSessions.delete(userId);
        }
        this.logger.log(
          `[SESSION_GATEWAY] User ${userId} disconnected. Remaining sockets: ${socketIds.size}`
        );
        break;
      }
    }
  }

  // Called by AuthService when a user logs in kicks all other sessions of that user
  kickOtherSessions(userId: string): void {
    if (!this.userSessions.has(userId)) {
      this.logger.debug(`[SESSION_GATEWAY] No active sessions to kick for userId=${userId}`);
      return;
    }

    // Broadcast SESSION_KICKED to all sockets for this user
    // They will receive it and immediately logout
    this.server.to(`user:${userId}`).emit('SESSION_KICKED', {
      message: 'Your session was closed because a new session was opened on another device or tab.',
      timestamp: new Date().toISOString(),
    });

    this.logger.log(
      `[SESSION_GATEWAY] Kicked all sessions for userId=${userId}. Affected sockets: ${this.userSessions.get(userId)!.size}`
    );
  }

  
  getActiveSessionCount(userId: string): number {
    return this.userSessions.get(userId)?.size || 0;
  }
}
