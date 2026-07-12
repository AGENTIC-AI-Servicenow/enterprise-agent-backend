package com.enterprise.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

/**
 * IncidentRendererService
 *
 * RESPONSABILIDAD:
 * - Convertir JSON ya filtrado (Policy Layer)
 *   en una respuesta natural usando LLM.
 *
 * IMPORTANTE:
 * - SOLO recibe datos seguros.
 * - NO llama a ServiceNow.
 * - NO aplica reglas de negocio.
 * - SOLO renderiza.
 */
@Service
public class IncidentRendererService {

    private final LLMProvider llmProvider;

    public IncidentRendererService(LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    public String renderIncident(JsonNode safeIncidentJson) {
        return renderFull(safeIncidentJson);
    }

    public String renderFull(JsonNode safeIncidentJson) {

        String prompt = """
Actúa como analista senior de operaciones IT.

Genera una respuesta clara, directa y profesional basada EXCLUSIVAMENTE
en el JSON proporcionado.

Reglas:
- No inventes información.
- No agregues saludos ni despedidas.
- No escribas como carta o correo.
- No agregues recomendaciones si no se solicitan.
- Sé directo y estructurado.
- Usa formato claro (máx. 5 líneas si aplica).

Datos del incidente:
%s
""".formatted(safeIncidentJson.toPrettyString());

        return llmProvider.generate(prompt, 0.2, 220);
    }

    public String renderField(JsonNode safeIncidentJson, String field) {

        // ✅ Campo puntual ahora es determinístico (sin LLM)
        if (safeIncidentJson == null || !safeIncidentJson.has(field)) {
            return "No disponible";
        }

        JsonNode valueNode = safeIncidentJson.get(field);

        if (valueNode == null || valueNode.isNull()) {
            return "No disponible";
        }

        if (valueNode.isObject() && valueNode.has("display_value")) {
            return valueNode.get("display_value").asText();
        }

        if (valueNode.isValueNode()) {
            return valueNode.asText();
        }

        return valueNode.toString();
    }

    public String renderExecutiveSummary(JsonNode safeIncidentJson) {

        String prompt = """
Actúa como analista senior de operaciones.

Genera un resumen ejecutivo en máximo 3 líneas.
Debe permitir entender rápidamente:
- Qué ocurrió
- Estado actual
- Nivel de prioridad

Reglas:
- No inventes información.
- No agregues saludos ni despedidas.
- Sé conciso y orientado a decisión.

Datos del incidente:
%s
""".formatted(safeIncidentJson.toPrettyString());

        return llmProvider.generate(prompt, 0.2, 110);
    }

    public String renderShortSummary(JsonNode safeIncidentJson) {

        String prompt = """
Resume el incidente en una sola frase profesional y directa.
No inventes información.
No agregues saludos ni texto adicional.

Datos del incidente:
%s
""".formatted(safeIncidentJson.toPrettyString());

        return llmProvider.generate(prompt, 0.2, 60);
    }

    /**
     * Vista estructurada profesional (sin estilo carta)
     */
    public String renderStructuredView(JsonNode safeIncidentJson) {

        String number = safeIncidentJson.has("number")
                ? safeIncidentJson.get("number").asText()
                : "No disponible";

        String state = extractDisplayValue(safeIncidentJson, "state");
        String priority = extractDisplayValue(safeIncidentJson, "priority");
        String assigned = extractDisplayValue(safeIncidentJson, "assigned_to");
        String created = extractDisplayValue(safeIncidentJson, "opened_at");
        if ("No disponible".equals(created) || created.isBlank()) {
            created = extractDisplayValue(safeIncidentJson, "sys_created_on");
        }
        String description = extractDisplayValue(safeIncidentJson, "short_description");

        return """
Incidente: %s
Descripción: %s
Estado: %s
Prioridad: %s
Asignado a: %s
Fecha de creación: %s
""".formatted(number, description, state, priority, assigned, created);
    }

    public String renderIncidentList(JsonNode incidentsNode, int maxItems) {
        if (incidentsNode == null || !incidentsNode.isArray() || incidentsNode.isEmpty()) {
            return "No se encontraron incidentes para mostrar.";
        }

        StringBuilder builder = new StringBuilder("Incidentes abiertos encontrados:\n");
        int count = 0;

        for (JsonNode incident : incidentsNode) {
            if (count >= maxItems) {
                break;
            }

            String number = extractDisplayValue(incident, "number");
            String description = extractDisplayValue(incident, "short_description");
            String state = extractDisplayValue(incident, "state");
            String priority = extractDisplayValue(incident, "priority");

            builder.append("- ")
                    .append(number)
                    .append(" | ")
                    .append(description)
                    .append(" | Estado: ")
                    .append(state)
                    .append(" | Prioridad: ")
                    .append(priority)
                    .append("\n");

            count++;
        }

        return builder.toString().trim();
    }

    private String extractDisplayValue(JsonNode node, String field) {

        if (node == null || !node.has(field)) {
            return "No disponible";
        }

        JsonNode valueNode = node.get(field);

        if (valueNode == null || valueNode.isNull()) {
            return "No disponible";
        }

        if (valueNode.isObject() && valueNode.has("display_value")) {
            return valueNode.get("display_value").asText();
        }

        if (valueNode.isValueNode()) {
            return valueNode.asText();
        }

        return valueNode.toString();
    }
}
