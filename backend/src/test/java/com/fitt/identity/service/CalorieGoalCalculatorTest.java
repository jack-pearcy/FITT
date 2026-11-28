package com.fitt.identity.service;

import com.fitt.identity.domain.GoalDirection;
import com.fitt.identity.domain.RateWarning;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// Unit test for CalorieGoalCalculator. Pure calculation, no collaborators, so
// no mocks and no Spring context. Locks the three direction offsets and the
// rounding to a whole calorie.
class CalorieGoalCalculatorTest {

    private final CalorieGoalCalculator calculator = new CalorieGoalCalculator();

    @Test
    void bulk_appliesFifteenPercentSurplus() {
        assertThat(calculator.adjustForGoal(2000, GoalDirection.BULK)).isEqualTo(2300);
    }

    @Test
    void cut_appliesTwentyPercentDeficit() {
        assertThat(calculator.adjustForGoal(2000, GoalDirection.CUT)).isEqualTo(1600);
    }

    @Test
    void maintain_returnsMaintenanceUnchanged() {
        assertThat(calculator.adjustForGoal(2500, GoalDirection.MAINTAIN)).isEqualTo(2500);
    }

    @Test
    void bulk_roundsToNearestWholeCalorie() {
        // 2611 * 1.15 = 3002.65, rounds to 3003.
        assertThat(calculator.adjustForGoal(2611, GoalDirection.BULK)).isEqualTo(3003);
    }

    @Test
    void cut_roundsToNearestWholeCalorie() {
        // 2607 * 0.80 = 2085.6, rounds to 2086.
        assertThat(calculator.adjustForGoal(2607, GoalDirection.CUT)).isEqualTo(2086);
    }

    private static final BigDecimal EIGHTY_KG = new BigDecimal("80");

    @Test
    void weeklyRateKg_surplusIsAGainAndDeficitIsALoss() {
        // 440 kcal a day is 3,080 a week, and 3,080 / 7,700 is 0.40 kg.
        assertThat(calculator.weeklyRateKg(2940, 2500)).isEqualByComparingTo("0.40");
        assertThat(calculator.weeklyRateKg(2000, 2500)).isEqualByComparingTo("-0.45");
        assertThat(calculator.weeklyRateKg(2500, 2500)).isEqualByComparingTo("0.00");
    }

    @Test
    void weeklyRatePercent_isTheRateOverCurrentWeight() {
        assertThat(calculator.weeklyRatePercent(2940, 2500, EIGHTY_KG)).isEqualByComparingTo("0.50");
        assertThat(calculator.weeklyRatePercent(1620, 2500, EIGHTY_KG)).isEqualByComparingTo("-1.00");
    }

    @Test
    void rateWarning_gainWarnsOnlyAboveHalfAPercent() {
        // At 80 kg, 0.5 percent a week is 0.4 kg, which is 440 kcal a day.
        assertThat(calculator.rateWarning(2940, 2500, EIGHTY_KG)).isNull();
        assertThat(calculator.rateWarning(2941, 2500, EIGHTY_KG)).isEqualTo(RateWarning.GAIN_TOO_FAST);
    }

    @Test
    void rateWarning_lossWarnsOnlyAboveOnePercent() {
        // At 80 kg, 1 percent a week is 0.8 kg, which is 880 kcal a day.
        assertThat(calculator.rateWarning(1620, 2500, EIGHTY_KG)).isNull();
        assertThat(calculator.rateWarning(1619, 2500, EIGHTY_KG)).isEqualTo(RateWarning.LOSS_TOO_FAST);
    }

    @Test
    void rateWarning_atMaintenance_isNull() {
        assertThat(calculator.rateWarning(2500, 2500, EIGHTY_KG)).isNull();
    }
}
