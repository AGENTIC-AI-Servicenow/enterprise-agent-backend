package com.enterprise.agent.service;

import com.enterprise.agent.client.ServiceNowClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing user context and validation.
 * Provides user-specific token management and validation against ServiceNow.
 */
@Service
public class UserContextService {

    private static final Logger logger = LoggerFactory.getLogger(UserContextService.class);

    private final OAuthAuthorizationService authorizationService;
    private final ConcurrentHashMap<String, UserInfo> userInfoCache = new ConcurrentHashMap<>();

    public UserContextService(OAuthAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * Validates a user's token and retrieves their information from ServiceNow.
     * This method ensures the user corresponds to the authenticated token.
     *
     * @param userId The user ID
     * @return UserInfo containing validated user details
     * @throws RuntimeException if validation fails
     */
    public UserInfo validateAndGetUserInfo(String userId) {
        logger.info("Validating user context for: {}", userId);

        // Check if we have cached user info
        UserInfo cachedInfo = userInfoCache.get(userId);
        if (cachedInfo != null && authorizationService.hasValidToken(userId)) {
            logger.debug("Using cached user info for: {}", userId);
            return cachedInfo;
        }

        try {
            // Get valid token for the user
            String accessToken = authorizationService.getValidAccessToken(userId);
            
            // Create a temporary ServiceNowClient with this user's token
            JsonNode userResponse = makeUserInfoCall(accessToken);
            
            if (userResponse == null || !userResponse.has("result")) {
                throw new RuntimeException("Invalid user info response from ServiceNow");
            }

            JsonNode user = userResponse.get("result");
            UserInfo userInfo = new UserInfo(
                    user.path("sys_id").asText(),
                    user.path("user_name").asText(),
                    user.path("first_name").asText(),
                    user.path("last_name").asText(),
                    user.path("email").asText(),
                    accessToken
            );

            // Cache the user info
            userInfoCache.put(userId, userInfo);
            
            logger.info("User validation successful - User: {} ({})", 
                       userInfo.getUsername(), userInfo.getSysId());
            
            return userInfo;

        } catch (Exception e) {
            logger.error("User validation failed for userId: {}", userId, e);
            // Remove from cache if validation fails
            userInfoCache.remove(userId);
            throw new RuntimeException("User validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets a valid access token for a user, with validation.
     *
     * @param userId The user ID
     * @return Valid access token
     * @throws RuntimeException if no valid token exists
     */
    public String getValidAccessTokenForUser(String userId) {
        if (!authorizationService.hasValidToken(userId)) {
            throw new RuntimeException("No valid token found for user: " + userId);
        }

        return authorizationService.getValidAccessToken(userId);
    }

    /**
     * Creates a ServiceNow client configured for a specific user.
     * This creates a WebClient with the user's token pre-configured.
     *
     * @param userId The user ID
     * @return ServiceNowClient configured with user's token
     */
    public ServiceNowClient createUserServiceNowClient(String userId) {
        String accessToken = getValidAccessTokenForUser(userId);
        
        // Create a WebClient with the user's token and proper configuration
        WebClient userWebClient = WebClient.builder()
                .baseUrl("https://everisspainsludemo3.service-now.com")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();

        // Return a ServiceNowClient that uses this user-specific WebClient
        return new ServiceNowClient(userWebClient);
    }

    /**
     * Invalidates a user's session and clears cached data.
     *
     * @param userId The user ID
     */
    public void invalidateUser(String userId) {
        logger.info("Invalidating user session: {}", userId);
        authorizationService.invalidateUserSession(userId);
        userInfoCache.remove(userId);
    }

    /**
     * Checks if a user has a valid authenticated session.
     *
     * @param userId The user ID
     * @return true if user has valid session
     */
    public boolean isUserAuthenticated(String userId) {
        return authorizationService.hasValidToken(userId);
    }

    /**
     * Gets the count of active user sessions.
     *
     * @return Number of active sessions
     */
    public int getActiveUserCount() {
        return authorizationService.getActiveSessionCount();
    }

    /**
     * Makes a direct API call to get user information using the provided token.
     */
    private JsonNode makeUserInfoCall(String accessToken) {
        WebClient tempClient = WebClient.builder()
                .baseUrl("https://everisspainsludemo3.service-now.com")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();

        return tempClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/now/table/sys_user")
                        .queryParam("sysparm_query", "sys_id=javascript:gs.getUserID()")
                        .queryParam("sysparm_fields", "sys_id,user_name,first_name,last_name,email")
                        .build())
                .retrieve()
                .onStatus(
                        status -> status.is4xxClientError(),
                        response -> response.bodyToMono(String.class)
                                .map(errorBody -> {
                                    logger.error("User info API call failed: {}", errorBody);
                                    return new RuntimeException("User validation failed: " + errorBody);
                                })
                )
                .bodyToMono(JsonNode.class)
                .block();
    }

    /**
     * User information holder class.
     */
    public static class UserInfo {
        private final String sysId;
        private final String username;
        private final String firstName;
        private final String lastName;
        private final String email;
        private final String accessToken;

        public UserInfo(String sysId, String username, String firstName, 
                       String lastName, String email, String accessToken) {
            this.sysId = sysId;
            this.username = username;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.accessToken = accessToken;
        }

        public String getSysId() { return sysId; }
        public String getUsername() { return username; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getEmail() { return email; }
        public String getAccessToken() { return accessToken; }
    }

}
