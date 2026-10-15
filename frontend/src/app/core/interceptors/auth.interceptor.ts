import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { platformTargets } from '../config/api-endpoints';
import { AuthService } from '../auth/auth.service';

// The only place in the application that sets an Authorization header.
 
function shouldAttachToken(url: string): boolean {
  let target: URL;
  try {
    target = new URL(url, window.location.origin);
  } catch {
    return false;
  }

  return platformTargets().some(
    (allowed) =>
      target.origin === allowed.origin &&
      (allowed.exactPaths.includes(target.pathname) ||
        allowed.pathPrefixes.some((prefix) => target.pathname.startsWith(prefix)))
  );
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  if (!shouldAttachToken(req.url)) {
    return next(req);
  }

  const token = auth.getToken();
  const outgoing = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      const is401 = error instanceof HttpErrorResponse && error.status === 401;
      const hasToken = !!token;
      const isRefreshRequest = req.url.includes('/auth/refresh');
      const alreadyRetried = req.headers.has('X-Auth-Retry');

      if (!is401 || !hasToken) {
        return throwError(() => error);
      }

      if (isRefreshRequest || alreadyRetried) {
        auth.logout('Unauthorised. Your session is no longer valid. Please sign in again.');
        return throwError(() => error);
      }

      return auth.refreshAccessToken().pipe(
        switchMap((nextToken) => {
          const retried = req.clone({
            setHeaders: {
              Authorization: `Bearer ${nextToken}`,
              'X-Auth-Retry': '1'
            }
          });
          return next(retried);
        }),
        catchError((refreshError: unknown) => {
          auth.logout('Unauthorised. Your session is no longer valid. Please sign in again.');
          return throwError(() => refreshError);
        })
      );
    })
  );
};
