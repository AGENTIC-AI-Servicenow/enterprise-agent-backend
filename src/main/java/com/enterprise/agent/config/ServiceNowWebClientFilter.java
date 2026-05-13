package com.enterprise.agent.config;

import com.enterprise.agent.service.ServiceNowAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * WebClient filter that automatically injects authentication headers
 * Supports both Basic Auth and OAuth strategies based on configuration.
 * 
 * Enterprise Pattern: Filter + Strategy
 * - Transparently handles authentication for all ServiceNow API calls
 * - Supports multiple auth strategies without changing client code
 * - Provides centralized error handling and logging
 */
@Component
public class ServiceNowWebClientFilter implements ExchangeFilterFunction {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNowWebClientFilter.class);

    private final ServiceNowAuthService authService;

    public ServiceNowWebClientFilter(ServiceNowAuthService authService) {
        this.authService = authService;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        // Skip auth injection for OAuth token endpoint
        if (isOAuthTokenEndpoint(request)) {
            logger.debug("Skipping auth injection for OAuth token endpoint");
            return next.exchange(request);
        }

        try {
            logger.debug("Adding authentication headers for request: {} {}", request.method(), request.url());

            HttpHeaders authHeaders = new HttpHeaders();
            authService.addAuthHeaders(authHeaders);

            ClientRequest mutated = ClientRequest.from(request)
                    .headers(h -> authHeaders.forEach((k, v) -> h.put(k, v)))
                    .build();

            // Debug: verify Authorization header is actually present in the final ClientRequest
            String auth = mutated.headers().getFirst(HttpHeaders.AUTHORIZATION);
            if (auth == null) {
                logger.warn("Authorization header is NULL after mutation for request: {} {}", mutated.method(), mutated.url());
            } else {
                String preview = auth.length() > 15 ? auth.substring(0, 15) + "..." : auth;
                logger.info("Authorization header present. length={}, preview='{}' for {} {}",
                        auth.length(), preview, mutated.method(), mutated.url());
            }

            logger.debug("Authentication headers added successfully using {} mode", authService.getAuthMode());

            return next.exchange(mutated).flatMap(this::handleResponse);

        } catch (Exception e) {
            logger.error("Failed to add authentication headers: {}", e.getMessage(), e);
            return Mono.error(new RuntimeException("Authentication failed: " + e.getMessage(), e));
        }
    }

    /**
     * Handles authentication errors.
     * For OAuth: Future enhancement could implement automatic token refresh
     * For Basic Auth: Logs the error for investigation
     */
    private Mono<ClientResponse> handleResponse(ClientResponse response) {
        if (response.statusCode() == HttpStatus.UNAUTHORIZED) {
            logger.error("401 Unauthorized received from ServiceNow. Auth mode: {}", 
                    authService.getAuthMode());
            logger.error("Request URL: {}", response.request().getURI());
            
            if ("oauth".equalsIgnoreCase(authService.getAuthMode())) {
                logger.error("OAuth token may be expired or invalid. User needs to re-authenticate.");
            } else {
                logger.error("Basic Auth credentials may be incorrect. Check SERVICENOW_USERNAME and SERVICENOW_PASSWORD.");
            }
        }

        return Mono.just(response);
    }

    /**
     * Checks if the request is targeting the OAuth token endpoint.
     */
    private boolean isOAuthTokenEndpoint(ClientRequest request) {
        String uri = request.url().toString();
        return uri.contains("/oauth_token.do");
    }
}
