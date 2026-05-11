package com.enterprise.agent.context;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

/**
 * UserContextFilter: Inyecta contexto de usuario en ThreadLocal
 * 
 * FLUJO:
 * 1. Intercept cada request
 * 2. Extraer Authorization header
 * 3. Extraer userId (del JWT o header custom)
 * 4. Validar token (en producción)
 * 5. Cargar permisos/roles del usuario
 * 6. Guardar en ThreadLocal
 * 7. Continuar con chain
 * 8. Cleanup al finalizar
 * 
 * IMPORTANTE: Cada thread tiene su propio UserContext
 */
@Component
@Log4j2
public class UserContextFilter implements WebFilter {

    /**
     * ThreadLocal para almacenar UserContext por thread
     * Garantiza que cada request tenga contexto aislado
     */
    private static final ThreadLocal<UserContext> contextHolder = new ThreadLocal<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            try {
                // 1. Extraer Authorization header
                String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
                
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    log.debug("No Bearer token found, skipping UserContext initialization");
                    return chain.filter(exchange);
                }

                String accessToken = authHeader.substring(7);

                // 2. Extraer userId del header
                // En MVP: buscar X-User-Id header (para testing)
                // En producción: decodificar JWT
                String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
                if (userId == null) {
                    userId = extractUserIdFromJwt(accessToken);
                }

                // 3. Crear UserContext
                UserContext userContext = buildUserContext(userId, accessToken, exchange);

                // 4. Guardar en ThreadLocal
                contextHolder.set(userContext);

                log.info("UserContext initialized: user={}, roles={}, timestamp={}", 
                    userContext.getUsername(), 
                    userContext.getRoles(),
                    userContext.getRequestTimestamp());

                // 5. Continuar con chain
                return chain.filter(exchange)
                    .doFinally(signalType -> {
                        // Cleanup
                        log.debug("Cleaning up UserContext for user={}", userContext.getUsername());
                        contextHolder.remove();
                    });

            } catch (Exception e) {
                log.error("Error in UserContextFilter: {}", e.getMessage(), e);
                return Mono.error(new RuntimeException("Failed to initialize UserContext"));
            }
        });
    }

    /**
     * Obtener el UserContext del thread actual
     * 
     * @return UserContext del usuario actual
     * @throws IllegalStateException si no hay contexto
     */
    public static UserContext getCurrentContext() {
        UserContext ctx = contextHolder.get();
        if (ctx == null) {
            throw new IllegalStateException("UserContext not initialized for this request");
        }
        return ctx;
    }

    /**
     * Obtener userId del thread actual
     */
    public static String getCurrentUserId() {
        return getCurrentContext().getUserId();
    }

    /**
     * Extraer userId del JWT (implementación simple para MVP)
     * 
     * En producción: usar library de JWT validation
     * (io.jsonwebtoken, nimbus-jose-jwt, etc.)
     */
    private String extractUserIdFromJwt(String token) {
        // MVP: token simple = "user-{userId}-{timestamp}"
        // Producción: JWT.decode(token).getClaim("sub")
        
        if (token.startsWith("user-")) {
            String[] parts = token.split("-");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        
        // Fallback: generar ID temporal (para testing)
        return "user-" + System.currentTimeMillis();
    }

    /**
     * Construir UserContext con datos del usuario
     * 
     * Aquí irían llamadas a:
     * - LDAP/AD para validar usuario
     * - ServiceNow para obtener roles
     * - OAuth provider para refresh token
     */
    private UserContext buildUserContext(String userId, String accessToken, ServerWebExchange exchange) {
        
        // MVP: simulación de datos
        // Producción: consultar ServiceNow API + LDAP
        
        Set<String> roles = new HashSet<>();
        
        // Simulación: si userId contiene "admin" → roles [admin, analyst]
        if (userId.contains("admin")) {
            roles.add("admin");
            roles.add("analyst");
        } else if (userId.contains("analyst")) {
            roles.add("analyst");
        } else {
            roles.add("user");
        }

        return UserContext.builder()
            .userId(userId)
            .username("user-" + userId)
            .email(userId + "@company.com")
            .fullName("User " + userId)
            .accessToken(accessToken)
            .serviceNowToken(accessToken)
            .roles(roles)
            .requestTimestamp(System.currentTimeMillis())
            .departmentId("IT")
            .build();
    }

    /**
     * Limpiar contexto (útil para testing)
     */
    public static void clear() {
        contextHolder.remove();
    }
}
