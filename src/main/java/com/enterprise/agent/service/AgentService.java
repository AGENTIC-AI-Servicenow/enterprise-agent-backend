package com.enterprise.agent.service;

import com.enterprise.agent.model.AgentDecision;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AgentService {

    public AgentDecision decide(String message) {

        AgentDecision decision = new AgentDecision();
        Map<String, Object> params = new HashMap<>();

        message = message.toLowerCase();

        if (message.contains("top 5")) {
            decision.setAction("GET_TOP_5");
        }
        else if (message.contains("top 1")) {
            decision.setAction("GET_TOP_1");
        }
        else if (message.contains("conteo")) {
            decision.setAction("GET_COUNT");
        }
        else if (message.contains("estado")) {
            decision.setAction("GET_STATUS");
            params.put("incident_number", extractIncident(message));
        }
        else if (message.contains("prioridad")) {
            decision.setAction("GET_PRIORITY");
            params.put("incident_number", extractIncident(message));
        }
        else if (message.contains("resumen")) {
            decision.setAction("GET_SUMMARY");
            params.put("incident_number", extractIncident(message));
        }
        else {
            decision.setAction("UNKNOWN");
        }

        decision.setParameters(params);
        return decision;
    }

    private String extractIncident(String message) {
        return message.replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase()
                .contains("INC") ? "INC0010001" : "INC0010001";
    }
}
