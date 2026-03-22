package com.fitt.workouts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "plan_day_exercises")
public class PlanDayExerciseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owning side — holds workout_plan_day_id FK.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_plan_day_id", nullable = false)
    private WorkoutPlanDayEntity workoutPlanDay;

    // Reference to the exercise library — holds exercise_id FK.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private ExerciseEntity exercise;

    @Column(nullable = false)
    private int sets;

    @Column(nullable = false)
    private int reps;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    protected PlanDayExerciseEntity() {}

    public PlanDayExerciseEntity(ExerciseEntity exercise, int sets, int reps, int orderIndex) {
        this.exercise = exercise;
        this.sets = sets;
        this.reps = reps;
        this.orderIndex = orderIndex;
    }

    public Long getId() { return id; }
    public WorkoutPlanDayEntity getWorkoutPlanDay() { return workoutPlanDay; }
    public ExerciseEntity getExercise() { return exercise; }
    public int getSets() { return sets; }
    public int getReps() { return reps; }
    public int getOrderIndex() { return orderIndex; }

    // Called by WorkoutPlanDayEntity.addExercise() to sync both sides.
    void setWorkoutPlanDay(WorkoutPlanDayEntity workoutPlanDay) {
        this.workoutPlanDay = workoutPlanDay;
    }

    public void setSets(int sets) { this.sets = sets; }
    public void setReps(int reps) { this.reps = reps; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
