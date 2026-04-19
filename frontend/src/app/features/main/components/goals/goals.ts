import { Component, inject, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import { AuthService } from '../../../../core/services/auth.service';
import {
  IdentityService,
  GoalsResponse,
  SaveGoalsRequest
} from '../../../../core/services/identity.service';

@Component({
  selector: 'app-goals',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './goals.html',
  styleUrl: './goals.scss'
})
export class GoalsComponent implements OnInit {

  private readonly auth = inject(AuthService);
  private readonly identity = inject(IdentityService);

  readonly mode = signal<'loading' | 'view' | 'edit' | 'error'>('loading');
  readonly goals = signal<GoalsResponse | null>(null);
  readonly tdee = signal<number | null>(null);
  readonly saving = signal(false);
  readonly error = signal('');

  // Form controls with explicit types so patchValue stays type-safe
  readonly goalDirection = new FormControl('MAINTAIN', {
    nonNullable: true, validators: Validators.required
  });
  readonly targetWeightKg = new FormControl<number | null>(null);
  readonly timelineWeeks = new FormControl<number | null>(null);
  readonly calorieGoalMode = new FormControl('AUTO', {
    nonNullable: true, validators: Validators.required
  });
  readonly calorieGoal = new FormControl<number | null>({
    value: null, disabled: true
  });

  constructor() {
    // Toggle calorie goal field when the user switches between AUTO and MANUAL.
    // emitEvent, false prevents the valueChanges loop described in the chapter.
    this.calorieGoalMode.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(mode => {
        if (mode === 'MANUAL') {
          this.calorieGoal.enable({ emitEvent: false });
        } else {
          this.calorieGoal.disable({ emitEvent: false });
        }
      });
  }

  ngOnInit(): void {
    const userId = this.auth.getUserId();
    if (userId === null) return;

    // Both calls are independent so forkJoin runs them in parallel
    forkJoin({
      goals: this.identity.getGoals(userId),
      tdee: this.identity.getTdee(userId)
    }).subscribe({
      next: ({ goals, tdee }) => {
        this.goals.set(goals);
        this.tdee.set(tdee.tdee);
        this.mode.set('view');
      },
      error: () => {
        this.error.set('Failed to load your goals');
        this.mode.set('error');
      }
    });
  }

  startEdit(): void {
    const g = this.goals();
    if (!g) return;

    this.goalDirection.setValue(g.goalDirection);
    this.targetWeightKg.setValue(g.targetWeightKg);
    this.timelineWeeks.setValue(g.timelineWeeks);
    this.calorieGoalMode.setValue(g.calorieGoalMode);
    this.calorieGoal.setValue(g.calorieGoal);

    // Sync disabled state to match the loaded mode
    if (g.calorieGoalMode === 'MANUAL') {
      this.calorieGoal.enable({ emitEvent: false });
    } else {
      this.calorieGoal.disable({ emitEvent: false });
    }

    this.error.set('');
    this.mode.set('edit');
  }

  cancelEdit(): void {
    this.mode.set('view');
  }

  saveGoals(): void {
    this.goalDirection.markAsTouched();
    this.calorieGoalMode.markAsTouched();

    if (this.goalDirection.invalid || this.calorieGoalMode.invalid) return;
    if (this.calorieGoalMode.value === 'MANUAL' &&
      (this.calorieGoal.value === null || this.calorieGoal.value <= 0)) return;

    const userId = this.auth.getUserId();
    if (userId === null) return;

    this.saving.set(true);
    this.error.set('');

    const request: SaveGoalsRequest = {
      goalDirection: this.goalDirection.value as SaveGoalsRequest['goalDirection'],
      targetWeightKg: this.targetWeightKg.value,
      timelineWeeks: this.timelineWeeks.value,
      calorieGoalMode: this.calorieGoalMode.value as SaveGoalsRequest['calorieGoalMode'],
      calorieGoal: this.calorieGoalMode.value === 'MANUAL' ? this.calorieGoal.value : null
    };

    this.identity.saveGoals(userId, request).subscribe({
      next: () => {
        // Re-fetch so AUTO mode shows the server-calculated calorie goal
        this.identity.getGoals(userId).subscribe({
          next: goals => {
            this.goals.set(goals);
            this.mode.set('view');
            this.saving.set(false);
          },
          error: () => {
            this.mode.set('view');
            this.saving.set(false);
          }
        });
      },
      error: () => {
        this.error.set('Failed to save goals');
        this.saving.set(false);
      }
    });
  }

  // Template helper for readable labels
  directionLabel(dir: string): string {
    switch (dir) {
      case 'BULK': return 'Bulk';
      case 'CUT': return 'Cut';
      case 'MAINTAIN': return 'Maintain';
      default: return dir;
    }
  }

  modeLabel(mode: string): string {
    return mode === 'AUTO' ? 'Auto (calculated from TDEE)' : 'Manual';
  }
}
