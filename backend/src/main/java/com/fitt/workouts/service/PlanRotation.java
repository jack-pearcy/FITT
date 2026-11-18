package com.fitt.workouts.service;

import com.fitt.workouts.persistence.entity.WorkoutSessionEntity;
import com.fitt.workouts.repository.WorkoutSessionRepository;

// Which day of a plan comes next. Used when the next lift is shown and when a
// session is created, so both always agree.
final class PlanRotation {

    private PlanRotation() {}

    // The position of the next day, from 0. dayCount must be at least 1.
    //
    // The next day is the one after the day the user last finished. That holds
    // when an older workout is deleted and when a day is added to the plan, which
    // counting finished sessions does not: deleting one moved the plan back a
    // day, and adding a day changed what the count pointed at.
    //
    // Sessions saved before the plan day was recorded have no day. If the last
    // finished session is one of those, the count is all there is to go on.
    static int nextDayIndex(WorkoutSessionRepository sessions, Long ownerUserId, Long planId, int dayCount) {
        Integer lastDay = sessions
                .findFirstByOwnerUserIdAndPlanIdAndFinishedAtIsNotNullOrderByFinishedAtDesc(ownerUserId, planId)
                .map(WorkoutSessionEntity::getPlanDayIndex)
                .orElse(null);
        if (lastDay != null) {
            // The modulo also covers a plan that has lost days since that session.
            return (lastDay + 1) % dayCount;
        }
        long finished = sessions.countByOwnerUserIdAndPlanIdAndFinishedAtIsNotNull(ownerUserId, planId);
        return (int) (finished % dayCount);
    }
}
