import { describe, it, expect } from 'vitest';
import { ExerciseSetsPipe, ExerciseRepsPipe } from './exercise-field.pipe';
import { AddPlanDayExerciseRequest } from '../../../workouts/models/workout.model';

// Pipe tests for ExerciseSetsPipe and ExerciseRepsPipe. Each pipe is a
// pure transform with three branches: list undefined, exercise not in
// list, exercise present in list. The defaults (3 sets, 8 reps) are
// domain values used as placeholders in the plan wizard's number inputs
// when the user has not chosen a value yet. Pipes are pure functions so
// no TestBed setup is needed; the pipe is instantiated directly with new.
describe('exercise-field pipes', () => {

  const EXERCISE_ID = 5;

  const sampleEntries: AddPlanDayExerciseRequest[] = [
    { exerciseId: 1, sets: 4, reps: 12 },
    { exerciseId: EXERCISE_ID, sets: 5, reps: 6 },
    { exerciseId: 9, sets: 3, reps: 10 }
  ];

  describe('ExerciseSetsPipe', () => {
    const pipe = new ExerciseSetsPipe();

    it('returns the sets value when the exercise is found in the list', () => {
      expect(pipe.transform(sampleEntries, EXERCISE_ID)).toBe(5);
    });

    it('returns the default 3 when the exercise is not in the list', () => {
      expect(pipe.transform(sampleEntries, 999)).toBe(3);
    });

    it('returns the default 3 when the exercises list is undefined', () => {
      expect(pipe.transform(undefined, EXERCISE_ID)).toBe(3);
    });
  });

  describe('ExerciseRepsPipe', () => {
    const pipe = new ExerciseRepsPipe();

    it('returns the reps value when the exercise is found in the list', () => {
      expect(pipe.transform(sampleEntries, EXERCISE_ID)).toBe(6);
    });

    it('returns the default 8 when the exercise is not in the list', () => {
      expect(pipe.transform(sampleEntries, 999)).toBe(8);
    });

    it('returns the default 8 when the exercises list is undefined', () => {
      expect(pipe.transform(undefined, EXERCISE_ID)).toBe(8);
    });
  });
});
