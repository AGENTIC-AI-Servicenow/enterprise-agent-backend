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
 * WebClient filter that automatically injects OAuth Bearer tokens
 * and handles authentication failures with automatic token refresh.
 */
@Component
public class ServiceNowWebClientFilter implements ExchangeFilterFunction {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNowWebClientFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final ServiceNowAuthService authService;

    public ServiceNowWebClientFilter(ServiceNowAuthService authService) {
        this.authService = authService;
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        // Skip token injection for OAuth token endpoint
        if (isOAuthTokenEndpoint(request)) {
            return next.exchange(request);
        }

        return addBearerToken(request)
                .flatMap(next::exchange)
                .flatMap(this::handleResponse);
    }

    /**
     * Adds Bearer token to the request headers.
     */
    private Mono<ClientRequest> addBearerToken(ClientRequest request) {
        return Mono.fromCallable(() -> {
            String accessToken = authService.getAccessToken();

            logger.debug("Injecting Bearer token into request: {} {}",
                    request.method(),
                    request.url());

            return ClientRequest.from(request)
                    .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken)
                    .build();
        });
    }

    /**
     * Handles authentication errors with automatic token refresh and retry.
     */
    private Mono<ClientResponse> handleResponse(ClientResponse response) {
        if (response.statusCode() == HttpStatus.UNAUTHORIZED) {
            logger.warn("401 Unauthorized received from ServiceNow.");

            logger.info("Triggering token refresh due to 401 response.");
            authService.refreshIfNeeded();
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
