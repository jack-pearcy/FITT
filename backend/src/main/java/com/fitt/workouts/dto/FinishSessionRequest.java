package com.fitt.workouts.dto;

import jakarta.validation.constraints.Pattern;
import java.time.Instant;

// The optional body of a finish. Every field may be missing, and so may the body.
public record FinishSessionRequest(

        // The id of the session as the client made it. Checked for shape only:
        // finishing twice already keeps the first finish time, so there is nothing
        // to store.
        @Pattern(regexp = ClientIds.PATTERN, message = ClientIds.MESSAGE)
        String clientId,

        // When the user finished on the device. The server's clock is used when
        // this is missing.
        @WithinTimeBounds
        Instant finishedAt
) {}
