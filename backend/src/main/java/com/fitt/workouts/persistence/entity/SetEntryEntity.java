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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
// The unique key is the last line of defence against two requests giving two sets
// the same number. The service takes a row lock first, so it should never fire.
// The second key stops one logged set being stored twice when it is sent twice.
@Table(name = "set_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_set_entries_entry_number",
                columnNames = {"exercise_entry_id", "set_number"}),
        @UniqueConstraint(name = "uk_set_entries_entry_client",
                columnNames = {"exercise_entry_id", "client_id"})})
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

    // A random UUID the app made for this set, in lower case. Null when none was sent.
    @Column(name = "client_id", length = 36, columnDefinition = "char(36)")
    private String clientId;

    // When the set was done on the device. Null when the client sent no time.
    @Column(name = "performed_at")
    private Instant performedAt;

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
    public String getClientId() { return clientId; }
    public Instant getPerformedAt() { return performedAt; }

    // Called by ExerciseEntryEntity.addSetEntry() to sync both sides.
    void setExerciseEntry(ExerciseEntryEntity exerciseEntry) {
        this.exerciseEntry = exerciseEntry;
    }

    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public void setReps(Integer reps) { this.reps = reps; }
    public void setRpe(BigDecimal rpe) { this.rpe = rpe; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public void setPerformedAt(Instant performedAt) { this.performedAt = performedAt; }
}