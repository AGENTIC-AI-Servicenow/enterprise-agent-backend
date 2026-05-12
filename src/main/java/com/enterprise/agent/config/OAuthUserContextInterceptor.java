package com.enterprise.agent.config;

import com.enterprise.agent.service.ServiceNowAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor that reads X-User-Id header from each request
 * and sets the OAuth user context in OAuthStrategy's ThreadLocal.
 *
 * This bridges the gap between HTTP requests and the per-user
 * token storage in OAuthAuthorizationService.
 *
 * Flow:
 *   1. Client sends: GET /api/incidents  +  X-User-Id: user_1234567890
 *   2. This interceptor reads the header and calls setOAuthUserContext()
 *   3. OAuthStrategy.addAuthHeaders() can now find the user's token
 *   4. After the request, clearOAuthUserContext() cleans up the ThreadLocal
 */
@Component
public class OAuthUserContextInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OAuthUserContextInterceptor.class);
    private static final String USER_ID_HEADER = "X-User-Id";

    private final ServiceNowAuthService serviceNowAuthService;

    public OAuthUserContextInterceptor(ServiceNowAuthService serviceNowAuthService) {
        this.serviceNowAuthService = serviceNowAuthService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(USER_ID_HEADER);
        if (userId != null && !userId.isBlank()) {
            serviceNowAuthService.setOAuthUserContext(userId);
            log.debug("OAuth user context set for userId={} on {} {}", userId,
                    request.getMethod(), request.getRequestURI());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        serviceNowAuthService.clearOAuthUserContext();
        log.debug("OAuth user context cleared after {} {}", request.getMethod(), request.getRequestURI());
    }
}
