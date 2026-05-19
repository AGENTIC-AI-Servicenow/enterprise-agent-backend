package com.enterprise.agent.agent;

import com.enterprise.agent.memory.ConversationMemory;
import com.enterprise.agent.service.LLMProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AgentDecisionEngine
 *
 * RESPONSABILIDAD:
 * - Usar LLM para:
 *   1. Comprender intención
 *   2. Generar plan estructurado
 *   3. Determinar si requiere reasoning adicional
 *
 * Este componente convierte el sistema en un agente cognitivo real.
 */
@Service
public class AgentDecisionEngine {

    private final LLMProvider llmProvider;
    private final ConversationMemory memory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentDecisionEngine(LLMProvider llmProvider,
                               ConversationMemory memory) {
        this.llmProvider = llmProvider;
        this.memory = memory;
    }

    @Data
    public static class AgentPlan {
        private String intent;
        private String incidentNumber;
        private String fieldRequested;
        private String summaryType;
        private boolean requiresReasoning;
        private double confidence;

        // ✅ Nuevo: consulta analítica estructurada
        private com.enterprise.agent.analytics.AnalyticsQuery analyticsQuery;
    }

    public AgentPlan analyze(String sessionId, String userMessage) {

        String context = memory.getRecentHistory(sessionId, 10);

        String prompt = """
Eres un motor de decisión para un agente corporativo IT.

Analiza el mensaje del usuario y genera un plan estructurado en JSON.

Devuelve SOLO JSON válido con esta estructura:

{
  "intent": "GET_INCIDENT | QUERY_FIELD | SUMMARY | RECOMMENDATION | ANALYTICS_QUERY | GREETING | CHAT | UNKNOWN",
  "incidentNumber": "INCxxxx o null",
  "fieldRequested": "state | priority | assigned_to | sys_created_on | short_description | null",
  "summaryType": "executive | short | null",
  "requiresReasoning": true/false,
  "confidence": 0.0-1.0,
  "analyticsQuery": {
      "metric": "count | group | list | average",
      "filters": { "priority": "1", "state": "1" },
      "dateRange": "today | yesterday | last_week | until_now | this_week",
      "groupBy": "assigned_to | category | state | none",
      "outputMode": "numeric_only | summary | detailed",
      "limit": 5
  }
}

Reglas de interpretación (MUY IMPORTANTE):

- Si el usuario solo saluda → GREETING.
- Si pregunta por capacidades o conversación general → CHAT.
- Si el usuario pregunta por:
  cantidad de tickets,
  tipos de tickets,
  tickets críticos,
  alta criticidad,
  próximos a vencer,
  responsables,
  métricas,
  estadísticas,
  agrupaciones,
  fechas (ayer, semana pasada, hasta hoy),
  o cualquier dato cuantitativo relacionado con tickets
  → SIEMPRE usar ANALYTICS_QUERY.

- Nunca uses CHAT cuando la pregunta sea sobre datos reales de tickets.
- ANALYTICS_QUERY tiene prioridad sobre CHAT.
- Si el usuario pide evaluación, recomendación o decisión → requiresReasoning=true.
- Si solo pide un dato puntual de un ticket específico → QUERY_FIELD.
- Si pide resumen de un ticket → SUMMARY.
- Usa SIEMPRE el contexto conversacional si el número no está explícito.
- Si la intención no es clara pero habla de tickets → usa ANALYTICS_QUERY.
- Solo usa UNKNOWN si realmente no tiene relación con soporte TI.
- No expliques nada fuera del JSON.

Contexto conversación:
%s

Mensaje usuario:
%s
""".formatted(context, userMessage);

        String response = llmProvider.generate(prompt, 0.1, 300);

        try {
            JsonNode node = objectMapper.readTree(response);

            AgentPlan plan = new AgentPlan();
            plan.setIntent(node.path("intent").asText());
            plan.setIncidentNumber(node.path("incidentNumber").isNull() ? null : node.path("incidentNumber").asText());
            plan.setFieldRequested(node.path("fieldRequested").isNull() ? null : node.path("fieldRequested").asText());
            plan.setSummaryType(node.path("summaryType").isNull() ? null : node.path("summaryType").asText());
            plan.setRequiresReasoning(node.path("requiresReasoning").asBoolean(false));
            plan.setConfidence(node.path("confidence").asDouble(0.0));

            // ✅ Parse analyticsQuery si existe
            JsonNode analyticsNode = node.path("analyticsQuery");
            if (!analyticsNode.isMissingNode() && !analyticsNode.isNull()) {

                com.enterprise.agent.analytics.AnalyticsQuery query =
                        new com.enterprise.agent.analytics.AnalyticsQuery();

                query.setMetric(analyticsNode.path("metric").asText(null));
                query.setDateRange(analyticsNode.path("dateRange").asText(null));
                query.setGroupBy(analyticsNode.path("groupBy").asText(null));
                query.setOutputMode(analyticsNode.path("outputMode").asText(null));

                if (analyticsNode.has("limit")) {
                    query.setLimit(analyticsNode.path("limit").asInt());
                }

                if (analyticsNode.has("filters") && analyticsNode.path("filters").isObject()) {
                    java.util.Iterator<String> fieldNames = analyticsNode.path("filters").fieldNames();
                    java.util.Map<String, String> filters = new java.util.HashMap<>();
                    while (fieldNames.hasNext()) {
                        String field = fieldNames.next();
                        filters.put(field, analyticsNode.path("filters").path(field).asText());
                    }
                    query.setFilters(filters);
                }

                plan.setAnalyticsQuery(query);
            }

            return plan;

        } catch (Exception e) {

            AgentPlan fallback = new AgentPlan();
            fallback.setIntent("UNKNOWN");
            fallback.setRequiresReasoning(false);
            fallback.setConfidence(0.0);
            return fallback;
        }
    }
}
