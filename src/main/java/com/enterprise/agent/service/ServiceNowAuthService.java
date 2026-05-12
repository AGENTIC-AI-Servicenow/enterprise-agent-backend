package com.enterprise.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * Central authentication service that selects and applies the appropriate
 * authentication strategy (Basic Auth or OAuth) based on configuration
 * and per-user session state.
 *
 * Strategy selection priority (per request):
 * 1. If current ThreadLocal userId has an OAuth token → use OAuthStrategy
 * 2. If current ThreadLocal userId has a BasicAuth session → use BasicAuthStrategy
 * 3. Fall back to global auth.mode config
 *
 * Enterprise Pattern: Strategy Pattern + Factory Pattern
 */
@Service
public class ServiceNowAuthService {

    private static final Logger log = LoggerFactory.getLogger(ServiceNowAuthService.class);

    @Value("${servicenow.auth.mode:oauth}")
    private String authMode;

    private final BasicAuthStrategy basicAuthStrategy;
    private final OAuthStrategy oAuthStrategy;
    private final BasicAuthSessionService basicAuthSessionService;

    public ServiceNowAuthService(BasicAuthStrategy basicAuthStrategy,
                                 OAuthStrategy oAuthStrategy,
                                 BasicAuthSessionService basicAuthSessionService) {
        this.basicAuthStrategy = basicAuthStrategy;
        this.oAuthStrategy = oAuthStrategy;
        this.basicAuthSessionService = basicAuthSessionService;
    }

    /**
     * Returns the best authentication strategy for the current request/user.
     *
     * Priority:
     * 1. Per-user OAuth token (ThreadLocal userId has a valid OAuth token)
     * 2. Per-user Basic Auth session (ThreadLocal userId starts with "basic_")
     * 3. Global config (auth.mode in application.yml)
     */
    public ServiceNowAuthStrategy getActiveStrategy() {
        // Check per-user OAuth token first
        if (oAuthStrategy.isReady()) {
            log.debug("Using per-user OAuth strategy");
            return oAuthStrategy;
        }

        // Check per-user Basic Auth session
        if (basicAuthStrategy.isReady()) {
            String userId = basicAuthStrategy.getCurrentUserId();
            if (userId != null && basicAuthSessionService.hasSession(userId)) {
                log.debug("Using per-user Basic Auth strategy for userId: {}", userId);
            } else {
                log.debug("Using global Basic Auth strategy");
            }
            return basicAuthStrategy;
        }

        // Neither strategy is ready — return the configured default (will throw on use)
        log.warn("No auth strategy is ready. Falling back to configured mode: {}", authMode);
        if ("basic".equalsIgnoreCase(authMode)) {
            return basicAuthStrategy;
        }
        return oAuthStrategy;
    }

    /**
     * Adds authentication headers to an HTTP request using the active strategy.
     *
     * @param headers The HttpHeaders to modify
     * @throws IllegalStateException if authentication is not properly configured
     */
    public void addAuthHeaders(HttpHeaders headers) {
        ServiceNowAuthStrategy strategy = getActiveStrategy();

        if (!strategy.isReady()) {
            String errorMsg = String.format(
                "Authentication not ready. Mode: %s. " +
                "For Basic Auth: POST /api/auth/login with {username, password}. " +
                "For OAuth: Authenticate via /oauth/authorize endpoint.",
                authMode
            );
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        strategy.addAuthHeaders(headers);
        log.debug("Authentication headers added using strategy: {}", strategy.getAuthMode());
    }

    /**
     * Checks if the current authentication configuration is ready to use.
     */
    public boolean isAuthReady() {
        return getActiveStrategy().isReady();
    }

    /**
     * Returns the globally configured authentication mode name.
     */
    public String getAuthMode() {
        return authMode;
    }

    /**
     * Sets the current user context in BOTH strategies (ThreadLocal).
     * Called by OAuthUserContextInterceptor on each request that carries X-User-Id.
     *
     * @param userId The user ID to set (may be an OAuth userId OR a Basic Auth "basic_*" userId)
     */
    public void setOAuthUserContext(String userId) {
        oAuthStrategy.setCurrentUserId(userId);
        basicAuthStrategy.setCurrentUserId(userId);
        log.debug("User context set for userId: {}", userId);
    }

    /**
     * Clears the current user context from BOTH strategies (ThreadLocal cleanup).
     * Called by OAuthUserContextInterceptor after each request.
     */
    public void clearOAuthUserContext() {
        oAuthStrategy.clearCurrentUserId();
        basicAuthStrategy.clearCurrentUserId();
        log.debug("User context cleared");
    }
}
