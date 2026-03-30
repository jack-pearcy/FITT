package com.fitt.identity.repository;

import com.fitt.identity.domain.UserBiometricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserBiometricsRepository
        extends JpaRepository<UserBiometricsEntity, Long> {

    Optional<UserBiometricsEntity> findByOwnerUserId(Long ownerUserId);
    boolean existsByOwnerUserId(Long ownerUserId);
}