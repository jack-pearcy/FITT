package com.fitt.workouts.service;

import com.fitt.workouts.dto.NextLiftResponse;

public interface NextLiftService {

    NextLiftResponse getNextLift(Long ownerUserId);
}