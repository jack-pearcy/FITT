package com.fitt.measurements.service;

import com.fitt.measurements.persistence.entity.BodyWeightEntryEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BodyWeightService {

    WeightLogResult logWeight(Long ownerUserId, LocalDate loggedAt, BigDecimal weight);

    List<BodyWeightEntryEntity> getEntries(Long ownerUserId, int days);

    BigDecimal getSevenDayAverage(Long ownerUserId);

    // The newest logged weight in kilograms, for other contexts that need the
    // user's current weight. Empty when nothing was logged.
    Optional<BigDecimal> getLatestWeightKg(Long ownerUserId);

    void deleteEntry(Long ownerUserId, Long entryId);
}