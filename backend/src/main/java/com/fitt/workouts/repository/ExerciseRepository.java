package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.ExerciseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExerciseRepository extends JpaRepository<ExerciseEntity, Long> {

    // All global exercises, owner_user_id IS NULL.
    List<ExerciseEntity> findByOwnerUserIdIsNull();

    // All custom exercises belonging to one user.
    List<ExerciseEntity> findByOwnerUserId(Long ownerUserId);

    // Global exercises plus this user's custom exercises.
    // Used by the exercise picker so the user sees everything available to them.
    List<ExerciseEntity> findByOwnerUserIdIsNullOrOwnerUserId(Long ownerUserId);

    // Ownership check before editing or deleting a custom exercise.
    boolean existsByIdAndOwnerUserId(Long id, Long ownerUserId);
}
