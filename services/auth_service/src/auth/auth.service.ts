import {
    Injectable,
    UnauthorizedException,
    Logger,
    ConflictException,
    BadRequestException,
    ServiceUnavailableException
} from '@nestjs/common';

import { randomInt } from 'crypto';

/**
 * Marks a user who is still on the emailed default password. It lives in the existing roles
 * column, so no schema change is needed. Nothing authorises on it; it only tells the client to
 * ask for a new password, and it is removed when the password is changed.
 */
export const MUST_CHANGE_PASSWORD_ROLE = 'MUST_CHANGE_PASSWORD';


import * as argon2 from 'argon2';

import { AuthUserRepository }
    from './repository/auth-user.repository';

import { RefreshTokenService }
    from './services/refresh-token.service';

import { JwtTokenService }
    from './services/jwt-token.service';

import { AuthUser } from './entity/auth-user.entity';
import { RefreshTokenRepository } from './repository/refresh-token.repository';
import { SessionGateway } from './gateways/session.gateway';
import { MailService } from './services/mail.service';



@Injectable()
export class AuthService {


    private readonly logger =
        new Logger(AuthService.name);
   
    private readonly DUMMY_HASH = 
        '$argon2id$v=19$m=65536,t=3,p=4$' +
        'Z29vZGJhZHBhc3N3b3Jk$' +
        'Z29vZGJhZHBhc3N3b3Jk';

    // Login throttle configuration
    private readonly LOGIN_ATTEMPT_LIMIT = 5;
    private readonly LOGIN_THROTTLE_WINDOW_MS = 60 * 1000; // 60 seconds (matches frontend)
    private readonly loginAttempts = new Map<string, { count: number; firstAttempt: number }>();

    // Session management: Map of userId -> active sessionId
    private readonly activeSessionIds = new Map<string, string>();

    constructor(

        private userRepo: AuthUserRepository,

        private jwtService: JwtTokenService,

        private refreshService: RefreshTokenService,

        private refreshTokenRepo: RefreshTokenRepository,

        private sessionGateway: SessionGateway,

        private mailService: MailService

    ) { 
        // Clean up expired throttle entries periodically
        const throttleCleanupTimer = setInterval(() => {
            const now = Date.now();
            for (const [username, data] of this.loginAttempts.entries()) {
                if (now - data.firstAttempt > this.LOGIN_THROTTLE_WINDOW_MS) {
                    this.loginAttempts.delete(username);
                }
            }
        }, this.LOGIN_THROTTLE_WINDOW_MS);

        throttleCleanupTimer.unref?.();
    }

    private isThrottled(username: string): boolean {
        const attempt = this.loginAttempts.get(username);
        if (!attempt) return false;

        const now = Date.now();
        const windowExpired = now - attempt.firstAttempt > this.LOGIN_THROTTLE_WINDOW_MS;

        if (windowExpired) {
            this.loginAttempts.delete(username);
            return false;
        }

        return attempt.count >= this.LOGIN_ATTEMPT_LIMIT;
    }

    private recordFailedAttempt(username: string): void {
        const now = Date.now();
        const attempt = this.loginAttempts.get(username);

        if (!attempt) {
            this.loginAttempts.set(username, { count: 1, firstAttempt: now });
        } else {
            const windowExpired = now - attempt.firstAttempt > this.LOGIN_THROTTLE_WINDOW_MS;
            if (windowExpired) {
                // Reset the window
                this.loginAttempts.set(username, { count: 1, firstAttempt: now });
            } else {
                // Increment within the same window
                attempt.count++;
            }
        }
    }

    private clearLoginAttempts(username: string): void {
        this.loginAttempts.delete(username);
    }

    /**
     * Generate a unique session ID
     */
    private generateSessionId(): string {
        return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    }

    /**
     * Set active session for a user (invalidates previous session)
     */
    private setActiveSession(userId: string, sessionId: string): void {
        this.activeSessionIds.set(userId, sessionId);
        this.logger.debug(`[SESSION] New session created for userId=${userId} sessionId=${sessionId}`);
    }

    /**
     * Validate if a sessionId is active for a user
     */
    isSessionValid(userId: string, sessionId: string): boolean {
        const activeSessionId = this.activeSessionIds.get(userId);
        return activeSessionId === sessionId;
    }

    /**
     * Invalidate all sessions for a user
     */
    private invalidateUserSessions(userId: string): void {
        this.activeSessionIds.delete(userId);
        this.logger.debug(`[SESSION] All sessions invalidated for userId=${userId}`);
    }




    async login(username: string, password: string) {

        this.logger.log(
            `[AUTH_EVENT] login_attempt_started username=${username}`
        );

        // Check if username is throttled
        if (this.isThrottled(username)) {
            this.logger.warn(
                `[AUTH_EVENT] login_rejected_throttled username=${username}`
            );
            throw new UnauthorizedException({
                errorCode: 'AUTH-401',
                message: 'Unauthorised',
            });
        }

        const user =
            await this.userRepo.findByUsername(username);
        
        const passwordHashToVerify = user ? user.passwordHash : this.DUMMY_HASH;

        const valid =
            await argon2.verify(
                passwordHashToVerify,
                password
            );

        // Both paths (user found or not) now take the same time due to argon2 verification
        if (!user || !valid) {

            this.recordFailedAttempt(username);

            if (!user) {
                this.logger.warn(
                    `[AUTH_EVENT] login_rejected_user_not_found username=${username}`
                );
            } else {
                this.logger.warn(
                    `[AUTH_EVENT] login_rejected_invalid_password username=${username} userId=${user.id}`
                );
            }

            throw new UnauthorizedException({

                errorCode: "AUTH-401",

                message: "Unauthorised"

            });

        }

        // Password is valid and user exists
        const sessionId = this.generateSessionId();
        this.setActiveSession(user.id, sessionId);

        // Kick all other sessions for this user (multi-device logout)
        // This sends SESSION_KICKED message to all other browsers/tabs
        this.sessionGateway.kickOtherSessions(user.id);

        const accessToken =
            this.jwtService.generateAccessToken(

                user.id,

                user.accountId,

                user.roles,

                sessionId

            );

        const refreshToken =
            await this.refreshService.create(
                user.id
            );

        // Clear failed attempts on successful login
        this.clearLoginAttempts(username);

        this.logger.log(
            `[AUTH_EVENT] login_succeeded userId=${user.id} accountId=${user.accountId} roles=${user.roles.join(',')} sessionId=${sessionId}`
        );

        return {

            accessToken,

            refreshToken,

            tokenType: "Bearer",

            expiresIn: 900,

            mustChangePassword: (user.roles ?? []).includes(MUST_CHANGE_PASSWORD_ROLE)

        };

    }

    /**
     * Without a password, a default one is generated and emailed to `email`, and the user is
     * flagged to change it. The generated password is never logged or returned.
     */
    async register(username: string, password: string | undefined, accountId: number, roles?: string[], email?: string) {
        this.logger.log(
            `[AUTH_EVENT] register_attempt_started username=${username} accountId=${accountId}`
        );

        const generated = password === undefined || password === '';
        if (generated) {
            if (!email) {
                throw new BadRequestException({
                    errorCode: 'VAL-422',
                    message: 'Invalid input',
                });
            }
            if (!this.mailService.isConfigured()) {
                // Refuse before creating anything: a user nobody can tell the password to is useless.
                this.logger.error('[AUTH_EVENT] register_rejected_mail_not_configured');
                throw new ServiceUnavailableException({
                    errorCode: 'AUTH-503',
                    message: 'Email is not configured, so a default password cannot be sent',
                });
            }
        }
        const effectivePassword = generated ? this.generateDefaultPassword() : (password as string);

        // Check if username already exists
        const exists = await this.userRepo.existsByUsername(username);
        if (exists) {
            this.logger.warn(
                `[AUTH_EVENT] register_rejected_duplicate_username username=${username}`
            );
            throw new ConflictException({
                errorCode: 'AUTH-409',
                message: 'Registration failed',
            });
        }

        // Hash the password with argon2
        const passwordHash = await argon2.hash(effectivePassword, {
            type: argon2.argon2id,
            memoryCost: 65536,
            timeCost: 3,
            parallelism: 4,
        });

        // Create the user entity
        const authUser = new AuthUser();
        authUser.username = username;
        authUser.passwordHash = passwordHash;
        authUser.accountId = accountId;
        const baseRoles = roles && roles.length > 0 ? roles : ['CUSTOMER'];
        authUser.roles = generated ? [...baseRoles, MUST_CHANGE_PASSWORD_ROLE] : baseRoles;

        try {
            const savedUser = await this.userRepo.create(authUser);
            this.logger.log(
                `[AUTH_EVENT] register_succeeded userId=${savedUser.id} username=${username} accountId=${accountId}`
            );

            let emailSent = false;
            if (generated) {
                try {
                    await this.mailService.sendWelcome(email as string, username, username, effectivePassword);
                    emailSent = true;
                } catch (mailError) {
                    // The caller must not be left with a user whose password nobody received.
                    await this.userRepo.deleteById(savedUser.id);
                    this.logger.error(
                        `[AUTH_EVENT] register_rolled_back_email_failed username=${username} error=${(mailError as Error).message}`
                    );
                    throw new ServiceUnavailableException({
                        errorCode: 'AUTH-503',
                        message: 'The welcome email could not be sent',
                    });
                }
            }

            return {
                id: savedUser.id,
                username: savedUser.username,
                accountId: savedUser.accountId,
                roles: savedUser.roles,
                emailSent,
                createdOn: new Date().toISOString(),
            };
        } catch (error) {
            if (error instanceof ServiceUnavailableException) {
                throw error;
            }
            this.logger.error(
                `[AUTH_EVENT] register_failed_database_error username=${username} error=${error}`
            );
            throw new ConflictException({
                errorCode: 'AUTH-409',
                message: 'Registration failed',
            });
        }
    }

    /** 16 characters, every class present, no look-alikes (0/O, 1/l/I). */
    private generateDefaultPassword(): string {
        const upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
        const lower = 'abcdefghijkmnopqrstuvwxyz';
        const digits = '23456789';
        const symbols = '!@#$%^&*?';
        const all = upper + lower + digits + symbols;
        const pick = (set: string) => set[randomInt(set.length)];

        const chars = [pick(upper), pick(lower), pick(digits), pick(symbols)];
        while (chars.length < 16) {
            chars.push(pick(all));
        }
        for (let i = chars.length - 1; i > 0; i--) {
            const j = randomInt(i + 1);
            [chars[i], chars[j]] = [chars[j], chars[i]];
        }
        return chars.join('');
    }

    /**
     * Lets a signed-in user replace their password. Signs out their other refresh tokens, and
     * clears the "must change" flag. A wrong current password is a 400, not a 401: a 401 would
     * make the client think the session itself had died.
     */
    async changePassword(userId: string, currentPassword: string, newPassword: string): Promise<void> {
        const user = await this.userRepo.findById(userId);
        if (!user) {
            throw new UnauthorizedException({ errorCode: 'AUTH-401', message: 'Unauthorised' });
        }

        if (this.isThrottled(user.username)) {
            throw new BadRequestException({ errorCode: 'AUTH-429', message: 'Too many attempts. Try again later.' });
        }

        const currentOk = await argon2.verify(user.passwordHash, currentPassword);
        if (!currentOk) {
            this.recordFailedAttempt(user.username);
            this.logger.warn(`[AUTH_EVENT] change_password_wrong_current userId=${user.id}`);
            throw new BadRequestException({ errorCode: 'AUTH-400', message: 'Current password is incorrect' });
        }

        if (currentPassword === newPassword || await argon2.verify(user.passwordHash, newPassword)) {
            throw new BadRequestException({ errorCode: 'AUTH-400', message: 'New password must be different' });
        }

        user.passwordHash = await argon2.hash(newPassword, {
            type: argon2.argon2id,
            memoryCost: 65536,
            timeCost: 3,
            parallelism: 4,
        });
        user.roles = (user.roles ?? []).filter((role) => role !== MUST_CHANGE_PASSWORD_ROLE);
        await this.userRepo.create(user);
        await this.refreshTokenRepo.revokeAllUserTokens(user.id);
        this.clearLoginAttempts(user.username);

        this.logger.log(`[AUTH_EVENT] change_password_succeeded userId=${user.id}`);
    }

    async refresh(refreshToken: string) {
        this.logger.log(
            `[AUTH_EVENT] refresh_attempt_started`
        );

        // Find and verify the refresh token
        const tokenRecord = await this.refreshTokenRepo.verifyAndFindToken(refreshToken);
        
        if (!tokenRecord) {
            this.logger.warn(
                `[AUTH_EVENT] refresh_rejected_token_not_found`
            );
            throw new UnauthorizedException({
                errorCode: 'AUTH-401',
                message: 'Unauthorised',
            });
        }

        // Check if token has been consumed
        if (tokenRecord.consumedAt) {
            this.logger.error(
                `[AUTH_EVENT] refresh_rejected_token_reuse userId=${tokenRecord.userId}`
            );
            // Token reuse detected - revoke all tokens for this user
            await this.refreshTokenRepo.revokeAllUserTokens(tokenRecord.userId);
            throw new UnauthorizedException({
                errorCode: 'AUTH-401',
                message: 'Unauthorised',
            });
        }

        // Check if token has been revoked
        if (tokenRecord.revokedAt) {
            this.logger.warn(
                `[AUTH_EVENT] refresh_rejected_token_revoked userId=${tokenRecord.userId}`
            );
            throw new UnauthorizedException({
                errorCode: 'AUTH-401',
                message: 'Unauthorised',
            });
        }

        // Check if token has expired
        if (new Date() > tokenRecord.expiresAt) {
            this.logger.warn(
                `[AUTH_EVENT] refresh_rejected_token_expired userId=${tokenRecord.userId}`
            );
            throw new UnauthorizedException({
                errorCode: 'AUTH-401',
                message: 'Unauthorised',
            });
        }

        // Mark the old token as consumed
        await this.refreshTokenRepo.markAsConsumed(tokenRecord.id);

        // Get the user details
        const user = await this.userRepo.findById(tokenRecord.userId);
        if (!user) {
            this.logger.error(
                `[AUTH_EVENT] refresh_failed_user_not_found userId=${tokenRecord.userId}`
            );
            throw new UnauthorizedException({
                errorCode: 'AUTH-401',
                message: 'Unauthorised',
            });
        }

        // Get the current active session ID for this user
        // (session ID doesn't change on refresh, only on login)
        const sessionId = this.activeSessionIds.get(user.id);
        if (!sessionId) {
            this.logger.error(
                `[AUTH_EVENT] refresh_failed_no_session userId=${user.id}`
            );
            throw new UnauthorizedException({
                errorCode: 'AUTH-401',
                message: 'Unauthorised',
            });
        }

        // Generate new tokens with same session ID
        const accessToken = this.jwtService.generateAccessToken(
            user.id,
            user.accountId,
            user.roles,
            sessionId
        );

        const newRefreshToken = await this.refreshService.create(user.id);

        this.logger.log(
            `[AUTH_EVENT] refresh_succeeded userId=${user.id} accountId=${user.accountId} sessionId=${sessionId}`
        );

        return {
            accessToken,
            refreshToken: newRefreshToken,
            tokenType: 'Bearer',
            expiresIn: 900,
        };
    }

    async getCurrentUser(userId: string) {
        this.logger.log(
            `[AUTH_EVENT] get_current_user_started userId=${userId}`
        );

        const user = await this.userRepo.findById(userId);
        if (!user) {
            this.logger.warn(
                `[AUTH_EVENT] get_current_user_rejected_user_not_found userId=${userId}`
            );
            throw new UnauthorizedException({
                errorCode: 'AUTH-401',
                message: 'Unauthorised',
            });
        }

        this.logger.log(
            `[AUTH_EVENT] get_current_user_succeeded userId=${userId}`
        );

        return {
            id: user.id,
            username: user.username,
            accountId: user.accountId,
            roles: user.roles,
            createdOn: new Date().toISOString(),
        };
    }

}