import { describe, it, beforeEach, expect, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree
} from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

// Functional guard test for authGuard. Two branches: logged in returns
// true directly, not logged in returns a UrlTree pointing at /login.
// Both AuthService and Router are stubbed via Vitest mocks and provided
// through TestBed so the guard's inject() calls resolve to the mocks.
// The guard itself is invoked via TestBed.runInInjectionContext so
// inject() works outside an Angular runtime.
describe('authGuard', () => {

  const routeStub = {} as ActivatedRouteSnapshot;
  const stateStub = {} as RouterStateSnapshot;

  const mockAuthService = {
    isLoggedIn: vi.fn()
  };

  const mockRouter = {
    createUrlTree: vi.fn()
  };

  beforeEach(() => {
    // Full reset between tests so call history and return values do not
    // leak across the suite. clearAllMocks keeps implementations,
    // resetAllMocks wipes them too which is what we want here.
    vi.resetAllMocks();

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter }
      ]
    });
  });

  it('returns true and does not touch the router when the user is logged in', () => {
    mockAuthService.isLoggedIn.mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() => authGuard(routeStub, stateStub));

    expect(result).toBe(true);
    expect(mockRouter.createUrlTree).not.toHaveBeenCalled();
  });

  it('returns a UrlTree pointing at /login when the user is not logged in', () => {
    // Sentinel object stands in for a real UrlTree. The guard returns
    // whatever the mocked createUrlTree returns, so reference equality
    // via toBe is enough to verify the wiring without constructing a
    // real UrlTree (which needs a UrlSerializer).
    const fakeUrlTree = { __sentinel: 'login-tree' } as unknown as UrlTree;
    mockAuthService.isLoggedIn.mockReturnValue(false);
    mockRouter.createUrlTree.mockReturnValue(fakeUrlTree);

    const result = TestBed.runInInjectionContext(() => authGuard(routeStub, stateStub));

    expect(mockRouter.createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(result).toBe(fakeUrlTree);
  });
});
