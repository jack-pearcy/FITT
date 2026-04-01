import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { IdentityService } from '../../../core/services/identity.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class LoginComponent {

  private readonly fb       = inject(NonNullableFormBuilder);
  private readonly auth     = inject(AuthService);
  private readonly identity = inject(IdentityService);
  private readonly router   = inject(Router);

  readonly form = this.fb.group({
    email:    ['', [Validators.required, Validators.email]],
    password: ['', Validators.required]
  });

  readonly loading = signal(false);
  readonly error   = signal<string | null>(null);

  login(): void {
    if (this.form.invalid) return;

    this.loading.set(true);
    this.error.set(null);

    const { email, password } = this.form.getRawValue();

    this.auth.login(email, password).subscribe({
      next: () => {
        const userId = this.auth.getUserId();
        if (userId === null) {
          this.router.navigate(['/onboarding']);
          return;
        }

        // Check onboarding status post-login so we route to the right place
        this.identity.getOnboardingStatus(userId).subscribe({
          next: res => {
            if (res.onboardingComplete) {
              this.auth.markOnboardingComplete();
              this.router.navigate(['/home']);
            } else {
              this.router.navigate(['/onboarding']);
            }
          },
          error: () => this.router.navigate(['/onboarding'])
        });
      },
      error: () => {
        this.error.set('Invalid email or password.');
        this.loading.set(false);
      }
    });
  }
}
