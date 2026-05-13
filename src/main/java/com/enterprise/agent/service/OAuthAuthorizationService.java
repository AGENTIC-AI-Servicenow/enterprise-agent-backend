package com.enterprise.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * OAuth 2.0 Authorization Code Flow service for ServiceNow integration.
 * Handles code exchange, token storage, and refresh token management.
 * Thread-safe implementation with per-user token storage.
 */
@Service
public class OAuthAuthorizationService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthAuthorizationService.class);

    @Value("${servicenow.oauth.token-url}")
    private String tokenUrl;

    @Value("${servicenow.oauth.client-id}")
    private String clientId;

    @Value("${servicenow.oauth.client-secret}")
    private String clientSecret;

    @Value("${servicenow.oauth.redirect-uri}")
    private String redirectUri;

    @Value("${servicenow.oauth.authorization-url}")
    private String authorizationUrl;

    @Value("${servicenow.oauth.scope:useraccount}")
    private String scope;

    @Value("${servicenow.oauth.token-expiry-margin-seconds:30}")
    private int expirationMarginSeconds;

    @Value("${servicenow.oauth.max-retry-attempts:3}")
    private int maxRetryAttempts;

    // Thread-safe token storage per user
    private final ConcurrentHashMap<String, UserTokenInfo> userTokens = new ConcurrentHashMap<>();
    private final ReentrantLock tokenLock = new ReentrantLock();

    // Dedicated WebClient for OAuth operations to avoid circular dependency
    private final WebClient oauthWebClient;

    public OAuthAuthorizationService() {
        this.oauthWebClient = WebClient.builder().build();
    }

    /**
     * Builds the ServiceNow Authorization URL for Authorization Code flow.
     */
    public String buildAuthorizationUrl(String state) {
        // ServiceNow expects the scopes requested in the initial authorize step
        // to match what you'll ask for later when exchanging the code for tokens.
        //
        // NOTE: "useraccount" alone is not sufficient for Table API access; we also
        // need at least "useraccount_read" and "useraccount_write" for typical CRUD use cases.
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String encodedScope = URLEncoder.encode(scope, StandardCharsets.UTF_8);

        return String.format(
                "%s?response_type=code&client_id=%s&redirect_uri=%s&scope=%s&state=%s",
                authorizationUrl,
                clientId,
                encodedRedirect,
                encodedScope,
                state
        );
    }

    /**
     * Exchanges authorization code for access and refresh tokens.
     *
     * @param authorizationCode The authorization code from ServiceNow
     * @return The user ID extracted from the token response
     * @throws RuntimeException if code exchange fails
     */
    public String exchangeCodeForToken(String authorizationCode) {
        return exchangeCodeForToken(authorizationCode, null);
    }

    /**
     * Exchanges authorization code for access and refresh tokens, storing them
     * under a stable key that the frontend will send back via X-User-Id.
     *
     * @param authorizationCode The authorization code from ServiceNow
     * @param userKey           Stable key to store tokens under (recommended: OAuth state). If null/blank, falls back to generated key.
     * @return The user key used to store the tokens (what the frontend must use as X-User-Id)
     */
    public String exchangeCodeForToken(String authorizationCode, String userKey) {
        logger.info("Exchanging authorization code for access token");

        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "authorization_code");
            formData.add("code", authorizationCode);
            formData.add("redirect_uri", redirectUri);
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);
            // Must match scopes requested at /oauth/authorize
            formData.add("scope", scope);

            JsonNode tokenResponse = oauthWebClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> response.bodyToMono(String.class)
                                    .map(errorBody -> {
                                        logger.error("Authorization code exchange failed: {}", errorBody);
                                        return new RuntimeException(
                                                "Authorization code exchange failed: " + errorBody);
                                    })
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .map(errorBody -> {
                                        logger.error("ServiceNow server error during code exchange: {}", errorBody);
                                        return new RuntimeException(
                                                "ServiceNow server error: " + errorBody);
                                    })
                    )
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofSeconds(1))
                            .filter(throwable -> throwable instanceof WebClientResponseException.InternalServerError))
                    .block();

            if (tokenResponse == null || !tokenResponse.has("access_token")) {
                throw new RuntimeException("Invalid token response received");
            }

            // Use provided stable userKey (state) when available; otherwise fallback.
            String resolvedUserId = (userKey != null && !userKey.isBlank())
                    ? userKey
                    : extractUserIdFromToken(tokenResponse);

            // Store tokens for this user key
            storeUserTokens(resolvedUserId, tokenResponse);

            logger.info("Authorization code successfully exchanged for userKey: {}", resolvedUserId);
            return resolvedUserId;

        } catch (Exception e) {
            logger.error("Failed to exchange authorization code for token", e);
            throw new RuntimeException("Authorization code exchange failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets a valid access token for the specified user.
     * Automatically refreshes if token is expired or close to expiration.
     *
     * @param userId The user ID
     * @return Valid access token
     * @throws RuntimeException if no token exists or refresh fails
     */
    public String getValidAccessToken(String userId) {
        UserTokenInfo tokenInfo = userTokens.get(userId);
        
        if (tokenInfo == null) {
            throw new RuntimeException("No token found for user: " + userId);
        }

        if (isTokenValid(tokenInfo)) {
            return tokenInfo.accessToken;
        }

        tokenLock.lock();
        try {
            // Double-check pattern
            tokenInfo = userTokens.get(userId);
            if (tokenInfo != null && isTokenValid(tokenInfo)) {
                return tokenInfo.accessToken;
            }

            // Attempt token refresh
            logger.info("Refreshing token for user: {}", userId);
            return refreshToken(userId, tokenInfo);

        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Refreshes the access token using the refresh token.
     *
     * @param userId The user ID
     * @param tokenInfo Current token information
     * @return New access token
     */
    private String refreshToken(String userId, UserTokenInfo tokenInfo) {
        if (tokenInfo == null || tokenInfo.refreshToken == null) {
            throw new RuntimeException("No refresh token available for user: " + userId);
        }

        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "refresh_token");
            formData.add("refresh_token", tokenInfo.refreshToken);
            formData.add("client_id", clientId);
            formData.add("client_secret", clientSecret);
            // Must match scopes requested at /oauth/authorize
            formData.add("scope", scope);

            JsonNode tokenResponse = oauthWebClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> response.bodyToMono(String.class)
                                    .map(errorBody -> {
                                        logger.error("Token refresh failed for user {}: {}", userId, errorBody);
                                        return new RuntimeException(
                                                "Token refresh failed: " + errorBody);
                                    })
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .map(errorBody -> {
                                        logger.error("ServiceNow server error during token refresh for user {}: {}", userId, errorBody);
                                        return new RuntimeException(
                                                "ServiceNow server error during token refresh: " + errorBody);
                                    })
                    )
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(maxRetryAttempts, Duration.ofSeconds(1))
                            .filter(throwable -> throwable instanceof WebClientResponseException.InternalServerError))
                    .block();

            if (tokenResponse == null || !tokenResponse.has("access_token")) {
                throw new RuntimeException("Invalid refresh token response");
            }

            // Update stored tokens
            storeUserTokens(userId, tokenResponse);
            
            logger.info("Token successfully refreshed for user: {}", userId);
            return tokenResponse.path("access_token").asText();

        } catch (Exception e) {
            logger.error("Failed to refresh token for user: {}", userId, e);
            // Remove invalid token info
            userTokens.remove(userId);
            throw new RuntimeException("Token refresh failed for user " + userId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Stores token information for a user.
     */
    private void storeUserTokens(String userId, JsonNode tokenResponse) {
        String accessToken = tokenResponse.path("access_token").asText();
        String refreshToken = tokenResponse.path("refresh_token").asText();
        int expiresInSeconds = tokenResponse.path("expires_in").asInt(3600);

        Instant expiresAt = Instant.now().plusSeconds(expiresInSeconds);

        UserTokenInfo tokenInfo = new UserTokenInfo(
                accessToken,
                refreshToken,
                expiresAt
        );

        userTokens.put(userId, tokenInfo);
        logger.debug("Stored tokens for user: {}, expires at: {}", userId, expiresAt);
    }

    /**
     * Extracts user ID from token response.
     * This is a simplified implementation - in practice, you might decode JWT or make an API call.
     */
    private String extractUserIdFromToken(JsonNode tokenResponse) {
        // For now, use a timestamp-based approach or check if there's user info in the response
        // In a real implementation, you'd either:
        // 1. Decode JWT token to get user info
        // 2. Make a /api/now/user call with the token
        // 3. Use user info from the authorization flow
        
        // For this PoC, we'll generate a session-based user ID
        // This will be replaced when we implement proper user validation
        return "user_" + System.currentTimeMillis();
    }

    /**
     * Checks if a token is still valid considering the expiration margin.
     */
    private boolean isTokenValid(UserTokenInfo tokenInfo) {
        if (tokenInfo == null || tokenInfo.accessToken == null || tokenInfo.expiresAt == null) {
            return false;
        }

        return Instant.now().plusSeconds(expirationMarginSeconds).isBefore(tokenInfo.expiresAt);
    }

    /**
     * Invalidates a user's session by removing their tokens.
     */
    public void invalidateUserSession(String userId) {
        tokenLock.lock();
        try {
            userTokens.remove(userId);
            logger.info("Session invalidated for user: {}", userId);
        } finally {
            tokenLock.unlock();
        }
    }

    /**
     * Checks if a user has valid tokens stored.
     */
    public boolean hasValidToken(String userId) {
        UserTokenInfo tokenInfo = userTokens.get(userId);
        return tokenInfo != null && isTokenValid(tokenInfo);
    }

    /**
     * Gets all active user sessions (for monitoring/debugging).
     */
    public int getActiveSessionCount() {
        return userTokens.size();
    }

    /**
     * Returns the set of user keys that currently have tokens stored in-memory.
     * Diagnostic only (does not expose tokens).
     */
    public java.util.Set<String> getSessionKeys() {
        return userTokens.keySet();
    }

    /**
     * Token information storage class.
     */
    private static class UserTokenInfo {
        final String accessToken;
        final String refreshToken;
        final Instant expiresAt;

        UserTokenInfo(String accessToken, String refreshToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAt = expiresAt;
        }
    }
}
