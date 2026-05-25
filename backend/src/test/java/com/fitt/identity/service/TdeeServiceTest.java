package com.fitt.identity.service;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Pure unit test for TdeeService. No Spring, DB, mocks
// TdeeService has no dependencies so just construct it directly in setUp.
class TdeeServiceTest {

    private TdeeService tdeeService;

    @BeforeEach
    void setUp() {
        tdeeService = new TdeeService();
    }

    @Test
    void calculate_male_returnsTdeeUsingMaleFormula() {
        BigDecimal heightCm = new BigDecimal("180");
        BigDecimal weightKg = new BigDecimal("80");
        LocalDate dateOfBirth = LocalDate.now().minusYears(30);

        int tdee = tdeeService.calculate(
                heightCm,
                weightKg,
                dateOfBirth,
                BiologicalSex.MALE,
                ActivityLevel.SEDENTARY
        );

        // BMR = (10 * 80) + (6.25 * 180) - (5 * 30) + 5 = 1780
        // TDEE = 1780 * 1.2 = 2136
        assertThat(tdee).isEqualTo(2136);
    }

    @Test
    void calculate_female_returnsTdeeUsingFemaleFormula() {
        BigDecimal heightCm = new BigDecimal("165");
        BigDecimal weightKg = new BigDecimal("60");
        LocalDate dateOfBirth = LocalDate.now().minusYears(25);

        int tdee = tdeeService.calculate(
                heightCm,
                weightKg,
                dateOfBirth,
                BiologicalSex.FEMALE,
                ActivityLevel.MODERATELY_ACTIVE
        );

        // BMR = (10 * 60) + (6.25 * 165) - (5 * 25) - 161 = 1345.25
        // TDEE = 1345.25 * 1.55 = 2085.1375 rounds to 2085
        assertThat(tdee).isEqualTo(2085);
    }

    @Test
    void calculate_lightlyActive_multipliesBmrBy1_375() {
        BigDecimal heightCm = new BigDecimal("170");
        BigDecimal weightKg = new BigDecimal("70");
        LocalDate dateOfBirth = LocalDate.now().minusYears(30);

        int tdee = tdeeService.calculate(
                heightCm,
                weightKg,
                dateOfBirth,
                BiologicalSex.MALE,
                ActivityLevel.LIGHTLY_ACTIVE
        );

        // BMR = (10 * 70) + (6.25 * 170) - (5 * 30) + 5 = 1617.5
        // TDEE = 1617.5 * 1.375 = 2224.0625 rounds to 2224
        assertThat(tdee).isEqualTo(2224);
    }

    @Test
    void calculate_active_multipliesBmrBy1_725() {
        BigDecimal heightCm = new BigDecimal("175");
        BigDecimal weightKg = new BigDecimal("75");
        LocalDate dateOfBirth = LocalDate.now().minusYears(35);

        int tdee = tdeeService.calculate(
                heightCm,
                weightKg,
                dateOfBirth,
                BiologicalSex.MALE,
                ActivityLevel.ACTIVE
        );

        // BMR = (10 * 75) + (6.25 * 175) - (5 * 35) + 5 = 1673.75
        // TDEE = 1673.75 * 1.725 = 2887.21875 rounds to 2887
        assertThat(tdee).isEqualTo(2887);
    }

    @Test
    void calculate_veryActive_multipliesBmrBy1_9() {
        BigDecimal heightCm = new BigDecimal("190");
        BigDecimal weightKg = new BigDecimal("85");
        LocalDate dateOfBirth = LocalDate.now().minusYears(28);

        int tdee = tdeeService.calculate(
                heightCm,
                weightKg,
                dateOfBirth,
                BiologicalSex.MALE,
                ActivityLevel.VERY_ACTIVE
        );

        // BMR = (10 * 85) + (6.25 * 190) - (5 * 28) + 5 = 1902.5
        // TDEE = 1902.5 * 1.9 = 3614.75 rounds to 3615
        assertThat(tdee).isEqualTo(3615);
    }
}