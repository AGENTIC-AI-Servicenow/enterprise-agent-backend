package com.enterprise.agent.service;

import org.springframework.http.HttpHeaders;

/**
 * Strategy interface for ServiceNow authentication
 * Supports both Basic Auth and OAuth flows
 */
public interface ServiceNowAuthStrategy {
    
    /**
     * Add authentication headers to the request
     * @param headers The HTTP headers to modify
     */
    void addAuthHeaders(HttpHeaders headers);
    
    /**
     * Get the authentication mode name
     * @return "basic" or "oauth"
     */
    String getAuthMode();
    
    /**
     * Check if this auth strategy is ready to use
     * @return true if configured and ready
     */
    boolean isReady();
}
