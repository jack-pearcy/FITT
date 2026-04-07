import { Component, DestroyRef, inject, OnInit, signal, computed } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../../core/services/auth.service';
import { WorkoutService } from '../../../core/services/workout.service';
import { forkJoin, interval } from 'rxjs';
import {
  ExerciseEntryResponse,
  SetEntryResponse,
  WorkoutSessionResponse,
  WorkoutPlanResponse
} from '../models/workout.model';

// Which top-level screen the component shows
type SessionView = 'loading' | 'active' | 'summary' | 'error';

@Component({
  selector: 'app-active-session',
  standalone: true,
  imports: [DecimalPipe, FormsModule],
  templateUrl: './active-session.html',
  styleUrl: './active-session.scss'
})
export class ActiveSessionComponent implements OnInit {

  private readonly route      = inject(ActivatedRoute);
  private readonly router     = inject(Router);
  private readonly auth       = inject(AuthService);
  private readonly workouts   = inject(WorkoutService);
  private readonly destroyRef = inject(DestroyRef);
  readonly plan               = signal<WorkoutPlanResponse | null>(null);

  readonly view             = signal<SessionView>('loading');
  readonly session          = signal<WorkoutSessionResponse | null>(null);
  readonly error            = signal<string | null>(null);
  readonly actionLoading    = signal(false);
  private readonly finished = signal(false);

  // Global session timer in seconds
  readonly elapsedSeconds = signal(0);

  // Tracks which entry row the user is currently working on
  readonly activeEntryId  = signal<number | null>(null);

  // Per-entry rest timers: entryId -> seconds remaining
  readonly restTimers     = signal<Record<number, number>>({});

  // Set logging inputs keyed by entryId
  readonly setInputs      = signal<Record<number, { reps: number; weight: number; rpe: number | null }>>({});

  // Computed elapsed time formatted as MM:SS
  readonly elapsedFormatted = computed(() => {
    const s = this.elapsedSeconds();
    const mins = Math.floor(s / 60).toString().padStart(2, '0');
    const secs = (s % 60).toString().padStart(2, '0');
    return `${mins}:${secs}`;
  });

  ngOnInit(): void {
    this.startGlobalTimer();
    this.loadSession();
  }

  private startGlobalTimer(): void {
    interval(1000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.finished()) return;
        this.elapsedSeconds.update(s => s + 1);
      });
  }

  private loadSession(): void {
    const userId    = this.auth.getUserId();
    const sessionId = Number(this.route.snapshot.paramMap.get('sessionId'));

    if (!userId || !sessionId) {
      this.view.set('error');
      return;
    }

    // Load both at the same time, need both before rendering
    forkJoin({
      session: this.workouts.getSession(userId, sessionId),
      plans:   this.workouts.listPlans(userId)
    }).pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ session, plans }) => {
          this.session.set(session);
          const active = plans.find(p => p.active) ?? null;
          this.plan.set(active);
          this.initSetInputs(session);
          if (session.exerciseEntries.length > 0) {
            this.activeEntryId.set(session.exerciseEntries[0].id);
          }
          this.view.set('active');
        },
        error: () => {
          this.error.set('Could not load your session.');
          this.view.set('error');
        }
      });
  }
  // Passes in sets for workout day into session, to cap set count logging
  prescribedSets(exerciseId: number): number {
    const plan = this.plan();
    if (!plan) return 999;
    for (const day of plan.days) {
      const match = day.exercises.find(e => e.exercise.id === exerciseId);
      if (match) return match.sets;
    }
    // No match found in plan, default to no cap
    return 999;
  }

  // Seed default input values for every entry so the template always has something to bind
  private initSetInputs(session: WorkoutSessionResponse): void {
    const inputs: Record<number, { reps: number; weight: number; rpe: number | null }> = {};
    for (const entry of session.exerciseEntries) {
      inputs[entry.id] = { reps: 8, weight: 0, rpe: null };
    }
    this.setInputs.set(inputs);
  }

  getInput(entryId: number): { reps: number; weight: number; rpe: number | null } {
    return this.setInputs()[entryId] ?? { reps: 8, weight: 0, rpe: null };
  }

  updateInput(entryId: number, field: 'reps' | 'weight' | 'rpe', value: number): void {
    this.setInputs.update(inputs => ({
      ...inputs,
      [entryId]: { ...this.getInput(entryId), [field]: value }
    }));
  }

  logSet(entry: ExerciseEntryResponse): void {
    const userId    = this.auth.getUserId();
    const sessionId = this.session()?.id;
    if (!userId || !sessionId) return;

    const input = this.getInput(entry.id);
    this.actionLoading.set(true);

    this.workouts.logSet(userId, sessionId, entry.id, {
      reps:   input.reps,
      weight: input.weight > 0 ? input.weight : undefined,
      rpe:    input.rpe    ?? undefined
    }).pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: updated => {
          this.session.set(updated);
          this.actionLoading.set(false);
          // Start a 90 second rest timer for this entry
          this.startRestTimer(entry.id, 90);
        },
        error: () => {
          this.error.set('Could not log that set. Try again.');
          this.actionLoading.set(false);
        }
      });
  }

  // Stores cleanup functions for active rest timers so we can cancel before restarting
  private restTimerCleanups: Record<number, () => void> = {};

  private startRestTimer(entryId: number, seconds: number): void {
    // Cancel the previous interval for this entry if one is running
    if (this.restTimerCleanups[entryId]) {
      this.restTimerCleanups[entryId]();
      delete this.restTimerCleanups[entryId];
    }

    this.restTimers.update(t => ({ ...t, [entryId]: seconds }));

    const subscription = interval(1000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        const current = this.restTimers()[entryId] ?? 0;
        if (current <= 0) {
          subscription.unsubscribe();
          return;
        }
        this.restTimers.update(t => ({ ...t, [entryId]: current - 1 }));
      });

    // Store the cleanup so the next logSet call on this entry can cancel it
    this.restTimerCleanups[entryId] = () => subscription.unsubscribe();
  }

  getRestTimer(entryId: number): number {
    return this.restTimers()[entryId] ?? 0;
  }

  restTimerFormatted(entryId: number): string {
    const s    = this.getRestTimer(entryId);
    const mins = Math.floor(s / 60).toString().padStart(2, '0');
    const secs = (s % 60).toString().padStart(2, '0');
    return `${mins}:${secs}`;
  }

  setActive(entryId: number): void {
    this.activeEntryId.set(entryId);
  }

  finishSession(): void {
    const userId    = this.auth.getUserId();
    const sessionId = this.session()?.id;
    if (!userId || !sessionId) return;

    this.actionLoading.set(true);
    this.workouts.finishSession(userId, sessionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: finished => {
          this.session.set(finished);
          this.finished.set(true);
          this.view.set('summary');
          this.actionLoading.set(false);
        },
        error: () => {
          this.error.set('Could not finish your session. Try again.');
          this.actionLoading.set(false);
        }
      });
  }

  // Total volume = sum of (weight x reps) across all completed sets
  get totalVolume(): number {
    const session = this.session();
    if (!session) return 0;
    return session.exerciseEntries.reduce((total, entry) => {
      return total + entry.sets.reduce((entryTotal, set) => {
        if (!set.completed) return entryTotal;
        const w = parseFloat(set.weight ?? '0');
        return entryTotal + (w * (set.reps ?? 0));
      }, 0);
    }, 0);
  }

  get totalSetsLogged(): number {
    const session = this.session();
    if (!session) return 0;
    return session.exerciseEntries.reduce((t, e) => t + e.sets.filter(s => s.completed).length, 0);
  }

  backToHome(): void {
    this.router.navigate(['/home']);
  }

  getCompletedSets(entry: ExerciseEntryResponse): SetEntryResponse[] {
    return entry.sets.filter(s => s.completed);
  }
}
