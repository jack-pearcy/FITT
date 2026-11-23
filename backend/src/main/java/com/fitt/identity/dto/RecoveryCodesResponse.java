package com.fitt.identity.dto;

import java.util.List;

// Shown once. The server keeps only hashes and cannot show them again.
public record RecoveryCodesResponse(List<String> recoveryCodes) {
}
