import { describe, it, beforeEach, afterEach, expect } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import {
  IdentityService,
  OnboardingStatusResponse,
  SaveBiometricsRequest,
  SaveGoalsRequest,
  TdeeResponse,
  GoalsResponse
} from './identity.service';

// Service test for IdentityService. Pure HTTP pass-through: every method
// is a single http.post or http.get with the userId interpolated into the
// URL and the request body forwarded as the JSON payload. No signals, no
// transformations, no side effects. Tests lock down the URL templates,
// the HTTP methods, the request bodies, and that the subscriber receives
// the typed response untouched.
describe('IdentityService', () => {

  const USER_ID = 42;

  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    // Fails the test if any HTTP request the service kicked off was never
    // matched and flushed. Catches leaks.
    controller.verify();
  });

  it('saveBiometrics POSTs the request body to /api/users/{userId}/identity/onboarding/biometrics', () => {
    const service = TestBed.inject(IdentityService);
    const biometrics: SaveBiometricsRequest = {
      heightCm: 180,
      weightKg: 80,
      dateOfBirth: '1990-01-01',
      biologicalSex: 'MALE',
      activityLevel: 'MODERATELY_ACTIVE',
      unitsPreference: 'METRIC'
    };

    service.saveBiometrics(USER_ID, biometrics).subscribe();

    const req = controller.expectOne(`/api/users/${USER_ID}/identity/onboarding/biometrics`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(biometrics);
    req.flush(null);
  });

  it('saveGoals POSTs the request body to /api/users/{userId}/identity/goals', () => {
    const service = TestBed.inject(IdentityService);
    // Mixed nullable fields exercise that null values survive serialization.
    const goals: SaveGoalsRequest = {
      goalDirection: 'CUT',
      targetWeightKg: 75,
      timelineWeeks: 12,
      calorieGoalMode: 'AUTO',
      calorieGoal: null
    };

    service.saveGoals(USER_ID, goals).subscribe();

    const req = controller.expectOne(`/api/users/${USER_ID}/identity/goals`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(goals);
    req.flush(null);
  });

  it('getOnboardingStatus GETs the status URL and delivers the typed response to the subscriber', () => {
    const service = TestBed.inject(IdentityService);
    const mockResponse: OnboardingStatusResponse = {
      biometricsComplete: true,
      goalsComplete: false,
      onboardingComplete: false
    };

    let received: OnboardingStatusResponse | undefined;
    service.getOnboardingStatus(USER_ID).subscribe(r => received = r);

    const req = controller.expectOne(`/api/users/${USER_ID}/identity/onboarding/status`);
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(received).toEqual(mockResponse);
  });

  it('getTdee GETs the tdee URL and delivers the response to the subscriber', () => {
    const service = TestBed.inject(IdentityService);
    const mockResponse: TdeeResponse = { tdee: 2450 };

    let received: TdeeResponse | undefined;
    service.getTdee(USER_ID).subscribe(r => received = r);

    const req = controller.expectOne(`/api/users/${USER_ID}/identity/onboarding/tdee`);
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(received).toEqual(mockResponse);
  });

  it('getGoals GETs the goals URL and delivers the response to the subscriber', () => {
    const service = TestBed.inject(IdentityService);
    const mockResponse: GoalsResponse = {
      goalDirection: 'BULK',
      targetWeightKg: 85,
      timelineWeeks: 16,
      calorieGoalMode: 'MANUAL',
      calorieGoal: 3200
    };

    let received: GoalsResponse | undefined;
    service.getGoals(USER_ID).subscribe(r => received = r);

    const req = controller.expectOne(`/api/users/${USER_ID}/identity/goals`);
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    expect(received).toEqual(mockResponse);
  });
});
