import { describe, it, beforeEach, afterEach, expect, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

// Functional interceptor test for authInterceptor. Verifies four
// categories of behavior: conditional Bearer Authorization (added only
// when AuthService has a token), unconditional X-Request-ID on every
// request, uniqueness of X-Request-ID across separate requests, and that
// the rest of the request (url, method, body, existing headers) passes
// through unchanged via the setHeaders merge semantics of clone(). The
// interceptor is wired through real HttpClient plumbing via
// withInterceptors so tests exercise it in its real execution context,
// not via a hand-rolled call that could drift from how Angular invokes it.
describe('authInterceptor', () => {

  const mockAuthService = {
    getToken: vi.fn()
  };

  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    vi.resetAllMocks();

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });

    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  it('adds Authorization Bearer header when AuthService returns a token', () => {
    mockAuthService.getToken.mockReturnValue('jwt-token-value');

    http.get('/api/anything').subscribe();

    const req = controller.expectOne('/api/anything');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token-value');
    req.flush(null);
  });

  it('omits the Authorization header when AuthService returns null', () => {
    mockAuthService.getToken.mockReturnValue(null);

    http.get('/api/anything').subscribe();

    const req = controller.expectOne('/api/anything');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(null);
  });

  it('attaches an X-Request-ID header on every request', () => {
    mockAuthService.getToken.mockReturnValue('any-token');

    http.get('/api/anything').subscribe();

    const req = controller.expectOne('/api/anything');
    const requestId = req.request.headers.get('X-Request-ID');
    expect(requestId).toBeTruthy();
    // RFC 4122 v4 UUIDs from crypto.randomUUID are 36 characters including dashes.
    expect(requestId!.length).toBe(36);
    req.flush(null);
  });

  it('generates a unique X-Request-ID per request', () => {
    mockAuthService.getToken.mockReturnValue(null);

    http.get('/api/first').subscribe();
    http.get('/api/second').subscribe();

    const first = controller.expectOne('/api/first');
    const second = controller.expectOne('/api/second');
    expect(first.request.headers.get('X-Request-ID'))
      .not.toBe(second.request.headers.get('X-Request-ID'));
    first.flush(null);
    second.flush(null);
  });

  it('passes the request method and body through unchanged', () => {
    mockAuthService.getToken.mockReturnValue('any-token');
    const body = { foo: 'bar', count: 5 };

    http.post('/api/echo', body).subscribe();

    const req = controller.expectOne('/api/echo');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(null);
  });

  it('preserves headers set on the original request via the setHeaders merge', () => {
    mockAuthService.getToken.mockReturnValue('any-token');

    http.get('/api/anything', { headers: { 'X-Custom': 'preserved' } }).subscribe();

    const req = controller.expectOne('/api/anything');
    // setHeaders merges with existing headers rather than replacing them, so
    // X-Custom must survive alongside the interceptor's own additions.
    expect(req.request.headers.get('X-Custom')).toBe('preserved');
    expect(req.request.headers.get('Authorization')).toBe('Bearer any-token');
    expect(req.request.headers.get('X-Request-ID')).toBeTruthy();
    req.flush(null);
  });
});
