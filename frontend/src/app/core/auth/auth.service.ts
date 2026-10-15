import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, TimeoutError, from, of, throwError } from 'rxjs';
import { catchError, map, switchMap, tap, timeout } from 'rxjs/operators';
import { API_ENDPOINTS } from '../config/api-endpoints';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  // Support both legacy `{ token, user }` and new auth-service `{ accessToken, refreshToken, tokenType, expiresIn }` shapes
  token?: string;
  user?: {
    id: string;
    username: string;
    email: string;
  };
  accessToken?: string;
  refreshToken?: string;
  tokenType?: string;
  expiresIn?: number;
  mustChangePassword?: boolean;
}

export interface LoginRegistration {
  id: string;
  username: string;
  accountId: number;
  roles: string[];
  emailSent: boolean;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface User {
  id?: string;
  username?: string;
  email?: string;
  accountId?: number;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private readonly storageKey = 'access_token';
  private readonly refreshStorageKey = 'refresh_token';
  private readonly userStorageKey = 'auth_user';
  private readonly sessionIdKey = 'session_id';
  private readonly mustChangeKey = 'must_change_password';
  private readonly authChangePasswordUrl = `${API_ENDPOINTS.authApiBase}/auth/change-password`;
  private readonly authRegisterUrl = `${API_ENDPOINTS.authApiBase}/auth/register`;
  private readonly forceLandingKey = 'force_landing_after_logout';
  private readonly authLoginUrl = `${API_ENDPOINTS.authApiBase}/auth/login`;
  private readonly authSessionValidateUrl = `${API_ENDPOINTS.authApiBase}/auth/session/validate`;
  private readonly authRefreshUrl = `${API_ENDPOINTS.authApiBase}/auth/refresh`;
  private readonly authPublicKeyUrl = `${API_ENDPOINTS.authApiBase}/auth/public-key`;
  private publicKeyPromise: Promise<CryptoKey | null> | null = null;
  private readonly requestTimeoutMs = 12000;

  //Token is mirrored in memory and sessionStorage.

  private tokenSignal = signal<string | null>(this.readTokenFromStorage());

  // Refresh token is kept in memory and sessionStorage only.
   
  private refreshTokenSignal = signal<string | null>(this.readRefreshTokenFromStorage());

  // User information from login response
   
  private userSignal = signal<User | null>(this.readUserFromStorage());

  // Session ID from JWT token (for multi-device logout detection)

  private sessionIdSignal = signal<string | null>(this.readSessionIdFromStorage());

  //Computed signal to track authentication state
   
  isAuthenticated = computed(() => this.tokenSignal() !== null);

  /** True while the account is still on the default password that was emailed to the customer. */
  private mustChangeSignal = signal<boolean>(sessionStorage.getItem(this.mustChangeKey) === '1');
  readonly mustChangePassword = this.mustChangeSignal.asReadonly();

  /** Roles from the verified-by-the-server token, normalised ("ROLE_ADMIN" and "admin" both count). */
  readonly roles = computed<string[]>(() => {
    const token = this.tokenSignal();
    const claims = token ? this.decodeToken(token) : null;
    const roles: unknown = claims?.roles ?? claims?.sub?.roles ?? [];
    return Array.isArray(roles)
      ? roles.filter((r): r is string => typeof r === 'string').map((r) => r.trim().toUpperCase().replace(/^ROLE_/, ''))
      : [];
  });

  /** Only decides what the UI shows. The services check the role on every request. */
  readonly isAdmin = computed(() => this.roles().includes('ADMIN'));

  // Get current user
  user = this.userSignal.asReadonly();

  // Get current token (memory-only, cleared on logout)
  getToken(): string | null {
    return this.tokenSignal();
  }

  /**
   * Get current session ID
   */
  getSessionId(): string | null {
    return this.sessionIdSignal();
  }

  // Read only decoded claims from current access token.
  getTokenClaims(): any | null {
    const token = this.tokenSignal();
    return token ? this.decodeToken(token) : null;
  }

  // Store token in memory and sessionStorage.
  private setToken(token: string): void {
    this.tokenSignal.set(token);
    sessionStorage.setItem(this.storageKey, token);
  }

  // Store refresh token in memory and sessionStorage.
  private setRefreshToken(token: string): void {
    this.refreshTokenSignal.set(token);
    sessionStorage.setItem(this.refreshStorageKey, token);
  }

  // Store session ID in memory and sessionStorage.
  private setSessionId(sessionId: string | null): void {
    this.sessionIdSignal.set(sessionId);
    if (sessionId) {
      sessionStorage.setItem(this.sessionIdKey, sessionId);
    } else {
      sessionStorage.removeItem(this.sessionIdKey);
    }
  }

  private setMustChange(value: boolean): void {
    this.mustChangeSignal.set(value);
    if (value) {
      sessionStorage.setItem(this.mustChangeKey, '1');
    } else {
      sessionStorage.removeItem(this.mustChangeKey);
    }
  }

  // Creates the login for an account
  registerLogin(username: string, email: string, accountId: number): Observable<LoginRegistration> {
    return this.http
      .post<LoginRegistration>(this.authRegisterUrl, { username, email, accountId })
      .pipe(timeout(this.requestTimeoutMs * 2));
  }

  // Replace the password. 

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(this.authChangePasswordUrl, { currentPassword, newPassword }).pipe(
      timeout(this.requestTimeoutMs),
      tap(() => this.setMustChange(false))
    );
  }

  //Store user in memory and sessionStorage.
   
  private setUser(user: User | null): void {
    this.userSignal.set(user);
    if (user) {
      sessionStorage.setItem(this.userStorageKey, JSON.stringify(user));
    } else {
      sessionStorage.removeItem(this.userStorageKey);
    }
  }

  // Restore token from sessionStorage. It must match the stored copy at all times, see verifyStorageIntegrity.
  private readTokenFromStorage(): string | null {
    const token = sessionStorage.getItem(this.storageKey);
    return token && token.trim().length > 0 ? token : null;
  }

  // Restore refresh token from sessionStorage first, then legacy localStorage.
  private readRefreshTokenFromStorage(): string | null {
    const token = sessionStorage.getItem(this.refreshStorageKey) ?? localStorage.getItem(this.refreshStorageKey);
    return token && token.trim().length > 0 ? token : null;
  }

  // Restore session ID from sessionStorage first, then legacy localStorage.
  private readSessionIdFromStorage(): string | null {
    const sessionId = sessionStorage.getItem(this.sessionIdKey) ?? localStorage.getItem(this.sessionIdKey);
    return sessionId && sessionId.trim().length > 0 ? sessionId : null;
  }

  // Restore user from sessionStorage first, then legacy localStorage.
  private readUserFromStorage(): User | null {
    try {
      const user = sessionStorage.getItem(this.userStorageKey) ?? localStorage.getItem(this.userStorageKey);
      return user ? JSON.parse(user) : null;
    } catch {
      return null;
    }
  }

  // Decode JWT and extract claims  
  private decodeToken(token: string): any {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      
      const decoded = atob(parts[1]);
      return JSON.parse(decoded);
    } catch {
      return null;
    }
  }

  // Build the login body, encrypting credentials if possible.
  private async buildLoginBody(credentials: LoginRequest): Promise<LoginRequest | { payload: string }> {
    try {
      const key = await this.loadPublicKey();
      if (!key) {
        return credentials;
      }
      const plain = new TextEncoder().encode(
        JSON.stringify({ u: credentials.username, p: credentials.password, t: Date.now() })
      );
      const cipher = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, key, plain);
      return { payload: this.toBase64(new Uint8Array(cipher)) };
    } catch {
      return credentials;
    }
  }

  private canEncryptLogin(): boolean {
    return typeof crypto !== 'undefined' && !!crypto.subtle;
  }

  private loadPublicKey(): Promise<CryptoKey | null> {
    // Fetched fresh per page load: the service regenerates its key pair on every start.
    this.publicKeyPromise ??= (async () => {
      const response = await new Promise<{ publicKey: string }>((resolve, reject) =>
        this.http.get<{ publicKey: string }>(this.authPublicKeyUrl).pipe(timeout(5000)).subscribe({
          next: resolve,
          error: reject
        })
      );
      const der = Uint8Array.from(atob(response.publicKey), (char) => char.charCodeAt(0));
      return crypto.subtle.importKey('spki', der, { name: 'RSA-OAEP', hash: 'SHA-256' }, false, ['encrypt']);
    })().catch(() => {
      this.publicKeyPromise = null;
      return null;
    });
    return this.publicKeyPromise;
  }

  private toBase64(bytes: Uint8Array): string {
    let binary = '';
    bytes.forEach((byte) => (binary += String.fromCharCode(byte)));
    return btoa(binary);
  }

  
  // Sign in with username and password against the Auth service
  login(credentials: LoginRequest): Observable<LoginResponse> {
    const body$ = this.canEncryptLogin() ? from(this.buildLoginBody(credentials)) : of(credentials);
    return body$
      .pipe(
        switchMap(body => this.http.post<LoginResponse>(this.authLoginUrl, body)),
        timeout(this.requestTimeoutMs),
        tap(response => {
          this.clearForceLandingMarker();
          this.setMustChange((response as LoginResponse).mustChangePassword === true);

          // Accept either legacy `token` or `accessToken` returned by auth-service.
          const token = (response as any).token ?? (response as any).accessToken;
          const refreshToken = (response as any).refreshToken;
          if (token) {
            this.setToken(token);

            if (refreshToken) {
              this.setRefreshToken(refreshToken);
            }
            
            // Extract claims from JWT
            const claims = this.decodeToken(token);
            if (claims) {
              // Store sessionId for multi-device logout detection
              if (claims.sessionId) {
                this.setSessionId(claims.sessionId);
              }
            }
            
            // Extract user info from response or decode from JWT
            let user: User | null = null;
            
            if (response.user) {
              // Use user from response if provided
              user = {
                id: response.user.id,
                username: response.user.username,
                email: response.user.email
              };
            }
            
            // Also try to extract accountId from JWT claims
            if (claims) {
              if (!user) {
                user = {};
              }
              user.accountId = claims.accountId || claims.sub?.accountId;
              user.username = user.username || claims.sub?.username || claims.username;
            }
            
            if (user) {
              this.setUser(user);
            }
          }
        }),
        catchError((error: unknown) => {
          // Never log the credentials or token
          if (error instanceof HttpErrorResponse) {
            console.error('[Auth Error]', error.status, error.statusText);
            return throwError(() => this.handleLoginError(error));
          }
          if (error instanceof TimeoutError) {
            return throwError(() => new Error('Login request timed out. Please try again.'));
          }
          return throwError(() => new Error('Network error. Please check your connection and try again.'));
        })
      );
  }

  // Refresh access token using refresh token. Refresh failure means session is no longer valid.
  
  refreshAccessToken(): Observable<string> {
    const refreshToken = this.refreshTokenSignal();
    if (!refreshToken) {
      return throwError(() => new Error('Refresh token is missing. Please sign in again.'));
    }

    const body: RefreshRequest = { refreshToken };

    return this.http.post<LoginResponse>(this.authRefreshUrl, body).pipe(
      timeout(this.requestTimeoutMs),
      map((response) => {
        const nextAccessToken = response.accessToken ?? response.token;
        const nextRefreshToken = response.refreshToken;
        if (!nextAccessToken || !nextRefreshToken) {
          throw new Error('Invalid refresh response. Please sign in again.');
        }

        this.setToken(nextAccessToken);
        this.setRefreshToken(nextRefreshToken);

        const claims = this.decodeToken(nextAccessToken);
        if (claims?.sessionId) {
          this.setSessionId(claims.sessionId);
        }

        return nextAccessToken;
      }),
      catchError((error: unknown) => {
        if (error instanceof TimeoutError) {
          return throwError(() => new Error('Token refresh timed out. Please sign in again.'));
        }
        if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
          return throwError(() => new Error('Session expired. Please sign in again.'));
        }
        return throwError(() => error);
      })
    );
  }

  // Sign out and clear session. Clears token immediately and navigates to landing page.
  logout(reason?: string): void {
    // Immediately clear token, user, and session ID from memory and local storage
    this.tokenSignal.set(null);
    this.refreshTokenSignal.set(null);
    this.userSignal.set(null);
    this.sessionIdSignal.set(null);
    sessionStorage.setItem(this.forceLandingKey, '1');
    sessionStorage.removeItem(this.storageKey);
    sessionStorage.removeItem(this.refreshStorageKey);
    sessionStorage.removeItem(this.userStorageKey);
    sessionStorage.removeItem(this.sessionIdKey);
    sessionStorage.removeItem(this.mustChangeKey);
    this.mustChangeSignal.set(false);
    localStorage.removeItem(this.storageKey);
    localStorage.removeItem(this.refreshStorageKey);
    localStorage.removeItem(this.userStorageKey);
    localStorage.removeItem(this.sessionIdKey);
    // A half-filled order belongs to the signed-in user, not to whoever signs in next.
    sessionStorage.removeItem('order_ticket_draft');
    // The account a customer opened on belongs to that sign-in, not to whoever signs in next.
    sessionStorage.removeItem('active_account_id');

    if (reason) {
      // Forced sign-out (tampered or rejected token): say why, on the sign-in page.
      sessionStorage.removeItem(this.forceLandingKey);
      this.router.navigate(['/sign-in'], { queryParams: { sessionMessage: reason }, replaceUrl: true });
      return;
    }
    
    // Clear the landing flag after a brief delay so it only protects against immediate back button use
    setTimeout(() => this.clearForceLandingMarker(), 50);
    
    // Navigate to landing page
    this.router.navigateByUrl('/', { replaceUrl: true });
  }

  // If the stored copy was edited or removed (dev tools, another script) it no longer matches what this app issued: treat the session as unauthorised and send the user to sign in. 
  verifyStorageIntegrity(): boolean {
    const inMemory = this.tokenSignal();
    if (inMemory === null) {
      return true;
    }
    if (sessionStorage.getItem(this.storageKey) !== inMemory) {
      console.warn('[Auth] Stored access token changed outside the app; signing out.');
      this.logout('Unauthorised. Your session could not be verified, so you were signed out. Please sign in again.');
      return false;
    }
    return true;
  }

  shouldForceLandingAfterLogout(): boolean {
    return sessionStorage.getItem(this.forceLandingKey) === '1';
  }

  clearForceLandingMarker(): void {
    sessionStorage.removeItem(this.forceLandingKey);
  }

  // Validate if current session is still active on the server
  
  validateSession(): Observable<{isValid: boolean}> {
    return this.http.get<{isValid: boolean}>(this.authSessionValidateUrl)
      .pipe(
        timeout(this.requestTimeoutMs),
        catchError((error) => {
          // If we get a 401, the session was invalidated
          if (error instanceof HttpErrorResponse && error.status === 401) {
            return throwError(() => new Error('Session invalidated'));
          }
          if (error instanceof TimeoutError) {
            return throwError(() => new Error('Session validation timed out'));
          }
          return throwError(() => error);
        })
      );
  }

  // Handle login errors and return user-friendly messages  
  private handleLoginError(error: HttpErrorResponse): Error {
    let userMessage = 'An error occurred during sign-in. Please try again.';

    if (error.status === 401 || error.status === 400) {
      // Invalid credentials
      userMessage = 'Invalid username or password. Please try again.';
    } else if (error.status === 422) {
      userMessage = 'Please check your username and password format and try again.';
    } else if (error.status === 429) {
      userMessage = 'Too many sign-in attempts. Please wait a minute and try again.';
    } else if (error.status === 403) {
      userMessage = 'You are not allowed to perform this action.';
    } else if (error.status === 0) {
      // Network error
      userMessage = 'Login request timed out or network failed. Please try again.';
    } else if (error.status === 500 || error.status === 503) {
      // Server error
      userMessage = 'The authentication service is temporarily unavailable. Please try again later.';
    }

    return new Error(userMessage);
  }
}