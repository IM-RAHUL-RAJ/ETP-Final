import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { adminGuard, authGuard, customerGuard } from './auth.guard';
import { AuthService } from './auth.service';
import { of, throwError, firstValueFrom } from 'rxjs';

describe('authGuard', () => {
  let authService: any;
  let router: Router;
  let routerState: Partial<RouterStateSnapshot>;
  let route: Partial<ActivatedRouteSnapshot>;

  beforeEach(() => {
    authService = {
      isAuthenticated: vi.fn().mockReturnValue(false),
      validateSession: vi.fn().mockReturnValue(of(undefined)),
      verifyStorageIntegrity: vi.fn().mockReturnValue(true),
      logout: vi.fn(),
      shouldForceLandingAfterLogout: vi.fn().mockReturnValue(false),
      clearForceLandingMarker: vi.fn()
    };

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService }
      ]
    });

    router = TestBed.inject(Router);
    routerState = { url: '/dashboard' } as RouterStateSnapshot;
    route = {} as ActivatedRouteSnapshot;
  });

  it('should allow access when authenticated', async () => {
    authService.isAuthenticated.mockReturnValue(true);

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() =>
        authGuard(route as ActivatedRouteSnapshot, routerState as RouterStateSnapshot) as any
      )
    );

    expect(result).toBe(true);
    expect(authService.validateSession).toHaveBeenCalled();
  });

  it('should redirect to landing when logout marker is set', async () => {
    authService.shouldForceLandingAfterLogout.mockReturnValue(true);
    authService.isAuthenticated.mockReturnValue(true);

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() =>
        authGuard(route as ActivatedRouteSnapshot, routerState as RouterStateSnapshot) as any
      )
    );

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/');
  });

  it('should redirect to sign-in when not authenticated', async () => {
    authService.isAuthenticated.mockReturnValue(false);
    routerState.url = '/dashboard';

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() =>
        authGuard(route as ActivatedRouteSnapshot, routerState as RouterStateSnapshot) as any
      )
    );

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toContain('/sign-in');
  });

  it('should include return URL in redirect', async () => {
    authService.isAuthenticated.mockReturnValue(false);
    routerState.url = '/dashboard?tab=overview';

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() =>
        authGuard(route as ActivatedRouteSnapshot, routerState as RouterStateSnapshot) as any
      )
    );

    expect(router.serializeUrl(result as UrlTree)).toBe('/sign-in?returnUrl=%2Fdashboard%3Ftab%3Doverview');
  });

  it('should prevent open redirect attacks', async () => {
    authService.isAuthenticated.mockReturnValue(false);
    routerState.url = 'https://evil.com/dashboard';

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() =>
        authGuard(route as ActivatedRouteSnapshot, routerState as RouterStateSnapshot) as any
      )
    );

    // URL API normalizes this input into an internal path, still same-origin and safe.
    expect(router.serializeUrl(result as UrlTree)).toBe('/sign-in?returnUrl=%2Fdashboard');
  });

  it('should prevent double-slash attack vectors', async () => {
    authService.isAuthenticated.mockReturnValue(false);
    routerState.url = '//evil.com/dashboard';

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() =>
        authGuard(route as ActivatedRouteSnapshot, routerState as RouterStateSnapshot) as any
      )
    );

    // URL API normalizes //evil.com/dashboard to /dashboard on the same origin
    // The guard then includes it as returnUrl since it's a valid path
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/sign-in?returnUrl=%2Fdashboard');
  });

  it('should handle session validation failure', async () => {
    authService.isAuthenticated.mockReturnValue(true);
    authService.validateSession.mockReturnValue(throwError(() => new Error('Session expired')));

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() =>
        authGuard(route as ActivatedRouteSnapshot, routerState as RouterStateSnapshot) as any
      )
    );

    // logout() itself sends the user to sign-in with the reason.
    expect(result).toBe(false);
    expect(authService.logout).toHaveBeenCalledWith(expect.stringContaining('Unauthorised'));
  });

  it('should block and not call the server when the stored token was tampered with', async () => {
    authService.isAuthenticated.mockReturnValue(true);
    authService.verifyStorageIntegrity.mockReturnValue(false);

    const result = await firstValueFrom(
      TestBed.runInInjectionContext(() =>
        authGuard(route as ActivatedRouteSnapshot, routerState as RouterStateSnapshot) as any
      )
    );

    expect(result).toBe(false);
    expect(authService.validateSession).not.toHaveBeenCalled();
  });
});

describe('adminGuard and customerGuard', () => {
  let authService: any;
  let router: Router;

  beforeEach(() => {
    authService = { isAuthenticated: vi.fn().mockReturnValue(true), isAdmin: vi.fn().mockReturnValue(false) };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: authService }]
    });
    router = TestBed.inject(Router);
  });

  const run = (guard: any) => TestBed.runInInjectionContext(() => guard({} as any, {} as any));

  it('lets an admin into the admin dashboard', () => {
    authService.isAdmin.mockReturnValue(true);
    expect(run(adminGuard)).toBe(true);
  });

  it('sends a customer who asks for the admin dashboard to their own dashboard', () => {
    const result = run(adminGuard);
    expect(router.serializeUrl(result)).toBe('/dashboard');
  });

  it('sends someone who is not signed in to sign-in', () => {
    authService.isAuthenticated.mockReturnValue(false);
    expect(router.serializeUrl(run(adminGuard))).toBe('/sign-in');
  });

  it('sends an admin who asks for a customer page to the admin dashboard', () => {
    authService.isAdmin.mockReturnValue(true);
    expect(router.serializeUrl(run(customerGuard))).toBe('/admin');
  });

  it('lets a customer into the customer pages', () => {
    expect(run(customerGuard)).toBe(true);
  });
});
