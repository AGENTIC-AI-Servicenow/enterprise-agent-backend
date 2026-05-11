package com.enterprise.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * OAuth 2.0 Authentication strategy for ServiceNow
 * Uses OAuth tokens for authentication
 * 
 * Enterprise Note: OAuth is recommended for:
 * - Production environments
 * - External API access
 * - User-delegated permissions
 * - Token refresh and revocation capabilities
 */
@Component
public class OAuthStrategy implements ServiceNowAuthStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(OAuthStrategy.class);
    
    @Value("${servicenow.auth.mode:oauth}")
    private String authMode;
    
    private final OAuthAuthorizationService oAuthAuthorizationService;
    
    // ThreadLocal to store current user ID for the request
    private final ThreadLocal<String> currentUserId = new ThreadLocal<>();
    
    public OAuthStrategy(OAuthAuthorizationService oAuthAuthorizationService) {
        this.oAuthAuthorizationService = oAuthAuthorizationService;
    }
    
    /**
     * Set the current user ID for this request context
     */
    public void setCurrentUserId(String userId) {
        this.currentUserId.set(userId);
    }
    
    /**
     * Clear the current user ID after request processing
     */
    public void clearCurrentUserId() {
        this.currentUserId.remove();
    }
    
    @Override
    public void addAuthHeaders(HttpHeaders headers) {
        String userId = currentUserId.get();
        if (userId == null) {
            throw new IllegalStateException("No user context set. Call setCurrentUserId() before making requests.");
        }
        
        if (!isReady()) {
            throw new IllegalStateException("OAuth not configured. User must authenticate first via /oauth/authorize");
        }
        
        String accessToken = oAuthAuthorizationService.getValidAccessToken(userId);
        headers.setBearerAuth(accessToken);
        
        log.debug("Added OAuth Bearer token for user: {}", userId);
    }
    
    @Override
    public String getAuthMode() {
        return "oauth";
    }
    
    @Override
    public boolean isReady() {
        String userId = currentUserId.get();
        if (userId == null) {
            log.warn("OAuth not ready: No user context set");
            return false;
        }
        
        boolean ready = "oauth".equalsIgnoreCase(authMode) 
            && oAuthAuthorizationService.hasValidToken(userId);
        
        if (!ready) {
            log.warn("OAuth not ready. Mode: {}, User: {}, Has token: {}", 
                authMode, userId, oAuthAuthorizationService.hasValidToken(userId));
        }
        
        return ready;
    }
}
