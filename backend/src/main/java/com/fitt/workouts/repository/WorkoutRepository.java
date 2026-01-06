package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.WorkoutEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface WorkoutRepository extends JpaRepository<WorkoutEntity, Long> {

    Optional<WorkoutEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    boolean existsByIdAndOwnerUserId(Long id, Long ownerUserId);
}
