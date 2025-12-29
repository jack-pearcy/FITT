package com.fitt.identity.controller;

import com.fitt.identity.dto.IdentityHealthResponse;
import com.fitt.identity.service.IdentityHealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/identity")
public class IdentityHealthController {

    private final IdentityHealthService identityHealthService;

    public IdentityHealthController(IdentityHealthService identityHealthService) {
        this.identityHealthService = identityHealthService;
    }

    @GetMapping("/health")
    public IdentityHealthResponse health() {
        return identityHealthService.getHealth();
    }
}

