package com.enterprise.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * OperationalRiskService
 *
 * Motor determinístico de scoring operativo.
 * Permite demostrar priorización inteligente en la PoC.
 */
@Service
public class OperationalRiskService {

    public static class RiskAssessment {
        public final int score;
        public final String level;

        public RiskAssessment(int score, String level) {
            this.score = score;
            this.level = level;
        }
    }

    public RiskAssessment evaluate(JsonNode incident) {

        int score = 0;

        String priority = extract(incident, "priority");
        String state = extract(incident, "state");
        String assigned = extract(incident, "assigned_to");
        String created = extract(incident, "sys_created_on");

        // Prioridad
        if ("1".equals(priority) || priority.toLowerCase().contains("critical")) {
            score += 40;
        } else if (priority.toLowerCase().contains("alta")) {
            score += 30;
        }

        // Estado
        if (state.toLowerCase().contains("new")) {
            score += 10;
        }

        // Sin asignar
        if (assigned == null || assigned.equalsIgnoreCase("No disponible") || assigned.isBlank()) {
            score += 20;
        }

        // Antigüedad (si parseable)
        try {
            Instant createdTime = Instant.parse(created.replace(" ", "T") + "Z");
            long hours = Duration.between(createdTime, Instant.now()).toHours();
            if (hours > 24) score += 20;
            if (hours > 72) score += 30;
        } catch (Exception ignored) {}

        String level;
        if (score >= 80) level = "CRITICAL";
        else if (score >= 60) level = "HIGH";
        else if (score >= 30) level = "MEDIUM";
        else level = "LOW";

        return new RiskAssessment(score, level);
    }

    private String extract(JsonNode node, String field) {
        if (!node.has(field)) return "";
        JsonNode v = node.get(field);
        if (v.isObject() && v.has("display_value")) return v.get("display_value").asText();
        return v.asText("");
    }
}
