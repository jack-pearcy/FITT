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

import java.math.BigDecimal;

@Entity
@Table(name = "set_entries")
public class SetEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owning side — this is where the FK column lives.
    // LAZY because we never need to load upward through the hierarchy.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_entry_id", nullable = false)
    private ExerciseEntryEntity exerciseEntry;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    // Nullable — bodyweight exercises have no weight.
    @Column(precision = 6, scale = 2)
    private BigDecimal weight;

    // Nullable — timed sets don't use reps.
    private Integer reps;

    // Nullable — RPE is always optional.
    @Column(precision = 3, scale = 1)
    private BigDecimal rpe;

    @Column(nullable = false)
    private boolean completed = false;

    protected SetEntryEntity() {}

    public SetEntryEntity(int setNumber, BigDecimal weight, Integer reps, BigDecimal rpe) {
        this.setNumber = setNumber;
        this.weight = weight;
        this.reps = reps;
        this.rpe = rpe;
    }

    public Long getId() { return id; }
    public ExerciseEntryEntity getExerciseEntry() { return exerciseEntry; }
    public int getSetNumber() { return setNumber; }
    public BigDecimal getWeight() { return weight; }
    public Integer getReps() { return reps; }
    public BigDecimal getRpe() { return rpe; }
    public boolean isCompleted() { return completed; }

    // Called by ExerciseEntryEntity.addSetEntry() to sync both sides.
    void setExerciseEntry(ExerciseEntryEntity exerciseEntry) {
        this.exerciseEntry = exerciseEntry;
    }

    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public void setReps(Integer reps) { this.reps = reps; }
    public void setRpe(BigDecimal rpe) { this.rpe = rpe; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}