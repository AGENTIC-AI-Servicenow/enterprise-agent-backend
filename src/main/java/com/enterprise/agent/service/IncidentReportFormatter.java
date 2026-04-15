package com.enterprise.agent.service;

import com.enterprise.agent.model.AgentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IncidentReportFormatter {

    public AgentResponse format(String action, JsonNode raw) {

        AgentResponse response = new AgentResponse();
        response.setCorrelationId(UUID.randomUUID().toString());
        response.setActionExecuted(action);

        JsonNode results = raw.get("result");

        switch (action) {

            case "GET_COUNT" -> {
                int count = results.size();
                response.setSummary("Actualmente existen " + count + " incidentes activos.");
                response.setData(count);
            }

            case "GET_TOP_5", "GET_TOP_1" -> {
                response.setSummary("Listado priorizado de incidentes.");
                response.setData(results);
            }

            case "GET_STATUS" -> {
                String state = results.get(0).get("state").asText();
                response.setSummary("El incidente está en estado: " + state);
                response.setData(results.get(0));
            }

            case "GET_PRIORITY" -> {
                String priority = results.get(0).get("priority").asText();
                response.setSummary("La prioridad del incidente es: " + priority);
                response.setData(results.get(0));
            }

            case "GET_SUMMARY" -> {
                response.setSummary("Resumen del incidente.");
                response.setData(results.get(0));
            }
        }

        return response;
    }
}
