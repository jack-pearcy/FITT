import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import {catchError, forkJoin, Observable, of, switchMap, throwError} from 'rxjs';
import {
  AddExerciseEntryRequest,
  CreateExerciseRequest,
  CreateWorkoutPlanRequest,
  CreateWorkoutSessionRequest,
  ExerciseResponse,
  LogSetRequest,
  MuscleGroup,
  ProgressionModel,
  ProgressionSuggestionResponse,
  UpdateProgressionPreferenceRequest, WorkoutPlanDayResponse,
  WorkoutPlanResponse,
  WorkoutSessionResponse
} from '../../features/workouts/models/workout.model';
import {map} from 'rxjs/operators';

// Reads the open workout id from a 409 "Open Session Exists" problem. Spring Boot
// nests problem properties under "properties", so that comes first, with a
// top-level openSessionId as the fallback. Null for any other error.
export function openSessionIdFrom(error: unknown): number | null {
  if (!(error instanceof HttpErrorResponse) || error.status !== 409) return null;
  const body = error.error as { openSessionId?: unknown; properties?: { openSessionId?: unknown } } | null;
  const id = body?.properties?.openSessionId ?? body?.openSessionId;
  return typeof id === 'number' ? id : null;
}

// An empty open workout younger than this may still be getting its exercises
// from the device that started it, so resuming leaves it alone.
export const RESUME_FILL_MIN_AGE_MS = 60_000;

@Injectable({ providedIn: 'root' })
export class WorkoutService {

  private readonly http = inject(HttpClient);

  // -- Exercises --

  listExercises(userId: number, muscleGroup?: MuscleGroup): Observable<ExerciseResponse[]> {
    const params: Record<string, string> = {};
    if (muscleGroup) params['muscleGroup'] = muscleGroup;
    return this.http.get<ExerciseResponse[]>(
      `/api/users/${userId}/exercises`,
      { params }
    );
  }

  createCustomExercise(userId: number, request: CreateExerciseRequest): Observable<ExerciseResponse> {
    return this.http.post<ExerciseResponse>(
      `/api/users/${userId}/exercises`,
      request
    );
  }

  deleteCustomExercise(userId: number, exerciseId: number): Observable<void> {
    return this.http.delete<void>(`/api/users/${userId}/exercises/${exerciseId}`);
  }

  // -- Workout plans --

  createPlan(userId: number, request: CreateWorkoutPlanRequest): Observable<WorkoutPlanResponse> {
    return this.http.post<WorkoutPlanResponse>(
      `/api/users/${userId}/workout-plans`,
      request
    );
  }

  listPlans(userId: number): Observable<WorkoutPlanResponse[]> {
    return this.http.get<WorkoutPlanResponse[]>(`/api/users/${userId}/workout-plans`);
  }

  getPlan(userId: number, planId: number): Observable<WorkoutPlanResponse> {
    return this.http.get<WorkoutPlanResponse>(`/api/users/${userId}/workout-plans/${planId}`);
  }

  activatePlan(userId: number, planId: number): Observable<WorkoutPlanResponse> {
    return this.http.post<WorkoutPlanResponse>(
      `/api/users/${userId}/workout-plans/${planId}/activate`,
      {}
    );
  }

  deletePlan(userId: number, planId: number): Observable<void> {
    return this.http.delete<void>(`/api/users/${userId}/workout-plans/${planId}`);
  }

  // -- Workout sessions --

  createSession(userId: number, request: CreateWorkoutSessionRequest): Observable<WorkoutSessionResponse> {
    return this.http.post<WorkoutSessionResponse>(
      `/api/users/${userId}/workout-sessions`,
      request
    );
  }

  // The workout started and not yet finished, so Start can resume it instead of
  // starting another. The server answers 204 with no body when none is open.
  getOpenSession(userId: number): Observable<WorkoutSessionResponse | null> {
    return this.http.get<WorkoutSessionResponse>(
      `/api/users/${userId}/workout-sessions/open`,
      { observe: 'response' }
    ).pipe(map(response => response.status === 204 ? null : response.body));
  }

  listSessions(userId: number): Observable<WorkoutSessionResponse[]> {
    return this.http.get<WorkoutSessionResponse[]>(`/api/users/${userId}/workout-sessions`);
  }

  // Keeps count so user can pick up at incomplete sessions
  countCompletedSessions(userId: number, planId: number): Observable<number> {
    const params: Record<string, string> = { planId: planId.toString() };
    return this.http.get<number>(
      `/api/users/${userId}/workout-sessions/count`,
      { params }
    );
  }

  getSession(userId: number, sessionId: number): Observable<WorkoutSessionResponse> {
    return this.http.get<WorkoutSessionResponse>(
      `/api/users/${userId}/workout-sessions/${sessionId}`
    );
  }

  // Creates a session for the given day and populates it with that day's
  // exercises, then emits the created session so the caller can route to it.
  startSessionForDay(userId: number, planId: number, day: WorkoutPlanDayResponse): Observable<WorkoutSessionResponse> {
    return this.createSession(userId, {
      startedAt: new Date().toISOString(),
      planId
    }).pipe(
      switchMap(session => this.addDayExercises(userId, session, day))
    );
  }

  // The one Start path for every web screen. It asks for the open workout
  // first and resumes it. With none open it starts the day as before. If the
  // start still meets an open workout, the 409 names it and that one resumes.
  // A server without the /open route answers 400, 404 or 405, which reads as
  // none open so Start works as before. Unauthorized and no connection fail.
  startOrResumeForDay(userId: number, planId: number, day: WorkoutPlanDayResponse): Observable<WorkoutSessionResponse> {
    return this.getOpenSession(userId).pipe(
      catchError(err => {
        const status = err instanceof HttpErrorResponse ? err.status : 0;
        return status === 0 || status === 401 ? throwError(() => err) : of(null);
      }),
      switchMap(open => open !== null
        ? this.resumeSessionForDay(userId, planId, open, day)
        : this.startSessionForDay(userId, planId, day).pipe(
            catchError(err => {
              const openId = openSessionIdFrom(err);
              return openId === null
                ? throwError(() => err)
                : this.getSession(userId, openId).pipe(
                    switchMap(found => this.resumeSessionForDay(userId, planId, found, day))
                  );
            })
          ))
    );
  }

  // Resumes the open workout as it is. The one exception is a start that
  // failed before adding any exercise: a session of this plan with no entries,
  // started over a minute ago, gets the day's exercises. A session with any
  // entry may have had exercises removed on purpose or belong to another day,
  // and a younger empty one may still be filling on another device, so those
  // are left alone and any gaps are fixed on the session screen.
  private resumeSessionForDay(
    userId: number,
    planId: number,
    session: WorkoutSessionResponse,
    day: WorkoutPlanDayResponse
  ): Observable<WorkoutSessionResponse> {
    const startedAt = Date.parse(session.startedAt);
    const settled = Date.now() - startedAt >= RESUME_FILL_MIN_AGE_MS;
    if (session.planId !== planId || session.exerciseEntries.length > 0 || !settled) {
      return of(session);
    }
    return this.addDayExercises(userId, session, day);
  }

  // Adds an entry for each of the day's exercises in parallel, then emits the
  // session itself. A day with no exercises keeps the session as it is.
  private addDayExercises(
    userId: number,
    session: WorkoutSessionResponse,
    day: WorkoutPlanDayResponse
  ): Observable<WorkoutSessionResponse> {
    const entries = day.exercises
      .map(ex => this.addExerciseEntry(userId, session.id, { exerciseId: ex.exercise.id }));
    if (entries.length === 0) {
      return of(session);
    }
    return forkJoin(entries).pipe(map(() => session));
  }

  // Reusable method that calculates today's current session
  getTodaysDay(userId: number, plan: WorkoutPlanResponse): Observable<WorkoutPlanDayResponse> {
    return this.countCompletedSessions(userId, plan.id).pipe(
      map(completedCount => {
        const sortedDays = plan.days.slice().sort((a, b) => a.orderIndex - b.orderIndex);
        const dayIndex   = Number(completedCount) % sortedDays.length;
        return sortedDays[dayIndex];
      })
    );
  }

  finishSession(userId: number, sessionId: number): Observable<WorkoutSessionResponse> {
    return this.http.post<WorkoutSessionResponse>(
      `/api/users/${userId}/workout-sessions/${sessionId}/finish`,
      {}
    );
  }

  addExerciseEntry(
    userId: number,
    sessionId: number,
    request: AddExerciseEntryRequest
  ): Observable<WorkoutSessionResponse> {
    return this.http.post<WorkoutSessionResponse>(
      `/api/users/${userId}/workout-sessions/${sessionId}/entries`,
      request
    );
  }

  removeExerciseEntry(
    userId: number,
    sessionId: number,
    entryId: number
  ): Observable<WorkoutSessionResponse> {
    return this.http.delete<WorkoutSessionResponse>(
      `/api/users/${userId}/workout-sessions/${sessionId}/entries/${entryId}`
    );
  }

  logSet(
    userId: number,
    sessionId: number,
    entryId: number,
    request: LogSetRequest
  ): Observable<WorkoutSessionResponse> {
    return this.http.post<WorkoutSessionResponse>(
      `/api/users/${userId}/workout-sessions/${sessionId}/entries/${entryId}/sets`,
      request
    );
  }

  deleteSet(
    userId: number,
    sessionId: number,
    entryId: number,
    setId: number
  ): Observable<WorkoutSessionResponse> {
    return this.http.delete<WorkoutSessionResponse>(
      `/api/users/${userId}/workout-sessions/${sessionId}/entries/${entryId}/sets/${setId}`
    );
  }

  // -- Progression --

  getProgressionSuggestion(
    userId: number,
    exerciseId: number
  ): Observable<ProgressionSuggestionResponse> {
    return this.http.get<ProgressionSuggestionResponse>(
      `/api/users/${userId}/progression/suggestions`,
      { params: { exerciseId: exerciseId.toString() } }
    );
  }

  getProgressionPreference(userId: number): Observable<ProgressionModel> {
    return this.http.get<ProgressionModel>(`/api/users/${userId}/progression/preference`);
  }

  updateProgressionPreference(
    userId: number,
    request: UpdateProgressionPreferenceRequest
  ): Observable<void> {
    return this.http.put<void>(
      `/api/users/${userId}/progression/preference`,
      request
    );
  }
}
