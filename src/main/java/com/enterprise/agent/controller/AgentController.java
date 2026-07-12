package com.enterprise.agent.controller;

import com.enterprise.agent.agent.AgentOrchestrator;
import com.enterprise.agent.context.UserContext;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AgentController: API REST para el sistema agéntico
 * 
 * RESPONSABILIDAD:
 * - Exponer endpoints para el widget de ServiceNow
 * - Validar y construir UserContext desde la request
 * - Delegar lógica de negocio al AgentOrchestrator
 * - Manejar errores y devolver respuestas estructuradas
 * 
 * SEGURIDAD:
 * - En MVP: confía en el contexto enviado por el widget (session-based)
 * - En PRODUCCIÓN: validar JWT, integrar con OAuth2 Resource Server
 * 
 * ENDPOINTS:
 * - POST /api/agent/chat: procesamiento conversacional principal
 * - GET /api/agent/health: health check del sistema
 */
@RestController
@RequestMapping("/api/agent")
@Log4j2
public class AgentController {

    private final AgentOrchestrator orchestrator;

    public AgentController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * Request DTO desde el widget de ServiceNow
     */
    @Data
    public static class ChatRequest {
        private String message;           // Mensaje del usuario
        private String sessionId;         // ID de sesión conversacional
        private UserInfo user;            // Información del usuario autenticado
        private Map<String, Object> context; // Contexto conversacional/frontend
        
        @Data
        public static class UserInfo {
            private String sys_id;        // sys_id en ServiceNow
            private String username;      // Username
            private String email;         // Email
            private String fullName;      // Nombre completo
            private String departmentId;  // ID del departamento
            private List<String> roles;   // Roles del usuario
        }
    }

    /**
     * Response DTO estructurado
     */
    @Data
    public static class ChatResponse {
        private boolean success;
        private String message;
        private List<String> messages; // opcional: para entregar múltiples burbujas en un solo response
        private Map<String, Object> data;
        private String intent;
        private double confidence;
        private String sessionId;
        private long processingTimeMs;
        
        public static ChatResponse success(
                String message, 
                List<String> messages,
                Map<String, Object> data,
                String intent,
                double confidence,
                String sessionId,
                long processingTimeMs) {
            
            ChatResponse response = new ChatResponse();
            response.success = true;
            response.message = message;
            response.messages = messages;
            response.data = data != null ? data : new HashMap<>();
            response.intent = intent;
            response.confidence = confidence;
            response.sessionId = sessionId;
            response.processingTimeMs = processingTimeMs;
            return response;
        }
        
        public static ChatResponse error(String message, String sessionId) {
            ChatResponse response = new ChatResponse();
            response.success = false;
            response.message = message;
            response.data = new HashMap<>();
            response.sessionId = sessionId;
            return response;
        }
    }

    /**
     * POST /api/agent/chat
     * 
     * Endpoint principal para procesamiento conversacional
     * 
     * Request:
     * {
     *   "message": "Dame el estado de INC0010001",
     *   "sessionId": "abc-123-def-456",
     *   "user": {
     *     "sys_id": "user123",
     *     "username": "john.doe",
     *     "email": "john.doe@company.com",
     *     "fullName": "John Doe",
     *     "roles": ["user", "analyst"]
     *   }
     * }
     * 
     * Response:
     * {
     *   "success": true,
     *   "message": "El ticket INC0010001 está en estado Resuelto...",
     *   "data": {
     *     "incident_number": "INC0010001",
     *     "state": "Resuelto",
     *     "priority": "3"
     *   },
     *   "intent": "GET_INCIDENT",
     *   "confidence": 0.95,
     *   "sessionId": "abc-123-def-456",
     *   "processingTimeMs": 1250
     * }
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        
        long startTime = System.currentTimeMillis();
        
        // 1️⃣ Validar request
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            log.warn("Empty message received");
            return ResponseEntity
                .badRequest()
                .body(ChatResponse.error("El mensaje no puede estar vacío", request.getSessionId()));
        }
        
        if (request.getUser() == null || request.getUser().getSys_id() == null) {
            log.warn("Missing user context in request");
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ChatResponse.error("Contexto de usuario no válido", request.getSessionId()));
        }
        
        // 2️⃣ Generar sessionId si no existe
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            log.info("Generated new sessionId: {}", sessionId);
        }
        
        // 3️⃣ Construir UserContext desde la request
        UserContext userContext = buildUserContext(request.getUser());
        
        log.info("Processing chat request: user={}, session={}, message={}", 
            userContext.getUsername(), sessionId, request.getMessage());
        
        try {
            // 4️⃣ Construir AgentRequest
            AgentOrchestrator.AgentRequest agentRequest = new AgentOrchestrator.AgentRequest();
            agentRequest.setMessage(request.getMessage());
            agentRequest.setSessionId(sessionId);
            agentRequest.setUserContext(userContext);
            Map<String, Object> metadata = new HashMap<>();
            if (request.getContext() != null) {
                metadata.putAll(request.getContext());
            }
            agentRequest.setMetadata(metadata);
            
            // 5️⃣ Delegar al AgentOrchestrator
            AgentOrchestrator.AgentResponse result = orchestrator.process(agentRequest);
            
            // 6️⃣ Construir respuesta exitosa desde AgentResponse
            Map<String, Object> responseData = new HashMap<>();
            if (result.getMetadata().containsKey("action_result")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> actionResult = (Map<String, Object>) result.getMetadata().get("action_result");
                responseData.putAll(actionResult);
            }
            
            double confidence = result.getMetadata().containsKey("confidence")
                ? ((Number) result.getMetadata().get("confidence")).doubleValue()
                : 0.0;
            
            // Soporte para múltiples mensajes en 1 response:
            // para GET_INCIDENT siempre entregamos 2 burbujas (confirmación + resumen).
            List<String> multi = null;
            if ("GET_INCIDENT".equals(result.getIntent())) {
                String raw = result.getMessage() == null ? "" : result.getMessage();
                String[] parts = raw.split("\\n\\n+");
                if (parts.length >= 2) {
                    // tomar SOLO las 2 primeras partes para asegurar el layout pedido
                    String p1 = parts[0].trim();
                    String p2 = parts[1].trim();
                    multi = List.of(p1, p2);
                } else {
                    // fallback si por alguna razón llega 1 parte
                    multi = List.of(raw);
                }
            }

            ChatResponse response = ChatResponse.success(
                result.getMessage(),
                multi,
                responseData,
                result.getIntent(),
                confidence,
                sessionId,
                result.getExecutionTimeMs()
            );
            
            log.info("Request processed successfully: session={}, intent={}, time={}ms", 
                sessionId, result.getIntent(), result.getExecutionTimeMs());
            
            return ResponseEntity.ok(response);
            
        } catch (SecurityException e) {
            // Error de autorización
            log.warn("Security exception: user={}, message={}", 
                userContext.getUsername(), e.getMessage());
            
            return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ChatResponse.error(e.getMessage(), sessionId));
            
        } catch (Exception e) {
            // Error genérico
            log.error("Error processing chat request: session={}, user={}", 
                sessionId, userContext.getUsername(), e);
            
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ChatResponse.error(
                    "Error procesando tu solicitud. Por favor intenta nuevamente.",
                    sessionId
                ));
        }
    }

    /**
     * GET /api/agent/health
     * 
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "enterprise-agent");
        health.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(health);
    }

    /**
     * Construir UserContext desde la información del usuario
     */
    private UserContext buildUserContext(ChatRequest.UserInfo userInfo) {
        return UserContext.builder()
            .userId(userInfo.getSys_id())
            .username(userInfo.getUsername())
            .email(userInfo.getEmail())
            .fullName(userInfo.getFullName())
            .departmentId(userInfo.getDepartmentId())
            .roles(userInfo.getRoles() != null 
                ? new HashSet<>(userInfo.getRoles()) 
                : Set.of("user"))
            .requestTimestamp(System.currentTimeMillis())
            .build();
    }

    // =====================================================
    // ENDPOINTS ADICIONALES PARA DEBUGGING (MVP)
    // TODO: Remover en producción o proteger con roles admin
    // =====================================================
    
    /**
     * POST /api/agent/test
     * 
     * Endpoint de prueba sin autenticación completa
     * Solo para desarrollo local
     */
    @PostMapping("/test")
    public ResponseEntity<ChatResponse> test(@RequestBody Map<String, String> payload) {
        
        String message = payload.get("message");
        String sessionId = payload.getOrDefault("sessionId", UUID.randomUUID().toString());
        
        // Usuario de prueba hardcodeado
        UserContext testUser = UserContext.builder()
            .userId("test-user-123")
            .username("test.user")
            .email("test@company.com")
            .fullName("Test User")
            .roles(Set.of("user", "analyst"))
            .requestTimestamp(System.currentTimeMillis())
            .build();
        
        try {
            AgentOrchestrator.AgentRequest agentRequest = new AgentOrchestrator.AgentRequest();
            agentRequest.setMessage(message);
            agentRequest.setSessionId(sessionId);
            agentRequest.setUserContext(testUser);
            agentRequest.setMetadata(new HashMap<>());
            
            AgentOrchestrator.AgentResponse result = orchestrator.process(agentRequest);
            
            Map<String, Object> responseData = new HashMap<>();
            if (result.getMetadata().containsKey("action_result")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> actionResult = (Map<String, Object>) result.getMetadata().get("action_result");
                responseData.putAll(actionResult);
            }
            
            double confidence = result.getMetadata().containsKey("confidence")
                ? ((Number) result.getMetadata().get("confidence")).doubleValue()
                : 0.0;
            
            List<String> multi = null;
            if ("GET_INCIDENT".equals(result.getIntent())) {
                String raw = result.getMessage() == null ? "" : result.getMessage();
                String[] parts = raw.split("\\n\\n+");
                if (parts.length >= 2) {
                    String p1 = parts[0].trim();
                    String p2 = parts[1].trim();
                    multi = List.of(p1, p2);
                } else {
                    multi = List.of(raw);
                }
            }

            ChatResponse response = ChatResponse.success(
                result.getMessage(),
                multi,
                responseData,
                result.getIntent(),
                confidence,
                sessionId,
                result.getExecutionTimeMs()
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in test endpoint", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ChatResponse.error(e.getMessage(), sessionId));
        }
    }
}
