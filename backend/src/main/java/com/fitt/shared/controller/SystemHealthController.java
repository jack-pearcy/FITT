package com.fitt.shared.controller;

import com.fitt.shared.dto.SystemHealthResponse;
import com.fitt.shared.service.SystemHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemHealthController {

    private final SystemHealthService systemHealthService;

    public SystemHealthController(SystemHealthService systemHealthService) {
        this.systemHealthService = systemHealthService;
    }

    @GetMapping("/health")
    public SystemHealthResponse health() {
        return systemHealthService.getHealth();
    }
}