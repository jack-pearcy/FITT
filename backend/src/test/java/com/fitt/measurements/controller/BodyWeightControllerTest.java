package com.fitt.measurements.controller;

import com.fitt.measurements.persistence.entity.BodyWeightEntryEntity;
import com.fitt.measurements.service.BodyWeightService;
import com.fitt.measurements.service.WeightLogResult;
import com.fitt.measurements.service.exception.BodyWeightEntryNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

// Controller test for the body weight endpoints. Pins all four endpoints,
// the 201 vs 200 branching on logWeight (new entry vs upsert on same date),
// ownership enforcement, @Valid wiring, and the BodyWeightEntryNotFoundException
// to 404 mapping.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BodyWeightControllerTest {

    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;
    private static final long ENTRY_ID = 42L;

    private static final String VALID_LOG_BODY = """
            {
              "loggedAt": "2026-05-25",
              "weight": 80.5
            }
            """;

    private static final String LOG_BODY_MISSING_WEIGHT = """
            {
              "loggedAt": "2026-05-25"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BodyWeightService bodyWeightService;

    @Test
    void logWeight_newEntry_returns201WithEntry() throws Exception {
        when(bodyWeightService.logWeight(eq(USER_ID), any(), any()))
                .thenReturn(new WeightLogResult(sampleEntry(), true));

        mockMvc.perform(post("/api/users/{userId}/measurements/weight", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOG_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ENTRY_ID))
                .andExpect(jsonPath("$.weight").value(80.5));
    }

    @Test
    void logWeight_existingEntryUpserted_returns200WithEntry() throws Exception {
        // Same date already logged. Service returns created=false, controller
        // returns 200 OK instead of 201 Created to signal an upsert.
        when(bodyWeightService.logWeight(eq(USER_ID), any(), any()))
                .thenReturn(new WeightLogResult(sampleEntry(), false));

        mockMvc.perform(post("/api/users/{userId}/measurements/weight", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOG_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ENTRY_ID));
    }

    @Test
    void logWeight_ownershipMismatch_returns403() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/measurements/weight", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", OTHER_USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOG_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bodyWeightService);
    }

    @Test
    void logWeight_missingWeight_returns400() throws Exception {
        mockMvc.perform(post("/api/users/{userId}/measurements/weight", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOG_BODY_MISSING_WEIGHT))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bodyWeightService);
    }

    @Test
    void getEntries_validOwnership_returns200WithList() throws Exception {
        when(bodyWeightService.getEntries(USER_ID, 7))
                .thenReturn(List.of(sampleEntry()));

        mockMvc.perform(get("/api/users/{userId}/measurements/weight", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ENTRY_ID))
                .andExpect(jsonPath("$[0].weight").value(80.5));
    }

    @Test
    void getSevenDayAverage_validOwnership_returns200WithAverage() throws Exception {
        when(bodyWeightService.getSevenDayAverage(USER_ID))
                .thenReturn(new BigDecimal("80.3"));

        mockMvc.perform(get("/api/users/{userId}/measurements/weight/average", USER_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(80.3));
    }

    @Test
    void deleteEntry_validOwnership_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/{userId}/measurements/weight/{entryId}",
                        USER_ID, ENTRY_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isNoContent());

        verify(bodyWeightService).deleteEntry(USER_ID, ENTRY_ID);
    }

    @Test
    void deleteEntry_entryNotFound_returns404() throws Exception {
        // deleteEntry is void so doThrow is needed instead of when().thenThrow().
        doThrow(new BodyWeightEntryNotFoundException("entry not found: " + ENTRY_ID))
                .when(bodyWeightService).deleteEntry(USER_ID, ENTRY_ID);

        mockMvc.perform(delete("/api/users/{userId}/measurements/weight/{entryId}",
                        USER_ID, ENTRY_ID)
                        .with(jwt().jwt(token -> token.claim("uid", USER_ID))))
                .andExpect(status().isNotFound());
    }

    // Helper that builds a sample entry with id and createdAt set via reflection
    // because both fields are normally generated by JPA at persistence time.
    private BodyWeightEntryEntity sampleEntry() {
        BodyWeightEntryEntity entry = new BodyWeightEntryEntity(
                USER_ID, LocalDate.of(2026, 5, 25), new BigDecimal("80.5"));
        ReflectionTestUtils.setField(entry, "id", ENTRY_ID);
        ReflectionTestUtils.setField(entry, "createdAt", Instant.parse("2026-05-25T10:00:00Z"));
        return entry;
    }
}