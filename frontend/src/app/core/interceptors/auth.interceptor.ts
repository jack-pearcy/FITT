import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

// Adds the JWT and a unique request ID to every outgoing request.
// Angular HTTP requests are immutable so we clone before modifying.
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();
  const requestId = crypto.randomUUID();

  const headers: Record<string, string> = {
    'X-Request-ID': requestId
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const augmentedReq = req.clone({ setHeaders: headers });
  return next(augmentedReq);
};
