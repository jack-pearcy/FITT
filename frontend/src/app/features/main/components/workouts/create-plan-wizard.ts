import { Component, DestroyRef, EventEmitter, inject, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../../../core/services/auth.service';
import { WorkoutService } from '../../../../core/services/workout.service';
import { ExerciseRepsPipe, ExerciseSetsPipe } from './exercise-field.pipe';
import {
  AddPlanDayExerciseRequest,
  CreateWorkoutPlanDayRequest,
  ExerciseResponse,
  MuscleGroup,
  SplitType,
  WorkoutPlanResponse,
} from '../../../workouts/models/workout.model';

// One step per screen in the wizard
type WizardStep = 'name' | 'days-per-week' | 'split-type' | 'build-days' | 'review';

interface DayDraft {
  dayLabel: string;
  exercises: AddPlanDayExerciseRequest[];
}

@Component({
  selector: 'app-create-plan-wizard',
  standalone: true,
  imports: [FormsModule, ExerciseSetsPipe, ExerciseRepsPipe],
  templateUrl: './create-plan-wizard.html',
  styleUrl: './create-plan-wizard.scss'
})
export class CreatePlanWizardComponent {

  // Tells the parent the wizard is done so it can cancel or handle the new plan
  @Output() cancelled  = new EventEmitter<void>();
  @Output() planCreated = new EventEmitter<WorkoutPlanResponse>();

  private readonly auth       = inject(AuthService);
  private readonly workouts   = inject(WorkoutService);
  private readonly destroyRef = inject(DestroyRef);

  readonly step         = signal<WizardStep>('name');
  readonly loading      = signal(false);
  readonly error        = signal<string | null>(null);

  // Draft state built up across steps
  planName     = '';
  daysPerWeek  = 3;
  splitType    = signal<SplitType>('PPL');
  days         = signal<DayDraft[]>([]);

  // Exercise library state for the build-days step
  readonly currentDayIndex     = signal(0);
  readonly exerciseLibrary     = signal<ExerciseResponse[]>([]);
  readonly libraryLoading      = signal(false);
  readonly selectedMuscleGroup = signal<MuscleGroup | 'ALL'>('ALL');

  readonly muscleGroups: Array<MuscleGroup | 'ALL'> = [
    'ALL', 'CHEST', 'BACK', 'SHOULDERS', 'ARMS', 'LEGS', 'GLUTES'
  ];

  readonly splitTypes: SplitType[] = ['PPL', 'UPPER_LOWER', 'FULL_BODY', 'CUSTOM'];

  readonly splitTypeLabels: Record<SplitType, string> = {
    PPL:         'Push / Pull / Legs',
    UPPER_LOWER: 'Upper / Lower',
    FULL_BODY:   'Full Body',
    CUSTOM:      'Custom'
  };

  // Default day labels based on split type so the user has a sensible starting point
  private readonly defaultLabels: Record<SplitType, string[]> = {
    PPL:         ['Push', 'Pull', 'Legs', 'Push', 'Pull', 'Legs', 'Rest'],
    UPPER_LOWER: ['Upper', 'Lower', 'Upper', 'Lower', 'Upper', 'Lower', 'Rest'],
    FULL_BODY:   ['Full Body A', 'Full Body B', 'Full Body A', 'Full Body B', 'Full Body A', 'Full Body B', 'Rest'],
    CUSTOM:      ['Day 1', 'Day 2', 'Day 3', 'Day 4', 'Day 5', 'Day 6', 'Day 7']
  };

  get filteredExercises(): ExerciseResponse[] {
    const group = this.selectedMuscleGroup();
    if (group === 'ALL') return this.exerciseLibrary();
    return this.exerciseLibrary().filter(e => e.muscleGroup === group);
  }

  get currentDay(): DayDraft | null {
    return this.days()[this.currentDayIndex()] ?? null;
  }

  get stepValid(): boolean {
    switch (this.step()) {
      case 'name':         return this.planName.trim().length > 0;
      case 'days-per-week': return this.daysPerWeek >= 1 && this.daysPerWeek <= 7;
      case 'split-type':   return true;
      case 'build-days': {
        const idx = this.currentDayIndex();
        const day = this.days()[idx];
        return day ? day.dayLabel.trim().length > 0 : false;
      }
      case 'review':       return true;
      default:             return true;
    }
  }

  next(): void {
    const current = this.step();
    if (current === 'name')          { this.step.set('days-per-week'); return; }
    if (current === 'days-per-week') { this.buildDayDrafts(); this.step.set('split-type'); return; }
    if (current === 'split-type')    { this.applyDefaultLabels(); this.loadExerciseLibrary(); this.step.set('build-days'); return; }
    if (current === 'build-days') {
      const next = this.currentDayIndex() + 1;
      if (next < this.days().length) {
        this.currentDayIndex.set(next);
      } else {
        this.step.set('review');
      }
      return;
    }
    if (current === 'review') { this.submit(); }
  }

  back(): void {
    const current = this.step();
    if (current === 'name')          { this.cancelled.emit(); return; }
    if (current === 'days-per-week') { this.step.set('name'); return; }
    if (current === 'split-type')    { this.step.set('days-per-week'); return; }
    if (current === 'build-days') {
      const prev = this.currentDayIndex() - 1;
      if (prev >= 0) {
        this.currentDayIndex.set(prev);
      } else {
        this.step.set('split-type');
      }
      return;
    }
    if (current === 'review') { this.currentDayIndex.set(this.days().length - 1); this.step.set('build-days'); }
  }

  private buildDayDrafts(): void {
    const count = this.daysPerWeek;
    // Keep existing drafts if the user went back and changed the count
    const existing = this.days();
    const drafts: DayDraft[] = Array.from({ length: count }, (_, i) => ({
      dayLabel:  existing[i]?.dayLabel  ?? '',
      exercises: existing[i]?.exercises ?? []
    }));
    this.days.set(drafts);
  }

  private applyDefaultLabels(): void {
    const labels = this.defaultLabels[this.splitType()];
    this.days.update(list =>
      list.map((d, i) => ({
        ...d,
        // Only apply the default if the user left it blank
        dayLabel: d.dayLabel.trim() || (labels[i] ?? `Day ${i + 1}`)
      }))
    );
  }

  private loadExerciseLibrary(): void {
    const userId = this.auth.getUserId();
    if (userId === null) return;

    this.libraryLoading.set(true);
    this.workouts.listExercises(userId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: exercises => {
          this.exerciseLibrary.set(exercises);
          this.libraryLoading.set(false);
        },
        error: () => {
          this.error.set('Could not load the exercise library.');
          this.libraryLoading.set(false);
        }
      });
  }

  updateDayLabel(index: number, label: string): void {
    this.days.update(list =>
      list.map((d, i) => i === index ? { ...d, dayLabel: label } : d)
    );
  }

  toggleExercise(exercise: ExerciseResponse): void {
    const idx = this.currentDayIndex();
    const day = this.days()[idx];
    const already = day.exercises.find(e => e.exerciseId === exercise.id);

    this.days.update(list =>
      list.map((d, i) => {
        if (i !== idx) return d;
        const exercises = already
          ? d.exercises.filter(e => e.exerciseId !== exercise.id)
          : [...d.exercises, { exerciseId: exercise.id, sets: 3, reps: 8 }];
        return { ...d, exercises };
      })
    );
  }

  isSelected(exerciseId: number): boolean {
    return !!this.currentDay?.exercises.find(e => e.exerciseId === exerciseId);
  }

  updateSets(exerciseId: number, sets: number): void {
    const idx = this.currentDayIndex();
    this.days.update(list =>
      list.map((d, i) => {
        if (i !== idx) return d;
        return {
          ...d,
          exercises: d.exercises.map(e =>
            e.exerciseId === exerciseId ? { ...e, sets } : e
          )
        };
      })
    );
  }

  updateReps(exerciseId: number, reps: number): void {
    const idx = this.currentDayIndex();
    this.days.update(list =>
      list.map((d, i) => {
        if (i !== idx) return d;
        return {
          ...d,
          exercises: d.exercises.map(e =>
            e.exerciseId === exerciseId ? { ...e, reps } : e
          )
        };
      })
    );
  }

  getExerciseName(exerciseId: number): string {
    return this.exerciseLibrary().find(e => e.id === exerciseId)?.name ?? 'Unknown';
  }

  private submit(): void {
    const userId = this.auth.getUserId();
    if (userId === null) return;

    this.loading.set(true);
    this.error.set(null);

    const planDays: CreateWorkoutPlanDayRequest[] = this.days().map((d, i) => ({
      orderIndex: i,
      dayLabel:   d.dayLabel,
      exercises:  d.exercises
    }));

    this.workouts.createPlan(userId, {
      name:        this.planName.trim(),
      daysPerWeek: this.daysPerWeek,
      splitType:   this.splitType(),
      days:        planDays
    }).pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: plan => {
          this.planCreated.emit(plan);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Could not create your plan. Try again.');
          this.loading.set(false);
        }
      });
  }
}
