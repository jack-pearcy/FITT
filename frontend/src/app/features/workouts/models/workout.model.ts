// Muscle groups match the MuscleGroup enum on the backend exactly.
// String literal unions instead of TypeScript enums to avoid extra bundle weight.
export type MuscleGroup = 'CHEST' | 'BACK' | 'SHOULDERS' | 'ARMS' | 'LEGS' | 'GLUTES';

export type SplitType = 'PPL' | 'UPPER_LOWER' | 'FULL_BODY' | 'CUSTOM';

export type ProgressionModel = 'DOUBLE_PROGRESSION' | 'RPE_AUTOREGULATION' | 'LINEAR_PERIODIZATION';

// Response shapes (receive from backend)

export interface ExerciseResponse {
  id: number;
  name: string;
  muscleGroup: MuscleGroup;
  custom: boolean;
}

export interface SetEntryResponse {
  id: number;
  setNumber: number;
  reps: number | null;
  // BigDecimal comes across the wire as a string
  weight: string | null;
  rpe: string | null;
  completed: boolean;
}

export interface ExerciseEntryResponse {
  id: number;
  orderIndex: number;
  exercise: ExerciseResponse;
  sets: SetEntryResponse[];
}

export interface PlanDayExerciseResponse {
  id: number;
  orderIndex: number;
  sets: number;
  reps: number;
  exercise: ExerciseResponse;
}

export interface WorkoutPlanDayResponse {
  id: number;
  orderIndex: number;
  dayLabel: string;
  exercises: PlanDayExerciseResponse[];
}

export interface WorkoutPlanResponse {
  id: number;
  ownerUserId: number;
  name: string;
  daysPerWeek: number;
  splitType: SplitType;
  active: boolean;
  days: WorkoutPlanDayResponse[];
}


export interface WorkoutSessionResponse {
  // Instant from the backend serializes as an ISO-8601 string
  id: number;
  ownerUserId: number;
  planId: number | null;
  startedAt: string;
  finishedAt: string | null;
  notes: string | null;
  exerciseEntries: ExerciseEntryResponse[];
}

export interface ProgressionSuggestionResponse {
  exerciseId: number;
  modelUsed: ProgressionModel;
  // BigDecimal serialized as string
  suggestedWeight: string;
  suggestedReps: number;
  suggestedSets: number;
  reasoning: string;
}

//  Request shapes (what sends to the backend)

export interface CreateExerciseRequest {
  name: string;
  muscleGroup: MuscleGroup;
}

export interface AddPlanDayExerciseRequest {
  exerciseId: number;
  sets: number;
  reps: number;
}

export interface CreateWorkoutPlanDayRequest {
  orderIndex: number;
  dayLabel: string;
  exercises: AddPlanDayExerciseRequest[];
}

export interface CreateWorkoutPlanRequest {
  name: string;
  daysPerWeek: number;
  splitType: SplitType;
  days: CreateWorkoutPlanDayRequest[];
}

export interface CreateWorkoutSessionRequest {
  // ISO-8601 string, matches Instant on the backend
  startedAt: string;
  planId?: number;
  notes?: string;
}

export interface AddExerciseEntryRequest {
  exerciseId: number;
}

export interface LogSetRequest {
  reps: number;
  weight?: number;
  rpe?: number;
}

export interface UpdateProgressionPreferenceRequest {
  progressionModel: ProgressionModel;
}
