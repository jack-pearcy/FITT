import { Pipe, PipeTransform } from '@angular/core';
import { AddPlanDayExerciseRequest } from '../../../workouts/models/workout.model';

// Reads the sets value for a specific exercise from the days exercise list.
// Used in the wizard so the input binding stays clean in the template.
@Pipe({ name: 'exerciseSets', standalone: true, pure: true })
export class ExerciseSetsPipe implements PipeTransform {
  transform(exercises: AddPlanDayExerciseRequest[] | undefined, exerciseId: number): number {
    return exercises?.find(e => e.exerciseId === exerciseId)?.sets ?? 3;
  }
}

@Pipe({ name: 'exerciseReps', standalone: true, pure: true })
export class ExerciseRepsPipe implements PipeTransform {
  transform(exercises: AddPlanDayExerciseRequest[] | undefined, exerciseId: number): number {
    return exercises?.find(e => e.exerciseId === exerciseId)?.reps ?? 8;
  }
}
