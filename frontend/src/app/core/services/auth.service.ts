import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

interface RegisterRequest {
  email: string;
  password: string;
}

interface LoginRequest {
  email: string;
  password: string;
}

interface TokenResponse {
  token: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly TOKEN_KEY = 'fitt_token';

  private http = inject(HttpClient);

  isLoggedIn         = signal<boolean>(this.hasToken());
  onboardingComplete = signal<boolean>(false);

  register(email: string, password: string): Observable<void> {
    return this.http.post<TokenResponse>(
      '/api/identity/auth/register',
      { email, password } as RegisterRequest
    ).pipe(
      tap(response => {
        this.storeToken(response.token);
        this.isLoggedIn.set(true);
        // New users always go through onboarding first.
        this.onboardingComplete.set(false);
      })
    ) as unknown as Observable<void>;
  }

  login(email: string, password: string): Observable<void> {
    return this.http.post<TokenResponse>(
      '/api/identity/auth/login',
      { email, password } as LoginRequest
    ).pipe(
      tap(response => {
        this.storeToken(response.token);
        this.isLoggedIn.set(true);
        // onboardingComplete gets set after the login component
        // checks status via IdentityService, not here.
      })
    ) as unknown as Observable<void>;
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    this.isLoggedIn.set(false);
    this.onboardingComplete.set(false);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  getUserId(): number | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      // JWT payload is the middle segment, base64 encoded.
      const payload = token.split('.')[1];
      const decoded = atob(payload);
      return JSON.parse(decoded).uid ?? null;
    } catch {
      return null;
    }
  }

  markOnboardingComplete(): void {
    this.onboardingComplete.set(true);
  }

  private storeToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  private hasToken(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }
}
