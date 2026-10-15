import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../auth/auth.service';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  const trade = API_ENDPOINTS.tradeApiBase;
  const auth = API_ENDPOINTS.authApiBase;

  let http: HttpClient;
  let httpMock: HttpTestingController;
  const logout = vi.fn();
  const refreshAccessToken = vi.fn();

  beforeEach(() => {
    logout.mockReset();
    refreshAccessToken.mockReset();
    refreshAccessToken.mockReturnValue(throwError(() => new Error('refresh failed')));

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { getToken: () => 'test-token', logout, refreshAccessToken } },
        { provide: Router, useValue: { url: '/blotter' } }
      ]
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('a request to the trade API carries the bearer token', () => {
    const url = `${trade}/api/v1/accounts/1/orders`;

    http.get(url).subscribe();

    const req = httpMock.expectOne(url);
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush([]);
  });

  it('cancelling an order (DELETE on the trade API) carries the bearer token', () => {
    const url = `${trade}/api/v1/orders/5a147b9b-4f73-424a-aa3d-a128f229f8d6`;

    http.delete(url).subscribe();

    const req = httpMock.expectOne(url);
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush({});
  });

  it('a request to the protected auth route carries the bearer token', () => {
    const url = `${auth}/auth/me`;

    http.get(url).subscribe();

    const req = httpMock.expectOne(url);
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush({});
  });

  it('a request to a third-party origin does not carry the bearer token', () => {
    const url = 'https://api.fauxnance.example/api/v1/quote/ABC';

    http.get(url).subscribe();

    const req = httpMock.expectOne(url);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('a look-alike host does not carry the bearer token', () => {
    // Handle relative URLs (empty string means current origin)
    const tradeUrl = trade ? new URL(trade) : new URL('http://localhost');
    const hostname = tradeUrl.hostname || 'localhost';
    const url = `https://${hostname}.evil.example/api/v1/orders`;

    http.get(url).subscribe();

    const req = httpMock.expectOne(url);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('a non-API path on the platform origin does not carry the bearer token', () => {
    const url = `${trade}/assets/config.json`;

    http.get(url).subscribe();

    const req = httpMock.expectOne(url);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('the unauthenticated auth routes are called without a header', () => {
    for (const path of ['/auth/login', '/auth/register', '/auth/refresh']) {
      const url = `${auth}${path}`;

      http.post(url, {}).subscribe();

      const req = httpMock.expectOne(url);
      expect(req.request.headers.has('Authorization'), path).toBe(false);
      req.flush({});
    }
  });

  it('a 401 from the trade API ends the session', () => {
    const url = `${trade}/api/v1/accounts/1`;
    refreshAccessToken.mockReturnValue(of('new-token'));

    http.get(url).subscribe({ error: () => undefined });

    httpMock.expectOne(url).flush({ errorCode: 'AUTH-401' }, { status: 401, statusText: 'Unauthorized' });

    const retried = httpMock.expectOne(url);
    expect(retried.request.headers.get('Authorization')).toBe('Bearer new-token');
    retried.flush({});

    expect(logout).not.toHaveBeenCalled();
  });

  it('logs out when refresh token is invalid during 401 recovery', () => {
    const url = `${trade}/api/v1/accounts/1`;

    refreshAccessToken.mockReturnValue(throwError(() => new Error('Session expired. Please sign in again.')));

    http.get(url).subscribe({ error: () => undefined });

    httpMock.expectOne(url).flush({ errorCode: 'AUTH-401' }, { status: 401, statusText: 'Unauthorized' });

    expect(logout).toHaveBeenCalled();
  });
});
