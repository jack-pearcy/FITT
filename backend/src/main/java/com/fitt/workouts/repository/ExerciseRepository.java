package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.ExerciseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExerciseRepository extends JpaRepository<ExerciseEntity, Long> {

    // All global exercises, owner_user_id IS NULL.
    List<ExerciseEntity> findByOwnerUserIdIsNull();

    // An exercise the user may reference: a global library entry (owner null) or one
    // of the user's own custom exercises. A custom exercise owned by someone else
    // returns empty, so it reads as not found rather than leaking across users.
    @Query("SELECT e FROM ExerciseEntity e WHERE e.id = :id "
            + "AND (e.ownerUserId IS NULL OR e.ownerUserId = :ownerUserId)")
    Optional<ExerciseEntity> findAccessibleById(@Param("id") Long id, @Param("ownerUserId") Long ownerUserId);

    // All custom exercises belonging to one user.
    List<ExerciseEntity> findByOwnerUserId(Long ownerUserId);

    // Global exercises plus this user's custom exercises.
    // Used by the exercise picker so the user sees everything available to them.
    List<ExerciseEntity> findByOwnerUserIdIsNullOrOwnerUserId(Long ownerUserId);

    // Ownership check before editing or deleting a custom exercise.
    boolean existsByIdAndOwnerUserId(Long id, Long ownerUserId);
}
