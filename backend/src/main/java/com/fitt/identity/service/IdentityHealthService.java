package com.fitt.identity.service;

import com.fitt.identity.dto.IdentityHealthResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class IdentityHealthService {

    public IdentityHealthResponse getHealth() {
        return new IdentityHealthResponse(
                "UP",
                Instant.now(),
                "basic-auth"
        );
    }
}
