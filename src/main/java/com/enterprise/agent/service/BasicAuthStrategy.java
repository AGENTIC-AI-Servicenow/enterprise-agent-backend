package com.enterprise.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Basic Authentication strategy for ServiceNow.
 *
 * Supports two modes:
 * 1. Per-user session: credentials stored in BasicAuthSessionService, identified by ThreadLocal userId.
 * 2. Global fallback: single username/password from application.yml (for dev/testing).
 *
 * Enterprise Note: Basic Auth is suitable for:
 * - Development and testing
 * - Internal service-to-service communication behind firewalls
 * - Quick MVP validation
 *
 * For production, consider migrating to OAuth 2.0
 */
@Component
public class BasicAuthStrategy implements ServiceNowAuthStrategy {

    private static final Logger log = LoggerFactory.getLogger(BasicAuthStrategy.class);

    /** ThreadLocal holds the userId set by OAuthUserContextInterceptor (or BasicAuth login). */
    private final ThreadLocal<String> currentUserId = new ThreadLocal<>();

    private final BasicAuthSessionService basicAuthSessionService;

    @Value("${servicenow.auth.username:}")
    private String globalUsername;

    @Value("${servicenow.auth.password:}")
    private String globalPassword;

    @Value("${servicenow.auth.mode:oauth}")
    private String authMode;

    public BasicAuthStrategy(BasicAuthSessionService basicAuthSessionService) {
        this.basicAuthSessionService = basicAuthSessionService;
    }

    // -----------------------------------------------------------------------
    // ThreadLocal context management (called by ServiceNowAuthService)
    // -----------------------------------------------------------------------

    public void setCurrentUserId(String userId) {
        currentUserId.set(userId);
    }

    public void clearCurrentUserId() {
        currentUserId.remove();
    }

    public String getCurrentUserId() {
        return currentUserId.get();
    }

    // -----------------------------------------------------------------------
    // ServiceNowAuthStrategy implementation
    // -----------------------------------------------------------------------

    @Override
    public void addAuthHeaders(HttpHeaders headers) {
        String userId = currentUserId.get();

        if (userId != null && basicAuthSessionService.hasSession(userId)) {
            // Per-user credentials from session store
            String encoded = basicAuthSessionService.getEncodedCredentials(userId);
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
            log.debug("Added per-user Basic Auth header for userId: {}", userId);

        } else {
            // Fallback to global credentials from application.yml
            if (!isGlobalConfigReady()) {
                throw new IllegalStateException(
                        "Basic Auth not configured. Login via POST /api/auth/login " +
                        "or set SERVICENOW_USERNAME / SERVICENOW_PASSWORD environment variables.");
            }
            String auth = globalUsername + ":" + globalPassword;
            String encoded = Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
            log.debug("Added global Basic Auth header for user: {}", globalUsername);
        }
    }

    @Override
    public boolean isReady() {
        String userId = currentUserId.get();
        // Ready if per-user session exists
        if (userId != null && basicAuthSessionService.hasSession(userId)) {
            return true;
        }
        // Ready if global config is set and mode is basic
        return isGlobalConfigReady();
    }

    @Override
    public String getAuthMode() {
        return "basic";
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private boolean isGlobalConfigReady() {
        return "basic".equalsIgnoreCase(authMode)
                && globalUsername != null && !globalUsername.isEmpty()
                && !globalUsername.equals("your-username")
                && globalPassword != null && !globalPassword.isEmpty()
                && !globalPassword.equals("your-password");
    }
}
