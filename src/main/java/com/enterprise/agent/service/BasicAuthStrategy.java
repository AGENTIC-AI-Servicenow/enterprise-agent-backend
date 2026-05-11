package com.enterprise.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Basic Authentication strategy for ServiceNow
 * Uses username/password for authentication
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
    
    @Value("${servicenow.auth.username:}")
    private String username;
    
    @Value("${servicenow.auth.password:}")
    private String password;
    
    @Value("${servicenow.auth.mode:oauth}")
    private String authMode;
    
    @Override
    public void addAuthHeaders(HttpHeaders headers) {
        if (!isReady()) {
            throw new IllegalStateException("Basic Auth not configured. Set SERVICENOW_USERNAME and SERVICENOW_PASSWORD");
        }
        
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth);
        
        headers.set(HttpHeaders.AUTHORIZATION, authHeader);
        
        log.debug("Added Basic Auth header for user: {}", username);
    }
    
    @Override
    public String getAuthMode() {
        return "basic";
    }
    
    @Override
    public boolean isReady() {
        boolean ready = "basic".equalsIgnoreCase(authMode) 
            && username != null && !username.isEmpty() 
            && !username.equals("your-username")
            && password != null && !password.isEmpty()
            && !password.equals("your-password");
        
        if (!ready) {
            log.warn("Basic Auth not ready. Mode: {}, Username configured: {}", 
                authMode, username != null && !username.isEmpty());
        }
        
        return ready;
    }
}
