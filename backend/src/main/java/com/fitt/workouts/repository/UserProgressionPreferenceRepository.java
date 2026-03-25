package com.fitt.workouts.repository;

import com.fitt.workouts.persistence.entity.UserProgressionPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProgressionPreferenceRepository extends JpaRepository<UserProgressionPreferenceEntity, Long> {

    // Returns empty if the user has never set a preference,
    // the service defaults to DOUBLE_PROGRESSION.
    Optional<UserProgressionPreferenceEntity> findByOwnerUserId(Long ownerUserId);
}
