package com.fitt.measurements.repository;

import com.fitt.measurements.persistence.entity.BodyWeightEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BodyWeightEntryRepository extends JpaRepository<BodyWeightEntryEntity, Long> {

    // Powers the upsert check
    Optional<BodyWeightEntryEntity> findByOwnerUserIdAndLoggedAt(Long ownerUserId, LocalDate loggedAt);

    // Date range query for 7/30/60/90 day views, most recent first
    List<BodyWeightEntryEntity> findByOwnerUserIdAndLoggedAtBetweenOrderByLoggedAtDesc(
            Long ownerUserId, LocalDate start, LocalDate end);

    // Ownership safe lookup for delete
    Optional<BodyWeightEntryEntity> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}