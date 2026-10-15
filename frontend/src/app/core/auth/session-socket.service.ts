import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { io, Socket } from 'socket.io-client';
import { API_ENDPOINTS } from '../config/api-endpoints';

@Injectable({
  providedIn: 'root'
})
export class SessionSocketService {
  private router = inject(Router);
  private authService = inject(AuthService);
  
  private socket: Socket | null = null;
  private readonly wsBaseUrl = `${API_ENDPOINTS.authApiBase}/session`;
  private isConnected = false;

  // Connect to the WebSocket session gateway  
  connect(): void {
    // Don't reconnect if already connected
    if (this.socket?.connected) {
      return;
    }

    const token = this.authService.getToken();
    if (!token) {
      console.warn('[SESSION_WS] No token available, cannot connect');
      return;
    }

    this.socket = io(this.wsBaseUrl, {
      auth: {
        token: token
      },
      reconnection: true,
      reconnectionDelay: 1000,
      reconnectionDelayMax: 5000,
      reconnectionAttempts: 5
    });

    // Connection established
    this.socket.on('connect', () => {
      this.isConnected = true;
      console.log('[SESSION_WS] Connected to session gateway');
    });

    // Receive SESSION_KICKED message from server
    // This happens when user logs in from another tab/device
    this.socket.on('SESSION_KICKED', (data: any) => {
      console.warn('[SESSION_WS] SESSION_KICKED received:', data);
      this.isConnected = false;
      this.disconnect();
      
      // Clear session from memory and storage
      this.authService.logout();
      
      // Navigate to sign-in with message
      this.router.navigate(['/sign-in'], {
        queryParams: {
          sessionMessage: data.message || 'Your session was closed because a new session was opened on another device or tab. Please log in again.'
        }
      });
    });

    // Connection error
    this.socket.on('connect_error', (error: any) => {
      console.error('[SESSION_WS] Connection error:', error);
    });

    // Connection closed unexpectedly
    this.socket.on('disconnect', () => {
      console.log('[SESSION_WS] Disconnected from session gateway');
      this.isConnected = false;
      
      // Only auto-logout if we were previously connected and user is still authenticated
      if (this.authService.isAuthenticated() && this.isConnected) {
        console.warn('[SESSION_WS] Unexpected disconnect - logging out');
        this.authService.logout();
        this.router.navigate(['/sign-in'], {
          queryParams: {
            sessionMessage: 'Session connection was lost. Please log in again.'
          }
        });
      }
    });
  }

  // Gracefully disconnect from session gateway
 
  disconnect(): void {
    if (this.socket) {
      this.isConnected = false;
      this.socket.disconnect();
      this.socket = null;
    }
  }

  // Check if WebSocket is currently connected  
  isSessionConnected(): boolean {
    return this.isConnected && !!this.socket?.connected;
  }
}
