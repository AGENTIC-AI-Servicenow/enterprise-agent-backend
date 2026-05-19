package com.enterprise.agent.agent;

import com.enterprise.agent.client.ServiceNowClient;
import com.enterprise.agent.context.UserContext;
import com.enterprise.agent.memory.ConversationMemory;
import com.enterprise.agent.service.IncidentPolicyService;
import com.enterprise.agent.service.IncidentRendererService;
import com.enterprise.agent.service.LLMProvider;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * AgentOrchestrator - Copiloto Enterprise AI
 *
 * Arquitectura:
 * 1. ConversationMemory
 * 2. AgentDecisionEngine (LLM Planner)
 * 3. Tool Execution (determinístico)
 * 4. Policy Layer
 * 5. Reasoning opcional
 * 6. Renderer
 */
@Service
@Log4j2
public class AgentOrchestrator {

    private final ConversationMemory conversationMemory;
    private final LLMProvider llmProvider;
    private final IncidentPolicyService incidentPolicyService;
    private final IncidentRendererService incidentRendererService;
    private final ServiceNowClient serviceNowClient;
    private final AgentDecisionEngine decisionEngine;
    private final com.enterprise.agent.service.OperationalRiskService riskService;

    @Data
    public static class AgentRequest {
        private String message;
        private String sessionId;
        private UserContext userContext;
        private Map<String, Object> metadata;
    }

    @Data
    public static class AgentResponse {
        private final String message;
        private final String intent;
        private final boolean success;
        private final Map<String, Object> metadata;
        private final long executionTimeMs;
        private final Map<String, Object> execution;
    }

    public AgentOrchestrator(
            ConversationMemory conversationMemory,
            LLMProvider llmProvider,
            IncidentPolicyService incidentPolicyService,
            IncidentRendererService incidentRendererService,
            ServiceNowClient serviceNowClient,
            AgentDecisionEngine decisionEngine,
            com.enterprise.agent.service.OperationalRiskService riskService) {

        this.conversationMemory = conversationMemory;
        this.llmProvider = llmProvider;
        this.incidentPolicyService = incidentPolicyService;
        this.incidentRendererService = incidentRendererService;
        this.serviceNowClient = serviceNowClient;
        this.decisionEngine = decisionEngine;
        this.riskService = riskService;
    }

    public AgentResponse process(AgentRequest request) {

        long startTime = System.currentTimeMillis();
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        try {

            String message = request.getMessage() == null ? "" : request.getMessage();

            conversationMemory.addUserMessage(
                    sessionId,
                    message,
                    Map.of("user_id", request.getUserContext().getUserId())
            );

            // ==============================
            // 🤖 1. AI Planner
            // ==============================
            AgentDecisionEngine.AgentPlan plan =
                    decisionEngine.analyze(sessionId, message);

            String incidentNumber = plan.getIncidentNumber();

            // Recuperar del contexto si no viene explícito
            if (incidentNumber == null) {
                incidentNumber = conversationMemory.getLastIncidentId(sessionId);
            }

            if (incidentNumber == null) {
                // Si el usuario no mencionó un incidente, tratamos el mensaje como conversación libre.
                // Esto evita respuestas "UNKNOWN" ante saludos como "Hola".
                String systemPrompt = """
Eres AideBot 🤖, un asistente de soporte TI (ServiceNow copilot) amable y profesional.

ESTILO:
- Escribe en español natural, claro y conversacional.
- Usa emojis con moderación (1–3 por mensaje) para dar calidez, sin saturar.
- Sé proactivo: sugiere próximos pasos cuando aplique.
- Si el usuario pide datos de un incidente, solicita el número (ej: INC0012345).

REGLAS:
- No inventes datos. Si falta información, dilo y pide el dato.
- Si el usuario saluda, responde con saludo breve + pregunta de necesidad.

FORMATO:
- Respuestas cortas (2–6 líneas), con bullets cuando ayuden.

""";

                String reply = llmProvider.agentChat(systemPrompt, message);

                conversationMemory.addAssistantMessage(
                        sessionId,
                        reply,
                        Map.of("intent", "CHAT")
                );

                return buildResponse(
                        reply,
                        "CHAT",
                        true,
                        sessionId,
                        startTime
                );
            }

            // ==============================
            // 🛠 2. Tool Execution
            // ==============================
            var result = serviceNowClient.getIncidentByNumber(incidentNumber);

            if (!result.has("result")
                    || !result.get("result").isArray()
                    || result.get("result").size() == 0) {

                conversationMemory.addFailed(incidentNumber);

                return buildResponse(
                        "No se encontró el incidente " + incidentNumber + ". Verifica el número o tus permisos de acceso.",
                        "NOT_FOUND",
                        false,
                        sessionId,
                        startTime
                );
            }

            var incidentNode = result.get("result").get(0);

            // 🔎 DEBUG: Log JSON completo devuelto por ServiceNow
            log.info("Incident raw JSON from ServiceNow:\n{}",
                    incidentNode.toPrettyString());

            var safe = incidentPolicyService.applyPolicy(
                    incidentNode,
                    request.getUserContext()
            );

            conversationMemory.addSuccessful(incidentNumber);

            // ==============================
            // 🧠 3. Execution Based on Plan
            // ==============================

            String rendered;

            // Evaluar riesgo operativo
            var risk = riskService.evaluate(safe);

            switch (plan.getIntent()) {

                case "QUERY_FIELD": {
                    String field = plan.getFieldRequested();
                    rendered = incidentRendererService.renderField(safe, field);

                    // Si el renderer no encuentra el campo, damos un fallback “humano”
                    // para preguntas típicas (creación / apertura) y variaciones.
                    if (rendered == null || rendered.isBlank() || rendered.equalsIgnoreCase("No disponible")) {
                        String f = field == null ? "" : field.toLowerCase();

                        if (f.contains("created") || f.contains("creado") || f.contains("creación") || f.contains("sys_created_on")) {
                            var n = safe.get("sys_created_on");
                            String v = (n != null && !n.isNull() && !n.asText().isBlank()) ? n.asText() : null;
                            rendered = v != null
                                    ? "📅 La fecha de creación es: " + v
                                    : "🤔 No encuentro la fecha de creación en los datos disponibles.";
                        } else if (f.contains("opened") || f.contains("abierto") || f.contains("apertura") || f.contains("opened_at")) {
                            var n = safe.get("opened_at");
                            String v = (n != null && !n.isNull() && !n.asText().isBlank()) ? n.asText() : null;
                            rendered = v != null
                                    ? "📅 Se abrió el: " + v
                                    : "🤔 No encuentro la fecha de apertura (opened_at) en los datos disponibles.";
                        } else {
                            rendered = "🤔 No tengo ese dato disponible para este incidente. ¿Qué campo específico necesitas (prioridad, estado, asignado, fecha de creación)?";
                        }
                    }
                    break;
                }

                case "SUMMARY":
                    if ("executive".equals(plan.getSummaryType())) {
                        rendered = incidentRendererService.renderExecutiveSummary(safe);
                    } else {
                        rendered = incidentRendererService.renderShortSummary(safe);
                    }
                    break;

                case "RECOMMENDATION":
                    rendered = llmProvider.generate("""
Analiza el incidente y genera una recomendación profesional breve.

Evalúa:
- Prioridad
- Estado
- Asignación
- Riesgo operativo

Datos:
%s
""".formatted(safe.toPrettyString()), 0.2, 150);
                    break;

                case "GET_INCIDENT":
                default: {
                    // 1) mensaje de confirmación (primer mensaje)
                    String intro = "✅ ¡Encontré tu ticket! 🎯";

                    conversationMemory.addAssistantMessage(
                            sessionId,
                            intro,
                            Map.of("intent", "GET_INCIDENT",
                                   "incident_id", incidentNumber,
                                   "part", "intro")
                    );

                    // 2) mensaje de resumen (segundo mensaje)
                    String summary = incidentRendererService.renderStructuredView(safe)
                            + "\n\nRisk Score: " + risk.score
                            + "\nRisk Level: " + risk.level;

                    rendered = summary;
                    break;
                }
            }

            conversationMemory.addAssistantMessage(
                    sessionId,
                    rendered,
                    Map.of("intent", plan.getIntent(),
                           "incident_id", incidentNumber)
            );

            // Para GET_INCIDENT queremos 2 burbujas: confirmación + resumen.
            // El controller expone ChatResponse.messages y el frontend lo renderiza.
            // IMPORTANTE: aquí retornamos message con separador \n\n para que el controller lo parta en 2.
            if ("GET_INCIDENT".equals(plan.getIntent())) {
                return buildResponse(
                        "✅ ¡Encontré tu ticket! 🎯 Aquí tienes el resumen completo:\n\n" + rendered,
                        plan.getIntent(),
                        true,
                        sessionId,
                        startTime
                );
            }

            return buildResponse(
                    rendered,
                    plan.getIntent(),
                    true,
                    sessionId,
                    startTime
            );

        } catch (com.enterprise.agent.client.ServiceNowApiException e) {

            log.warn("ServiceNow API error: {}", e.getMessage());

            return buildResponse(
                    "No se encontró el incidente solicitado o no tienes permisos para visualizarlo.",
                    "NOT_FOUND",
                    false,
                    sessionId,
                    startTime
            );

        } catch (Exception e) {

            log.error("Error inesperado procesando solicitud", e);

            return buildResponse(
                    "No se pudo completar la solicitud en este momento. Intenta nuevamente.",
                    "ERROR",
                    false,
                    sessionId,
                    startTime
            );
        }
    }

    private AgentResponse buildResponse(
            String message,
            String intent,
            boolean success,
            String sessionId,
            long startTime) {

        long totalTime = System.currentTimeMillis() - startTime;

        return new AgentResponse(
                message,
                intent,
                success,
                Map.of(
                        "session_id", sessionId
                ),
                totalTime,
                Map.of(
                        "mode", "enterprise_ai",
                        "confidence", intent
                )
        );
    }
}
