import { Component, inject, OnInit, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { IdentityService } from '../../../core/services/identity.service';
import { MeasurementsService } from '../../../core/services/measurements.service';

@Component({
  selector: 'app-main-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, DecimalPipe],
  templateUrl: './main-shell.html',
  styleUrl: './main-shell.scss'
})
export class MainShellComponent implements OnInit {

  private readonly auth         = inject(AuthService);
  private readonly identity     = inject(IdentityService);
  private readonly measurements = inject(MeasurementsService);

  readonly sevenDayAverage = signal<number | null>(null);
  // TDEE from the onboarding endpoint, same number shown on the calorie step
  readonly tdeeCalories    = signal<number | null>(null);
  // No streak endpoint yet, Phase 16
  readonly sessionStreak   = signal<number | null>(null);

  readonly loadingAvg      = signal(true);
  readonly loadingTdee     = signal(true);

  ngOnInit(): void {
    const userId = this.auth.getUserId();
    if (userId === null) return;

    this.measurements.getSevenDayAverage(userId).subscribe({
      next: res => {
        this.sevenDayAverage.set(res.average);
        this.loadingAvg.set(false);
      },
      error: () => this.loadingAvg.set(false)
    });

    // getTdee requires biometrics to exist, returns 400 if not set
    // that's fine, just show "--" on error
    this.identity.getTdee(userId).subscribe({
      next: res => {
        this.tdeeCalories.set(res.tdee);
        this.loadingTdee.set(false);
      },
      error: () => this.loadingTdee.set(false)
    });
  }

  logout(): void {
    this.auth.logout();
  }
}
