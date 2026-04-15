package com.enterprise.agent.service;

import com.enterprise.agent.dto.ServiceNowTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service responsible for handling OAuth2 Client Credentials flow with ServiceNow.
 *
 * Responsibilities:
 * - Retrieve access_token using grant_type=client_credentials
 * - Cache token in memory
 * - Auto refresh when close to expiration
 *
 * Designed for future extension to support additional grant types.
 */
@Service
public class ServiceNowAuthService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNowAuthService.class);

    private final WebClient webClient;

    @Value("${servicenow.oauth.token-url}")
    private String tokenUrl;

    @Value("${servicenow.oauth.client-id}")
    private String clientId;

    @Value("${servicenow.oauth.client-secret}")
    private String clientSecret;

    @Value("${servicenow.oauth.token-expiry-margin-seconds:30}")
    private long expiryMarginSeconds;

    private final AtomicReference<String> cachedToken = new AtomicReference<>();
    private volatile Instant tokenExpiryTime = Instant.MIN;

    public ServiceNowAuthService(WebClient.Builder builder) {
        this.webClient = builder.build();
    }

    /**
     * Returns a valid access token, refreshing if needed.
     */
    public synchronized String getAccessToken() {
        if (isTokenExpired()) {
            logger.debug("Access token missing or expiring soon. Triggering refresh.");
            refreshToken();
        } else {
            logger.debug("Using cached OAuth token. Expires at: {}", tokenExpiryTime);
        }
        return cachedToken.get();
    }

    /**
     * Forces token refresh (used after 401).
     */
    public synchronized void refreshIfNeeded() {
        logger.info("Forcing OAuth token refresh due to 401 or manual trigger.");
        refreshToken();
    }

    private boolean isTokenExpired() {
        return cachedToken.get() == null ||
                Instant.now().isAfter(tokenExpiryTime.minusSeconds(expiryMarginSeconds));
    }

    private void refreshToken() {
        logger.debug("Requesting new OAuth token from ServiceNow using Client Credentials grant. Token URL: {}", tokenUrl);

        try {
            ServiceNowTokenResponse response = webClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                            .with("client_id", clientId)
                            .with("client_secret", clientSecret))
                    .retrieve()
                    .onStatus(status -> status.isError(), clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .doOnNext(body -> logger.error(
                                            "Token endpoint returned error. HTTP {} - Body: {}",
                                            clientResponse.statusCode(),
                                            body))
                                    .then(Mono.error(new RuntimeException(
                                            "Token endpoint error: " + clientResponse.statusCode())))
                    )
                    .bodyToMono(ServiceNowTokenResponse.class)
                    .block();

            if (response == null || response.getAccessToken() == null) {
                throw new IllegalStateException("Invalid token response from ServiceNow");
            }

            String token = response.getAccessToken();
            cachedToken.set(token);
            tokenExpiryTime = Instant.now().plusSeconds(response.getExpiresIn());

            logger.debug("OAuth token received. Length: {} chars, Last 6 chars: {}",
                    token.length(),
                    token.length() > 6 ? token.substring(token.length() - 6) : "N/A");

            logger.debug("Token expires at: {} (in {} seconds)",
                    tokenExpiryTime,
                    response.getExpiresIn());

        } catch (Exception e) {
            logger.error("OAuth token retrieval failed", e);
            throw new RuntimeException("Failed to retrieve OAuth token", e);
        }
    }
}
