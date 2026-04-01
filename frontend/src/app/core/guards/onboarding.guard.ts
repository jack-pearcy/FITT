import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { IdentityService } from '../services/identity.service';

export const onboardingGuard: CanActivateFn = () => {
  const auth     = inject(AuthService);
  const identity = inject(IdentityService);
  const router   = inject(Router);

  // Signal is true when the user completed onboarding in this same session.
  // Fast path, no network call needed.
  if (auth.onboardingComplete()) return true;

  // Signal is false on every fresh page load even if the user finished onboarding
  // in a previous session. Hit the backend to get the real status.
  const userId = auth.getUserId();
  if (userId === null) return router.createUrlTree(['/login']);

  return identity.getOnboardingStatus(userId).pipe(
    map(res => {
      if (res.onboardingComplete) {
        // Sync the signal so the rest of this session skips the network call
        auth.markOnboardingComplete();
        return true;
      }
      return router.createUrlTree(['/onboarding']);
    }),
    catchError(() => of(router.createUrlTree(['/onboarding'])))
  );
};
