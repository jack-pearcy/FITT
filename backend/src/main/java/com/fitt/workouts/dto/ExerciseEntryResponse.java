package com.fitt.workouts.dto;

import java.util.List;

public record ExerciseEntryResponse(
        Long id,
        int orderIndex,
        ExerciseResponse exercise,
        List<SetEntryResponse> sets
) {}
