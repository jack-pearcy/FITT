import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { inject } from '@angular/core';

export interface SaveBiometricsRequest {
  heightCm:        number;
  weightKg:        number;
  dateOfBirth:     string;
  biologicalSex:   'MALE' | 'FEMALE';
  activityLevel:   'SEDENTARY' | 'LIGHTLY_ACTIVE' | 'MODERATELY_ACTIVE' | 'ACTIVE' | 'VERY_ACTIVE';
  unitsPreference: 'METRIC' | 'IMPERIAL';
}

export interface SaveGoalsRequest {
  goalDirection:   'BULK' | 'CUT' | 'MAINTAIN';
  targetWeightKg:  number | null;
  timelineWeeks:   number | null;
  calorieGoalMode: 'AUTO' | 'MANUAL';
  calorieGoal:     number | null;
}

export interface OnboardingStatusResponse {
  biometricsComplete: boolean;
  goalsComplete:      boolean;
  onboardingComplete: boolean;
}

export interface TdeeResponse {
  tdee: number;
}

export interface GoalsResponse {
  goalDirection:   'BULK' | 'CUT' | 'MAINTAIN';
  targetWeightKg:  number | null;
  timelineWeeks:   number | null;
  calorieGoalMode: 'AUTO' | 'MANUAL';
  calorieGoal:     number | null;
}

@Injectable({ providedIn: 'root' })
export class IdentityService {

  private http = inject(HttpClient);

  saveBiometrics(
    userId: number,
    request: SaveBiometricsRequest
  ): Observable<void> {
    return this.http.post<void>(
      `/api/users/${userId}/identity/onboarding/biometrics`,
      request
    );
  }

  saveGoals(
    userId: number,
    request: SaveGoalsRequest
  ): Observable<void> {
    return this.http.post<void>(
      `/api/users/${userId}/identity/goals`,
      request
    );
  }

  getOnboardingStatus(userId: number): Observable<OnboardingStatusResponse> {
    return this.http.get<OnboardingStatusResponse>(
      `/api/users/${userId}/identity/onboarding/status`
    );
  }

  getTdee(userId: number): Observable<TdeeResponse> {
    return this.http.get<TdeeResponse>(
      `/api/users/${userId}/identity/onboarding/tdee`
    );
  }

  getGoals(userId: number): Observable<GoalsResponse> {
    return this.http.get<GoalsResponse>(
      `/api/users/${userId}/identity/goals`
    );
  }
}
