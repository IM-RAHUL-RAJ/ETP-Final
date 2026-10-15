import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';
import { AuthService, LoginRequest, LoginResponse } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: { navigate: ReturnType<typeof vi.fn>; navigateByUrl: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    router = {
      navigate: vi.fn(),
      navigateByUrl: vi.fn()
    };

    localStorage.clear();
    sessionStorage.clear();

    // Most tests post the plain body. The encrypted path has its own test below.
    vi.stubGlobal('crypto', { ...globalThis.crypto, subtle: undefined });

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthService,
        { provide: Router, useValue: router }
      ]
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    httpMock.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  describe('Login', () => {
    it('should login with valid credentials', async () => {
      const credentials: LoginRequest = {
        username: 'testuser',
        password: 'password123'
      };

      const response: LoginResponse = {
        token: 'test-token-123',
        user: {
          id: '1',
          username: 'testuser',
          email: 'test@example.com'
        }
      };

      service.login(credentials).subscribe({
        next: (result) => {
          expect(result.token).toBe('test-token-123');
          expect(service.getToken()).toBe('test-token-123');
          expect(service.isAuthenticated()).toBe(true);
        }
      });

      const req = httpMock.expectOne('http://localhost:3000/auth/login');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(credentials);
      req.flush(response);
    });

    it('should handle invalid credentials error', async () => {
      const credentials: LoginRequest = {
        username: 'testuser',
        password: 'wrongpassword'
      };

      service.login(credentials).subscribe({
        error: (error) => {
          expect(error.message).toBe('Invalid username or password. Please try again.');
        }
      });

      const req = httpMock.expectOne('http://localhost:3000/auth/login');
      req.flush({ message: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });
    });

    it('should handle network error', async () => {
      const credentials: LoginRequest = {
        username: 'testuser',
        password: 'password123'
      };

      service.login(credentials).subscribe({
        error: (error) => {
          expect(error.message).toContain('timed out or network failed');
        }
      });

      const req = httpMock.expectOne('http://localhost:3000/auth/login');
      req.error(new ErrorEvent('Network error'));
    });

    it('should handle server error', async () => {
      const credentials: LoginRequest = {
        username: 'testuser',
        password: 'password123'
      };

      service.login(credentials).subscribe({
        error: (error) => {
          expect(error.message).toContain('temporarily unavailable');
        }
      });

      const req = httpMock.expectOne('http://localhost:3000/auth/login');
      req.flush({ message: 'Server error' }, { status: 500, statusText: 'Internal Server Error' });

    });

    it('should show throttle message on 429', async () => {
      const credentials: LoginRequest = {
        username: 'testuser',
        password: 'password123'
      };

      service.login(credentials).subscribe({
        error: (error) => {
          expect(error.message).toContain('Too many sign-in attempts');
        }
      });

      const req = httpMock.expectOne('http://localhost:3000/auth/login');
      req.flush({ message: 'Too many requests' }, { status: 429, statusText: 'Too Many Requests' });
    });
  });

  describe('Token Management', () => {
    it('should store token in memory', () => {
      const credentials: LoginRequest = {
        username: 'testuser',
        password: 'password123'
      };

      const response: LoginResponse = {
        token: 'test-token-123',
        user: {
          id: '1',
          username: 'testuser',
          email: 'test@example.com'
        }
      };

      service.login(credentials).subscribe();

      const req = httpMock.expectOne('http://localhost:3000/auth/login');
      req.flush(response);

      expect(service.getToken()).toBe('test-token-123');
    });

    it('should return null when no token is stored', () => {
      expect(service.getToken()).toBeNull();
    });

    it('should track authentication state', () => {
      expect(service.isAuthenticated()).toBe(false);

      const credentials: LoginRequest = {
        username: 'testuser',
        password: 'password123'
      };

      const response: LoginResponse = {
        token: 'test-token-123',
        user: {
          id: '1',
          username: 'testuser',
          email: 'test@example.com'
        }
      };

      service.login(credentials).subscribe();

      const req = httpMock.expectOne('http://localhost:3000/auth/login');
      req.flush(response);

      expect(service.isAuthenticated()).toBe(true);
    });
  });

  describe('Logout', () => {
    it('should clear token on logout', () => {
      const credentials: LoginRequest = {
        username: 'testuser',
        password: 'password123'
      };

      const response: LoginResponse = {
        token: 'test-token-123',
        user: {
          id: '1',
          username: 'testuser',
          email: 'test@example.com'
        }
      };

      service.login(credentials).subscribe();

      const req = httpMock.expectOne('http://localhost:3000/auth/login');
      req.flush(response);

      expect(service.getToken()).toBe('test-token-123');
      expect(service.isAuthenticated()).toBe(true);

      service.logout();

      expect(service.getToken()).toBeNull();
      expect(service.isAuthenticated()).toBe(false);
    });

    it('should navigate to landing page on logout', () => {
      service.logout();
      expect(router.navigateByUrl).toHaveBeenCalledWith('/', { replaceUrl: true });
    });
  });

  describe('Security', () => {
    it('should not log credentials or tokens', async () => {
      const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);

      const credentials: LoginRequest = {
        username: 'testuser',
        password: 'password123'
      };

      service.login(credentials).subscribe({
        error: () => {
          const calls = errorSpy.mock.calls.map((args) => args.join(' ')).join(' ');
          expect(calls).not.toContain('password');
          expect(calls).not.toContain('token');
          errorSpy.mockRestore();
        }
      });

      const req = httpMock.expectOne('http://localhost:3000/auth/login');
      req.flush({ message: 'Error' }, { status: 401, statusText: 'Unauthorized' });
    });

    it('should persist token to sessionStorage and not localStorage', () => {
      const setItemSpy = vi.spyOn(Storage.prototype, 'setItem');

      const credentials: LoginRequest = {
        username: 'testuser',
        password: 'password123'
      };

      const response: LoginResponse = {
        token: 'test-token-123',
        user: {
          id: '1',
          username: 'testuser',
          email: 'test@example.com'
        }
      };

      service.login(credentials).subscribe();

      const req = httpMock.expectOne('http://localhost:3000/auth/login');
      req.flush(response);

      expect(setItemSpy).toHaveBeenCalledWith('access_token', 'test-token-123');
      expect(sessionStorage.getItem('access_token')).toBe('test-token-123');
      expect(localStorage.getItem('access_token')).toBeNull();
      setItemSpy.mockRestore();
    });

    it('should store refresh token in sessionStorage only', () => {
      const credentials: LoginRequest = {
        username: 'testuser',
        password: 'password123'
      };

      service.login(credentials).subscribe();

      const req = httpMock.expectOne('http://localhost:3000/auth/login');
      req.flush({
        accessToken: 'access-token',
        refreshToken: 'refresh-token',
        tokenType: 'Bearer',
        expiresIn: 900
      });

      expect(sessionStorage.getItem('refresh_token')).toBe('refresh-token');
      expect(localStorage.getItem('refresh_token')).toBeNull();
    });
  });
  describe('Token tampering', () => {
    function signIn(token: string) {
      service.login({ username: 'testuser', password: 'password123' }).subscribe();
      httpMock.expectOne('http://localhost:3000/auth/login').flush({ token });
    }

    it('signs out with an Unauthorised message when the stored token is edited', () => {
      signIn('aaa.bbb.ccc');
      expect(service.verifyStorageIntegrity()).toBe(true);

      sessionStorage.setItem('access_token', 'aaa.bbb.tampered');

      expect(service.verifyStorageIntegrity()).toBe(false);
      expect(service.isAuthenticated()).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/sign-in'], {
        queryParams: { sessionMessage: expect.stringContaining('Unauthorised') },
        replaceUrl: true
      });
    });

    it('signs out when the stored token is removed', () => {
      signIn('aaa.bbb.ccc');
      sessionStorage.removeItem('access_token');

      expect(service.verifyStorageIntegrity()).toBe(false);
      expect(service.isAuthenticated()).toBe(false);
    });

    it('does nothing when nobody is signed in', () => {
      expect(service.verifyStorageIntegrity()).toBe(true);
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  describe('Encrypted login', () => {
    it('sends an opaque payload that contains neither username nor password', async () => {
      vi.unstubAllGlobals();
      if (!globalThis.crypto?.subtle) {
        return; // No WebCrypto in this environment: the plain-body fallback is covered above.
      }
      const pair = await crypto.subtle.generateKey(
        { name: 'RSA-OAEP', modulusLength: 3072, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
        true,
        ['encrypt', 'decrypt']
      );
      const spki = new Uint8Array(await crypto.subtle.exportKey('spki', pair.publicKey));
      const publicKey = btoa(String.fromCharCode(...spki));

      service.login({ username: 'priya.menon', password: 'S3cret-pass-phrase' }).subscribe();

      httpMock.expectOne('http://localhost:3000/auth/public-key').flush({ publicKey });
      const req = await vi.waitFor(() => httpMock.expectOne('http://localhost:3000/auth/login'));

      const raw = JSON.stringify(req.request.body);
      expect(Object.keys(req.request.body)).toEqual(['payload']);
      expect(raw).not.toContain('priya.menon');
      expect(raw).not.toContain('S3cret-pass-phrase');

      // The service side decrypts with its private key and gets the credentials back.
      const cipher = Uint8Array.from(atob(req.request.body.payload), (char) => char.charCodeAt(0));
      const plain = JSON.parse(
        new TextDecoder().decode(await crypto.subtle.decrypt({ name: 'RSA-OAEP' }, pair.privateKey, cipher))
      );
      expect(plain).toEqual({ u: 'priya.menon', p: 'S3cret-pass-phrase', t: expect.any(Number) });

      req.flush({ token: 'a.b.c' });
    });
  });

  describe('Roles, default password and change password', () => {
    const tokenWith = (claims: object) => `h.${btoa(JSON.stringify(claims))}.s`;

    it('reads the admin role from the token and normalises it', () => {
      service.login({ username: 'boss', password: 'a-long-enough-password' }).subscribe();
      httpMock.expectOne('http://localhost:3000/auth/login').flush({ token: tokenWith({ roles: ['ROLE_admin'], accountId: 1 }) });

      expect(service.isAdmin()).toBe(true);
    });

    it('a customer is not an admin', () => {
      service.login({ username: 'cust', password: 'a-long-enough-password' }).subscribe();
      httpMock.expectOne('http://localhost:3000/auth/login').flush({ token: tokenWith({ roles: ['CUSTOMER'], accountId: 1 }) });

      expect(service.isAdmin()).toBe(false);
    });

    it('remembers that the login used the emailed default password, until it is changed', () => {
      service.login({ username: 'cust', password: 'Temp-Password-123' }).subscribe();
      httpMock
        .expectOne('http://localhost:3000/auth/login')
        .flush({ token: tokenWith({ roles: ['CUSTOMER'], accountId: 1 }), mustChangePassword: true });
      expect(service.mustChangePassword()).toBe(true);

      service.changePassword('Temp-Password-123', 'A-brand-new-password').subscribe();
      const request = httpMock.expectOne('http://localhost:3000/auth/change-password');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ currentPassword: 'Temp-Password-123', newPassword: 'A-brand-new-password' });
      request.flush(null);

      expect(service.mustChangePassword()).toBe(false);
    });

    it('registers a login without a password and without any token, as the open endpoint allows', () => {
      service.registerLogin('priya.menon', 'priya.menon@example.com', 9).subscribe((result) => {
        expect(result.emailSent).toBe(true);
      });

      const request = httpMock.expectOne('http://localhost:3000/auth/register');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ username: 'priya.menon', email: 'priya.menon@example.com', accountId: 9 });
      expect(JSON.stringify(request.request.body)).not.toMatch(/password/i);
      request.flush({ id: 'u-1', username: 'priya.menon', accountId: 9, roles: ['CUSTOMER'], emailSent: true });
    });

    it('forgets the flag on logout', () => {
      service.login({ username: 'cust', password: 'Temp-Password-123' }).subscribe();
      httpMock
        .expectOne('http://localhost:3000/auth/login')
        .flush({ token: tokenWith({ roles: ['CUSTOMER'], accountId: 1 }), mustChangePassword: true });

      service.logout();

      expect(service.mustChangePassword()).toBe(false);
    });
  });
});
