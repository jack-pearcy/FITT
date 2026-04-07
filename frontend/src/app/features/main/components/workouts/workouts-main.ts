import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../../../core/services/auth.service';
import { WorkoutService } from '../../../../core/services/workout.service';
import { CreatePlanWizardComponent } from './create-plan-wizard';
import { forkJoin } from 'rxjs';
import {
  ProgressionModel,
  WorkoutPlanResponse
} from '../../../workouts/models/workout.model';

// Drives which block the template renders. One signal, no boolean mess.
type WorkoutsView = 'loading' | 'no-plan' | 'plan-view' | 'create-plan';

@Component({
  selector: 'app-workouts-main',
  standalone: true,
  imports: [CreatePlanWizardComponent],
  templateUrl: './workouts-main.html',
  styleUrl: './workouts-main.scss'
})
export class WorkoutsMainComponent implements OnInit {

  private readonly auth       = inject(AuthService);
  private readonly workouts   = inject(WorkoutService);
  private readonly router     = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly view           = signal<WorkoutsView>('loading');
  readonly plans          = signal<WorkoutPlanResponse[]>([]);
  readonly activePlan     = signal<WorkoutPlanResponse | null>(null);
  readonly error          = signal<string | null>(null);
  readonly actionLoading  = signal(false);

  // Progression model for the active plan. Fetched separately after plans load.
  readonly progressionModel = signal<ProgressionModel | null>(null);

  readonly progressionLabels: Record<ProgressionModel, string> = {
    DOUBLE_PROGRESSION:   'Double Progression',
    RPE_AUTOREGULATION:   'RPE Autoregulation',
    LINEAR_PERIODIZATION: 'Linear Periodization'
  };

  readonly progressionEntries = signal(
    (Object.entries(this.progressionLabels) as [ProgressionModel, string][])
      .map(([key, label]) => ({ key, label }))
  );

  ngOnInit(): void {
    this.loadPlans();
  }

  private loadPlans(): void {
    const userId = this.auth.getUserId();
    if (userId === null) return;

    this.workouts.listPlans(userId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: plans => {
          this.plans.set(plans);
          const active = plans.find(p => p.active) ?? null;
          this.activePlan.set(active);

          if (active) {
            this.view.set('plan-view');
            this.loadProgressionPreference(userId);
          } else if (plans.length === 0) {
            this.view.set('no-plan');
          } else {
            // User has plans but none is active yet
            this.view.set('no-plan');
          }
        },
        error: () => {
          this.error.set('Could not load your plans. Try refreshing.');
          this.view.set('no-plan');
        }
      });
  }

  private loadProgressionPreference(userId: number): void {
    this.workouts.getProgressionPreference(userId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: model => this.progressionModel.set(model),
        // Not fatal, just leave it null
        error: () => {}
      });
  }

  activatePlan(planId: number): void {
    const userId = this.auth.getUserId();
    if (userId === null) return;

    this.actionLoading.set(true);
    this.workouts.activatePlan(userId, planId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: activated => {
          // Update local state so the UI reflects the new active plan
          // without a second round trip to the server
          this.plans.update(list =>
            list.map(p => ({ ...p, active: p.id === activated.id }))
          );
          this.activePlan.set(activated);
          this.view.set('plan-view');
          this.actionLoading.set(false);
        },
        error: () => {
          this.error.set('Could not activate that plan. Try again.');
          this.actionLoading.set(false);
        }
      });
  }

  deletePlan(planId: number): void {
    const userId = this.auth.getUserId();
    if (userId === null) return;

    this.actionLoading.set(true);
    this.workouts.deletePlan(userId, planId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          const remaining = this.plans().filter(p => p.id !== planId);
          this.plans.set(remaining);

          // If we deleted the active plan, drop back to no-plan view
          if (this.activePlan()?.id === planId) {
            this.activePlan.set(null);
            this.view.set('no-plan');
          }

          this.actionLoading.set(false);
        },
        error: () => {
          this.error.set('Could not delete that plan. Try again.');
          this.actionLoading.set(false);
        }
      });
  }

  setProgressionModel(model: ProgressionModel): void {
    const userId = this.auth.getUserId();
    if (userId === null) return;

    this.workouts.updateProgressionPreference(userId, { progressionModel: model })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.progressionModel.set(model),
        error: () => this.error.set('Could not update progression model.')
      });
  }

  startSession(): void {
    const userId = this.auth.getUserId();
    const plan   = this.activePlan();
    if (!userId || !plan) return;

    this.actionLoading.set(true);

    // Count finished sessions for this plan to determine which day is next
    this.workouts.countCompletedSessions(userId, plan.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: completedCount => {
          // Sort days by orderIndex so the rotation is always consistent
          const sortedDays = plan.days.slice().sort((a, b) => a.orderIndex - b.orderIndex);
          const dayIndex   = Number(completedCount) % sortedDays.length;
          const planDay    = sortedDays[dayIndex];

          this.workouts.createSession(userId, {
            startedAt: new Date().toISOString(),
            planId:    plan.id
          }).pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
              next: session => {
                const exercises = planDay.exercises.map(ex =>
                  this.workouts.addExerciseEntry(userId, session.id, { exerciseId: ex.exercise.id })
                );

                if (exercises.length === 0) {
                  this.router.navigate(['/session', session.id]);
                  this.actionLoading.set(false);
                  return;
                }

                forkJoin(exercises)
                  .pipe(takeUntilDestroyed(this.destroyRef))
                  .subscribe({
                    next: () => {
                      this.router.navigate(['/session', session.id]);
                      this.actionLoading.set(false);
                    },
                    error: () => {
                      this.error.set('Could not populate your session. Try again.');
                      this.actionLoading.set(false);
                    }
                  });
              },
              error: () => {
                this.error.set('Could not start your session. Try again.');
                this.actionLoading.set(false);
              }
            });
        },
        error: () => {
          this.error.set('Could not start your session. Try again.');
          this.actionLoading.set(false);
        }
      });
  }

  showCreatePlan(): void {
    this.view.set('create-plan');
  }

  cancelCreatePlan(): void {
    this.view.set(this.activePlan() ? 'plan-view' : 'no-plan');
  }

  onPlanCreated(plan: WorkoutPlanResponse): void {
    this.plans.update(list => [...list, plan]);
    this.activePlan.set(plan);
    this.view.set('plan-view');
    const userId = this.auth.getUserId();
    if (userId) this.loadProgressionPreference(userId);
  }
}
