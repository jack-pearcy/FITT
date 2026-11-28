package com.fitt.identity.controller;

import com.fitt.identity.domain.CalorieGoalMode;
import com.fitt.identity.domain.GoalDirection;
import com.fitt.identity.domain.RateWarning;
import com.fitt.identity.dto.GoalsResponse;
import com.fitt.identity.service.GoalService;
import com.fitt.identity.service.exception.InvalidGoalException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller test for the goals route. Same pattern as OnboardingControllerTest,
// full Spring context, real security chain, GoalService swapped with a
// MockitoBean. This case moved here from OnboardingControllerTest when goals
// left the onboarding route.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalControllerTest {

    private static final long USER_ID = 1L;

    private static final String VALID_GOALS_BODY = """
            {
              "goalDirection": "BULK",
              "targetWeightKg": 85,
              "timelineWeeks": 12,
              "calorieGoalMode": "AUTO",
              "calorieGoal": null
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoalService goalService;

    @Test
    void saveGoals_validRequestAndOwnership_returns204() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/identity/goals", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_GOALS_BODY))
                .andExpect(status().isNoContent());

        verify(goalService).saveGoals(eq(USER_ID), any());
    }

    private static String goalsBody(String maintenanceOverrideKcal, String adjustmentKcal) {
        return """
                {
                  "goalDirection": "CUT",
                  "calorieGoalMode": "AUTO",
                  "maintenanceOverrideKcal": %s,
                  "adjustmentKcal": %s
                }
                """.formatted(maintenanceOverrideKcal, adjustmentKcal);
    }

    @Test
    void saveGoals_maintenanceAndAdjustmentInRange_returns204() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/identity/goals", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalsBody("2600", "-400")))
                .andExpect(status().isNoContent());
    }

    @Test
    void saveGoals_maintenanceOutOfRange_returns400UnderTheField() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/identity/goals", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalsBody("999", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.maintenanceOverrideKcal").exists());

        verifyNoInteractions(goalService);
    }

    @Test
    void saveGoals_adjustmentOutOfRange_returns400UnderTheField() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/identity/goals", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalsBody("null", "1501")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.adjustmentKcal").exists());

        verifyNoInteractions(goalService);
    }

    @Test
    void saveGoals_valuesThatDoNotWorkTogether_returns400UnderTheField() throws Exception {
        doThrow(new InvalidGoalException("adjustmentKcal", "Maintenance plus adjustment must be between 500 and 10000"))
                .when(goalService).saveGoals(eq(USER_ID), any());

        mockMvc.perform(post("/api/users/{userId}/identity/goals", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalsBody("1000", "-1500")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.errors.adjustmentKcal")
                        .value("Maintenance plus adjustment must be between 500 and 10000"));
    }

    @Test
    void saveGoals_anotherUsersPath_returns403() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/identity/goals", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID + 1)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(goalsBody("2600", "-400")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(goalService);
    }

    @Test
    void getGoals_returnsMaintenanceAdjustmentAndRate() throws Exception {
        when(goalService.getGoals(USER_ID)).thenReturn(new GoalsResponse(
                GoalDirection.CUT, null, null, CalorieGoalMode.AUTO, 1700,
                2600, true, -900,
                new BigDecimal("-0.82"), new BigDecimal("-1.02"), RateWarning.LOSS_TOO_FAST));

        mockMvc.perform(get("/api/users/{userId}/identity/goals", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calorieGoal").value(1700))
                .andExpect(jsonPath("$.maintenanceKcal").value(2600))
                .andExpect(jsonPath("$.maintenanceIsUserSet").value(true))
                .andExpect(jsonPath("$.adjustmentKcal").value(-900))
                .andExpect(jsonPath("$.weeklyRateKg").value(-0.82))
                .andExpect(jsonPath("$.weeklyRatePercent").value(-1.02))
                .andExpect(jsonPath("$.rateWarning").value("LOSS_TOO_FAST"));
    }

    @Test
    void getGoals_anotherUsersPath_returns403() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/identity/goals", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID + 1))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(goalService);
    }
}
