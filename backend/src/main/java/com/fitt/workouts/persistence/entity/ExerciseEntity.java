package com.fitt.workouts.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercises")
public class ExerciseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null means global exercise available to all users.
    // Non-null means user-created custom exercise.
    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(nullable = false)
    private String name;

    @Column(name = "muscle_group", nullable = false)
    private String muscleGroup;

    // Convenience flag, mirrors whether ownerUserId is null.
    // Avoids null checks in query logic.
    @Column(name = "is_custom", nullable = false)
    private boolean isCustom;

    protected ExerciseEntity() {}

    public ExerciseEntity(Long ownerUserId, String name, String muscleGroup, boolean isCustom) {
        this.ownerUserId = ownerUserId;
        this.name = name;
        this.muscleGroup = muscleGroup;
        this.isCustom = isCustom;
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public String getName() { return name; }
    public String getMuscleGroup() { return muscleGroup; }
    public boolean isCustom() { return isCustom; }

    public void setName(String name) { this.name = name; }
    public void setMuscleGroup(String muscleGroup) { this.muscleGroup = muscleGroup; }
}
