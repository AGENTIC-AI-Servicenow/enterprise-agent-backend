package com.enterprise.agent.agent;

import com.enterprise.agent.client.ServiceNowClient;
import com.enterprise.agent.context.UserContext;
import com.enterprise.agent.service.LLMService;
import com.enterprise.agent.service.ServiceNowOAuthTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import com.enterprise.agent.memory.ConversationMemory;

import java.util.HashMap;
import java.util.Map;

/**
 * ActionRouter: Ejecutor de acciones del sistema agéntico
 * 
 * RESPONSABILIDAD:
 * - Recibe una IntentResult del IntentClassifier
 * - Ejecuta la acción apropiada (GET_INCIDENT, CREATE_INCIDENT, etc)
 * - Interactúa con ServiceNowClient para operaciones ITSM
 * - Devuelve ActionResult con el resultado de la operación
 * 
 * FASE 1 (MVP): Acciones síncronas simples
 * - GET_INCIDENT: obtener ticket por número
 * - SEARCH_INCIDENTS: buscar tickets del usuario
 * - CREATE_INCIDENT: crear nuevo ticket
 * - ANALYZE_INCIDENT: análisis básico con LLM
 * - CHAT: respuesta conversacional
 * 
 * FASE 2 (Agentes autónomos):
 * - Acciones asíncronas con callbacks
 * - Workflows multi-step con planificación
 * - Tool calling para integraciones externas
 * - Human-in-the-loop para aprobaciones
 */
@Component
@Log4j2
public class ActionRouter {

    private final ServiceNowClient serviceNowClient;
    private final ServiceNowOAuthTokenProvider tokenProvider;
    private final LLMService llmService;
    private final ConversationMemory memory;

    /**
     * Resultado de una acción ejecutada
     */
    @Data
    public static class ActionResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> data;
        
        public static ActionResult success(String message, Map<String, Object> data) {
            return new ActionResult(true, message, data != null ? data : new HashMap<>());
        }
        
        public static ActionResult failure(String message) {
            return new ActionResult(false, message, new HashMap<>());
        }
    }

    public ActionRouter(ServiceNowClient serviceNowClient,
                        ServiceNowOAuthTokenProvider tokenProvider,
                        LLMService llmService,
                        ConversationMemory memory) {
        this.serviceNowClient = serviceNowClient;
        this.tokenProvider = tokenProvider;
        this.llmService = llmService;
        this.memory = memory;
    }

    /**
     * Ejecutar acción basada en la intención clasificada
     * 
     * @param intentResult resultado del clasificador de intención
     * @param userContext contexto del usuario autenticado
     * @param sessionId ID de sesión para contexto conversacional
     * @return resultado de la acción
     */
    public ActionResult execute(
            IntentClassifier.IntentResult intentResult, 
            UserContext userContext,
            String sessionId) {
        
        log.info("Executing action: intent={}, user={}, session={}", 
            intentResult.intent(), 
            userContext.getUserId(), 
            sessionId);
        
        try {
            return switch (intentResult.intent()) {
                case GET_INCIDENT -> handleGetIncident(intentResult, userContext);
                case SEARCH_INCIDENTS -> handleSearchIncidents(userContext);
                case CREATE_INCIDENT -> handleCreateIncident(intentResult, userContext);
                case ANALYZE_INCIDENT -> handleAnalyzeIncident(intentResult, userContext);
                case CHAT -> handleChat(intentResult);
            };
            
        } catch (SecurityException e) {
            log.warn("Security exception during action execution: {}", e.getMessage());
            throw e; // Propagate to AgentOrchestrator
            
        } catch (Exception e) {
            log.error("Error executing action: intent={}, error={}", 
                intentResult.intent(), e.getMessage(), e);
            return ActionResult.failure("Error al ejecutar la acción: " + e.getMessage());
        }
    }

    /**
     * GET_INCIDENT: Obtener detalles de un ticket específico
     */
    private ActionResult handleGetIncident(
            IntentClassifier.IntentResult intentResult, 
            UserContext userContext) {
        
        String incidentNumber = (String) intentResult.parameters().get("number");
        
        // Si no hay número, intentar obtener el último del contexto
        if (incidentNumber == null || incidentNumber.isBlank()) {
            log.info("No incident number provided, searching for last incident");
            return handleSearchIncidents(userContext);
        }
        
        log.info("Getting incident: number={}, user={}", incidentNumber, userContext.getUserId());
        
        try {
            JsonNode response = serviceNowClient.getIncidentSecureByNumberAndCaller(
                incidentNumber, 
                userContext.getUserId()
            );
            
            if (response != null 
                && response.has("result") 
                && response.get("result").isArray()
                && response.get("result").size() > 0) {
                
                JsonNode incident = response.get("result").get(0);
                
                String shortDesc = incident.path("short_description").asText("Sin descripción");
                String state = translateState(incident.path("state").asText());
                String priority = incident.path("priority").asText("No definida");
                
                // Generar resumen con LLM
                String contextData = String.format(
                    "Número: %s\nDescripción: %s\nEstado: %s\nPrioridad: %s",
                    incidentNumber, shortDesc, state, priority
                );
                
                String summary = llmService.generateIncidentSummary(contextData);
                
                Map<String, Object> data = new HashMap<>();
                data.put("incident_number", incidentNumber);
                data.put("short_description", shortDesc);
                data.put("state", state);
                data.put("priority", priority);
                
                return ActionResult.success(summary, data);
                
            } else {
                log.warn("Incident not found or no access: {}", incidentNumber);
                return ActionResult.failure(
                    "No encontré el ticket " + incidentNumber + " o no tienes permisos para verlo."
                );
            }
            
        } catch (Exception e) {
            log.error("Error getting incident: {}", incidentNumber, e);
            return ActionResult.failure("Error al consultar el ticket: " + e.getMessage());
        }
    }

    /**
     * SEARCH_INCIDENTS: Buscar tickets del usuario
     */
    private ActionResult handleSearchIncidents(UserContext userContext) {
        
        log.info("Searching incidents for user: {}", userContext.getUserId());
        
        try {
            JsonNode response = serviceNowClient.getIncidentsByCallerId(
                userContext.getUserId()
            );
            
            if (response != null 
                && response.has("result") 
                && response.get("result").isArray()
                && response.get("result").size() > 0) {
                
                JsonNode incident = response.get("result").get(0);
                
                String number = incident.path("number").asText();
                String shortDesc = incident.path("short_description").asText("Sin descripción");
                String state = translateState(incident.path("state").asText());
                String priority = incident.path("priority").asText("No definida");
                
                // Generar resumen con LLM
                String contextData = String.format(
                    "Número: %s\nDescripción: %s\nEstado: %s\nPrioridad: %s",
                    number, shortDesc, state, priority
                );
                
                String summary = llmService.generateIncidentSummary(contextData);
                
                Map<String, Object> data = new HashMap<>();
                data.put("incident_number", number);
                data.put("short_description", shortDesc);
                data.put("state", state);
                data.put("priority", priority);
                data.put("total_incidents", response.get("result").size());
                
                return ActionResult.success(summary, data);
                
            } else {
                return ActionResult.failure("No tienes tickets registrados.");
            }
            
        } catch (Exception e) {
            log.error("Error searching incidents", e);
            return ActionResult.failure("Error al buscar tickets: " + e.getMessage());
        }
    }

    /**
     * CREATE_INCIDENT: Crear nuevo ticket
     */
    private ActionResult handleCreateIncident(
            IntentClassifier.IntentResult intentResult, 
            UserContext userContext) {
        
        String shortDesc = (String) intentResult.parameters().get("short_description");
        String description = (String) intentResult.parameters().get("description");
        String priority = (String) intentResult.parameters().getOrDefault("priority", "3");
        
        log.info("Creating incident: user={}, short_desc={}", 
            userContext.getUserId(), shortDesc);
        
        try {
            JsonNode response = serviceNowClient.createIncident(
                shortDesc,
                description,
                priority,
                userContext.getUserId()
            );
            
            if (response != null && response.has("result")) {
                String number = response.get("result").get("number").asText();
                
                Map<String, Object> data = new HashMap<>();
                data.put("incident_number", number);
                
                return ActionResult.success(
                    "✅ Ticket creado correctamente: " + number,
                    data
                );
                
            } else {
                return ActionResult.failure("No se pudo crear el ticket.");
            }
            
        } catch (Exception e) {
            log.error("Error creating incident", e);
            return ActionResult.failure("Error al crear el ticket: " + e.getMessage());
        }
    }

    /**
     * ANALYZE_INCIDENT: Análisis inteligente de un ticket
     * TODO: integrar con RAG para recomendaciones basadas en KB
     */
    private ActionResult handleAnalyzeIncident(
            IntentClassifier.IntentResult intentResult, 
            UserContext userContext) {
        
        // Por ahora, reutilizar GET_INCIDENT con análisis mejorado
        // En fase 2: agregar RAG, detectar patrones, sugerir resoluciones
        return handleGetIncident(intentResult, userContext);
    }

    /**
     * CHAT: Respuesta conversacional general
     */
    private ActionResult handleChat(IntentClassifier.IntentResult intentResult) {
        
        String userMessage = (String) intentResult.parameters().get("message");
        
        try {
            String response = llmService.generateChatResponse(userMessage);
            return ActionResult.success(response, new HashMap<>());
            
        } catch (Exception e) {
            log.error("Error generating chat response", e);
            return ActionResult.failure("Disculpa, tuve un problema generando la respuesta.");
        }
    }

    /**
     * Traducir códigos de estado de ServiceNow a texto legible
     */
    private String translateState(String state) {
        return switch (state) {
            case "1" -> "Nuevo";
            case "2" -> "En Progreso";
            case "3" -> "En Espera";
            case "6" -> "Resuelto";
            case "7" -> "Cerrado";
            default -> "Desconocido";
        };
    }
}
