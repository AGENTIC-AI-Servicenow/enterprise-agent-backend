package com.enterprise.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring Security Configuration for Enterprise Agent Backend.
 * 
 * This configuration defines security rules for the application:
 * - Public endpoints (no authentication required)
 * - Protected endpoints (authentication required)
 * - CORS configuration
 * - CSRF protection (disabled for REST API)
 * - Session management (stateless for REST API)
 * 
 * @author Enterprise Agent Team
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    /**
     * Configure HTTP Security for the application.
     * 
     * Security Rules:
     * 1. Public endpoints (permitAll):
     *    - Health/actuator endpoints
     *    - Debug endpoints (for development)
     *    - Auth status endpoint
     *    - OAuth callback endpoints
     * 
     * 2. Protected endpoints (authenticated):
     *    - Agent API endpoints
     *    - Incident management endpoints
     *    - ServiceNow integration endpoints
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        logger.info("Configuring Spring Security for Enterprise Agent Backend");
        
        http
            // CSRF: Disable for REST API (stateless)
            .csrf(csrf -> csrf.disable())
            
            // Session Management: Stateless (no server-side sessions)
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // Authorization Rules
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints - No authentication required
                
                // Health & Monitoring
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/health/**").permitAll()
                
                // Debug & Status endpoints (consider restricting in production)
                .requestMatchers("/debug/**").permitAll()
                .requestMatchers("/api/auth/status").permitAll()
                
                // OAuth Flow endpoints
                .requestMatchers("/oauth/**").permitAll()
                .requestMatchers("/api/oauth/callback").permitAll()
                .requestMatchers("/api/oauth/authorize").permitAll()
                .requestMatchers("/api/authorization-code/**").permitAll()
                
                // Development/Testing endpoints
                .requestMatchers("/api/oauth-test/**").permitAll()
                
                // Protected endpoints - Authentication required
                // (In MVP phase, we'll make these public for easier testing)
                // TODO: Enable authentication for production
                
                // Agent endpoints
                .requestMatchers("/api/agent/**").permitAll()
                
                // Incident management endpoints
                .requestMatchers("/api/incidents/**").permitAll()
                
                // Auth endpoints (user-specific operations)
                .requestMatchers("/api/auth/**").permitAll()
                
                // Default: Require authentication for any other endpoint
                .anyRequest().authenticated()
            )
            
            // CORS: Allow cross-origin requests (configured in CorsConfig)
            .cors(cors -> cors.configure(http));
        
        logger.info("Spring Security configuration completed");
        logger.warn("⚠️  MVP Mode: Most endpoints are PUBLIC for development. Enable authentication for production!");
        
        return http.build();
    }
}
