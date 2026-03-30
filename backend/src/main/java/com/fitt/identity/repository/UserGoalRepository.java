package com.fitt.identity.repository;

import com.fitt.identity.domain.UserGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserGoalRepository
        extends JpaRepository<UserGoalEntity, Long> {

    Optional<UserGoalEntity> findByOwnerUserId(Long ownerUserId);
    boolean existsByOwnerUserId(Long ownerUserId);
}