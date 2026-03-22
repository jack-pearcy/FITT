package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.ExerciseEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExerciseEntryRepository extends JpaRepository<ExerciseEntryEntity, Long> {

    // All entries for a given session, ordered by position.
    List<ExerciseEntryEntity> findByWorkoutSession_IdOrderByOrderIndex(Long workoutSessionId);

    // Single entry by ID scoped to a session, prevents fetching entries
    // that don't belong to the session being worked with.
    Optional<ExerciseEntryEntity> findByIdAndWorkoutSession_Id(Long id, Long workoutSessionId);
}