package com.fitt.identity.controller;

import com.fitt.identity.dto.OnboardingStatusResponse;
import com.fitt.identity.dto.TdeeResponse;
import com.fitt.identity.service.OnboardingService;
import com.fitt.identity.service.exception.BiometricsNotFoundException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller test for the onboarding endpoints. Same pattern as
// ProgressionControllerTest, full Spring context, real security chain,
// OnboardingService swapped with a MockitoBean. Covers happy paths,
// ownership enforcement on both verb types, validation wiring, and the
// BiometricsNotFoundException to 404 mapping.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingControllerTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;

    private static final String VALID_BIOMETRICS_BODY = """
            {
              "heightCm": 180,
              "weightKg": 80,
              "dateOfBirth": "1995-06-15",
              "biologicalSex": "MALE",
              "activityLevel": "MODERATELY_ACTIVE",
              "unitsPreference": "METRIC"
            }
            """;

    private static final String BIOMETRICS_MISSING_HEIGHT = """
            {
              "weightKg": 80,
              "dateOfBirth": "1995-06-15",
              "biologicalSex": "MALE",
              "activityLevel": "MODERATELY_ACTIVE",
              "unitsPreference": "METRIC"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnboardingService onboardingService;

    @Test
    void saveBiometrics_validRequestAndOwnership_returns204() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/identity/onboarding/biometrics", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BIOMETRICS_BODY))
                .andExpect(status().isNoContent());

        verify(onboardingService).saveBiometrics(eq(USER_ID), any());
    }

    @Test
    void saveBiometrics_ownershipMismatch_returns403() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/identity/onboarding/biometrics", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BIOMETRICS_BODY))
                .andExpect(status().isForbidden());

        // Ownership check throws before reaching the service.
        verifyNoInteractions(onboardingService);
    }

    @Test
    void saveBiometrics_missingRequiredField_returns400() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/identity/onboarding/biometrics", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BIOMETRICS_MISSING_HEIGHT))
                .andExpect(status().isBadRequest());

        // @Valid blocks the request before the controller body runs.
        verifyNoInteractions(onboardingService);
    }

    @Test
    void getStatus_validOwnership_returns200WithStatusBody() throws Exception {
        when(onboardingService.getStatus(USER_ID))
                .thenReturn(new OnboardingStatusResponse(true, true, true));

        mockMvc.perform(get("/api/users/{userId}/identity/onboarding/status", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biometricsComplete").value(true))
                .andExpect(jsonPath("$.goalsComplete").value(true))
                .andExpect(jsonPath("$.onboardingComplete").value(true));
    }

    @Test
    void getStatus_ownershipMismatch_returns403() throws Exception {
        mockMvc.perform(get("/api/users/{userId}/identity/onboarding/status", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(onboardingService);
    }

    @Test
    void getTdee_validOwnership_returns200WithTdeeBody() throws Exception {
        when(onboardingService.getTdee(USER_ID)).thenReturn(new TdeeResponse(2500));

        mockMvc.perform(get("/api/users/{userId}/identity/onboarding/tdee", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tdee").value(2500));
    }

    @Test
    void getTdee_biometricsMissing_returns404() throws Exception {
        when(onboardingService.getTdee(USER_ID))
                .thenThrow(new BiometricsNotFoundException(
                        "biometrics not found for user " + USER_ID));

        mockMvc.perform(get("/api/users/{userId}/identity/onboarding/tdee", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Biometrics Not Found"));
    }
}