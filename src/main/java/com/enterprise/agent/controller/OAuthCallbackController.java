package com.enterprise.agent.controller;

import com.enterprise.agent.service.OAuthAuthorizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth 2.0 Authorization Code callback controller.
 * Handles the redirect from ServiceNow after user authorization.
 */
@RestController
public class OAuthCallbackController {

    private static final Logger logger = LoggerFactory.getLogger(OAuthCallbackController.class);

    private final OAuthAuthorizationService authorizationService;

    @Value("${frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public OAuthCallbackController(OAuthAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * OAuth callback endpoint that receives the authorization code from ServiceNow.
     * This endpoint is called by ServiceNow after user authorization.
     * 
     * Expected URL: http://localhost:8080/oauth/callback?code=AUTHORIZATION_CODE&state=STATE_VALUE
     * 
     * @param code The authorization code from ServiceNow
     * @param state The state parameter (for CSRF protection)
     * @param error Optional error parameter if authorization failed
     * @param errorDescription Optional error description
     * @return Redirect to success/error page or JSON response
     */
    @GetMapping("/oauth/callback")
    public ResponseEntity<?> handleOAuthCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDescription) {

        logger.info("OAuth callback received - code present: {}, error: {}", 
                   code != null, error);

        // Handle authorization errors
        if (error != null) {
            logger.error("OAuth authorization failed: {} - {}", error, errorDescription);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", error);
            errorResponse.put("error_description", errorDescription != null ? errorDescription : "Authorization failed");
            errorResponse.put("message", "OAuth authorization was denied or failed");
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        // Validate that we received an authorization code
        if (code == null || code.trim().isEmpty()) {
            logger.error("No authorization code received in OAuth callback");
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "missing_code");
            errorResponse.put("message", "No authorization code received from ServiceNow");
            errorResponse.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        try {
            // Use state as a stable per-user/session key for token storage.
            // This prevents mismatches between frontend X-User-Id and backend token map.
            String userKey = (state != null && !state.isBlank()) ? state : null;

            // Exchange authorization code for tokens
            String userId = authorizationService.exchangeCodeForToken(code, userKey);

            logger.info("OAuth authorization successful for user: {}, redirecting to frontend", userId);

            // Redirect to the frontend callback page so it can store the userId (key)
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(frontendUrl + "/auth/callback?userId=" + userId + "&success=true"))
                    .build();

        } catch (Exception e) {
            logger.error("Failed to process OAuth callback", e);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(java.net.URI.create(frontendUrl + "/auth/callback?error=token_exchange_failed&details="
                            + java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8)))
                    .build();
        }
    }

    /**
     * Proper endpoint to start OAuth Authorization Code flow.
     * Redirects user to ServiceNow authorization endpoint.
     */
    @GetMapping("/oauth/authorize")
    public RedirectView authorize() {
        String state = generateStateParameter();
        String authUrl = authorizationService.buildAuthorizationUrl(state);

        logger.info("Redirecting user to ServiceNow OAuth endpoint. State: {}", state);

        return new RedirectView(authUrl);
    }

    /**
     * Simple error page for OAuth failures.
     */
    @GetMapping("/oauth/error")
    public ResponseEntity<Map<String, Object>> oauthError() {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("error", "oauth_error");
        errorResponse.put("message", "OAuth authorization failed or was cancelled");
        errorResponse.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Generates a random state parameter for CSRF protection.
     * In production, this should be cryptographically secure and stored per session.
     */
    private String generateStateParameter() {
        return "state_" + System.currentTimeMillis() + "_" + Math.random();
    }
}
