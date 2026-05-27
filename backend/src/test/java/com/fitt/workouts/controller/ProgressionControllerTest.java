package com.fitt.workouts.controller;

import com.fitt.workouts.domain.ProgressionModel;
import com.fitt.workouts.dto.ProgressionSuggestionResponse;
import com.fitt.workouts.service.ProgressionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller test using the full Spring context, matching the existing
// IdentityHealthControllerTest pattern. ProgressionService is replaced with
// a MockitoBean so only controller wiring and the ownership check are under test.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProgressionControllerTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long EXERCISE_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProgressionService progressionService;

    @Test
    void getSuggestion_jwtMatchesPathUserId_returns200WithSuggestion() throws Exception {
        ProgressionSuggestionResponse response = new ProgressionSuggestionResponse(
                EXERCISE_ID, ProgressionModel.LINEAR_PERIODIZATION, "102.5", 8, 3, "test reasoning");
        when(progressionService.getSuggestion(USER_ID, EXERCISE_ID)).thenReturn(response);

        mockMvc.perform(get("/api/users/{userId}/progression/suggestions", USER_ID)
                        .param("exerciseId", String.valueOf(EXERCISE_ID))
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseId").value(EXERCISE_ID))
                .andExpect(jsonPath("$.modelUsed").value("LINEAR_PERIODIZATION"))
                .andExpect(jsonPath("$.suggestedWeight").value("102.5"))
                .andExpect(jsonPath("$.suggestedReps").value(8))
                .andExpect(jsonPath("$.suggestedSets").value(3))
                .andExpect(jsonPath("$.reasoning").value("test reasoning"));
    }

    @Test
    void getSuggestion_jwtDoesNotMatchPathUserId_returns403() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/progression/suggestions", USER_ID)
                        .param("exerciseId", String.valueOf(EXERCISE_ID))
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID))))
                .andExpect(status().isForbidden());

        // Ownership check must reject before the service is touched.
        verifyNoInteractions(progressionService);
    }

    @Test
    void getPreference_jwtMatchesPathUserId_returns200WithModel() throws Exception {
        when(progressionService.getPreference(USER_ID)).thenReturn(ProgressionModel.RPE_AUTOREGULATION);

        mockMvc.perform(get("/api/users/{userId}/progression/preference", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("RPE_AUTOREGULATION"));
    }

    @Test
    void updatePreference_validRequest_returns204AndCallsService() throws Exception {
        mockMvc.perform(put("/api/users/{userId}/progression/preference", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progressionModel\":\"LINEAR_PERIODIZATION\"}"))
                .andExpect(status().isNoContent());

        verify(progressionService).updatePreference(USER_ID, ProgressionModel.LINEAR_PERIODIZATION);
    }

    @Test
    void updatePreference_missingProgressionModel_returns400() throws Exception {
        // Empty body fails @NotNull validation on progressionModel.
        mockMvc.perform(put("/api/users/{userId}/progression/preference", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(progressionService);
    }
}