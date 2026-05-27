import { describe, it, beforeEach, afterEach, expect, vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { ActiveSessionComponent } from './active-session';
import { AuthService } from '../../../core/services/auth.service';
import { WorkoutService } from '../../../core/services/workout.service';
import {
  ExerciseEntryResponse,
  WorkoutPlanResponse,
  WorkoutSessionResponse
} from '../models/workout.model';

// Component test for ActiveSessionComponent. The signal-heavy workout
// runtime: a four-state view machine, a global elapsed-time interval
// gated by a finished signal, per-entry rest countdowns with explicit
// cancellation on restart, prescribed set caps derived from the plan,
// and volume aggregation across completed sets only.
//
// Strategy: Vitest fake timers control all interval observables.
// AuthService, WorkoutService, ActivatedRoute, and Router are stubbed.
// fixture.detectChanges triggers ngOnInit which kicks off the global
// timer and the forkJoin load. Synchronous of() mocks for both
// observables means the load resolves in the same tick as detectChanges.
describe('ActiveSessionComponent', () => {

  const USER_ID = 42;
  const SESSION_ID = 100;
  const ENTRY_ID = 300;
  const EXERCISE_ID = 5;

  const sampleEntry: ExerciseEntryResponse = {
    id: ENTRY_ID,
    orderIndex: 0,
    exercise: {
      id: EXERCISE_ID,
      name: 'Bench Press',
      muscleGroup: 'CHEST',
      custom: false
    },
    sets: []
  };

  const sampleSession: WorkoutSessionResponse = {
    id: SESSION_ID,
    ownerUserId: USER_ID,
    planId: 7,
    startedAt: '2026-05-26T18:00:00Z',
    finishedAt: null,
    notes: null,
    exerciseEntries: [sampleEntry]
  };

  const samplePlan: WorkoutPlanResponse = {
    id: 7,
    ownerUserId: USER_ID,
    name: 'PPL 6-day',
    daysPerWeek: 6,
    splitType: 'PPL',
    active: true,
    days: [{
      id: 1,
      orderIndex: 0,
      dayLabel: 'Push',
      exercises: [{
        id: 1,
        orderIndex: 0,
        sets: 4,
        reps: 8,
        exercise: { id: EXERCISE_ID, name: 'Bench Press', muscleGroup: 'CHEST', custom: false }
      }]
    }]
  };

  const mockAuth = {
    getUserId: vi.fn()
  };

  const mockWorkouts = {
    getSession: vi.fn(),
    listPlans: vi.fn(),
    logSet: vi.fn(),
    finishSession: vi.fn()
  };

  const mockRoute = {
    snapshot: {
      paramMap: {
        get: vi.fn()
      }
    }
  };

  const mockRouter = {
    navigate: vi.fn()
  };

  let fixture: ComponentFixture<ActiveSessionComponent>;
  let component: ActiveSessionComponent;

  beforeEach(() => {
    vi.resetAllMocks();
    vi.useFakeTimers();

    mockAuth.getUserId.mockReturnValue(USER_ID);
    mockRoute.snapshot.paramMap.get.mockReturnValue(String(SESSION_ID));
    mockWorkouts.getSession.mockReturnValue(of(sampleSession));
    mockWorkouts.listPlans.mockReturnValue(of([samplePlan]));
    mockWorkouts.logSet.mockReturnValue(of(sampleSession));
    mockWorkouts.finishSession.mockReturnValue(of({
      ...sampleSession,
      finishedAt: '2026-05-26T19:00:00Z'
    }));

    TestBed.configureTestingModule({
      imports: [ActiveSessionComponent],
      providers: [
        { provide: AuthService, useValue: mockAuth },
        { provide: WorkoutService, useValue: mockWorkouts },
        { provide: ActivatedRoute, useValue: mockRoute },
        { provide: Router, useValue: mockRouter }
      ]
    });

    fixture = TestBed.createComponent(ActiveSessionComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('view state machine', () => {

    it('starts in loading view before ngOnInit runs', () => {
      expect(component.view()).toBe('loading');
    });

    it('transitions to active and seeds session, plan, and activeEntryId on successful load', () => {
      fixture.detectChanges();

      expect(component.view()).toBe('active');
      expect(component.session()).toEqual(sampleSession);
      expect(component.plan()).toEqual(samplePlan);
      // First entry is auto-activated so the user always has a row to work on.
      expect(component.activeEntryId()).toBe(ENTRY_ID);
    });

    it('transitions to error view when the forkJoin load fails', () => {
      mockWorkouts.getSession.mockReturnValue(throwError(() => new Error('boom')));

      fixture.detectChanges();

      expect(component.view()).toBe('error');
      expect(component.error()).toBe('Could not load your session.');
    });

    it('transitions to summary and halts the global timer after finishSession', () => {
      fixture.detectChanges();
      // Let the global timer tick a few times so the assertion is non-trivial.
      vi.advanceTimersByTime(3000);
      expect(component.elapsedSeconds()).toBe(3);

      component.finishSession();

      expect(component.view()).toBe('summary');
      // The finished signal gates the interval callback. Further time
      // advancement must not increment elapsedSeconds even though the
      // interval itself is still subscribed.
      vi.advanceTimersByTime(5000);
      expect(component.elapsedSeconds()).toBe(3);
    });
  });

  describe('timers', () => {

    it('elapsedSeconds increments once per second and elapsedFormatted reads as MM:SS', () => {
      fixture.detectChanges();

      vi.advanceTimersByTime(65000);

      expect(component.elapsedSeconds()).toBe(65);
      // 65 seconds is one minute and five seconds, both zero-padded.
      expect(component.elapsedFormatted()).toBe('01:05');
    });

    it('logSet starts a 90-second rest countdown that decrements each second', () => {
      fixture.detectChanges();

      component.logSet(sampleEntry);

      // The success handler sets restTimers[entryId] to 90 synchronously
      // before the interval fires its first tick.
      expect(component.getRestTimer(ENTRY_ID)).toBe(90);
      vi.advanceTimersByTime(1000);
      expect(component.getRestTimer(ENTRY_ID)).toBe(89);
      vi.advanceTimersByTime(2000);
      expect(component.getRestTimer(ENTRY_ID)).toBe(87);
    });

    it('logSet on an entry with a running rest timer cancels the old interval and restarts at 90', () => {
      fixture.detectChanges();

      component.logSet(sampleEntry);
      vi.advanceTimersByTime(10000);
      expect(component.getRestTimer(ENTRY_ID)).toBe(80);

      // Log another set on the same entry. The old interval should be
      // cancelled via restTimerCleanups and a fresh one started.
      component.logSet(sampleEntry);
      expect(component.getRestTimer(ENTRY_ID)).toBe(90);

      // After one more second, if the old subscription had leaked both
      // intervals would tick and the timer would land at 88. Cancellation
      // means only the new interval is alive, so it lands at 89.
      vi.advanceTimersByTime(1000);
      expect(component.getRestTimer(ENTRY_ID)).toBe(89);
    });
  });

  describe('prescribed sets cap', () => {

    it('returns the plan day set count when the exercise is in the active plan', () => {
      fixture.detectChanges();

      // samplePlan's only day has Bench Press with sets: 4.
      expect(component.prescribedSets(EXERCISE_ID)).toBe(4);
    });

    it('returns the default cap of 999 when no plan is loaded', () => {
      // No active plan in the response, so plan signal stays null.
      mockWorkouts.listPlans.mockReturnValue(of([]));
      fixture.detectChanges();

      expect(component.prescribedSets(EXERCISE_ID)).toBe(999);
    });
  });

  describe('totals', () => {

    it('totalVolume sums weight times reps across completed sets only, skipping incomplete sets', () => {
      // Mixed completed and incomplete sets to exercise the filter branch.
      const sessionWithSets: WorkoutSessionResponse = {
        ...sampleSession,
        exerciseEntries: [{
          ...sampleEntry,
          sets: [
            { id: 1, setNumber: 1, reps: 10, weight: '100.00', rpe: null, completed: true },
            { id: 2, setNumber: 2, reps: 8,  weight: '105.00', rpe: null, completed: true },
            { id: 3, setNumber: 3, reps: 6,  weight: '110.00', rpe: null, completed: false }
          ]
        }]
      };
      component.session.set(sessionWithSets);

      // 100 * 10 + 105 * 8 = 1000 + 840 = 1840. The incomplete third set
      // contributes nothing.
      expect(component.totalVolume).toBe(1840);
    });
  });
});
