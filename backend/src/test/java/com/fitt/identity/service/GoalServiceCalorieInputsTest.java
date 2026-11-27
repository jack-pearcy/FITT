package com.fitt.identity.service;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import com.fitt.identity.domain.CalorieGoalMode;
import com.fitt.identity.domain.GoalDirection;
import com.fitt.identity.domain.RateWarning;
import com.fitt.identity.domain.UnitsPreference;
import com.fitt.identity.domain.UserBiometricsEntity;
import com.fitt.identity.domain.UserGoalEntity;
import com.fitt.identity.dto.GoalsResponse;
import com.fitt.identity.dto.SaveGoalsRequest;
import com.fitt.identity.repository.UserBiometricsRepository;
import com.fitt.identity.repository.UserGoalRepository;
import com.fitt.identity.service.exception.InvalidGoalException;
import com.fitt.measurements.service.BodyWeightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The user's own maintenance, the daily adjustment and the weekly rate. The
// calculator is the real one here, so the numbers in the response are the
// numbers a client would get. The estimate is pinned at 2,500 kcal.
@ExtendWith(MockitoExtension.class)
class GoalServiceCalorieInputsTest {

    private static final long USER_ID = 1L;
    private static final int ESTIMATE = 2500;

    @Mock
    private UserGoalRepository goalRepository;

    @Mock
    private UserBiometricsRepository biometricsRepository;

    @Mock
    private TdeeService tdeeService;

    @Mock
    private BodyWeightService bodyWeightService;

    private GoalService service;

    @BeforeEach
    void setUp() {
        service = new GoalService(goalRepository, biometricsRepository, tdeeService,
                new CalorieGoalCalculator(), bodyWeightService);
        UserBiometricsEntity biometrics = new UserBiometricsEntity(
                USER_ID, new BigDecimal("180"), new BigDecimal("80"),
                LocalDate.of(1995, 6, 15),
                BiologicalSex.MALE, ActivityLevel.MODERATELY_ACTIVE, UnitsPreference.METRIC);
        lenient().when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(biometrics));
        lenient().when(tdeeService.calculate(any(), any(), any(), any(), any())).thenReturn(ESTIMATE);
        lenient().when(bodyWeightService.getLatestWeightKg(USER_ID)).thenReturn(Optional.empty());
    }

    private UserGoalEntity save(GoalDirection direction, CalorieGoalMode mode, Integer calorieGoal,
                                Integer maintenanceOverrideKcal, Integer adjustmentKcal) {
        when(goalRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.empty());
        service.saveGoals(USER_ID, new SaveGoalsRequest(
                direction, null, null, mode, calorieGoal, maintenanceOverrideKcal, adjustmentKcal));
        ArgumentCaptor<UserGoalEntity> saved = ArgumentCaptor.forClass(UserGoalEntity.class);
        verify(goalRepository).save(saved.capture());
        return saved.getValue();
    }

    private GoalsResponse read(UserGoalEntity goal) {
        when(goalRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(goal));
        return service.getGoals(USER_ID);
    }

    @Test
    void neitherValueSet_keepsTheOldNumbers() {
        UserGoalEntity bulk = save(GoalDirection.BULK, CalorieGoalMode.AUTO, null, null, null);

        // 2,500 plus the built-in 15 percent, exactly as before this change.
        assertThat(bulk.getCalorieGoal()).isEqualTo(2875);
        assertThat(bulk.getMaintenanceOverrideKcal()).isNull();
        assertThat(bulk.getDailyAdjustmentKcal()).isNull();

        GoalsResponse response = read(bulk);
        assertThat(response.goalDirection()).isEqualTo(GoalDirection.BULK);
        assertThat(response.calorieGoalMode()).isEqualTo(CalorieGoalMode.AUTO);
        assertThat(response.calorieGoal()).isEqualTo(2875);
        assertThat(response.maintenanceKcal()).isEqualTo(ESTIMATE);
        assertThat(response.maintenanceIsUserSet()).isFalse();
        assertThat(response.adjustmentKcal()).isNull();
    }

    @Test
    void maintenanceOverride_replacesTheEstimate() {
        UserGoalEntity cut = save(GoalDirection.CUT, CalorieGoalMode.AUTO, null, 3000, null);

        // The built-in 20 percent cut, taken from the user's 3,000.
        assertThat(cut.getCalorieGoal()).isEqualTo(2400);
        verify(tdeeService, never()).calculate(any(), any(), any(), any(), any());

        GoalsResponse response = read(cut);
        assertThat(response.maintenanceKcal()).isEqualTo(3000);
        assertThat(response.maintenanceIsUserSet()).isTrue();
    }

    @Test
    void adjustment_replacesTheBuiltInPercentage() {
        UserGoalEntity bulk = save(GoalDirection.BULK, CalorieGoalMode.AUTO, null, null, 300);

        assertThat(bulk.getCalorieGoal()).isEqualTo(2800);
        assertThat(read(bulk).adjustmentKcal()).isEqualTo(300);
    }

    @Test
    void overrideAndAdjustment_addTogether() {
        UserGoalEntity cut = save(GoalDirection.CUT, CalorieGoalMode.AUTO, null, 2600, -400);

        assertThat(cut.getCalorieGoal()).isEqualTo(2200);
    }

    @Test
    void zeroAdjustment_meansEatAtMaintenance() {
        UserGoalEntity bulk = save(GoalDirection.BULK, CalorieGoalMode.AUTO, null, null, 0);

        assertThat(bulk.getCalorieGoal()).isEqualTo(ESTIMATE);
    }

    @Test
    void adjustmentThatLeavesTooFewCalories_isRefusedUnderItsField() {
        SaveGoalsRequest request = new SaveGoalsRequest(
                GoalDirection.CUT, null, null, CalorieGoalMode.AUTO, null, 1000, -501);

        assertThatThrownBy(() -> service.saveGoals(USER_ID, request))
                .isInstanceOf(InvalidGoalException.class)
                .satisfies(ex -> assertThat(((InvalidGoalException) ex).getField()).isEqualTo("adjustmentKcal"));

        verify(goalRepository, never()).save(any(UserGoalEntity.class));
    }

    @Test
    void manualMode_keepsTheTypedGoalAndDropsTheAdjustment() {
        UserGoalEntity manual = save(GoalDirection.CUT, CalorieGoalMode.MANUAL, 2100, 2600, -400);

        assertThat(manual.getCalorieGoal()).isEqualTo(2100);
        assertThat(manual.getMaintenanceOverrideKcal()).isEqualTo(2600);
        assertThat(manual.getDailyAdjustmentKcal()).isNull();
    }

    @Test
    void savingAgainWithoutTheValues_clearsThem() {
        UserGoalEntity goal = save(GoalDirection.CUT, CalorieGoalMode.AUTO, null, 2600, -400);
        when(goalRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.of(goal));

        service.saveGoals(USER_ID, new SaveGoalsRequest(
                GoalDirection.CUT, null, null, CalorieGoalMode.AUTO, null, null, null));

        assertThat(goal.getMaintenanceOverrideKcal()).isNull();
        assertThat(goal.getDailyAdjustmentKcal()).isNull();
        assertThat(goal.getCalorieGoal()).isEqualTo(2000);
    }

    @Test
    void weeklyRate_usesTheOnboardingWeightWhenNothingIsLogged() {
        // 440 kcal over maintenance at 80 kg: 0.40 kg a week, 0.50 percent, no warning.
        GoalsResponse response = read(save(GoalDirection.BULK, CalorieGoalMode.AUTO, null, null, 440));

        assertThat(response.weeklyRateKg()).isEqualByComparingTo("0.40");
        assertThat(response.weeklyRatePercent()).isEqualByComparingTo("0.50");
        assertThat(response.rateWarning()).isNull();
    }

    @Test
    void weeklyRate_usesTheNewestLoggedWeight() {
        // The same surplus at a logged 70 kg is 0.57 percent, over the bulk line.
        when(bodyWeightService.getLatestWeightKg(USER_ID)).thenReturn(Optional.of(new BigDecimal("70.00")));

        GoalsResponse response = read(save(GoalDirection.BULK, CalorieGoalMode.AUTO, null, null, 440));

        assertThat(response.weeklyRatePercent()).isEqualByComparingTo("0.57");
        assertThat(response.rateWarning()).isEqualTo(RateWarning.GAIN_TOO_FAST);
    }

    @Test
    void weeklyRate_warnsOnACutFasterThanOnePercent() {
        GoalsResponse atTheLine = read(save(GoalDirection.CUT, CalorieGoalMode.AUTO, null, null, -880));
        assertThat(atTheLine.weeklyRateKg()).isEqualByComparingTo("-0.80");
        assertThat(atTheLine.rateWarning()).isNull();
    }

    @Test
    void weeklyRate_pastTheCutLine_warnsAndStillSaves() {
        GoalsResponse response = read(save(GoalDirection.CUT, CalorieGoalMode.AUTO, null, null, -881));

        assertThat(response.calorieGoal()).isEqualTo(1619);
        assertThat(response.rateWarning()).isEqualTo(RateWarning.LOSS_TOO_FAST);
    }

    @Test
    void manualGoal_stillGetsARateAgainstMaintenance() {
        GoalsResponse response = read(save(GoalDirection.CUT, CalorieGoalMode.MANUAL, 2000, null, null));

        assertThat(response.maintenanceKcal()).isEqualTo(ESTIMATE);
        assertThat(response.weeklyRateKg()).isEqualByComparingTo("-0.45");
    }

    @Test
    void noBodyMeasuresAndNoOverride_leavesMaintenanceAndRateEmpty() {
        UserGoalEntity goal = save(GoalDirection.MAINTAIN, CalorieGoalMode.MANUAL, 2000, null, null);
        when(biometricsRepository.findByOwnerUserId(USER_ID)).thenReturn(Optional.empty());

        GoalsResponse response = read(goal);

        assertThat(response.calorieGoal()).isEqualTo(2000);
        assertThat(response.maintenanceKcal()).isNull();
        assertThat(response.weeklyRateKg()).isNull();
        assertThat(response.weeklyRatePercent()).isNull();
        assertThat(response.rateWarning()).isNull();
    }
}
