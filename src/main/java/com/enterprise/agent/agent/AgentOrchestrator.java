package com.enterprise.agent.agent;

import com.enterprise.agent.analytics.AnalyticsQuery;
import com.enterprise.agent.analytics.AnalyticsService;
import com.enterprise.agent.client.ServiceNowClient;
import com.enterprise.agent.context.UserContext;
import com.enterprise.agent.memory.ConversationMemory;
import com.enterprise.agent.service.IncidentPolicyService;
import com.enterprise.agent.service.IncidentRendererService;
import com.enterprise.agent.service.LLMProvider;
import com.enterprise.agent.service.OperationalRiskService;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nueva versión AI-Orchestrated
 *
 * - El Planner decide la intención
 * - AnalyticsService ejecuta herramientas
 * - El LLM formatea resultados
 */
@Service
@Log4j2
public class AgentOrchestrator {

    private final ConversationMemory memory;
    private final LLMProvider llm;
    private final IncidentPolicyService policyService;
    private final IncidentRendererService renderer;
    private final ServiceNowClient serviceNowClient;
    private final AgentDecisionEngine decisionEngine;
    private final OperationalRiskService riskService;
    private final AnalyticsService analyticsService;

    public AgentOrchestrator(
            ConversationMemory memory,
            LLMProvider llm,
            IncidentPolicyService policyService,
            IncidentRendererService renderer,
            ServiceNowClient serviceNowClient,
            AgentDecisionEngine decisionEngine,
            OperationalRiskService riskService,
            AnalyticsService analyticsService) {

        this.memory = memory;
        this.llm = llm;
        this.policyService = policyService;
        this.renderer = renderer;
        this.serviceNowClient = serviceNowClient;
        this.decisionEngine = decisionEngine;
        this.riskService = riskService;
        this.analyticsService = analyticsService;
    }

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

    public AgentResponse process(AgentRequest request) {

        long start = System.currentTimeMillis();
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : UUID.randomUUID().toString();

        String message = request.getMessage() == null ? "" : request.getMessage();

        try {

            memory.addUserMessage(sessionId, message, Map.of());

            // ✅ Fecha actual (determinístico)
            if (message.toLowerCase().contains("fecha")) {
                LocalDate today = LocalDate.now(ZoneId.of("America/Lima"));
                return build(
                        "📅 Hoy es " + today.getDayOfMonth()
                                + " de " + today.getMonth().name().toLowerCase()
                                + " de " + today.getYear() + ".",
                        "CURRENT_DATE",
                        true,
                        sessionId,
                        start,
                        0.99
                );
            }

            // ✅ Planner AI decide todo lo demás
            AgentDecisionEngine.AgentPlan plan =
                    decisionEngine.analyze(sessionId, message);

            double confidence = plan.getConfidence() > 0
                    ? plan.getConfidence()
                    : 0.85;

            // ✅ Si el mensaje contiene directamente un número de ticket (ej: INC0010198)
            // Forzamos flujo GET_INCIDENT para evitar que el planner lo clasifique como analytics.
            java.util.regex.Matcher ticketMatcher =
                    java.util.regex.Pattern.compile("INC\\d{6,}", java.util.regex.Pattern.CASE_INSENSITIVE)
                            .matcher(message);

            if (ticketMatcher.find()) {

                String ticketNumber = ticketMatcher.group().toUpperCase();

                var result =
                        serviceNowClient.getIncidentByNumber(ticketNumber);

                if (!result.has("result")
                        || result.get("result").isEmpty()) {

                    return build(
                            "No se encontró el ticket " + ticketNumber + ".",
                            "NOT_FOUND",
                            false,
                            sessionId,
                            start,
                            0.9
                    );
                }

                var incident = result.get("result").get(0);
                var safe = policyService.applyPolicy(incident, request.getUserContext());

                // ✅ En vez de dejar que el LLM "invente" campos faltantes,
                // usamos el renderer estructurado para mostrar estado,
                // urgencia, prioridad y demás datos reales.
                String rendered = renderer.renderStructuredView(safe);

                return build(rendered, "GET_INCIDENT", true, sessionId, start, 0.95);
            }

            List<com.fasterxml.jackson.databind.JsonNode> visibleIncidents = resolveVisibleIncidents(sessionId, request);

            // ✅ Casos frecuentes de demo: "mis incidentes abiertos" / "mis tickets abiertos"
            String normalizedMessage = message.toLowerCase();
            if ((normalizedMessage.contains("incidentes abiertos")
                    || normalizedMessage.contains("tickets abiertos")
                    || normalizedMessage.contains("incidentes open")
                    || normalizedMessage.contains("tickets open"))
                    && !ticketMatcher.find()) {

                if (visibleIncidents.isEmpty()) {
                    return build(
                            "No encontré incidentes visibles en este momento.",
                            "LIST_OPEN_INCIDENTS",
                            true,
                            sessionId,
                            start,
                            0.95
                    );
                }

                com.fasterxml.jackson.databind.node.ArrayNode openIncidents =
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();

                for (var incident : visibleIncidents) {
                    String stateRaw = incident.path("state").asText("");
                    if ("1".equals(stateRaw) || "2".equals(stateRaw) || "3".equals(stateRaw)
                            || "New".equalsIgnoreCase(stateRaw)
                            || "In Progress".equalsIgnoreCase(stateRaw)
                            || "On Hold".equalsIgnoreCase(stateRaw)) {
                        openIncidents.add(incident);
                    }
                }

                if (openIncidents.isEmpty()) {
                    return build(
                            "No tienes incidentes abiertos en este momento.",
                            "LIST_OPEN_INCIDENTS",
                            true,
                            sessionId,
                            start,
                            0.95
                    );
                }

                String rendered = renderer.renderIncidentList(openIncidents, Math.min(openIncidents.size(), 5));
                return build(rendered, "LIST_OPEN_INCIDENTS", true, sessionId, start, 0.95);
            }

            // ✅ ANALYTICS_QUERY → ejecutamos herramienta dinámica
            if ("ANALYTICS_QUERY".equalsIgnoreCase(plan.getIntent())) {

                try {

                    AnalyticsQuery query = plan.getAnalyticsQuery();

                    // ✅ Hardening: si el planner devolvió intent pero no estructura válida
                    if (query == null) {
                        query = new AnalyticsQuery();
                        query.setMetric("count");
                        query.setDateRange("until_now");
                    }

                    if (query.getMetric() == null) {
                        query.setMetric("count");
                    }

                    Map<String, Object> data =
                            analyticsService.execute(query);

                    if (data == null) {
                        data = Map.of("count", 0);
                    }

                    String natural;
                    if (data.containsKey("count")) {
                        natural = "Se encontraron " + data.get("count") + " incidentes para esa consulta.";
                    } else if (data.containsKey("list")) {
                        natural = "Resultados obtenidos: " + data.get("list");
                    } else if (data.containsKey("grouped")) {
                        natural = "Distribución encontrada: " + data.get("grouped");
                    } else {
                        natural = "No encontré datos para esa consulta.";
                    }

                    return build(
                            natural,
                            "ANALYTICS_QUERY",
                            true,
                            sessionId,
                            start,
                            confidence
                    );

                } catch (Exception ex) {
                    log.error("Error ejecutando ANALYTICS_QUERY", ex);

                    return build(
                            "No pude obtener las métricas en este momento.",
                            "ERROR",
                            false,
                            sessionId,
                            start,
                            0.6
                    );
                }
            }

            // ✅ Fallback inteligente si el planner falló pero la pregunta es analítica
            if (message.toLowerCase().contains("ticket")) {

                AnalyticsQuery fallbackQuery = new AnalyticsQuery();
                fallbackQuery.setMetric("count");
                fallbackQuery.setDateRange("until_now");
                fallbackQuery.setOutputMode("summary");

                Map<String, Object> data =
                        analyticsService.execute(fallbackQuery);

                String natural = data.containsKey("count")
                        ? "Se encontraron " + data.get("count") + " tickets."
                        : "No encontré datos para esa consulta.";

                return build(
                        natural,
                        "ANALYTICS_QUERY",
                        true,
                        sessionId,
                        start,
                        0.9
                );
            }

            // ✅ Consulta de ticket específico
            if ("GET_INCIDENT".equalsIgnoreCase(plan.getIntent())
                    && plan.getIncidentNumber() != null) {

                var result =
                        serviceNowClient.getIncidentByNumber(plan.getIncidentNumber());

                if (!result.has("result")
                        || result.get("result").isEmpty()) {

                    return build(
                            "No se encontró el ticket.",
                            "NOT_FOUND",
                            false,
                            sessionId,
                            start,
                            0.8
                    );
                }

                var incident = result.get("result").get(0);
                var safe = policyService.applyPolicy(incident, request.getUserContext());

                if ("SUMMARY".equalsIgnoreCase(plan.getIntent())
                        || "short".equalsIgnoreCase(plan.getSummaryType())) {

                    String summary = llm.generate("""
Resume este ticket en lenguaje claro y profesional.
Máximo 5 líneas.

%s
""".formatted(safe.toPrettyString()), 0.2, 150);

                    return build(summary, "SUMMARY", true, sessionId, start, confidence);
                }

                String rendered = renderer.renderStructuredView(safe);

                return build(rendered, "GET_INCIDENT", true, sessionId, start, confidence);
            }

            // ✅ Fallback conversacional
            String reply = llm.agentChat("""
Eres AideBot, asistente profesional de soporte TI.
Responde claro y útil.
""", message);

            return build(reply, "CHAT", true, sessionId, start, confidence);

        } catch (Exception e) {
            log.error("Error en AgentOrchestrator", e);
            return build(
                    "Ocurrió un error procesando la solicitud.",
                    "ERROR",
                    false,
                    sessionId,
                    start,
                    0.3
            );
        }
    }

    private List<com.fasterxml.jackson.databind.JsonNode> resolveVisibleIncidents(String sessionId, AgentRequest request) {
        List<com.fasterxml.jackson.databind.JsonNode> incidentsFromRequest = extractIncidentsFromMetadata(request.getMetadata());

        if (!incidentsFromRequest.isEmpty()) {
            memory.setSessionValue(sessionId, "visible_incidents", incidentsFromRequest);
            return incidentsFromRequest;
        }

        Object cached = memory.getSessionValue(sessionId, "visible_incidents");
        if (cached instanceof List<?> cachedList && !cachedList.isEmpty()) {
            List<com.fasterxml.jackson.databind.JsonNode> restored = new ArrayList<>();
            for (Object item : cachedList) {
                if (item instanceof com.fasterxml.jackson.databind.JsonNode node) {
                    restored.add(node);
                }
            }
            if (!restored.isEmpty()) {
                return restored;
            }
        }

        List<com.fasterxml.jackson.databind.JsonNode> incidentsFromServiceNow = new ArrayList<>();
        var result = serviceNowClient.getAllIncidents(null, null, null, 10000, 0);
        if (result != null && result.has("result") && result.get("result").isArray() && !result.get("result").isEmpty()) {
            result.get("result").forEach(incidentsFromServiceNow::add);
        }

        if (!incidentsFromServiceNow.isEmpty()) {
            memory.setSessionValue(sessionId, "visible_incidents", incidentsFromServiceNow);
        }

        return incidentsFromServiceNow;
    }

    private List<com.fasterxml.jackson.databind.JsonNode> extractIncidentsFromMetadata(Map<String, Object> metadata) {
        List<com.fasterxml.jackson.databind.JsonNode> incidents = new ArrayList<>();

        if (metadata == null) {
            return incidents;
        }

        Object visibleIncidents = metadata.get("visibleIncidents");
        if (!(visibleIncidents instanceof List<?> items)) {
            return incidents;
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (Object item : items) {
            if (item instanceof com.fasterxml.jackson.databind.JsonNode node) {
                incidents.add(node);
            } else {
                incidents.add(mapper.valueToTree(item));
            }
        }

        return incidents;
    }

    private AgentResponse build(
            String message,
            String intent,
            boolean success,
            String sessionId,
            long start,
            double confidence) {

        return new AgentResponse(
                message,
                intent,
                success,
                Map.of("session_id", sessionId),
                System.currentTimeMillis() - start,
                Map.of(
                        "mode", "ai_orchestrated",
                        "confidence", confidence
                )
        );
    }
}
