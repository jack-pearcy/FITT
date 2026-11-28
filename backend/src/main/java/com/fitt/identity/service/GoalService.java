package com.fitt.identity.service;

import com.fitt.identity.domain.CalorieGoalMode;
import com.fitt.identity.domain.RateWarning;
import com.fitt.identity.domain.UserBiometricsEntity;
import com.fitt.identity.domain.UserGoalEntity;
import com.fitt.identity.dto.GoalsResponse;
import com.fitt.identity.dto.SaveGoalsRequest;
import com.fitt.identity.repository.UserBiometricsRepository;
import com.fitt.identity.repository.UserGoalRepository;
import com.fitt.identity.service.exception.BiometricsNotFoundException;
import com.fitt.identity.service.exception.GoalNotFoundException;
import com.fitt.identity.service.exception.InvalidGoalException;
import com.fitt.measurements.service.BodyWeightService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

// Owns goal data: reading it, writing it, and resolving the calorie goal.
// Pulled out of onboarding because changing a goal is a normal thing a user
// does any time, not a one time setup step. Biometrics and TDEE still live in
// the picture here because an AUTO calorie goal is derived from them.
@Service
@Transactional(readOnly = true)
public class GoalService {

    private final UserGoalRepository goalRepository;
    private final UserBiometricsRepository biometricsRepository;
    private final TdeeService tdeeService;
    private final CalorieGoalCalculator calorieGoalCalculator;
    private final BodyWeightService bodyWeightService;

    // The same bounds a MANUAL calorie goal has in SaveGoalsRequest.
    private static final int MIN_CALORIE_GOAL = 500;
    private static final int MAX_CALORIE_GOAL = 10000;

    public GoalService(
            UserGoalRepository goalRepository,
            UserBiometricsRepository biometricsRepository,
            TdeeService tdeeService,
            CalorieGoalCalculator calorieGoalCalculator,
            BodyWeightService bodyWeightService
    ) {
        this.bodyWeightService = bodyWeightService;
        this.goalRepository = goalRepository;
        this.biometricsRepository = biometricsRepository;
        this.tdeeService = tdeeService;
        this.calorieGoalCalculator = calorieGoalCalculator;
    }

    @Transactional
    public void saveGoals(Long userId, SaveGoalsRequest request) {
        // Goals need biometrics to exist so TDEE can be calculated in AUTO mode.
        UserBiometricsEntity biometrics = biometricsRepository
                .findByOwnerUserId(userId)
                .orElseThrow(() -> new BiometricsNotFoundException(
                        "biometrics not found for user " + userId
                ));

        Integer calorieGoal = resolveCalorieGoal(request, biometrics);
        // A MANUAL goal is the number the user typed, so an adjustment has
        // nothing to act on there and is not kept.
        Integer adjustment = request.calorieGoalMode() == CalorieGoalMode.AUTO
                ? request.adjustmentKcal()
                : null;

        Optional<UserGoalEntity> existing = goalRepository.findByOwnerUserId(userId);
        UserGoalEntity goal;
        if (existing.isPresent()) {
            goal = existing.get();
            goal.update(
                    request.goalDirection(),
                    request.targetWeightKg(),
                    request.timelineWeeks(),
                    request.calorieGoalMode(),
                    calorieGoal
            );
        } else {
            goal = new UserGoalEntity(
                    userId,
                    request.goalDirection(),
                    request.targetWeightKg(),
                    request.timelineWeeks(),
                    request.calorieGoalMode(),
                    calorieGoal
            );
        }
        goal.setCalorieInputs(request.maintenanceOverrideKcal(), adjustment);
        if (existing.isEmpty()) {
            goalRepository.save(goal);
        }
    }

    public GoalsResponse getGoals(Long userId) {
        UserGoalEntity goal = goalRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> new GoalNotFoundException(
                        "goals not found for user " + userId
                ));

        Optional<UserBiometricsEntity> biometrics = biometricsRepository.findByOwnerUserId(userId);
        Integer override = goal.getMaintenanceOverrideKcal();
        // Null only when the user has no body measures and set no number of their own.
        Integer maintenance = override != null
                ? override
                : biometrics.map(this::estimateMaintenance).orElse(null);
        // The newest logged weight, else the weight given at onboarding.
        BigDecimal currentWeightKg = bodyWeightService.getLatestWeightKg(userId)
                .or(() -> biometrics.map(UserBiometricsEntity::getWeightKg))
                .filter(weight -> weight.signum() > 0)
                .orElse(null);

        Integer calorieGoal = goal.getCalorieGoal();
        BigDecimal weeklyRateKg = null;
        BigDecimal weeklyRatePercent = null;
        RateWarning rateWarning = null;
        if (calorieGoal != null && maintenance != null) {
            weeklyRateKg = calorieGoalCalculator.weeklyRateKg(calorieGoal, maintenance);
            if (currentWeightKg != null) {
                weeklyRatePercent = calorieGoalCalculator
                        .weeklyRatePercent(calorieGoal, maintenance, currentWeightKg);
                rateWarning = calorieGoalCalculator
                        .rateWarning(calorieGoal, maintenance, currentWeightKg);
            }
        }

        return new GoalsResponse(
                goal.getGoalDirection(),
                goal.getTargetWeightKg(),
                goal.getTimelineWeeks(),
                goal.getCalorieGoalMode(),
                calorieGoal,
                maintenance,
                override != null,
                goal.getDailyAdjustmentKcal(),
                weeklyRateKg,
                weeklyRatePercent,
                rateWarning
        );
    }

    // Onboarding asks this to report whether the goals step is done, so it
    // never has to reach into the goal table itself.
    public boolean hasGoals(Long userId) {
        return goalRepository.existsByOwnerUserId(userId);
    }

    private Integer resolveCalorieGoal(
            SaveGoalsRequest request,
            UserBiometricsEntity biometrics
    ) {
        return switch (request.calorieGoalMode()) {
            case AUTO -> {
                // Maintenance is only the starting point. The goal direction moves it
                // above maintenance for a bulk, below for a cut, or leaves it at maintain.
                // The user's own maintenance wins over the estimate, and their own
                // adjustment wins over the built-in bulk or cut percentage.
                int maintenance = request.maintenanceOverrideKcal() != null
                        ? request.maintenanceOverrideKcal()
                        : estimateMaintenance(biometrics);
                if (request.adjustmentKcal() == null) {
                    yield calorieGoalCalculator.adjustForGoal(maintenance, request.goalDirection());
                }
                int adjusted = maintenance + request.adjustmentKcal();
                if (adjusted < MIN_CALORIE_GOAL || adjusted > MAX_CALORIE_GOAL) {
                    throw new InvalidGoalException("adjustmentKcal",
                            "Maintenance plus adjustment must be between "
                                    + MIN_CALORIE_GOAL + " and " + MAX_CALORIE_GOAL);
                }
                yield adjusted;
            }
            case MANUAL -> {
                if (request.calorieGoal() == null) {
                    throw new IllegalArgumentException(
                            "calorie goal is required when mode is MANUAL"
                    );
                }
                yield request.calorieGoal();
            }
        };
    }

    private int estimateMaintenance(UserBiometricsEntity biometrics) {
        return tdeeService.calculate(
                biometrics.getHeightCm(),
                biometrics.getWeightKg(),
                biometrics.getDateOfBirth(),
                biometrics.getBiologicalSex(),
                biometrics.getActivityLevel()
        );
    }
}
