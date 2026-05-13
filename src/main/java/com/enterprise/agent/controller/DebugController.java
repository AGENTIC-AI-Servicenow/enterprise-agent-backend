package com.enterprise.agent.controller;

import com.enterprise.agent.client.ServiceNowClient;
import com.enterprise.agent.service.OAuthAuthorizationService;
import com.enterprise.agent.service.ServiceNowAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Enumeration;
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
    private final OAuthAuthorizationService oAuthAuthorizationService;
    private final ServiceNowClient serviceNowClient;

    public DebugController(ServiceNowAuthService authService,
                           OAuthAuthorizationService oAuthAuthorizationService,
                           ServiceNowClient serviceNowClient) {
        this.authService = authService;
        this.oAuthAuthorizationService = oAuthAuthorizationService;
        this.serviceNowClient = serviceNowClient;
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

    /**
     * GET /debug/headers
     *
     * Echoes request headers to validate reverse-proxy/cors behavior and
     * confirm X-User-Id is received by the backend.
     *
     * WARNING: Do not enable in production.
     */
    @GetMapping("/debug/headers")
    public Map<String, Object> debugHeaders(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("checkedAt", Instant.now());
        response.put("method", request.getMethod());
        response.put("uri", request.getRequestURI());

        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }
        response.put("headers", headers);
        response.put("xUserId", request.getHeader("X-User-Id"));
        return response;
    }

    /**
     * GET /debug/servicenow/sessions
     *
     * Shows how many OAuth token sessions are currently stored in-memory.
     * WARNING: Do not enable in production.
     */
    @GetMapping("/debug/servicenow/sessions")
    public Map<String, Object> debugOAuthSessions() {
        Map<String, Object> response = new HashMap<>();
        response.put("checkedAt", Instant.now());
        response.put("activeSessionCount", oAuthAuthorizationService.getActiveSessionCount());
        return response;
    }

    /**
     * GET /debug/servicenow/sessions/_dump
     *
     * Dumps current session keys stored in-memory (no tokens).
     * WARNING: Do not enable in production.
     */
    @GetMapping("/debug/servicenow/sessions/_dump")
    public Map<String, Object> debugOAuthSessionsDump() {
        Map<String, Object> response = new HashMap<>();
        response.put("checkedAt", Instant.now());
        response.put("activeSessionCount", oAuthAuthorizationService.getActiveSessionCount());
        response.put("sessionKeys", oAuthAuthorizationService.getSessionKeys());
        return response;
    }

    /**
     * GET /debug/servicenow/sessions/{userId}
     *
     * Checks whether a specific userId has a valid OAuth token stored.
     * WARNING: Do not enable in production.
     */
    @GetMapping("/debug/servicenow/sessions/{userId}")
    public Map<String, Object> debugOAuthSessionForUser(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        response.put("checkedAt", Instant.now());
        response.put("userId", userId);
        response.put("hasValidToken", oAuthAuthorizationService.hasValidToken(userId));
        return response;
    }

    /**
     * GET /debug/servicenow/ping-user
     *
     * Calls ServiceNow Table API sys_user (limit=1) to validate whether the current
     * OAuth token is accepted for Table API.
     */
    @GetMapping("/debug/servicenow/ping-user")
    public Object pingServiceNowUser() {
        return serviceNowClient.getCurrentUser();
    }
}
