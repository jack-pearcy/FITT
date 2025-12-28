package com.fitt.shared.service;

import com.fitt.shared.dto.SystemHealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class SystemHealthService {

    private final String applicationName;

    public SystemHealthService(
            @Value("${spring.application.name:backend}") String applicationName // Reads from app.properties, default is backend
    ) {
        this.applicationName = applicationName;
    }

    // Returns fully populated DTO
    public SystemHealthResponse getHealth() {
        return new SystemHealthResponse(
                "UP",
                Instant.now(),
                applicationName
        );
    }
}
