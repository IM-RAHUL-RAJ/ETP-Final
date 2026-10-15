import { inject } from '@angular/core';
import { CanActivateFn, Router, ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { AuthService } from './auth.service';

//Auth Guard: Protects routes that require authentication
 
export const authGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
): Observable<boolean | UrlTree> => {
  const authService = inject(AuthService);
  const router = inject(Router);

  // After logout, keep user on landing even if browser back lands on a guarded URL.
  
  if (authService.shouldForceLandingAfterLogout()) {
    return of(router.createUrlTree(['/']));
  }

  // Check if user is authenticated
  if (!authService.isAuthenticated()) {
    // Extract return URL from current route
    const url = new URL(state.url, window.location.origin);
    const returnUrl = url.pathname + url.search;

    // Validate return URL to prevent open redirect attacks
    
    if (returnUrl.startsWith('/') && !returnUrl.startsWith('//')) {
      return of(router.createUrlTree(['/sign-in'], {
        queryParams: { returnUrl }
      }));
    }

    // Reject suspicious return URL, redirect to sign-in without it
    return of(router.createUrlTree(['/sign-in']));
  }

  // The stored token must still be the one this app issued.
  if (!authService.verifyStorageIntegrity()) {
    return of(false);
  }

  // User is authenticated, validate session with server
  // This detects when session was invalidated from another device
  return authService.validateSession().pipe(
    map(() => true),
    catchError(() => {
      // Session is invalid (logged out from another device)
      authService.logout('Unauthorised. Your session is no longer valid. Please sign in again.');
      return of(false);
    })
  );
};

//Admin Guard: the admin dashboard is for ADMIN tokens only.
export const adminGuard: CanActivateFn = (): boolean | UrlTree => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    return router.createUrlTree(['/sign-in']);
  }
  return authService.isAdmin() ? true : router.createUrlTree(['/dashboard']);
};

// Customer Guard: the trading pages belong to a customer's own account
export const customerGuard: CanActivateFn = (): boolean | UrlTree => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.isAdmin() ? router.createUrlTree(['/admin']) : true;
};

