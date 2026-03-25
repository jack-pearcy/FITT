package com.fitt.workouts.persistence.entity;

import com.fitt.workouts.domain.ProgressionModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_progression_preferences")
public class UserProgressionPreferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Same cross-context reference pattern as WorkoutSessionEntity and ExerciseEntity.
    // Long by ID only so the workouts domain never imports from identity.
    @Column(name = "owner_user_id", nullable = false, unique = true)
    private Long ownerUserId;

    // STRING storage so adding new enum values later is safe.
    // ORDINAL would break existing rows if reorder or insert values.
    @Enumerated(EnumType.STRING)
    @Column(name = "progression_model", nullable = false)
    private ProgressionModel progressionModel = ProgressionModel.DOUBLE_PROGRESSION;

    // JPA needs this to rebuild objects from query results
    protected UserProgressionPreferenceEntity() {}

    public UserProgressionPreferenceEntity(Long ownerUserId, ProgressionModel progressionModel) {
        this.ownerUserId = ownerUserId;
        this.progressionModel = progressionModel;
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public ProgressionModel getProgressionModel() { return progressionModel; }

    public void setProgressionModel(ProgressionModel progressionModel) {
        this.progressionModel = progressionModel;
    }
}
