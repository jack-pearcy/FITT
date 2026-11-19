package com.fitt.identity.repository;

import com.fitt.identity.domain.UserGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserGoalRepository
        extends JpaRepository<UserGoalEntity, Long> {

    Optional<UserGoalEntity> findByOwnerUserId(Long ownerUserId);
    boolean existsByOwnerUserId(Long ownerUserId);

    // Account deletion.
    @Modifying
    @Query("delete from UserGoalEntity e where e.ownerUserId = :ownerUserId")
    int deleteAllForUser(@Param("ownerUserId") Long ownerUserId);
}