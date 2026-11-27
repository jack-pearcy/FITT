package com.fitt.workouts.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
// A start that is sent twice carries the same client id. The key lets only one in.
@Table(name = "workout_sessions", uniqueConstraints = @UniqueConstraint(
        name = "uk_workout_sessions_owner_client", columnNames = {"owner_user_id", "client_id"}))
public class WorkoutSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Ownership anchor — checked by the controller before any service call.
    // Long by ID only — the workouts domain never imports from identity.
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    // Nullable — sessions created before plan tracking was added have no planId.
    // Used to count completed sessions per plan for day progression.
    @Column(name = "plan_id")
    private Long planId;

    // Which day of the plan this session trained, as that day's position in the
    // plan (0 for the first). Null for a session with no plan, and for sessions
    // saved before this column existed. A position, not a day id, because editing
    // a plan replaces its day rows and their ids.
    @Column(name = "plan_day_index")
    private Integer planDayIndex;

    // Instant is timezone-safe. Always stored as UTC in MySQL DATETIME(6).
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    // Null means session is still in progress.
    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // A random UUID the app made for this start, in lower case. Null for sessions
    // started by a client that sends none.
    @Column(name = "client_id", length = 36, columnDefinition = "char(36)")
    private String clientId;

    // Inverse side — WorkoutSessionEntity does not own the FK column.
    // CascadeType.ALL propagates persist, merge, remove down to entries.
    // orphanRemoval deletes an entry from the DB when removed from this list.
    @OneToMany(mappedBy = "workoutSession", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExerciseEntryEntity> exerciseEntries = new ArrayList<>();

    protected WorkoutSessionEntity() {}

    public WorkoutSessionEntity(Long ownerUserId, Instant startedAt) {
        this.ownerUserId = ownerUserId;
        this.startedAt = startedAt;
    }

    // Always use these helpers — they sync both sides of the relationship.
    public void addExerciseEntry(ExerciseEntryEntity entry) {
        exerciseEntries.add(entry);
        entry.setWorkoutSession(this);
    }

    public void removeExerciseEntry(ExerciseEntryEntity entry) {
        exerciseEntries.remove(entry);
        entry.setWorkoutSession(null);
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getNotes() { return notes; }
    public Long getPlanId() { return planId; }
    public Integer getPlanDayIndex() { return planDayIndex; }
    public String getClientId() { return clientId; }

    public List<ExerciseEntryEntity> getExerciseEntries() {
        return Collections.unmodifiableList(exerciseEntries);
    }

    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public void setPlanDayIndex(Integer planDayIndex) { this.planDayIndex = planDayIndex; }
    public void setClientId(String clientId) { this.clientId = clientId; }
}
