package com.fitt.workouts.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

// Changes a session after the fact. Each field is optional and a missing one
// keeps what is stored. Blank notes clear them, as on the notes route.
// A finish time can only be changed on a finished session: an open one is
// finished through the finish route, which keeps the one-open-workout rule.
public record UpdateSessionRequest(

        Instant startedAt,

        Instant finishedAt,

        @Size(max = 5000, message = "Notes must be 5000 characters or fewer")
        String notes
) {}
