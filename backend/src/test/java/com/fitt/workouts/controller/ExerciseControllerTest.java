package com.fitt.workouts.controller;

import com.fitt.workouts.domain.MuscleGroup;
import com.fitt.workouts.dto.ExerciseResponse;
import com.fitt.workouts.service.ExerciseService;
import com.fitt.workouts.service.exception.ExerciseNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller test for the exercise endpoints. Pins all three endpoints,
// the muscleGroup query parameter binding, ownership enforcement on DELETE,
// the IllegalArgumentException to 400 mapping for global library delete
// attempts, and the ExerciseNotFoundException to 404 mapping.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExerciseControllerTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long EXERCISE_ID = 100L;

    private static final String CREATE_BODY = """
            {
              "name": "Cable Row",
              "muscleGroup": "BACK"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExerciseService exerciseService;

    @Test
    void listExercises_validOwnership_returns200WithList() throws Exception {
        when(exerciseService.listExercises(USER_ID, null))
                .thenReturn(List.of(
                        new ExerciseResponse(10L, "Bench Press", MuscleGroup.CHEST, false),
                        new ExerciseResponse(11L, "My Curl", MuscleGroup.ARMS, true)));

        mockMvc.perform(get("/api/users/{userId}/exercises", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Bench Press"))
                .andExpect(jsonPath("$[0].muscleGroup").value("CHEST"))
                .andExpect(jsonPath("$[0].custom").value(false))
                .andExpect(jsonPath("$[1].name").value("My Curl"));
    }

    @Test
    void listExercises_withMuscleGroupQueryParam_passesFilterToService() throws Exception {
        when(exerciseService.listExercises(USER_ID, MuscleGroup.CHEST))
                .thenReturn(List.of(
                        new ExerciseResponse(10L, "Bench Press", MuscleGroup.CHEST, false)));

        mockMvc.perform(get("/api/users/{userId}/exercises", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .param("muscleGroup", "CHEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Service receives the MuscleGroup enum value parsed from the string query param.
        verify(exerciseService).listExercises(USER_ID, MuscleGroup.CHEST);
    }

    @Test
    void createCustomExercise_validOwnership_returns201WithBody() throws Exception {
        when(exerciseService.createCustomExercise(eq(USER_ID), any()))
                .thenReturn(new ExerciseResponse(50L, "Cable Row", MuscleGroup.BACK, true));

        mockMvc.perform(post("/api/users/{userId}/exercises", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.name").value("Cable Row"))
                .andExpect(jsonPath("$.muscleGroup").value("BACK"))
                .andExpect(jsonPath("$.custom").value(true));
    }

    @Test
    void deleteCustomExercise_validOwnership_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/{userId}/exercises/{exerciseId}",
                        USER_ID, EXERCISE_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isNoContent());

        verify(exerciseService).deleteCustomExercise(USER_ID, EXERCISE_ID);
    }

    @Test
    void deleteCustomExercise_ownershipMismatch_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/{userId}/exercises/{exerciseId}",
                        USER_ID, EXERCISE_ID)
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(exerciseService);
    }

    @Test
    void deleteCustomExercise_globalLibraryExercise_returns400() throws Exception {
        // Service throws IllegalArgumentException when caller tries to delete
        // a global library exercise. ApiExceptionHandler maps this to 400.
        doThrow(new IllegalArgumentException("Fixed library exercises cannot be deleted"))
                .when(exerciseService).deleteCustomExercise(USER_ID, EXERCISE_ID);

        mockMvc.perform(delete("/api/users/{userId}/exercises/{exerciseId}",
                        USER_ID, EXERCISE_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteCustomExercise_notFound_returns404() throws Exception {
        doThrow(new ExerciseNotFoundException(EXERCISE_ID))
                .when(exerciseService).deleteCustomExercise(USER_ID, EXERCISE_ID);

        mockMvc.perform(delete("/api/users/{userId}/exercises/{exerciseId}",
                        USER_ID, EXERCISE_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Exercise Not Found"));
    }
}