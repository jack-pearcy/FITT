package com.fitt.workouts.controller;

import com.fitt.workouts.dto.NextLiftResponse;
import com.fitt.workouts.dto.WorkoutPlanDayResponse;
import com.fitt.workouts.service.NextLiftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller test using the full Spring context, matching ProgressionControllerTest.
// NextLiftService is a MockitoBean so only controller wiring and ownership are tested.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NextLiftControllerTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NextLiftService nextLiftService;

    @Test
    void getNextLift_jwtMatchesPathUserId_returns200WithPayload() throws Exception {
        WorkoutPlanDayResponse day = new WorkoutPlanDayResponse(5L, 0, "Push", List.of());
        NextLiftResponse response = new NextLiftResponse(
                true, 10L, "PPL", 3, 1, 1, false, false, day);
        when(nextLiftService.getNextLift(USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/users/{userId}/next-lift", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActivePlan").value(true))
                .andExpect(jsonPath("$.planName").value("PPL"))
                .andExpect(jsonPath("$.dayNumber").value(1))
                .andExpect(jsonPath("$.weekInCycle").value(1))
                .andExpect(jsonPath("$.day.dayLabel").value("Push"));
    }

    @Test
    void getNextLift_jwtDoesNotMatchPathUserId_returns403() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/next-lift", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID))))
                .andExpect(status().isForbidden());

        // Ownership check must reject before the service is touched.
        verifyNoInteractions(nextLiftService);
    }

    @Test
    void getNextLift_noActivePlan_returns200WithEmptyState() throws Exception {
        NextLiftResponse empty = new NextLiftResponse(false, null, null, 0, 0, 1, false, false, null);
        when(nextLiftService.getNextLift(USER_ID)).thenReturn(empty);

        mockMvc.perform(get("/api/users/{userId}/next-lift", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActivePlan").value(false))
                .andExpect(jsonPath("$.dayNumber").value(0));
    }
}