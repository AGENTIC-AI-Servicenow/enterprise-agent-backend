package com.enterprise.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

/**
 * Central authentication service that selects and applies the appropriate
 * authentication strategy (Basic Auth or OAuth) based on configuration.
 * 
 * Enterprise Pattern: Strategy Pattern + Factory Pattern
 * 
 * This service:
 * - Abstracts authentication complexity from API clients
 * - Enables hot-swapping between auth methods without code changes
 * - Provides unified error handling and logging
 * - Supports gradual migration from Basic to OAuth
 */
@Service
public class ServiceNowAuthService {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceNowAuthService.class);
    
    @Value("${servicenow.auth.mode:oauth}")
    private String authMode;
    
    private final BasicAuthStrategy basicAuthStrategy;
    private final OAuthStrategy oAuthStrategy;
    
    public ServiceNowAuthService(BasicAuthStrategy basicAuthStrategy, 
                                 OAuthStrategy oAuthStrategy) {
        this.basicAuthStrategy = basicAuthStrategy;
        this.oAuthStrategy = oAuthStrategy;
    }
    
    /**
     * Gets the currently active authentication strategy based on configuration
     * 
     * @return The active authentication strategy
     */
    public ServiceNowAuthStrategy getActiveStrategy() {
        if ("basic".equalsIgnoreCase(authMode)) {
            log.debug("Using Basic Authentication strategy");
            return basicAuthStrategy;
        } else {
            log.debug("Using OAuth Authentication strategy");
            return oAuthStrategy;
        }
    }
    
    /**
     * Adds authentication headers to an HTTP request
     * 
     * @param headers The HttpHeaders to modify
     * @throws IllegalStateException if authentication is not properly configured
     */
    public void addAuthHeaders(HttpHeaders headers) {
        ServiceNowAuthStrategy strategy = getActiveStrategy();
        
        if (!strategy.isReady()) {
            String errorMsg = String.format(
                "Authentication not ready. Mode: %s. " +
                "For Basic Auth: Set SERVICENOW_USERNAME and SERVICENOW_PASSWORD. " +
                "For OAuth: Authenticate via /oauth/authorize endpoint.",
                authMode
            );
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        
        strategy.addAuthHeaders(headers);
        log.debug("Authentication headers added successfully using {} mode", strategy.getAuthMode());
    }
    
    /**
     * Checks if the current authentication configuration is ready to use
     * 
     * @return true if auth is configured and ready
     */
    public boolean isAuthReady() {
        return getActiveStrategy().isReady();
    }
    
    /**
     * Gets the name of the currently active authentication mode
     * 
     * @return "basic" or "oauth"
     */
    public String getAuthMode() {
        return authMode;
    }
    
    /**
     * For OAuth mode: sets the current user context for subsequent requests
     * This is a no-op for Basic Auth mode.
     * 
     * @param userId The user ID to set in context
     */
    public void setOAuthUserContext(String userId) {
        if ("oauth".equalsIgnoreCase(authMode) && oAuthStrategy != null) {
            oAuthStrategy.setCurrentUserId(userId);
            log.debug("Set OAuth user context: {}", userId);
        }
    }
    
    /**
     * For OAuth mode: clears the current user context after request processing
     * This is a no-op for Basic Auth mode.
     */
    public void clearOAuthUserContext() {
        if ("oauth".equalsIgnoreCase(authMode) && oAuthStrategy != null) {
            oAuthStrategy.clearCurrentUserId();
            log.debug("Cleared OAuth user context");
        }
    }
}
