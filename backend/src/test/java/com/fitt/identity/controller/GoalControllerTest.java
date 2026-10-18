package com.fitt.identity.controller;

import com.fitt.identity.service.GoalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}