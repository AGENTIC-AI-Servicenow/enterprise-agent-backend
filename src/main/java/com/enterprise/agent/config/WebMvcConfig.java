package com.enterprise.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration.
 * Registers interceptors and other MVC-level beans.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final OAuthUserContextInterceptor oAuthUserContextInterceptor;

    public WebMvcConfig(OAuthUserContextInterceptor oAuthUserContextInterceptor) {
        this.oAuthUserContextInterceptor = oAuthUserContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Apply OAuth user context resolution to all /api/** endpoints.
        // The interceptor reads the X-User-Id header and populates the
        // OAuthStrategy ThreadLocal so ServiceNow calls can find the right token.
        registry.addInterceptor(oAuthUserContextInterceptor)
                .addPathPatterns("/api/**", "/debug/**");
    }
}
