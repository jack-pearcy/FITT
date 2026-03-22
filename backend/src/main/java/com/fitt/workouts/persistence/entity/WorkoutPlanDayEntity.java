package com.fitt.workouts.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "workout_plan_days")
public class WorkoutPlanDayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owning side — holds workout_plan_id FK.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_plan_id", nullable = false)
    private WorkoutPlanEntity workoutPlan;

    @Column(name = "day_label", nullable = false)
    private String dayLabel;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    // Inverse side — cascades all operations down to exercises.
    // orphanRemoval deletes an exercise slot when removed from the list.
    @OneToMany(mappedBy = "workoutPlanDay", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlanDayExerciseEntity> exercises = new ArrayList<>();

    protected WorkoutPlanDayEntity() {}

    public WorkoutPlanDayEntity(String dayLabel, int orderIndex) {
        this.dayLabel = dayLabel;
        this.orderIndex = orderIndex;
    }

    public void addExercise(PlanDayExerciseEntity exercise) {
        exercises.add(exercise);
        exercise.setWorkoutPlanDay(this);
    }

    public void removeExercise(PlanDayExerciseEntity exercise) {
        exercises.remove(exercise);
        exercise.setWorkoutPlanDay(null);
    }

    public Long getId() { return id; }
    public WorkoutPlanEntity getWorkoutPlan() { return workoutPlan; }
    public String getDayLabel() { return dayLabel; }
    public int getOrderIndex() { return orderIndex; }

    public List<PlanDayExerciseEntity> getExercises() {
        return Collections.unmodifiableList(exercises);
    }

    // Called by WorkoutPlanEntity.addDay() to sync both sides.
    void setWorkoutPlan(WorkoutPlanEntity workoutPlan) {
        this.workoutPlan = workoutPlan;
    }

    public void setDayLabel(String dayLabel) { this.dayLabel = dayLabel; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
