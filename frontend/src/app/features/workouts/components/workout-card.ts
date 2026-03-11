import { Component, Input, Output, EventEmitter } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Workout } from '../models/workout.model';

@Component({
  selector: 'app-workout-card',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './workout-card.html',
  styleUrl: './workout-card.scss'
})
export class WorkoutCardComponent {

  @Input() workout!: Workout;
  @Output() deleteRequested = new EventEmitter<number>();

  onDelete(): void {
    this.deleteRequested.emit(this.workout.id);
  }
}
