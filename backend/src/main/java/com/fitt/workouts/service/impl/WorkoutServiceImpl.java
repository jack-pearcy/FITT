package com.fitt.workouts.service.impl;

import com.fitt.workouts.service.WorkoutService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
  This is where workout business logic lives (Phase 4).
  @Service makes it discoverable via Spring component scanning.
  @Transactional belongs on the concrete class/methods (Spring team recommendation).
*/
@Service
@Transactional
public class WorkoutServiceImpl implements WorkoutService {

    @Override
    public void createWorkout(Long userId, String name) {
        // Phase 4 step: real logic comes next.
        // For now, skeleton only so we can commit the structure cleanly.
    }
}
