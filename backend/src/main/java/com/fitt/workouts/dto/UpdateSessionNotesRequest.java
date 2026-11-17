package com.fitt.workouts.dto;

import jakarta.validation.constraints.Size;

// Null or blank clears the notes.
public record UpdateSessionNotesRequest(

        @Size(max = 5000, message = "Notes must be 5000 characters or fewer")
        String notes
) {}
