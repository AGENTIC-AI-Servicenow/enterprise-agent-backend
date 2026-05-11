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

        return addAuthHeaders(request)
                .flatMap(next::exchange)
                .flatMap(this::handleResponse);
    }

    /**
     * Adds authentication headers using the configured strategy (Basic or OAuth).
     */
    private Mono<ClientRequest> addAuthHeaders(ClientRequest request) {
        return Mono.fromCallable(() -> {
            logger.debug("Adding authentication headers for request: {} {}", 
                    request.method(), request.url());

            ClientRequest.Builder builder = ClientRequest.from(request);
            HttpHeaders headers = new HttpHeaders();
            
            // Delegate to ServiceNowAuthService to add appropriate auth headers
            authService.addAuthHeaders(headers);
            
            // Apply headers to request
            headers.forEach((key, values) -> 
                values.forEach(value -> builder.header(key, value))
            );
            
            logger.debug("Authentication headers added successfully using {} mode", 
                    authService.getAuthMode());

            return builder.build();
        }).onErrorResume(e -> {
            logger.error("Failed to add authentication headers: {}", e.getMessage());
            return Mono.error(new RuntimeException("Authentication failed: " + e.getMessage(), e));
        });
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
