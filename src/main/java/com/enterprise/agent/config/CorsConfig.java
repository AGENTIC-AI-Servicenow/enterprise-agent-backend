package com.enterprise.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * CORS configuration for Spring MVC.
 * Allows requests from local frontend environments, the deployed Vercel frontend,
 * and the ServiceNow instance.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        
        // Allow requests from frontend and ServiceNow
        corsConfig.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",                               // Frontend development
                "http://localhost:3001",                               // Frontend alternative port
                "https://enterprise-agent-frontend-three.vercel.app",  // Frontend production
                "https://everisspainsludemo3.service-now.com"          // ServiceNow instance
        ));
        
        // Allow all HTTP methods
        corsConfig.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));
        
        // Allow all headers
        corsConfig.setAllowedHeaders(Arrays.asList("*"));
        
        // Allow credentials (for OAuth)
        corsConfig.setAllowCredentials(true);
        
        // Max age for preflight requests (1 hour)
        corsConfig.setMaxAge(3600L);
        
        // Expose headers to client
        corsConfig.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Total-Count"
        ));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        
        return new CorsFilter(source);
    }
}
