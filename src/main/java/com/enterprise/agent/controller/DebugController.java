package com.enterprise.agent.controller;

import com.enterprise.agent.service.ServiceNowAuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Temporary diagnostic controller for validating ServiceNow authentication.
 * 
 * WARNING: This controller MUST NOT be enabled in production environments.
 * Remove or secure with proper authorization before go-live.
 * 
 * Useful for:
 * - Validating authentication configuration
 * - Debugging auth issues during development
 * - Verifying strategy switching between Basic and OAuth
 */
@RestController
public class DebugController {

    private final ServiceNowAuthService authService;

    public DebugController(ServiceNowAuthService authService) {
        this.authService = authService;
    }

    /**
     * GET /debug/servicenow/auth
     * 
     * Returns current authentication status and configuration.
     * Helps diagnose auth issues without exposing sensitive credentials.
     */
    @GetMapping("/debug/servicenow/auth")
    public Map<String, Object> debugAuth() {
        Map<String, Object> response = new HashMap<>();
        response.put("checkedAt", Instant.now());
        response.put("authMode", authService.getAuthMode());

        try {
            boolean isReady = authService.isAuthReady();
            response.put("status", isReady ? "READY" : "NOT_READY");
            response.put("authReady", isReady);
            
            if ("basic".equalsIgnoreCase(authService.getAuthMode())) {
                response.put("message", isReady 
                    ? "Basic Auth configured and ready" 
                    : "Basic Auth not configured. Set SERVICENOW_USERNAME and SERVICENOW_PASSWORD environment variables.");
            } else {
                response.put("message", isReady 
                    ? "OAuth configured and ready" 
                    : "OAuth not configured. User must authenticate via /oauth/authorize endpoint.");
            }

        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("errorMessage", e.getMessage());
            response.put("errorType", e.getClass().getSimpleName());
        }

        return response;
    }
}
