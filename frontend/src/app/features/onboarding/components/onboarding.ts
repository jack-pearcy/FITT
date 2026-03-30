import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { IdentityService } from '../../../core/services/identity.service';
import {
  ActivityLevel,
  BiologicalSex,
  CalorieGoalMode,
  GoalDirection,
  UnitsPreference
} from '../models/onboarding.model';

const TOTAL_STEPS     = 10;
const BIOMETRICS_LAST = 5;
const FINAL_STEP      = 9;

@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './onboarding.html',
  styleUrl: './onboarding.scss'
})
export class OnboardingComponent {

  private fb              = inject(FormBuilder);
  private authService     = inject(AuthService);
  private identityService = inject(IdentityService);
  private router          = inject(Router);

  currentStep = signal(0);
  loading     = signal(false);
  error       = signal('');
  tdee        = signal<number | null>(null);

  readonly totalSteps = TOTAL_STEPS;

  // All biometrics fields are required so nonNullable is correct here.
  biometricsForm = this.fb.nonNullable.group({
    unitsPreference: ['' as UnitsPreference, Validators.required],
    height:          [0, [Validators.required, Validators.min(1)]],
    weight:          [0, [Validators.required, Validators.min(1)]],
    dateOfBirth:     ['',                     Validators.required],
    biologicalSex:   ['' as BiologicalSex,   Validators.required],
    activityLevel:   ['' as ActivityLevel,   Validators.required],
  });

  // Goals mix required and optional nullable fields so we use explicit FormControls.
  goalsForm = this.fb.group({
    goalDirection:   new FormControl<GoalDirection>(''  as GoalDirection, { nonNullable: true, validators: Validators.required }),
    targetWeightKg:  new FormControl<number | null>(null),
    timelineWeeks:   new FormControl<number | null>(null),
    calorieGoalMode: new FormControl<CalorieGoalMode>('AUTO',             { nonNullable: true, validators: Validators.required }),
    calorieGoal:     new FormControl<number | null>(null),
  });

  // Shortcut for reading units so the template stays clean.
  get units(): UnitsPreference {
    return this.biometricsForm.controls.unitsPreference.value;
  }

  get isManualCalories(): boolean {
    return this.goalsForm.controls.calorieGoalMode.value === 'MANUAL';
  }

  // Checks the specific controls for the current step so the Next button
  // stays disabled until the user has filled in a valid answer.
  get stepValid(): boolean {
    const step = this.currentStep();
    const b    = this.biometricsForm.controls;
    const g    = this.goalsForm.controls;

    switch (step) {
      case 0:  return b.unitsPreference.valid;
      case 1:  return b.height.valid;
      case 2:  return b.weight.valid;
      case 3:  return b.dateOfBirth.valid;
      case 4:  return b.biologicalSex.valid;
      case 5:  return b.activityLevel.valid;
      case 6:  return g.goalDirection.valid;
      case 7:  return true; // optional
      case 8:  return true; // optional
      case 9:  return g.calorieGoalMode.valid &&
        (g.calorieGoalMode.value !== 'MANUAL' ||
          (g.calorieGoal.value !== null && Number(g.calorieGoal.value) > 0));
      default: return true;
    }
  }

  next(): void {
    const step = this.currentStep();

    if (step === BIOMETRICS_LAST) {
      this.saveBiometrics();
      return;
    }

    if (step === FINAL_STEP) {
      this.saveGoals();
      return;
    }

    this.currentStep.set(step + 1);
  }

  back(): void {
    const step = this.currentStep();
    if (step > 0) this.currentStep.set(step - 1);
  }

  private saveBiometrics(): void {
    const userId = this.authService.getUserId();
    if (!userId) return;

    this.loading.set(true);
    this.error.set('');

    const { unitsPreference, height, weight, dateOfBirth, biologicalSex, activityLevel } =
      this.biometricsForm.getRawValue();

    // User inputs in their chosen unit. Backend always wants metric.
    const isImperial = unitsPreference === 'IMPERIAL';
    const heightCm   = isImperial ? Math.round(height * 2.54)                  : height;
    const weightKg   = isImperial ? parseFloat((weight * 0.453592).toFixed(1)) : weight;

    this.identityService.saveBiometrics(userId, {
      heightCm, weightKg, dateOfBirth, biologicalSex, activityLevel, unitsPreference,
    }).subscribe({
      next: () => {
        // Fetch TDEE right after so it's ready for the calorie step.
        this.identityService.getTdee(userId).subscribe({
          next: (res) => {
            this.tdee.set(res.tdee);
            this.loading.set(false);
            this.currentStep.set(BIOMETRICS_LAST + 1);
          },
          error: () => {
            // TDEE failure is not fatal, move on without it.
            this.loading.set(false);
            this.currentStep.set(BIOMETRICS_LAST + 1);
          }
        });
      },
      error: () => {
        this.error.set('Failed to save your information. Please try again.');
        this.loading.set(false);
      }
    });
  }

  private saveGoals(): void {
    const userId = this.authService.getUserId();
    if (!userId) return;

    this.loading.set(true);
    this.error.set('');

    const isImperial    = this.biometricsForm.controls.unitsPreference.value === 'IMPERIAL';
    const mode          = this.goalsForm.controls.calorieGoalMode.value as CalorieGoalMode;
    const goalDirection = this.goalsForm.controls.goalDirection.value as GoalDirection;
    const timelineWeeks = this.goalsForm.controls.timelineWeeks.value ?? null;
    const calorieGoal   = mode === 'MANUAL' ? (this.goalsForm.controls.calorieGoal.value ?? null) : null;

    // Target weight also needs unit conversion if the user is on imperial.
    const targetRaw      = this.goalsForm.controls.targetWeightKg.value;
    const targetWeightKg = targetRaw !== null
      ? (isImperial ? parseFloat((targetRaw * 0.453592).toFixed(1)) : targetRaw)
      : null;

    this.identityService.saveGoals(userId, {
      goalDirection,
      targetWeightKg,
      timelineWeeks,
      calorieGoalMode: mode,
      calorieGoal,
    }).subscribe({
      next: () => {
        this.authService.markOnboardingComplete();
        this.router.navigate(['/home']);
      },
      error: () => {
        this.error.set('Failed to save your goals. Please try again.');
        this.loading.set(false);
      }
    });
  }
}
