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
    }

    public AgentPlan analyze(String sessionId, String userMessage) {

        String context = memory.getRecentHistory(sessionId, 10);

        String prompt = """
Eres un motor de decisión para un agente corporativo IT.

Analiza el mensaje del usuario y genera un plan estructurado en JSON.

Devuelve SOLO JSON válido con esta estructura:

{
  "intent": "GET_INCIDENT | QUERY_FIELD | SUMMARY | RECOMMENDATION | UNKNOWN",
  "incidentNumber": "INCxxxx o null",
  "fieldRequested": "state | priority | assigned_to | sys_created_on | short_description | null",
  "summaryType": "executive | short | null",
  "requiresReasoning": true/false,
  "confidence": 0.0-1.0
}

Reglas:
- Si el usuario pide evaluación, recomendación o decisión → requiresReasoning=true.
- Si solo pide un dato puntual → QUERY_FIELD.
- Si pide resumen → SUMMARY.
- Usa el contexto conversacional si el número no está explícito.
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
