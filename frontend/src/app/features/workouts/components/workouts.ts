import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { WorkoutService } from '../../../core/services/workout.service';
import { WorkoutCardComponent } from './workout-card';
import { Workout } from '../models/workout.model';

@Component({
  selector: 'app-workouts',
  standalone: true,
  imports: [FormsModule, WorkoutCardComponent],
  templateUrl: './workouts.html',
  styleUrl: './workouts.scss'
})
export class WorkoutsComponent implements OnInit {

  private readonly userId = 1;

  workouts: Workout[] = [];
  newWorkoutName = '';
  errorMessage = '';
  loading = false;

  constructor(private workoutService: WorkoutService) {}

  ngOnInit(): void {
    this.loadWorkouts();
  }

  onCreateWorkout(): void {
    const name = this.newWorkoutName.trim();
    if (!name) return;

    this.workoutService.createWorkout(this.userId, name).subscribe({
      next: (created) => {
        this.workouts = [created, ...this.workouts];
        this.newWorkoutName = '';
      },
      error: () => {
        this.errorMessage = 'Failed to create workout.';
      }
    });
  }

  onDeleteWorkout(workoutId: number): void {
    this.workoutService.deleteWorkout(this.userId, workoutId).subscribe({
      next: () => {
        this.workouts = this.workouts.filter(w => w.id !== workoutId);
      },
      error: () => {
        this.errorMessage = 'Failed to delete workout.';
      }
    });
  }

  private loadWorkouts(): void {
    this.loading = true;
    this.workoutService.getWorkouts(this.userId).subscribe({
      next: (workouts) => {
        this.workouts = workouts;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Failed to load workouts.';
        this.loading = false;
      }
    });
  }
}
