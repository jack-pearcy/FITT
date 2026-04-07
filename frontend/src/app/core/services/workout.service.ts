import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
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
  UpdateProgressionPreferenceRequest,
  WorkoutPlanResponse,
  WorkoutSessionResponse
} from '../../features/workouts/models/workout.model';

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
