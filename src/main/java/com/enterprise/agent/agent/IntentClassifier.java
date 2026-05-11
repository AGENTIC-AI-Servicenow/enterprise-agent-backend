package com.enterprise.agent.agent;

import com.enterprise.agent.context.UserContext;
import com.enterprise.agent.memory.ConversationMemory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import com.enterprise.agent.service.LLMService;

import java.util.Map;

/**
 * IntentClassifier: Clasifica intención del usuario usando LLM
 * 
 * ENTRADA: "Dame el estado del ticket INC123"
 * SALIDA: {
 *   "intent": "GET_INCIDENT",
 *   "parameters": {"incident_id": "INC123"},
 *   "confidence": 0.95
 * }
 * 
 * VENTAJAS sobre regex:
 * - Entiende contexto y lenguaje natural
 * - Adapta a variaciones de frases
 * - Extrae parámetros automáticamente
 * - No requiere mantenimiento de patrones
 * 
 * INTENCIONES SOPORTADAS (Fase 1):
 * - GET_INCIDENT: obtener estado de un ticket
 * - SEARCH_INCIDENTS: buscar tickets del usuario
 * - CREATE_INCIDENT: crear nuevo ticket
 * - ANALYZE_INCIDENT: análisis profundo
 * - CHAT: conversación libre
 */
@Service
@Log4j2
public class IntentClassifier {

    private final LLMService llmService;
    private final ConversationMemory conversationMemory;
    private final ObjectMapper objectMapper;

    /**
     * Resultado de clasificación de intención
     */
    public record IntentResult(
        Intent intent,
        Map<String, Object> parameters,
        double confidence
    ) {}

    /**
     * Intenciones permitidas en Fase 1
     */
    public enum Intent {
        GET_INCIDENT("Obtener estado de un ticket específico"),
        SEARCH_INCIDENTS("Buscar/listar tickets del usuario"),
        CREATE_INCIDENT("Crear nuevo ticket"),
        ANALYZE_INCIDENT("Análisis profundo de un incidente"),
        CHAT("Conversación libre / otras preguntas");

        private final String description;

        Intent(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public IntentClassifier(LLMService llmService, ConversationMemory conversationMemory, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.conversationMemory = conversationMemory;
        this.objectMapper = objectMapper;
    }

    /**
     * Clasificar intención del usuario
     * 
     * @param userInput mensaje del usuario
     * @param userContext contexto del usuario (roles, permisos)
     * @param sessionId ID de sesión para historial
     * @return IntentResult con intención, parámetros y confianza
     */
    public IntentResult classify(String userInput, UserContext userContext, String sessionId) {
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.debug("Classifying intent for user={}, input={}", userContext.getUserId(), userInput);
            
            // 1. Construir prompt con contexto
            String systemPrompt = buildClassificationPrompt(userContext, sessionId);
            
            // 2. Llamar a LLM (Ollama)
            String llmResponse = llmService.classify(systemPrompt, userInput);
            
            log.debug("LLM response: {}", llmResponse);
            
            // 3. Parsear JSON response
            IntentClassificationResponse response = parseResponse(llmResponse);
            
            // 4. Validar intención permitida
            validateIntentPermissions(response.intent, userContext.getRoles());
            
            // 5. Retornar estructurado
            IntentResult result = new IntentResult(
                Intent.valueOf(response.intent),
                response.parameters != null ? response.parameters : Map.of(),
                response.confidence
            );
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Intent classified in {}ms: intent={}, confidence={}, user={}", 
                duration, result.intent(), result.confidence(), userContext.getUserId());
            
            return result;
            
        } catch (Exception e) {
            log.error("Error classifying intent: {}", e.getMessage(), e);
            // Fallback a CHAT si hay error
            return new IntentResult(Intent.CHAT, Map.of(), 0.0);
        }
    }

    /**
     * Construir system prompt para LLM
     * Incluye: contexto del usuario, historial reciente, instrucciones
     */
    private String buildClassificationPrompt(UserContext userContext, String sessionId) {
        
        // Obtener historial reciente (últimos 3 mensajes)
        String recentHistory = conversationMemory.getRecentHistory(sessionId, 3);
        
        return """
Eres un clasificador de intención para un sistema corporativo de Service Desk.

CONTEXTO DEL USUARIO:
- Usuario: %s (ID: %s)
- Roles: %s
- Es analyst: %b
- Departamento: %s

HISTORIAL RECIENTE:
%s

INTENCIONES DISPONIBLES:
1. GET_INCIDENT - Obtener estado de un ticket específico
   Ejemplos: "dame el estado del INC123", "¿cuál es el estado de mi ticket?", "muestra el ticket X"
   Parámetros: {incident_id: string}

2. SEARCH_INCIDENTS - Buscar/listar tickets
   Ejemplos: "mis tickets abiertos", "busca mis incidentes", "lista mis solicitudes"
   Parámetros: {status?: "open|closed|pending", limit?: number}

3. CREATE_INCIDENT - Crear nuevo ticket
   Ejemplos: "crear nuevo ticket", "reportar un problema", "abrir un incidente"
   Parámetros: {title?: string, description?: string, priority?: "low|medium|high"}

4. ANALYZE_INCIDENT - Análisis profundo
   Ejemplos: "analiza el ticket X", "damé un resumen del INC456", "qué pasó en este ticket"
   Parámetros: {incident_id: string, analysis_type?: "summary|root_cause|timeline"}

5. CHAT - Conversación libre
   Ejemplos: cualquier otra pregunta que no sea sobre tickets específicos
   Parámetros: {}

INSTRUCCIONES:
- Responde SOLO en formato JSON válido
- NO incluyas markdown, comentarios o explicaciones fuera del JSON
- confidence debe estar entre 0.0 y 1.0
- Si la intención es ambigua, elige CHAT
- Asegúrate de extraer parámetros relevantes del contexto y historial

FORMATO DE RESPUESTA (JSON PURO):
{
  "intent": "GET_INCIDENT|SEARCH_INCIDENTS|CREATE_INCIDENT|ANALYZE_INCIDENT|CHAT",
  "parameters": { /* opcional */ },
  "confidence": 0.0
}

MENSAJE DEL USUARIO:
""".formatted(
            userContext.getUsername(),
            userContext.getUserId(),
            userContext.getRoles(),
            userContext.isAnalyst(),
            userContext.getDepartmentId(),
            recentHistory.isEmpty() ? "(sin historial)" : recentHistory
        );
    }

    /**
     * Parsear response JSON del LLM
     * Limpia markdown code blocks si existen
     */
    private IntentClassificationResponse parseResponse(String json) throws Exception {
        
        // Limpiar markdown code blocks
        String cleaned = json.trim();
        
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        
        cleaned = cleaned.trim();
        
        log.debug("Cleaned response: {}", cleaned);
        
        return objectMapper.readValue(cleaned, IntentClassificationResponse.class);
    }

    /**
     * Validar que el usuario tiene permiso para esta intención
     * 
     * RBAC simple:
     * - "user": solo puede GET_INCIDENT, SEARCH_INCIDENTS, CHAT
     * - "analyst": puede todo
     * - "admin": puede todo
     */
    private void validateIntentPermissions(String intent, java.util.Set<String> roles) {
        
        // Si es admin o analyst, permitir todo
        if (roles.contains("admin") || roles.contains("analyst")) {
            return;
        }
        
        // Si es user regular, restricciones
        if (roles.contains("user")) {
            if (intent.equals("CREATE_INCIDENT") || intent.equals("ANALYZE_INCIDENT")) {
                log.warn("User without analyst role tried to perform {}", intent);
                throw new SecurityException("Not authorized for " + intent);
            }
        }
    }

    /**
     * DTO para parsear response del LLM
     */
    @Data
    public static class IntentClassificationResponse {
        private String intent;
        private Map<String, Object> parameters;
        private double confidence;
    }
}
