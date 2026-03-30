export type BiologicalSex = 'MALE' | 'FEMALE';

export type ActivityLevel =
  | 'SEDENTARY'
  | 'LIGHTLY_ACTIVE'
  | 'MODERATELY_ACTIVE'
  | 'ACTIVE'
  | 'VERY_ACTIVE';

export type GoalDirection = 'BULK' | 'CUT' | 'MAINTAIN';

export type CalorieGoalMode = 'AUTO' | 'MANUAL';

export type UnitsPreference = 'METRIC' | 'IMPERIAL';

export interface OnboardingBiometrics {
  heightCm:        number;
  weightKg:        number;
  dateOfBirth:     string;
  biologicalSex:   BiologicalSex;
  activityLevel:   ActivityLevel;
  unitsPreference: UnitsPreference;
}

export interface OnboardingGoals {
  goalDirection:   GoalDirection;
  targetWeightKg:  number | null;
  timelineWeeks:   number | null;
  calorieGoalMode: CalorieGoalMode;
  calorieGoal:     number | null;
}
