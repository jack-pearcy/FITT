package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.SetEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SetEntryRepository extends JpaRepository<SetEntryEntity, Long> {

    // All sets for a given exercise entry, ordered by set number.
    List<SetEntryEntity> findByExerciseEntry_IdOrderBySetNumber(Long exerciseEntryId);

    // Single set by ID scoped to an exercise entry.
    Optional<SetEntryEntity> findByIdAndExerciseEntry_Id(Long id, Long exerciseEntryId);
}
