package com.enterprise.agent.agent;

import com.enterprise.agent.context.UserContext;
import com.enterprise.agent.memory.ConversationMemory;
import com.enterprise.agent.service.LLMService;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AgentOrchestrator: Cerebro del sistema agéntico
 * 
 * FLUJO PRINCIPAL:
 * 1. Usuario envía mensaje → AgentController
 * 2. AgentOrchestrator recibe request con UserContext
 * 3. IntentClassifier analiza la intención con LLM
 * 4. ActionRouter ejecuta la acción apropiada
 * 5. ConversationMemory guarda el contexto
 * 6. Respuesta se envía al usuario
 * 
 * ARQUITECTURA:
 * ┌──────────────────────────────────────────┐
 * │         AgentOrchestrator                │
 * │  (Coordinador central - Stateless)       │
 * └──────────────────────────────────────────┘
 *           │              │            │
 *           ▼              ▼            ▼
 *    IntentClassifier  ActionRouter  ConversationMemory
 *           │              │            │
 *           ▼              ▼            ▼
 *       LLMService   ServiceNowClient  SessionStore
 * 
 * BENEFICIOS vs enfoque anterior (AgentService):
 * - Separación clara de responsabilidades
 * - Fácil testing (cada componente se prueba independiente)
 * - Escalable: agregar nuevas intenciones solo requiere actualizar ActionRouter
 * - Observabilidad: logs centralizados con tiempos de ejecución
 * - Multitenancy: cada request trae su UserContext
 * 
 * FASE 1: Copiloto para analistas
 * FASE 2: Agentes autónomos con planificación y herramientas
 */
@Service
@Log4j2
public class AgentOrchestrator {

    private final IntentClassifier intentClassifier;
    private final ActionRouter actionRouter;
    private final ConversationMemory conversationMemory;
    private final LLMService llmService;

    /**
     * Request de entrada del usuario
     */
    @Data
    public static class AgentRequest {
        private String message;
        private String sessionId; // Para mantener contexto conversacional
        private UserContext userContext; // Información del usuario autenticado
        private Map<String, Object> metadata; // Datos adicionales (ej: canal, timestamp)
    }

    /**
     * Response al usuario
     */
    @Data
    public static class AgentResponse {
        private String message;
        private String intent;
        private boolean success;
        private Map<String, Object> metadata;
        private long executionTimeMs;
        
        public AgentResponse(String message, String intent, boolean success, Map<String, Object> metadata, long executionTimeMs) {
            this.message = message;
            this.intent = intent;
            this.success = success;
            this.metadata = metadata != null ? metadata : new HashMap<>();
            this.executionTimeMs = executionTimeMs;
        }
    }

    public AgentOrchestrator(
            IntentClassifier intentClassifier,
            ActionRouter actionRouter,
            ConversationMemory conversationMemory,
            LLMService llmService) {
        this.intentClassifier = intentClassifier;
        this.actionRouter = actionRouter;
        this.conversationMemory = conversationMemory;
        this.llmService = llmService;
    }

    /**
     * Método principal: procesar request del usuario
     * 
     * @param request mensaje del usuario con contexto
     * @return respuesta del agente
     */
    public AgentResponse process(AgentRequest request) {
        
        long startTime = System.currentTimeMillis();
        
        // Generar sessionId si no existe
        String sessionId = request.getSessionId() != null 
            ? request.getSessionId() 
            : UUID.randomUUID().toString();
        
        try {
            log.info("Processing request: user={}, session={}, message={}", 
                request.getUserContext().getUserId(), 
                sessionId, 
                truncate(request.getMessage(), 50));
            
            // 1. Guardar mensaje del usuario en memoria
            conversationMemory.addUserMessage(
                sessionId, 
                request.getMessage(), 
                Map.of("user_id", request.getUserContext().getUserId())
            );
            
            // 2. Clasificar intención con LLM
            IntentClassifier.IntentResult intentResult = intentClassifier.classify(
                request.getMessage(),
                request.getUserContext(),
                sessionId
            );
            
            log.info("Intent classified: intent={}, confidence={}, session={}", 
                intentResult.intent(), 
                intentResult.confidence(), 
                sessionId);
            
            // 3. Ejecutar acción según intención
            ActionRouter.ActionResult actionResult = actionRouter.execute(
                intentResult,
                request.getUserContext(),
                sessionId
            );
            
            // 4. Generar respuesta final
            String finalResponse = generateResponse(intentResult, actionResult);
            
            // 5. Guardar respuesta en memoria
            conversationMemory.addAssistantMessage(
                sessionId,
                finalResponse,
                Map.of(
                    "intent", intentResult.intent().name(),
                    "success", actionResult.isSuccess()
                )
            );
            
            long executionTime = System.currentTimeMillis() - startTime;
            
            log.info("Request processed successfully: session={}, intent={}, time={}ms", 
                sessionId, 
                intentResult.intent(), 
                executionTime);
            
            // 6. Retornar response
            return new AgentResponse(
                finalResponse,
                intentResult.intent().name(),
                actionResult.isSuccess(),
                Map.of(
                    "session_id", sessionId,
                    "confidence", intentResult.confidence(),
                    "action_result", actionResult.getData()
                ),
                executionTime
            );
            
        } catch (SecurityException e) {
            // Usuario no autorizado
            log.warn("Security exception: user={}, session={}, error={}", 
                request.getUserContext().getUserId(), 
                sessionId, 
                e.getMessage());
            
            return new AgentResponse(
                "No tienes permisos para realizar esta acción.",
                "SECURITY_ERROR",
                false,
                Map.of("error", e.getMessage()),
                System.currentTimeMillis() - startTime
            );
            
        } catch (Exception e) {
            // Error general
            log.error("Error processing request: user={}, session={}, error={}", 
                request.getUserContext().getUserId(), 
                sessionId, 
                e.getMessage(), 
                e);
            
            // Fallback: respuesta conversacional
            String fallbackResponse = generateFallbackResponse(request.getMessage());
            
            return new AgentResponse(
                fallbackResponse,
                "ERROR",
                false,
                Map.of("error", e.getMessage()),
                System.currentTimeMillis() - startTime
            );
        }
    }

    /**
     * Generar respuesta final para el usuario
     * Combina el resultado de la acción con lenguaje natural
     */
    private String generateResponse(
            IntentClassifier.IntentResult intentResult, 
            ActionRouter.ActionResult actionResult) {
        
        // Si la acción ya incluye un mensaje formateado, usarlo
        if (actionResult.getMessage() != null && !actionResult.getMessage().isEmpty()) {
            return actionResult.getMessage();
        }
        
        // Si no hay mensaje, generar uno según la intención
        return switch (intentResult.intent()) {
            case GET_INCIDENT -> actionResult.isSuccess()
                ? "He encontrado el ticket solicitado."
                : "No pude obtener información del ticket.";
                
            case SEARCH_INCIDENTS -> actionResult.isSuccess()
                ? "He encontrado tus tickets."
                : "No pude buscar los tickets.";
                
            case CREATE_INCIDENT -> actionResult.isSuccess()
                ? "Ticket creado exitosamente."
                : "No pude crear el ticket.";
                
            case ANALYZE_INCIDENT -> actionResult.isSuccess()
                ? "Aquí está el análisis del ticket."
                : "No pude analizar el ticket.";
                
            case CHAT -> actionResult.getMessage();
        };
    }

    /**
     * Generar respuesta de fallback usando LLM
     * Se usa cuando hay un error inesperado
     */
    private String generateFallbackResponse(String userMessage) {
        try {
            String prompt = """
Eres un asistente corporativo. El usuario preguntó algo pero hubo un error técnico.

Responde de forma empática y profesional:
- Discúlpate brevemente
- No menciones detalles técnicos
- Ofrece intentar de nuevo
- Mantén un tono profesional pero cercano

Usuario: %s
""".formatted(userMessage);
            
            return llmService.generate(prompt, 0.7, 100);
            
        } catch (Exception e) {
            // Si el LLM también falla, respuesta estática
            log.error("Fallback LLM failed: {}", e.getMessage());
            return "Disculpa, tuve un problema procesando tu solicitud. ¿Podrías intentar de nuevo?";
        }
    }

    /**
     * Utilidad: truncar strings largos para logs
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength 
            ? text.substring(0, maxLength) + "..." 
            : text;
    }

    /**
     * Método para limpiar sesiones expiradas
     * TODO: En producción, ejecutar con @Scheduled
     */
    public void cleanupExpiredSessions() {
        conversationMemory.cleanExpiredSessions();
    }
}
