package com.enterprise.agent.controller;

import com.enterprise.agent.service.ServiceNowAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Temporary diagnostic controller for validating OAuth Client Credentials flow.
 * 
 * This controller MUST NOT be enabled in production environments.
 * Remove or secure before go-live.
 */
@RestController
public class DebugController {

    private final ServiceNowAuthService authService;

    public DebugController(ServiceNowAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/debug/servicenow/token")
    public Map<String, Object> debugToken() {

        Map<String, Object> response = new HashMap<>();
        response.put("checkedAt", Instant.now());

        try {
            String token = authService.getAccessToken();

            response.put("status", "OK");
            response.put("tokenCached", token != null);

        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("errorMessage", e.getMessage());
        }

        return response;
    }
}
