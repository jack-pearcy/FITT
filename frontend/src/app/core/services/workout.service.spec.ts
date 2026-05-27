import { describe, it, beforeEach, afterEach, expect } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { WorkoutService } from './workout.service';
import {
  AddExerciseEntryRequest,
  CreateExerciseRequest,
  CreateWorkoutPlanRequest,
  CreateWorkoutSessionRequest,
  LogSetRequest,
  ProgressionSuggestionResponse,
  UpdateProgressionPreferenceRequest,
  WorkoutPlanResponse,
  WorkoutSessionResponse
} from '../../features/workouts/models/workout.model';

// Service test for WorkoutService. The largest service in the codebase
// with 20 methods across four sections: exercises, workout plans, workout
// sessions, progression. Most are HTTP pass-through with userId path
// interpolation. Notable branches that warrant focused assertions:
// listExercises conditionally adds a muscleGroup query param, activatePlan
// and finishSession POST an empty {} body that the backend requires,
// countCompletedSessions and getProgressionSuggestion forward numeric ids
// as string query params, updateProgressionPreference is the only PUT.
// Service is injected in beforeEach because it is stateless. TestBed
// resets per test so isolation is preserved.
describe('WorkoutService', () => {

  const USER_ID = 42;

  let controller: HttpTestingController;
  let service: WorkoutService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    controller = TestBed.inject(HttpTestingController);
    service = TestBed.inject(WorkoutService);
  });

  afterEach(() => {
    controller.verify();
  });

  describe('Exercises', () => {

    it('listExercises GETs the exercises URL without query params when muscleGroup is omitted', () => {
      service.listExercises(USER_ID).subscribe();

      const req = controller.expectOne(
        r => r.url === `/api/users/${USER_ID}/exercises` && !r.params.has('muscleGroup')
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });

    it('listExercises forwards muscleGroup as a query parameter when supplied', () => {
      service.listExercises(USER_ID, 'CHEST').subscribe();

      const req = controller.expectOne(
        r => r.url === `/api/users/${USER_ID}/exercises` && r.params.get('muscleGroup') === 'CHEST'
      );
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });

    it('createCustomExercise POSTs the request body to the exercises URL', () => {
      const request: CreateExerciseRequest = { name: 'Sumo Deadlift', muscleGroup: 'LEGS' };

      service.createCustomExercise(USER_ID, request).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/exercises`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush({ id: 1, name: 'Sumo Deadlift', muscleGroup: 'LEGS', custom: true });
    });

    it('deleteCustomExercise DELETEs the exercise by id', () => {
      service.deleteCustomExercise(USER_ID, 99).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/exercises/99`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('Workout plans', () => {

    it('createPlan POSTs the request body to the plans URL and delivers the typed response', () => {
      const request: CreateWorkoutPlanRequest = {
        name: 'PPL 6-day',
        daysPerWeek: 6,
        splitType: 'PPL',
        days: []
      };
      const mockResponse: WorkoutPlanResponse = {
        id: 1, ownerUserId: USER_ID, name: 'PPL 6-day',
        daysPerWeek: 6, splitType: 'PPL', active: false, days: []
      };

      let received: WorkoutPlanResponse | undefined;
      service.createPlan(USER_ID, request).subscribe(r => received = r);

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-plans`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(mockResponse);

      expect(received).toEqual(mockResponse);
    });

    it('listPlans GETs the plans URL', () => {
      service.listPlans(USER_ID).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-plans`);
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });

    it('getPlan GETs the plan by id', () => {
      service.getPlan(USER_ID, 7).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-plans/7`);
      expect(req.request.method).toBe('GET');
      req.flush(null);
    });

    it('activatePlan POSTs an empty body to the activate URL', () => {
      service.activatePlan(USER_ID, 7).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-plans/7/activate`);
      expect(req.request.method).toBe('POST');
      // Backend requires a request body even though the operation takes no
      // arguments. Empty object satisfies the JSON content-type path.
      expect(req.request.body).toEqual({});
      req.flush(null);
    });

    it('deletePlan DELETEs the plan by id', () => {
      service.deletePlan(USER_ID, 7).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-plans/7`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('Workout sessions', () => {

    it('createSession POSTs the request body to the sessions URL and delivers the typed response', () => {
      const request: CreateWorkoutSessionRequest = {
        startedAt: '2026-05-26T18:00:00Z',
        planId: 7,
        notes: 'leg day'
      };
      const mockResponse: WorkoutSessionResponse = {
        id: 100, ownerUserId: USER_ID, planId: 7,
        startedAt: '2026-05-26T18:00:00Z', finishedAt: null,
        notes: 'leg day', exerciseEntries: []
      };

      let received: WorkoutSessionResponse | undefined;
      service.createSession(USER_ID, request).subscribe(r => received = r);

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-sessions`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(mockResponse);

      expect(received).toEqual(mockResponse);
    });

    it('listSessions GETs the sessions URL', () => {
      service.listSessions(USER_ID).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-sessions`);
      expect(req.request.method).toBe('GET');
      req.flush([]);
    });

    it('countCompletedSessions GETs the count URL with planId as a string query parameter', () => {
      service.countCompletedSessions(USER_ID, 7).subscribe();

      const req = controller.expectOne(
        r => r.url === `/api/users/${USER_ID}/workout-sessions/count` && r.params.get('planId') === '7'
      );
      expect(req.request.method).toBe('GET');
      req.flush(3);
    });

    it('getSession GETs the session by id', () => {
      service.getSession(USER_ID, 100).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-sessions/100`);
      expect(req.request.method).toBe('GET');
      req.flush(null);
    });

    it('finishSession POSTs an empty body to the finish URL', () => {
      service.finishSession(USER_ID, 100).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-sessions/100/finish`);
      expect(req.request.method).toBe('POST');
      // Same reason as activatePlan: explicit empty body for the JSON
      // content-type path on the backend.
      expect(req.request.body).toEqual({});
      req.flush(null);
    });

    it('addExerciseEntry POSTs the request body to the entries URL', () => {
      const request: AddExerciseEntryRequest = { exerciseId: 5 };

      service.addExerciseEntry(USER_ID, 100, request).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-sessions/100/entries`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(null);
    });

    it('removeExerciseEntry DELETEs the entry by id', () => {
      service.removeExerciseEntry(USER_ID, 100, 300).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-sessions/100/entries/300`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });

    it('logSet POSTs the request body to the sets URL', () => {
      const request: LogSetRequest = { reps: 10, weight: 100, rpe: 8 };

      service.logSet(USER_ID, 100, 300, request).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-sessions/100/entries/300/sets`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(null);
    });

    it('deleteSet DELETEs the set by id', () => {
      service.deleteSet(USER_ID, 100, 300, 400).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/workout-sessions/100/entries/300/sets/400`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('Progression', () => {

    it('getProgressionSuggestion GETs the suggestions URL with exerciseId as a string query parameter, delivers the typed response', () => {
      const mockResponse: ProgressionSuggestionResponse = {
        exerciseId: 5,
        modelUsed: 'DOUBLE_PROGRESSION',
        suggestedWeight: '102.50',
        suggestedReps: 8,
        suggestedSets: 3,
        reasoning: 'Hit top of range last session'
      };

      let received: ProgressionSuggestionResponse | undefined;
      service.getProgressionSuggestion(USER_ID, 5).subscribe(r => received = r);

      const req = controller.expectOne(
        r => r.url === `/api/users/${USER_ID}/progression/suggestions` && r.params.get('exerciseId') === '5'
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);

      expect(received).toEqual(mockResponse);
    });

    it('getProgressionPreference GETs the preference URL', () => {
      service.getProgressionPreference(USER_ID).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/progression/preference`);
      expect(req.request.method).toBe('GET');
      req.flush('DOUBLE_PROGRESSION');
    });

    it('updateProgressionPreference PUTs the request body to the preference URL', () => {
      const request: UpdateProgressionPreferenceRequest = { progressionModel: 'RPE_AUTOREGULATION' };

      service.updateProgressionPreference(USER_ID, request).subscribe();

      const req = controller.expectOne(`/api/users/${USER_ID}/progression/preference`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(request);
      req.flush(null);
    });
  });
});
