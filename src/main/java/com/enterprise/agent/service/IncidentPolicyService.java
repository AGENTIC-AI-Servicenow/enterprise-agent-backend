package com.enterprise.agent.service;

import com.enterprise.agent.context.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * IncidentPolicyService
 *
 * RESPONSABILIDAD:
 * - Filtrar y normalizar datos crudos provenientes de ServiceNow
 * - Aplicar reglas de negocio / visibilidad
 * - Preparar un JSON limpio y seguro para la capa de presentación
 *
 * IMPORTANTE:
 * Esta capa NO usa LLM.
 * Es completamente determinística.
 */
@Service
public class IncidentPolicyService {

    private final ObjectMapper objectMapper;
    private final com.enterprise.agent.config.ApplicationSettings settings;

    public IncidentPolicyService(ObjectMapper objectMapper,
                                 com.enterprise.agent.config.ApplicationSettings settings) {
        this.objectMapper = objectMapper;
        this.settings = settings;
    }

    /**
     * Aplica políticas de visibilidad y normalización.
     */
    public ObjectNode applyPolicy(JsonNode rawIncident, UserContext userContext) {

        ObjectNode safe = objectMapper.createObjectNode();

        // Campos permitidos (whitelist explícita)
        safe.put("number", rawIncident.path("number").asText("No disponible"));
        safe.put("short_description", rawIncident.path("short_description").asText("No disponible"));

        // Fecha de apertura: usar display_value directamente (ServiceNow ya respeta TZ usuario)
        String openedAt = "";

        if (rawIncident.has("opened_at") && rawIncident.get("opened_at").isObject()) {
            openedAt = rawIncident.get("opened_at").path("display_value").asText("");
        } else {
            openedAt = rawIncident.path("opened_at").asText("");
        }

        if (openedAt == null || openedAt.isBlank()) {

            if (rawIncident.has("sys_created_on") && rawIncident.get("sys_created_on").isObject()) {
                openedAt = rawIncident.get("sys_created_on").path("display_value").asText("");
            } else {
                openedAt = rawIncident.path("sys_created_on").asText("No disponible");
            }
        }

        safe.put("opened_at", openedAt);

        // Normalización de estado
        String state = rawIncident.path("state").asText();
        safe.put("state", mapState(state));

        // Normalización de prioridad
        String priority = rawIncident.path("priority").asText();
        safe.put("priority", mapPriority(priority));

        // Assigned to (puede venir vacío)
        String assigned = "";
        if (rawIncident.has("assigned_to") && rawIncident.get("assigned_to").isObject()) {
            assigned = rawIncident.get("assigned_to").path("display_value").asText("");
        } else {
            assigned = rawIncident.path("assigned_to").asText("");
        }
        safe.put("assigned_to", assigned.isBlank() ? "Sin asignar" : assigned);

        // Nunca exponer sys_id interno ni links internos
        // Nunca exponer caller_id completo

        return safe;
    }

    private String mapState(String state) {
        return switch (state) {
            case "1" -> "Nuevo";
            case "2" -> "En Progreso";
            case "3" -> "En Espera";
            case "6" -> "Resuelto";
            case "7" -> "Cerrado";
            default -> "Desconocido";
        };
    }

    private String mapPriority(String priority) {
        return switch (priority) {
            case "1" -> "Crítica";
            case "2" -> "Alta";
            case "3" -> "Media";
            case "4" -> "Baja";
            case "5" -> "Planificación";
            default -> "No definida";
        };
    }
}
