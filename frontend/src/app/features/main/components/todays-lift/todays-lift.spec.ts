import { describe, it, beforeEach, afterEach, expect, vi } from 'vitest';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { TodaysLift } from './todays-lift';
import { AuthService } from '../../../../core/services/auth.service';
import {
  ExerciseResponse,
  WorkoutPlanDayResponse,
  WorkoutSessionResponse
} from '../../../workouts/models/workout.model';

// Component test for the Start button on Today's Lift. The server allows one
// open workout per user, so Start asks for the open one first and resumes it.
// The real WorkoutService runs against HttpTestingController so the HTTP
// status codes drive each path: 200 resumes, 204 starts, a 409 from the start
// resumes the session it names, and a server without the /open route (400,
// 404) still starts as before. The day and plan signals are set directly so
// each test begins at the Start tap without replaying the plan load.
describe('TodaysLift', () => {

  const USER_ID = 42;
  const PLAN_ID = 7;
  const BASE = `/api/users/${USER_ID}/workout-sessions`;

  const day: WorkoutPlanDayResponse = {
    id: 3,
    dayLabel: 'Push',
    orderIndex: 0,
    exercises: []
  };

  const bench: ExerciseResponse = { id: 11, name: 'Bench Press', muscleGroup: 'CHEST', custom: false };
  const press: ExerciseResponse = { id: 12, name: 'Overhead Press', muscleGroup: 'SHOULDERS', custom: false };

  // A day with two exercises, for the cases that fill in a half-built session.
  const pushDay: WorkoutPlanDayResponse = {
    id: 3,
    dayLabel: 'Push',
    orderIndex: 0,
    exercises: [
      { id: 21, orderIndex: 0, sets: 3, reps: 8, exercise: bench },
      { id: 22, orderIndex: 1, sets: 3, reps: 10, exercise: press }
    ]
  };

  const session = (id: number): WorkoutSessionResponse => ({
    id, ownerUserId: USER_ID, planId: PLAN_ID,
    startedAt: '2026-11-25T18:00:00Z', finishedAt: null,
    notes: null, exerciseEntries: []
  });

  const mockAuth = { getUserId: vi.fn() };
  const mockRouter = { navigate: vi.fn() };

  let fixture: ComponentFixture<TodaysLift>;
  let component: TodaysLift;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.resetAllMocks();
    mockAuth.getUserId.mockReturnValue(USER_ID);
    mockRouter.navigate.mockResolvedValue(true);

    TestBed.configureTestingModule({
      imports: [TodaysLift],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: mockAuth },
        { provide: Router, useValue: mockRouter }
      ]
    });

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TodaysLift);
    component = fixture.componentInstance;
    component.todayDay.set(day);
    component.activePlanId.set(PLAN_ID);
  });

  afterEach(() => {
    http.verify();
  });

  it('resumes the open workout instead of starting another', () => {
    component.startSession();

    http.expectOne(`${BASE}/open`).flush(session(55));
    http.expectNone(r => r.method === 'POST' && r.url === BASE);

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/session', 55]);
    expect(component.starting()).toBe(false);
  });

  it('starts a new workout when none is open', () => {
    component.startSession();

    http.expectOne(`${BASE}/open`).flush(null, { status: 204, statusText: 'No Content' });
    const create = http.expectOne(r => r.method === 'POST' && r.url === BASE);
    expect(create.request.body.planId).toBe(PLAN_ID);
    create.flush(session(60));

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/session', 60]);
  });

  it('resumes the session a 409 names when the start finds one open', () => {
    component.startSession();

    http.expectOne(`${BASE}/open`).flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne(r => r.method === 'POST' && r.url === BASE).flush(
      { title: 'Open Session Exists', status: 409, properties: { openSessionId: 55 } },
      { status: 409, statusText: 'Conflict' }
    );
    http.expectOne(`${BASE}/55`).flush(session(55));

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/session', 55]);
    expect(component.error()).toBeNull();
  });

  it.each([
    [404, 'Not Found'],
    [400, 'Bad Request']
  ])('starts as before when the server has no /open route (%i)', (status, statusText) => {
    component.startSession();

    http.expectOne(`${BASE}/open`).flush({ title: statusText }, { status, statusText });
    http.expectOne(r => r.method === 'POST' && r.url === BASE).flush(session(61));

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/session', 61]);
    expect(component.error()).toBeNull();
  });

  it('keeps the start error when the /open call is unauthorized', () => {
    component.startSession();

    http.expectOne(`${BASE}/open`).flush(null, { status: 401, statusText: 'Unauthorized' });
    http.expectNone(r => r.method === 'POST' && r.url === BASE);

    expect(mockRouter.navigate).not.toHaveBeenCalled();
    expect(component.error()).toBe('Could not start your session. Try again.');
    expect(component.starting()).toBe(false);
  });

  it('keeps the start error when the /open call has no connection', () => {
    component.startSession();

    // HttpTestingController reports a ProgressEvent error as status 0.
    http.expectOne(`${BASE}/open`).error(new ProgressEvent('error'));
    http.expectNone(r => r.method === 'POST' && r.url === BASE);

    expect(mockRouter.navigate).not.toHaveBeenCalled();
    expect(component.error()).toBe('Could not start your session. Try again.');
    expect(component.starting()).toBe(false);
  });

  it('shows the start error when a 409 on the start names no open session', () => {
    component.startSession();

    http.expectOne(`${BASE}/open`).flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne(r => r.method === 'POST' && r.url === BASE).flush(
      { title: 'Conflict', status: 409 },
      { status: 409, statusText: 'Conflict' }
    );
    http.expectNone(r => r.method === 'GET' && r.url.startsWith(`${BASE}/`));

    expect(mockRouter.navigate).not.toHaveBeenCalled();
    expect(component.error()).toBe('Could not start your session. Try again.');
    expect(component.starting()).toBe(false);
  });

  it('adds the missing exercises to an open workout before resuming it', () => {
    component.todayDay.set(pushDay);
    component.startSession();

    http.expectOne(`${BASE}/open`).flush(session(55));
    const adds = http.match(r => r.method === 'POST' && r.url === `${BASE}/55/entries`);
    expect(adds.map(a => a.request.body.exerciseId)).toEqual([11, 12]);
    expect(mockRouter.navigate).not.toHaveBeenCalled();
    adds.forEach(a => a.flush(session(55)));

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/session', 55]);
    expect(component.starting()).toBe(false);
  });

  it('adds only the exercises a half-built workout lacks', () => {
    component.todayDay.set(pushDay);
    component.startSession();

    const partial: WorkoutSessionResponse = {
      ...session(55),
      exerciseEntries: [{ id: 81, orderIndex: 0, exercise: bench, sets: [] }]
    };
    http.expectOne(`${BASE}/open`).flush(partial);
    const add = http.expectOne(r => r.method === 'POST' && r.url === `${BASE}/55/entries`);
    expect(add.request.body.exerciseId).toBe(12);
    add.flush(session(55));

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/session', 55]);
  });

  it('leaves an open workout from another plan as it is', () => {
    component.todayDay.set(pushDay);
    component.startSession();

    http.expectOne(`${BASE}/open`).flush({ ...session(55), planId: PLAN_ID + 1 });
    http.expectNone(r => r.method === 'POST');

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/session', 55]);
  });

  it('adds the missing exercises to the session a 409 names before resuming it', () => {
    component.todayDay.set(pushDay);
    component.startSession();

    http.expectOne(`${BASE}/open`).flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne(r => r.method === 'POST' && r.url === BASE).flush(
      { title: 'Open Session Exists', status: 409, properties: { openSessionId: 55 } },
      { status: 409, statusText: 'Conflict' }
    );
    http.expectOne(`${BASE}/55`).flush(session(55));
    const adds = http.match(r => r.method === 'POST' && r.url === `${BASE}/55/entries`);
    expect(adds.length).toBe(2);
    adds.forEach(a => a.flush(session(55)));

    expect(mockRouter.navigate).toHaveBeenCalledWith(['/session', 55]);
    expect(component.error()).toBeNull();
  });

  it('shows the start error when filling in an open workout fails', () => {
    component.todayDay.set(pushDay);
    component.startSession();

    http.expectOne(`${BASE}/open`).flush(session(55));
    const adds = http.match(r => r.method === 'POST' && r.url === `${BASE}/55/entries`);
    adds[0].flush({ title: 'Server Error' }, { status: 500, statusText: 'Server Error' });
    // forkJoin cancels the sibling request once one fails.
    expect(adds[1].cancelled).toBe(true);

    expect(mockRouter.navigate).not.toHaveBeenCalled();
    expect(component.error()).toBe('Could not start your session. Try again.');
    expect(component.starting()).toBe(false);
  });
});
