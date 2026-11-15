package com.fitt.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerConstraintTest {

    @Test
    void dataIntegrityViolation_returns409WithGenericDetail() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement; constraint [uk_users_email]");

        ResponseEntity<ProblemDetail> response = new ApiExceptionHandler().handleDataIntegrity(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getTitle()).isEqualTo("Conflict");
        // The driver message names tables and constraints, so it must not reach the client.
        assertThat(response.getBody().getDetail()).doesNotContain("uk_users_email");
    }
}
