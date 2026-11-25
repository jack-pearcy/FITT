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
});
