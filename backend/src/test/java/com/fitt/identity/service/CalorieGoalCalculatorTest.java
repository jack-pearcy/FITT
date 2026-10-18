package com.fitt.identity.service;

import com.fitt.identity.domain.GoalDirection;
import org.junit.jupiter.api.Test;

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
}