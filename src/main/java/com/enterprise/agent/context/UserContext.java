package com.enterprise.agent.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * UserContext: Contiene información del usuario autenticado
 * 
 * Inyectado en ThreadLocal por UserContextFilter
 * Disponible en toda la request
 * 
 * IMPORTANTE: Thread-safe - cada request tiene su propio contexto
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    
    /** sys_id en ServiceNow */
    private String userId;
    
    /** Username en ServiceNow */
    private String username;
    
    /** OAuth2 access token (del cliente frontend) */
    private String accessToken;
    
    /** Access token para llamar a ServiceNow APIs (OAuth2) */
    private String serviceNowToken;
    
    /** Roles del usuario: ["analyst", "user", "admin", "manager"] */
    private Set<String> roles;
    
    /** Timestamp de creación del contexto */
    private long requestTimestamp;
    
    /** Email del usuario */
    private String email;
    
    /** Nombre completo */
    private String fullName;
    
    /** ID de departamento en ServiceNow */
    private String departmentId;
    
    /**
     * Validar si el usuario tiene un rol específico
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
    
    /**
     * Validar si el usuario es analyst (puede realizar acciones)
     */
    public boolean isAnalyst() {
        return hasRole("analyst") || hasRole("admin");
    }
    
    /**
     * Validar si el usuario es admin
     */
    public boolean isAdmin() {
        return hasRole("admin");
    }
}
