import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);

  // Login and register are how you get a token in the first place, so they
  // must go out without one. Attaching a stale token here gets the request
  // rejected as a malformed token before the server checks credentials.
  const isAuthEndpoint = req.url.includes('/api/identity/auth/');

  // Every request carries a fresh correlation ID. The backend echoes it in the
  // response and stamps it on every log line, so one click can be traced from
  // the browser to the server logs. The backend only accepts plain short tokens
  // (letters, digits, dot, dash, underscore, up to 64), which a UUID satisfies.
  const headers: Record<string, string> = { 'X-Request-ID': crypto.randomUUID() };

  const token = authService.getToken();
  if (token && !isAuthEndpoint) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  const augmentedReq = req.clone({ setHeaders: headers });

  return next(augmentedReq).pipe(
    catchError((error: HttpErrorResponse) => {
      // A 401 on a normal call means the session is dead, clear it and redirect
      // to login. Skip this for the auth endpoints, a failed login is already
      // on the login page and shouldn't trigger the expiry teardown.
      if (error.status === 401 && !isAuthEndpoint) {
        authService.onTokenExpiration();
      }
      return throwError(() => error);
    })
  );
};
