package com.fitt.workouts.controller;

import com.fitt.workouts.domain.SplitType;
import com.fitt.workouts.dto.WorkoutPlanResponse;
import com.fitt.workouts.service.WorkoutPlanService;
import com.fitt.workouts.service.exception.WorkoutPlanNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;


import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller test for the workout plan endpoints. Pins all five endpoints
// including the activate state-transition pattern. Covers happy paths,
// ownership enforcement on DELETE, and the WorkoutPlanNotFoundException
// to 404 mapping.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutPlanControllerTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long PLAN_ID = 200L;

    private static final String CREATE_BODY = """
        {
          "name": "PPL 6-day",
          "daysPerWeek": 6,
          "splitType": "PPL",
          "days": [
            { "orderIndex": 0, "dayLabel": "Push", "exercises": [] }
          ]
        }
        """;

    private static final String UPDATE_BODY = """
        {
          "name": "Upper Lower 4-day",
          "daysPerWeek": 4,
          "splitType": "UPPER_LOWER",
          "days": [
            { "orderIndex": 0, "dayLabel": "Upper", "exercises": [] }
          ]
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkoutPlanService workoutPlanService;

    @Test
    void createPlan_validOwnership_returns201WithBody() throws Exception {
        when(workoutPlanService.createPlan(eq(USER_ID), any()))
                .thenReturn(samplePlan("PPL 6-day", false));

        mockMvc.perform(post("/api/users/{userId}/workout-plans", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PLAN_ID))
                .andExpect(jsonPath("$.name").value("PPL 6-day"))
                .andExpect(jsonPath("$.splitType").value("PPL"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updatePlan_validOwnership_returns200WithBody() throws Exception {
        when(workoutPlanService.updatePlan(eq(USER_ID), eq(PLAN_ID), any()))
                .thenReturn(samplePlan("Upper Lower 4-day", true));

        mockMvc.perform(put("/api/users/{userId}/workout-plans/{planId}", USER_ID, PLAN_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PLAN_ID))
                .andExpect(jsonPath("$.name").value("Upper Lower 4-day"));
    }

    @Test
    void updatePlan_ownershipMismatch_returns403() throws Exception {
        // valid body, so validation passes and the request reaches enforceOwnership,
        // which is what we want to assert here rather than a 400
        mockMvc.perform(put("/api/users/{userId}/workout-plans/{planId}", USER_ID, PLAN_ID)
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workoutPlanService);
    }

    @Test
    void listPlans_validOwnership_returns200WithList() throws Exception {
        when(workoutPlanService.listPlans(USER_ID))
                .thenReturn(List.of(
                        samplePlan("PPL 6-day", true),
                        samplePlan("Upper Lower", false)));

        mockMvc.perform(get("/api/users/{userId}/workout-plans", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("PPL 6-day"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].name").value("Upper Lower"));
    }

    @Test
    void getPlan_validOwnership_returns200WithPlan() throws Exception {
        when(workoutPlanService.getPlan(USER_ID, PLAN_ID))
                .thenReturn(samplePlan("PPL 6-day", true));

        mockMvc.perform(get("/api/users/{userId}/workout-plans/{planId}", USER_ID, PLAN_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PLAN_ID))
                .andExpect(jsonPath("$.name").value("PPL 6-day"));
    }

    @Test
    void getPlan_notFound_returns404() throws Exception {
        when(workoutPlanService.getPlan(USER_ID, PLAN_ID))
                .thenThrow(new WorkoutPlanNotFoundException(PLAN_ID));

        mockMvc.perform(get("/api/users/{userId}/workout-plans/{planId}", USER_ID, PLAN_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Workout Plan Not Found"));
    }

    @Test
    void activatePlan_validOwnership_returns200WithActivatedPlan() throws Exception {
        // Activate is a state-transition endpoint modeled as POST to a sub-resource
        // rather than PATCH on the plan itself. Returns the now-active plan.
        when(workoutPlanService.activatePlan(USER_ID, PLAN_ID))
                .thenReturn(samplePlan("PPL 6-day", true));

        mockMvc.perform(post("/api/users/{userId}/workout-plans/{planId}/activate",
                        USER_ID, PLAN_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PLAN_ID))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void deletePlan_validOwnership_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/{userId}/workout-plans/{planId}",
                        USER_ID, PLAN_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isNoContent());

        verify(workoutPlanService).deletePlan(USER_ID, PLAN_ID);
    }

    @Test
    void deletePlan_ownershipMismatch_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/{userId}/workout-plans/{planId}",
                        USER_ID, PLAN_ID)
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(workoutPlanService);
    }

    private WorkoutPlanResponse samplePlan(String name, boolean active) {
        return new WorkoutPlanResponse(
                PLAN_ID, USER_ID, name, 6, SplitType.PPL, active, List.of());
    }
}