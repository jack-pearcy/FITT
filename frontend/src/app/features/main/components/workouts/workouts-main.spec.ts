import { describe, it, beforeEach, afterEach, expect, vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { WorkoutsMainComponent } from './workouts-main';
import { AuthService } from '../../../../core/services/auth.service';
import {
  WorkoutPlanResponse,
  WorkoutSessionResponse
} from '../../../workouts/models/workout.model';

// Component test for the Start button on the Workouts tab. It shares the
// Start path with Today's Lift, so it asks for the open workout first and
// resumes it, and a 409 from the start resumes the session it names. The real
// WorkoutService runs against HttpTestingController. The active plan is set
// directly so each test begins at the Start tap without replaying the load.
describe('WorkoutsMainComponent', () => {

  const USER_ID = 42;
  const PLAN_ID = 7;
  const BASE = `/api/users/${USER_ID}/workout-sessions`;

  const plan: WorkoutPlanResponse = {
    id: PLAN_ID, ownerUserId: USER_ID, name: 'Push Pull',
    daysPerWeek: 2, splitType: 'PPL', active: true,
    days: [
      {
        id: 3, orderIndex: 0, dayLabel: 'Push',
        exercises: [
          { id: 21, orderIndex: 0, sets: 3, reps: 8, exercise: { id: 11, name: 'Bench Press', muscleGroup: 'CHEST', custom: false } }
        ]
      },
      { id: 4, orderIndex: 1, dayLabel: 'Pull', exercises: [] }
    ]
  };

  const session = (id: number): WorkoutSessionResponse => ({
    id, ownerUserId: USER_ID, planId: PLAN_ID,
    startedAt: new Date().toISOString(), finishedAt: null,
    notes: null,
    exerciseEntries: [
      { id: 81, orderIndex: 0, exercise: { id: 11, name: 'Bench Press', muscleGroup: 'CHEST', custom: false }, sets: [] }
    ]
  });

  const mockAuth = { getUserId: vi.fn() };
  const mockRouter = { navigate: vi.fn() };

  let fixture: ComponentFixture<WorkoutsMainComponent>;
  let component: WorkoutsMainComponent;
  let http: HttpTestingController;

  // Today's day comes from the completed count, so answer it first.
  const flushTodaysDay = () =>
    http.expectOne(r => r.url === `${BASE}/count` && r.params.get('planId') === String(PLAN_ID)).flush(0);

  beforeEach(() => {
    vi.resetAllMocks();
    mockAuth.getUserId.mockReturnValue(USER_ID);
    mockRouter.navigate.mockResolvedValue(true);

    TestBed.configureTestingModule({
      imports: [WorkoutsMainComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: mockAuth },
        { provide: Router, useValue: mockRouter }
      ]
    });

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(WorkoutsMainComponent);
    component = fixture.componentInstance;
    component.activePlan.set(plan);
  });

  afterEach(() => {
    http.verify();
  });

  it('resumes the open workout instead of starting another', () => {
    component.startSession();

    flushTodaysDay();
    http.expectOne(`${BASE}/open`).flush(session(55));
    http.expectNone(r => r.method === 'POST');

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/session', 55]);
    expect(component.error()).toBeNull();
    expect(component.actionLoading()).toBe(false);
  });

  it('resumes the session a 409 names when the start finds one open', () => {
    component.startSession();

    flushTodaysDay();
    http.expectOne(`${BASE}/open`).flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne(r => r.method === 'POST' && r.url === BASE).flush(
      { title: 'Open Session Exists', status: 409, properties: { openSessionId: 55 } },
      { status: 409, statusText: 'Conflict' }
    );
    http.expectOne(`${BASE}/55`).flush(session(55));
    http.expectNone(r => r.method === 'POST');

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/session', 55]);
    expect(component.error()).toBeNull();
    expect(component.actionLoading()).toBe(false);
  });

  it('starts the day as before when the server has no /open route', () => {
    component.startSession();

    flushTodaysDay();
    http.expectOne(`${BASE}/open`).flush({ title: 'Method Not Allowed' }, { status: 405, statusText: 'Method Not Allowed' });
    const create = http.expectOne(r => r.method === 'POST' && r.url === BASE);
    expect(create.request.body.planId).toBe(PLAN_ID);
    create.flush({ ...session(60), exerciseEntries: [] });
    const add = http.expectOne(r => r.method === 'POST' && r.url === `${BASE}/60/entries`);
    expect(add.request.body.exerciseId).toBe(11);
    add.flush(session(60));

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/session', 60]);
    expect(component.error()).toBeNull();
  });

  it('keeps the start error when the /open call is unauthorized', () => {
    component.startSession();

    flushTodaysDay();
    http.expectOne(`${BASE}/open`).flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectNone(r => r.method === 'POST');

    expect(mockRouter.navigate).not.toHaveBeenCalled();
    expect(component.error()).toBe('Could not start your session. Try again.');
    expect(component.actionLoading()).toBe(false);
  });
});
