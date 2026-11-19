package com.fitt.identity.repository;

import com.fitt.identity.domain.UserBiometricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserBiometricsRepository
        extends JpaRepository<UserBiometricsEntity, Long> {

    Optional<UserBiometricsEntity> findByOwnerUserId(Long ownerUserId);
    boolean existsByOwnerUserId(Long ownerUserId);

    // Account deletion.
    @Modifying
    @Query("delete from UserBiometricsEntity e where e.ownerUserId = :ownerUserId")
    int deleteAllForUser(@Param("ownerUserId") Long ownerUserId);
}