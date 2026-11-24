package com.fitt.identity.dto;

import com.fitt.identity.domain.UnitsPreference;
import jakarta.validation.constraints.NotNull;

// An unknown value fails JSON binding and comes back as 400 before this record exists.
public record UpdateUnitsRequest(
        @NotNull UnitsPreference unitsPreference
) {}
