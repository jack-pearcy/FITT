import { describe, it, beforeEach, expect, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree
} from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { onboardingGuard } from './onboarding.guard';
import { AuthService } from '../services/auth.service';
import { IdentityService } from '../services/identity.service';

// Functional guard test for onboardingGuard. Five branches:
//   1. onboardingComplete signal true: return true (fast path, no network)
//   2. signal false and no user id: redirect to /login
//   3. signal false, network reports complete: sync the signal, return true
//   4. signal false, network reports incomplete: redirect to /onboarding
//   5. signal false, network errors out: redirect to /onboarding
// Synchronous and observable branches are both covered. The observable
// branches use rxjs of() and throwError() so emissions are synchronous
// and the subscribe assertion runs in the same tick as the test body.
describe('onboardingGuard', () => {

  const routeStub = {} as ActivatedRouteSnapshot;
  const stateStub = {} as RouterStateSnapshot;
  const USER_ID = 42;

  const mockAuthService = {
    onboardingComplete: vi.fn(),
    getUserId: vi.fn(),
    markOnboardingComplete: vi.fn()
  };

  const mockIdentityService = {
    getOnboardingStatus: vi.fn()
  };

  const mockRouter = {
    createUrlTree: vi.fn()
  };

  // Sentinel trees keyed by path so tests can assert which redirect ran by
  // reference equality instead of inspecting createUrlTree arguments.
  const loginUrlTree = { __sentinel: 'login-tree' } as unknown as UrlTree;
  const onboardingUrlTree = { __sentinel: 'onboarding-tree' } as unknown as UrlTree;

  beforeEach(() => {
    vi.resetAllMocks();

    mockRouter.createUrlTree.mockImplementation((commands: string[]) => {
      if (commands[0] === '/login') return loginUrlTree;
      if (commands[0] === '/onboarding') return onboardingUrlTree;
      return {} as UrlTree;
    });

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        { provide: IdentityService, useValue: mockIdentityService },
        { provide: Router, useValue: mockRouter }
      ]
    });
  });

  it('returns true via the signal fast path when onboardingComplete is true', () => {
    mockAuthService.onboardingComplete.mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() => onboardingGuard(routeStub, stateStub));

    expect(result).toBe(true);
    // Neither auth.getUserId nor any network call should fire on the fast path.
    expect(mockAuthService.getUserId).not.toHaveBeenCalled();
    expect(mockIdentityService.getOnboardingStatus).not.toHaveBeenCalled();
  });

  it('redirects to /login when the signal is false and no user id is available', () => {
    mockAuthService.onboardingComplete.mockReturnValue(false);
    mockAuthService.getUserId.mockReturnValue(null);

    const result = TestBed.runInInjectionContext(() => onboardingGuard(routeStub, stateStub));

    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(result).toBe(loginUrlTree);
    // No network call when the user is not authenticated.
    expect(mockIdentityService.getOnboardingStatus).not.toHaveBeenCalled();
  });

  it('returns true and syncs the signal when the backend reports onboardingComplete true', () => {
    mockAuthService.onboardingComplete.mockReturnValue(false);
    mockAuthService.getUserId.mockReturnValue(USER_ID);
    mockIdentityService.getOnboardingStatus.mockReturnValue(
      of({ biometricsComplete: true, goalsComplete: true, onboardingComplete: true })
    );

    const result = TestBed.runInInjectionContext(() => onboardingGuard(routeStub, stateStub));

    let emitted: boolean | UrlTree | undefined;
    (result as Observable<boolean | UrlTree>).subscribe(v => emitted = v);

    expect(mockIdentityService.getOnboardingStatus).toHaveBeenCalledWith(USER_ID);
    expect(emitted).toBe(true);
    // Signal sync so the next navigation hits the fast path without another network call.
    expect(mockAuthService.markOnboardingComplete).toHaveBeenCalled();
  });

  it('redirects to /onboarding when the backend reports onboardingComplete false', () => {
    mockAuthService.onboardingComplete.mockReturnValue(false);
    mockAuthService.getUserId.mockReturnValue(USER_ID);
    mockIdentityService.getOnboardingStatus.mockReturnValue(
      of({ biometricsComplete: false, goalsComplete: false, onboardingComplete: false })
    );

    const result = TestBed.runInInjectionContext(() => onboardingGuard(routeStub, stateStub));

    let emitted: boolean | UrlTree | undefined;
    (result as Observable<boolean | UrlTree>).subscribe(v => emitted = v);

    expect(emitted).toBe(onboardingUrlTree);
    // markOnboardingComplete must not run when the user has not actually
    // finished onboarding.
    expect(mockAuthService.markOnboardingComplete).not.toHaveBeenCalled();
  });

  it('redirects to /onboarding when the backend call errors out', () => {
    mockAuthService.onboardingComplete.mockReturnValue(false);
    mockAuthService.getUserId.mockReturnValue(USER_ID);
    mockIdentityService.getOnboardingStatus.mockReturnValue(
      throwError(() => new Error('backend down'))
    );

    const result = TestBed.runInInjectionContext(() => onboardingGuard(routeStub, stateStub));

    let emitted: boolean | UrlTree | undefined;
    (result as Observable<boolean | UrlTree>).subscribe(v => emitted = v);

    expect(emitted).toBe(onboardingUrlTree);
    // Failed network call must not silently mark onboarding complete.
    expect(mockAuthService.markOnboardingComplete).not.toHaveBeenCalled();
  });
});
