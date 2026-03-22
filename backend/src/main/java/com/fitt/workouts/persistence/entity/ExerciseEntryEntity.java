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
@Table(name = "exercise_entries")
public class ExerciseEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Owning side of the session relationship — holds workout_session_id FK.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_session_id", nullable = false)
    private WorkoutSessionEntity workoutSession;

    // Reference to the exercise library — holds exercise_id FK.
    // Not ownership — the exercise exists independently of this entry.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id", nullable = false)
    private ExerciseEntity exercise;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    // Inverse side — ExerciseEntryEntity does not own this FK.
    // CascadeType.ALL so sets persist and delete with the entry.
    // orphanRemoval so removing a set from the list deletes it from the DB.
    @OneToMany(mappedBy = "exerciseEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SetEntryEntity> setEntries = new ArrayList<>();

    protected ExerciseEntryEntity() {}

    public ExerciseEntryEntity(ExerciseEntity exercise, int orderIndex) {
        this.exercise = exercise;
        this.orderIndex = orderIndex;
    }

    // Syncs both sides of the bidirectional relationship.
    // Always use this instead of manipulating the list directly.
    public void addSetEntry(SetEntryEntity setEntry) {
        setEntries.add(setEntry);
        setEntry.setExerciseEntry(this);
    }

    public void removeSetEntry(SetEntryEntity setEntry) {
        setEntries.remove(setEntry);
        setEntry.setExerciseEntry(null);
    }

    public Long getId() { return id; }
    public WorkoutSessionEntity getWorkoutSession() { return workoutSession; }
    public ExerciseEntity getExercise() { return exercise; }
    public int getOrderIndex() { return orderIndex; }

    // Unmodifiable so callers cannot bypass addSetEntry/removeSetEntry.
    public List<SetEntryEntity> getSetEntries() {
        return Collections.unmodifiableList(setEntries);
    }

    // Called by WorkoutSessionEntity.addExerciseEntry() to sync both sides.
    void setWorkoutSession(WorkoutSessionEntity workoutSession) {
        this.workoutSession = workoutSession;
    }

    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
}
