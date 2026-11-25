// todays-lift component, shows the current day in the plan
// with its exercises, sets and reps, plus a button to start the session.
import {Component, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {Router} from '@angular/router';
import {WorkoutPlanDayResponse, WorkoutSessionResponse} from '../../../workouts/models/workout.model';
import {AuthService} from '../../../../core/services/auth.service';
import {openSessionIdFrom, WorkoutService} from '../../../../core/services/workout.service';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {HttpErrorResponse} from '@angular/common/http';
import {catchError, EMPTY, Observable, of, switchMap, throwError} from 'rxjs';

@Component({
  selector: 'app-todays-lift',
  standalone: true,
  imports: [],
  templateUrl: './todays-lift.html',
  styleUrl: './todays-lift.scss',
})
export class TodaysLift implements OnInit {

  private readonly workouts = inject(WorkoutService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly activePlanId = signal<number | null>(null);
  readonly starting = signal(false);

  // the day to render, stays null until the load resolves
  readonly todayDay = signal<WorkoutPlanDayResponse | null>(null);
  readonly loading = signal(true);
  // true when there is no active plan, so the template can say so
  readonly noActivePlan = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const userId = this.auth.getUserId();
    if (userId === null) return;

    // Pull all plans, pick the active one, then ask for today's day.
    // switchMap chains the two calls so the result comes back in one subscribe.
    this.workouts.listPlans(userId).pipe(
      switchMap(plans => {
        const active = plans.find(p => p.active) ?? null;

        // No active plan is a normal state for a new user, not an error.
        // Stop here and let the template show the empty message.
        if (active === null) {
          this.noActivePlan.set(true);
          this.loading.set(false);
          return EMPTY;
        }

        this.activePlanId.set(active.id)

        return this.workouts.getTodaysDay(userId, active);
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: day => {
        this.todayDay.set(day);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load today\'s lift. Try again.');
        this.loading.set(false);
      }
    });
  }

  // Routes back to the workouts tab instead of making the user cycle navs.
  goBack(): void {
    void this.router.navigate(['/home/workouts']);
  }

  // Asks for the open workout before starting one. A server without the /open
  // route answers 400, 404 or 405, which reads as none open so Start works as
  // before. Unauthorized and no connection still fail the start.
  private findOpenSession(userId: number): Observable<WorkoutSessionResponse | null> {
    return this.workouts.getOpenSession(userId).pipe(
      catchError(err => {
        const status = err instanceof HttpErrorResponse ? err.status : 0;
        return status === 0 || status === 401 ? throwError(() => err) : of(null);
      })
    );
  }

  startSession(): void {
    const userId = this.auth.getUserId();
    const day    = this.todayDay();
    const planId = this.activePlanId();
    if (userId === null || day === null || planId === null) return;

    this.starting.set(true);

    // A resumed session may come from a start that failed partway, so any of
    // the day's exercises it lacks are added before routing to it.
    this.findOpenSession(userId).pipe(
      switchMap(open => open !== null
        ? this.workouts.completeSessionForDay(userId, planId, open, day)
        : this.workouts.startSessionForDay(userId, planId, day).pipe(
            // The server allows one open workout. A 409 names it, so resume that one.
            catchError(err => {
              const openId = openSessionIdFrom(err);
              return openId === null
                ? throwError(() => err)
                : this.workouts.getSession(userId, openId).pipe(
                    switchMap(found => this.workouts.completeSessionForDay(userId, planId, found, day))
                  );
            })
          )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: session => {
        void this.router.navigate(['/session', session.id]);
        this.starting.set(false);
      },
      error: () => {
        this.error.set('Could not start your session. Try again.');
        this.starting.set(false);
      }
    });
  }
}
