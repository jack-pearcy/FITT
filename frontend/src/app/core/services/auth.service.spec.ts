import { describe, it, beforeEach, afterEach, expect } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { AuthService } from './auth.service';

// Service test for AuthService. HttpClient is mocked via the official
// provideHttpClientTesting harness, not a hand-rolled spy. localStorage is
// the real jsdom implementation, cleared before every test so the service
// constructor starts from a known state. Signal reads use the function call
// form, since Angular signals are getters.
describe('AuthService', () => {

  const TOKEN_KEY = 'fitt_token';

  // Real JWT shape with payload {"uid":42}. The service does not validate
  // the signature, so a fake header and signature segment are fine.
  const VALID_TOKEN_UID_42 = 'header.eyJ1aWQiOjQyfQ==.signature';

  let controller: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Fails the test if any HTTP request the service kicked off was never
    // matched and flushed. Catches leaks.
    controller.verify();
  });

  it('isLoggedIn starts false when no token is present in localStorage', () => {
    const service = TestBed.inject(AuthService);
    expect(service.isLoggedIn()).toBe(false);
    expect(service.onboardingComplete()).toBe(false);
  });

  it('isLoggedIn starts true when a token is present at injection time', () => {
    // Storage is set BEFORE the service is injected so the constructor
    // sees the populated state when it calls hasToken().
    localStorage.setItem(TOKEN_KEY, 'existing-jwt');
    const service = TestBed.inject(AuthService);
    expect(service.isLoggedIn()).toBe(true);
  });

  it('register POSTs email and password to /api/identity/auth/register', () => {
    const service = TestBed.inject(AuthService);
    service.register('a@b.com', 'pass-123').subscribe();

    const req = controller.expectOne('/api/identity/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@b.com', password: 'pass-123' });
    req.flush({ token: 'new-jwt' });
  });

  it('register on success stores token, flips isLoggedIn true, leaves onboardingComplete false', () => {
    const service = TestBed.inject(AuthService);
    service.register('a@b.com', 'pass-123').subscribe();
    controller.expectOne('/api/identity/auth/register').flush({ token: 'new-jwt' });

    expect(localStorage.getItem(TOKEN_KEY)).toBe('new-jwt');
    expect(service.isLoggedIn()).toBe(true);
    // New users always start with onboarding incomplete.
    expect(service.onboardingComplete()).toBe(false);
  });

  it('login POSTs to /api/identity/auth/login, stores token, and does not touch onboardingComplete', () => {
    const service = TestBed.inject(AuthService);
    service.login('a@b.com', 'pass-123').subscribe();

    const req = controller.expectOne('/api/identity/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'a@b.com', password: 'pass-123' });
    req.flush({ token: 'login-jwt' });

    expect(localStorage.getItem(TOKEN_KEY)).toBe('login-jwt');
    expect(service.isLoggedIn()).toBe(true);
    // onboardingComplete is set by the login component after it checks
    // status via IdentityService. login() itself never touches it.
    expect(service.onboardingComplete()).toBe(false);
  });

  it('logout clears stored token and resets both signals to false', () => {
    localStorage.setItem(TOKEN_KEY, 'existing-jwt');
    const service = TestBed.inject(AuthService);
    service.markOnboardingComplete();
    // Sanity check the pre-logout state so the test is honest about what
    // logout is actually clearing.
    expect(service.isLoggedIn()).toBe(true);
    expect(service.onboardingComplete()).toBe(true);

    service.logout();

    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(service.isLoggedIn()).toBe(false);
    expect(service.onboardingComplete()).toBe(false);
  });

  it('getUserId returns null when no token is present', () => {
    const service = TestBed.inject(AuthService);
    expect(service.getUserId()).toBeNull();
  });

  it('getUserId decodes the JWT payload and returns the uid claim', () => {
    localStorage.setItem(TOKEN_KEY, VALID_TOKEN_UID_42);
    const service = TestBed.inject(AuthService);
    expect(service.getUserId()).toBe(42);
  });

  it('getUserId returns null when the token cannot be decoded', () => {
    // Single segment with no dots, so split('.')[1] is undefined and atob
    // throws an InvalidCharacterError that the service swallows.
    localStorage.setItem(TOKEN_KEY, 'not-a-jwt');
    const service = TestBed.inject(AuthService);
    expect(service.getUserId()).toBeNull();
  });
});
