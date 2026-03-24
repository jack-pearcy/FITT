package com.fitt.measurements.service;

import com.fitt.measurements.persistence.entity.BodyWeightEntryEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface BodyWeightService {

    WeightLogResult logWeight(Long ownerUserId, LocalDate loggedAt, BigDecimal weight);

    List<BodyWeightEntryEntity> getEntries(Long ownerUserId, int days);

    BigDecimal getSevenDayAverage(Long ownerUserId);

    void deleteEntry(Long ownerUserId, Long entryId);
}