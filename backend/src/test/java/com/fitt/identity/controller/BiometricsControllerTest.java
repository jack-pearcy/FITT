package com.fitt.identity.controller;

import com.fitt.identity.domain.ActivityLevel;
import com.fitt.identity.domain.BiologicalSex;
import com.fitt.identity.domain.UnitsPreference;
import com.fitt.identity.dto.BiometricsResponse;
import com.fitt.identity.service.BiometricsService;
import com.fitt.identity.service.exception.BiometricsNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Controller test for reading biometrics and changing units after onboarding.
// Full Spring context with the real security chain and BiometricsService mocked.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BiometricsControllerTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final String BIOMETRICS_PATH = "/api/users/{userId}/identity/biometrics";
    private static final String UNITS_PATH = "/api/users/{userId}/identity/biometrics/units";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BiometricsService biometricsService;

    @Test
    void getBiometrics_owner_returns200WithEveryField() throws Exception {
        when(biometricsService.getBiometrics(USER_ID)).thenReturn(new BiometricsResponse(
                new BigDecimal("180.50"), new BigDecimal("82.30"),
                LocalDate.of(1995, 6, 15),
                BiologicalSex.MALE, ActivityLevel.MODERATELY_ACTIVE, UnitsPreference.IMPERIAL,
                Instant.parse("2026-11-20T08:30:00Z")));

        mockMvc.perform(get(BIOMETRICS_PATH, USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heightCm").value(180.50))
                .andExpect(jsonPath("$.weightKg").value(82.30))
                .andExpect(jsonPath("$.dateOfBirth").value("1995-06-15"))
                .andExpect(jsonPath("$.biologicalSex").value("MALE"))
                .andExpect(jsonPath("$.activityLevel").value("MODERATELY_ACTIVE"))
                .andExpect(jsonPath("$.unitsPreference").value("IMPERIAL"))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-20T08:30:00Z"));
    }

    @Test
    void getBiometrics_noneSaved_returns404() throws Exception {
        when(biometricsService.getBiometrics(USER_ID))
                .thenThrow(new BiometricsNotFoundException("biometrics not found for user " + USER_ID));

        mockMvc.perform(get(BIOMETRICS_PATH, USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Biometrics Not Found"));
    }

    @Test
    void getBiometrics_foreignUser_returns403() throws Exception {
        mockMvc.perform(get(BIOMETRICS_PATH, USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(biometricsService);
    }

    @Test
    void changeUnits_owner_returns204() throws Exception {
        mockMvc.perform(put(UNITS_PATH, USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitsPreference\":\"IMPERIAL\"}"))
                .andExpect(status().isNoContent());

        verify(biometricsService).changeUnits(USER_ID, UnitsPreference.IMPERIAL);
    }

    @Test
    void changeUnits_foreignUser_returns403() throws Exception {
        mockMvc.perform(put(UNITS_PATH, USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitsPreference\":\"IMPERIAL\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(biometricsService);
    }

    @Test
    void changeUnits_unknownValue_returns400() throws Exception {
        mockMvc.perform(put(UNITS_PATH, USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitsPreference\":\"FURLONGS\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(biometricsService);
    }

    @Test
    void changeUnits_missingValue_returns400() throws Exception {
        mockMvc.perform(put(UNITS_PATH, USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(biometricsService);
    }

    @Test
    void changeUnits_noneSaved_returns404() throws Exception {
        doThrow(new BiometricsNotFoundException("biometrics not found for user " + USER_ID))
                .when(biometricsService).changeUnits(USER_ID, UnitsPreference.METRIC);

        mockMvc.perform(put(UNITS_PATH, USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unitsPreference\":\"METRIC\"}"))
                .andExpect(status().isNotFound());
    }
}
