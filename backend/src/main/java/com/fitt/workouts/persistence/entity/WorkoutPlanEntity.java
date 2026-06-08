package com.fitt.workouts.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "workout_plans")
public class WorkoutPlanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ownership anchor — Long by ID only, never a join to UserEntity.
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false)
    private String name;

    @Column(name = "days_per_week", nullable = false)
    private int daysPerWeek;

    // PPL, Upper/Lower, Full Body, Custom — stored as String for flexibility.
    @Column(name = "split_type", nullable = false)
    private String splitType;

    // Only one plan active per user at a time. Enforced at service layer.
    @Column(name = "is_active", nullable = false)
    private boolean isActive = false;

    // Inverse side, full cascade means saving the plan saves all its days
    // and their exercises. Deleting the plan cascades all the way down.
    @OneToMany(mappedBy = "workoutPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkoutPlanDayEntity> days = new ArrayList<>();

    protected WorkoutPlanEntity() {
    }

    public WorkoutPlanEntity(Long ownerUserId, String name, int daysPerWeek, String splitType) {
        this.ownerUserId = ownerUserId;
        this.name = name;
        this.daysPerWeek = daysPerWeek;
        this.splitType = splitType;
    }

    public void addDay(WorkoutPlanDayEntity day) {
        days.add(day);
        day.setWorkoutPlan(this);
    }

    public void removeDay(WorkoutPlanDayEntity day) {
        days.remove(day);
        day.setWorkoutPlan(null);
    }

    // a full plan edit clears every day before the new set is added.
    // orphanRemoval deletes the detached rows on flush and the cascade takes the exercises too.
    public void clearDays() {
        days.clear();
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getName() {
        return name;
    }

    public int getDaysPerWeek() {
        return daysPerWeek;
    }

    public String getSplitType() {
        return splitType;
    }

    public boolean isActive() {
        return isActive;
    }

    public List<WorkoutPlanDayEntity> getDays() {
        return Collections.unmodifiableList(days);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDaysPerWeek(int daysPerWeek) {
        this.daysPerWeek = daysPerWeek;
    }

    public void setSplitType(String splitType) {
        this.splitType = splitType;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }
}
