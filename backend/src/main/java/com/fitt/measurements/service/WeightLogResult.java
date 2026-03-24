package com.fitt.measurements.service;

import com.fitt.measurements.persistence.entity.BodyWeightEntryEntity;

// Wraps the upsert result so the controller can pick 201 vs 200
public record WeightLogResult(BodyWeightEntryEntity entry, boolean created) {
}
