import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Workout } from '../../features/workouts/models/workout.model';

@Injectable({ providedIn: 'root' })
export class WorkoutService {

  constructor(private http: HttpClient) {}

  createWorkout(userId: number, name: string): Observable<Workout> {
    return this.http.post<Workout>(`/api/users/${userId}/workouts`, { name });
  }

  getWorkout(userId: number, workoutId: number): Observable<Workout> {
    return this.http.get<Workout>(`/api/users/${userId}/workouts/${workoutId}`);
  }

  deleteWorkout(userId: number, workoutId: number): Observable<void> {
    return this.http.delete<void>(`/api/users/${userId}/workouts/${workoutId}`);
  }
}
