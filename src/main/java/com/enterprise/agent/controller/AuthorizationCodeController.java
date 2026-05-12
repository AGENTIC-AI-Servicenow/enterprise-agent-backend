package com.enterprise.agent.controller;

import com.enterprise.agent.service.BasicAuthSessionService;
import com.enterprise.agent.service.OAuthAuthorizationService;
import com.enterprise.agent.service.UserContextService;
import com.enterprise.agent.client.ServiceNowClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for OAuth 2.0 Authorization Code Flow testing and user validation.
 * Replaces the old password-based OAuth testing with Authorization Code Flow.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthorizationCodeController {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationCodeController.class);

    private final OAuthAuthorizationService authorizationService;
    private final UserContextService userContextService;
    private final BasicAuthSessionService basicAuthSessionService;

    public AuthorizationCodeController(OAuthAuthorizationService authorizationService,
                                       UserContextService userContextService,
                                       BasicAuthSessionService basicAuthSessionService) {
        this.authorizationService = authorizationService;
        this.userContextService = userContextService;
        this.basicAuthSessionService = basicAuthSessionService;
    }

    // =========================================================================
    // Basic Auth login / logout
    // =========================================================================

    /**
     * Creates a Basic Auth session for a ServiceNow user.
     *
     * POST /api/auth/login
     * Body: { "username": "...", "password": "..." }
     *
     * Returns a userId that must be sent as X-User-Id header in subsequent calls.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        Map<String, Object> response = new HashMap<>();

        String username = credentials.getOrDefault("username", "").trim();
        String password = credentials.getOrDefault("password", "");

        if (username.isEmpty() || password.isEmpty()) {
            response.put("success", false);
            response.put("message", "username and password are required");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            String userId = basicAuthSessionService.createSession(username, password);

            response.put("success", true);
            response.put("message", "Login successful. Include X-User-Id header in all subsequent requests.");
            response.put("userId", userId);
            response.put("username", username);
            response.put("authMode", "basic");
            response.put("timestamp", System.currentTimeMillis());

            logger.info("Basic Auth session created for username: {} -> userId: {}", username, userId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Login failed for username: {}", username, e);
            response.put("success", false);
            response.put("message", "Login failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Removes a Basic Auth session.
     *
     * DELETE /api/auth/login/{userId}
     */
    @DeleteMapping("/login/{userId}")
    public ResponseEntity<Map<String, Object>> logout(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();

        if (!basicAuthSessionService.hasSession(userId)) {
            response.put("success", false);
            response.put("message", "Session not found for userId: " + userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        String username = basicAuthSessionService.getUsername(userId);
        basicAuthSessionService.invalidateSession(userId);

        response.put("success", true);
        response.put("message", "Session invalidated successfully");
        response.put("userId", userId);
        response.put("username", username);
        response.put("timestamp", System.currentTimeMillis());

        logger.info("Basic Auth session invalidated for userId: {} (username: {})", userId, username);
        return ResponseEntity.ok(response);
    }

    /**
     * Validates a user and returns their information.
     * This endpoint confirms that the user corresponds to the authenticated token.
     * 
     * GET /api/auth/validate/{userId}
     */
    @GetMapping("/validate/{userId}")
    public ResponseEntity<Map<String, Object>> validateUser(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Validating user: {}", userId);
            
            // Validate user and get their information
            UserContextService.UserInfo userInfo = userContextService.validateAndGetUserInfo(userId);
            
            response.put("success", true);
            response.put("message", "User validation successful");
            response.put("user", Map.of(
                "userId", userId,
                "sysId", userInfo.getSysId(),
                "username", userInfo.getUsername(),
                "firstName", userInfo.getFirstName(),
                "lastName", userInfo.getLastName(),
                "email", userInfo.getEmail(),
                "authenticated", userContextService.isUserAuthenticated(userId)
            ));
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("User validation successful for: {} ({})", userInfo.getUsername(), userInfo.getSysId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("User validation failed for userId: {}", userId, e);
            
            response.put("success", false);
            response.put("message", "User validation failed");
            response.put("userId", userId);
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    /**
     * Test ServiceNow API call with user-specific token.
     * This demonstrates how to make API calls on behalf of a specific user.
     * 
     * GET /api/auth/test-api/{userId}
     */
    @GetMapping("/test-api/{userId}")
    public ResponseEntity<Map<String, Object>> testUserApiCall(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Testing API call for user: {}", userId);
            
            // Create user-specific ServiceNow client
            ServiceNowClient userClient = userContextService.createUserServiceNowClient(userId);
            
            // Make API call to get current user (should match the authenticated user)
            JsonNode userResponse = userClient.getCurrentUser();
            
            if (userResponse != null && userResponse.has("result") 
                && userResponse.get("result").isArray() 
                && userResponse.get("result").size() > 0) {

                JsonNode user = userResponse.get("result").get(0);

                String username = user.path("user_name").asText();
                String sysId = user.path("sys_id").asText();
                String firstName = user.path("first_name").asText();
                String lastName = user.path("last_name").asText();
                String email = user.path("email").asText();
                
                response.put("success", true);
                response.put("message", "API call successful");
                response.put("userId", userId);
                response.put("apiResponse", Map.of(
                    "username", username,
                    "sysId", sysId,
                    "firstName", firstName,
                    "lastName", lastName,
                    "email", email
                ));
                response.put("timestamp", System.currentTimeMillis());
                
                logger.info("API call successful for user: {} -> ServiceNow user: {}", userId, username);
                
                return ResponseEntity.ok(response);
                
            } else {
                throw new RuntimeException("Invalid response from ServiceNow API");
            }
            
        } catch (Exception e) {
            logger.error("API call failed for userId: {}", userId, e);
            
            response.put("success", false);
            response.put("message", "API call failed");
            response.put("userId", userId);
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Test incident creation with user-specific token.
     * 
     * POST /api/auth/test-incident/{userId}
     */
    @PostMapping("/test-incident/{userId}")
    public ResponseEntity<Map<String, Object>> testIncidentCreation(
            @PathVariable String userId,
            @RequestBody(required = false) Map<String, String> incidentData) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Testing incident creation for user: {}", userId);
            
            // Get user info for caller ID
            UserContextService.UserInfo userInfo = userContextService.validateAndGetUserInfo(userId);
            
            // Create user-specific ServiceNow client
            ServiceNowClient userClient = userContextService.createUserServiceNowClient(userId);
            
            // Extract incident data or use defaults
            String shortDescription = incidentData != null ? 
                incidentData.getOrDefault("shortDescription", "Authorization Code Flow Test Incident") :
                "Authorization Code Flow Test Incident";
            
            String description = incidentData != null ?
                incidentData.getOrDefault("description", "Test incident created via Authorization Code Flow") :
                "Test incident created to validate OAuth 2.0 Authorization Code Flow integration";
            
            String priority = incidentData != null ?
                incidentData.getOrDefault("priority", "4") : "4"; // Low priority
            
            // Create incident
            JsonNode incidentResponse = userClient.createIncident(
                shortDescription,
                description,
                priority,
                userInfo.getSysId()
            );
            
            if (incidentResponse != null && incidentResponse.has("result")) {
                JsonNode incident = incidentResponse.get("result");
                
                String incidentNumber = incident.path("number").asText();
                String incidentShortDesc = incident.path("short_description").asText();
                String state = incident.path("state").asText();
                String incidentSysId = incident.path("sys_id").asText();
                
                response.put("success", true);
                response.put("message", "Incident created successfully");
                response.put("userId", userId);
                response.put("createdBy", userInfo.getUsername());
                response.put("incident", Map.of(
                    "number", incidentNumber,
                    "sysId", incidentSysId,
                    "shortDescription", incidentShortDesc,
                    "state", state,
                    "callerSysId", userInfo.getSysId()
                ));
                response.put("timestamp", System.currentTimeMillis());
                
                logger.info("Incident created successfully for user {}: {}", userId, incidentNumber);
                
                return ResponseEntity.ok(response);
                
            } else {
                throw new RuntimeException("Invalid response from ServiceNow incident creation API");
            }
            
        } catch (Exception e) {
            logger.error("Incident creation failed for userId: {}", userId, e);
            
            response.put("success", false);
            response.put("message", "Incident creation failed");
            response.put("userId", userId);
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get user's incidents (filtered by caller).
     * 
     * GET /api/auth/incidents/{userId}
     */
    @GetMapping("/incidents/{userId}")
    public ResponseEntity<Map<String, Object>> getUserIncidents(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Retrieving incidents for user: {}", userId);
            
            // Get user info
            UserContextService.UserInfo userInfo = userContextService.validateAndGetUserInfo(userId);
            
            // Create user-specific ServiceNow client
            ServiceNowClient userClient = userContextService.createUserServiceNowClient(userId);
            
            // Get incidents for this caller
            JsonNode incidentsResponse = userClient.getIncidentsByCallerId(userInfo.getSysId());
            
            response.put("success", true);
            response.put("message", "Incidents retrieved successfully");
            response.put("userId", userId);
            response.put("caller", userInfo.getUsername());
            response.put("callerSysId", userInfo.getSysId());
            response.put("incidents", incidentsResponse);
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("Incidents retrieved successfully for user: {}", userId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to retrieve incidents for userId: {}", userId, e);
            
            response.put("success", false);
            response.put("message", "Failed to retrieve incidents");
            response.put("userId", userId);
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Invalidate user session.
     * 
     * DELETE /api/auth/session/{userId}
     */
    @DeleteMapping("/session/{userId}")
    public ResponseEntity<Map<String, Object>> invalidateSession(@PathVariable String userId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Invalidating session for user: {}", userId);
            
            userContextService.invalidateUser(userId);
            
            response.put("success", true);
            response.put("message", "Session invalidated successfully");
            response.put("userId", userId);
            response.put("timestamp", System.currentTimeMillis());
            
            logger.info("Session invalidated successfully for user: {}", userId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to invalidate session for userId: {}", userId, e);
            
            response.put("success", false);
            response.put("message", "Failed to invalidate session");
            response.put("userId", userId);
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Get system status and active sessions.
     * 
     * GET /api/auth/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            int activeUsers = userContextService.getActiveUserCount();
            int activeSessions = authorizationService.getActiveSessionCount();
            
            response.put("success", true);
            response.put("message", "System status retrieved");
            response.put("status", Map.of(
                "activeUsers", activeUsers,
                "activeSessions", activeSessions,
                "authenticationFlow", "Authorization Code Flow",
                "tokenStorage", "In-Memory (ConcurrentHashMap)",
                "refreshTokenSupport", true
            ));
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Failed to get system status", e);
            
            response.put("success", false);
            response.put("message", "Failed to get system status");
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
