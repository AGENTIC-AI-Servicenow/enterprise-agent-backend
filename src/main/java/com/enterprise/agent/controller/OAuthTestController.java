package com.enterprise.agent.controller;

import com.enterprise.agent.client.ServiceNowClient;
import com.enterprise.agent.service.ServiceNowOAuthTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for testing OAuth 2.0 integration with ServiceNow.
 * Validates that authentication works correctly and user is 'smartiso'.
 */
@RestController
@RequestMapping("/api/oauth-test")
public class OAuthTestController {

    private static final Logger logger = LoggerFactory.getLogger(OAuthTestController.class);

    private final ServiceNowOAuthTokenProvider tokenProvider;
    private final ServiceNowClient serviceNowClient;

    public OAuthTestController(ServiceNowOAuthTokenProvider tokenProvider,
                              ServiceNowClient serviceNowClient) {
        this.tokenProvider = tokenProvider;
        this.serviceNowClient = serviceNowClient;
    }

    /**
     * Test OAuth token acquisition and validation.
     * GET /api/oauth-test/token
     */
    @GetMapping("/token")
    public ResponseEntity<Map<String, Object>> testToken() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Testing OAuth token acquisition");
            
            String token = tokenProvider.getValidAccessToken();
            String authenticatedUser = tokenProvider.getAuthenticatedUsername();
            
            response.put("success", true);
            response.put("message", "OAuth token acquired successfully");
            response.put("tokenPresent", token != null && !token.isEmpty());
            response.put("tokenLength", token != null ? token.length() : 0);
            response.put("authenticatedUser", authenticatedUser);
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("OAuth token test successful for user: {}", authenticatedUser);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("OAuth token test failed", e);
            
            response.put("success", false);
            response.put("message", "OAuth token test failed");
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Test authenticated ServiceNow API call to get current user.
     * GET /api/oauth-test/user
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> testCurrentUser() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Testing ServiceNow current user API call with OAuth");
            
            JsonNode userResponse = serviceNowClient.getCurrentUser();
            
            if (userResponse != null && userResponse.has("result")) {
                JsonNode user = userResponse.get("result");
                
                String username = user.path("user_name").asText();
                String sysId = user.path("sys_id").asText();
                String firstName = user.path("first_name").asText();
                String lastName = user.path("last_name").asText();
                String email = user.path("email").asText();
                
                response.put("success", true);
                response.put("message", "Current user retrieved successfully");
                response.put("username", username);
                response.put("sysId", sysId);
                response.put("firstName", firstName);
                response.put("lastName", lastName);
                response.put("email", email);
                response.put("isSmartiso", "smartiso".equals(username));
                response.put("timestamp", System.currentTimeMillis());
                
                logger.info("Current user API test successful. User: {} ({})", username, sysId);
                
                return ResponseEntity.ok(response);
                
            } else {
                throw new RuntimeException("Invalid response from ServiceNow user API");
            }
            
        } catch (Exception e) {
            logger.error("Current user API test failed", e);
            
            response.put("success", false);
            response.put("message", "Current user API test failed");
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Test creating an incident with OAuth authentication.
     * GET /api/oauth-test/incident
     */
    @GetMapping("/incident")
    public ResponseEntity<Map<String, Object>> testCreateIncident() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Testing incident creation with OAuth authentication");
            
            // First get current user to use as caller
            JsonNode userResponse = serviceNowClient.getCurrentUser();
            if (userResponse == null || !userResponse.has("result")) {
                throw new RuntimeException("Failed to get current user for incident creation");
            }
            
            String callerSysId = userResponse.get("result").path("sys_id").asText();
            
            // Create a test incident
            JsonNode incidentResponse = serviceNowClient.createIncident(
                "OAuth Integration Test Incident",
                "This is a test incident created to validate OAuth 2.0 authentication integration.",
                "4", // Low priority
                callerSysId
            );
            
            if (incidentResponse != null && incidentResponse.has("result")) {
                JsonNode incident = incidentResponse.get("result");
                
                String incidentNumber = incident.path("number").asText();
                String shortDescription = incident.path("short_description").asText();
                String state = incident.path("state").asText();
                
                response.put("success", true);
                response.put("message", "Test incident created successfully");
                response.put("incidentNumber", incidentNumber);
                response.put("shortDescription", shortDescription);
                response.put("state", state);
                response.put("callerSysId", callerSysId);
                response.put("timestamp", System.currentTimeMillis());
                
                logger.info("Incident creation test successful. Created: {}", incidentNumber);
                
                return ResponseEntity.ok(response);
                
            } else {
                throw new RuntimeException("Invalid response from ServiceNow incident creation API");
            }
            
        } catch (Exception e) {
            logger.error("Incident creation test failed", e);
            
            response.put("success", false);
            response.put("message", "Incident creation test failed");
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Force token refresh test.
     * GET /api/oauth-test/refresh
     */
    @GetMapping("/refresh")
    public ResponseEntity<Map<String, Object>> testTokenRefresh() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Testing OAuth token refresh");
            
            // Get current token info
            String tokenBefore = tokenProvider.getValidAccessToken();
            
            // Force refresh
            tokenProvider.forceTokenRefresh();
            
            // Get new token
            String tokenAfter = tokenProvider.getValidAccessToken();
            
            response.put("success", true);
            response.put("message", "Token refresh completed");
            response.put("tokenChanged", !tokenBefore.equals(tokenAfter));
            response.put("tokenLengthBefore", tokenBefore.length());
            response.put("tokenLengthAfter", tokenAfter.length());
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("Token refresh test successful");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Token refresh test failed", e);
            
            response.put("success", false);
            response.put("message", "Token refresh test failed");
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Clear token cache test (for testing scenarios).
     * GET /api/oauth-test/clear
     */
    @GetMapping("/clear")
    public ResponseEntity<Map<String, Object>> testClearCache() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Testing OAuth token cache clearing");
            
            tokenProvider.clearCache();
            
            response.put("success", true);
            response.put("message", "Token cache cleared successfully");
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("Token cache clear test successful");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Token cache clear test failed", e);
            
            response.put("success", false);
            response.put("message", "Token cache clear test failed");
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}
