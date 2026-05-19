package com.enterprise.agent.controller;

import com.enterprise.agent.agent.AgentOrchestrator;
import com.enterprise.agent.context.UserContext;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller para integración con Microsoft Teams vía Bot Framework
 *
 * Endpoint que Azure Bot Service llamará:
 * POST /api/teams/messages
 */
@RestController
@RequestMapping("/api/teams")
@Log4j2
public class TeamsBotController {

    private final AgentOrchestrator orchestrator;

    public TeamsBotController(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * DTO mínimo compatible con Activity de Bot Framework
     */
    @Data
    public static class TeamsActivity {
        private String type;
        private String id;
        private String text;
        private From from;
        private Conversation conversation;

        @Data
        public static class From {
            private String id;
            private String name;
        }

        @Data
        public static class Conversation {
            private String id;
        }
    }

    /**
     * Response en formato Bot Framework
     */
    @Data
    public static class TeamsMessageResponse {
        private String type = "message";
        private String text;

        public TeamsMessageResponse(String text) {
            this.text = text;
        }
    }

    /**
     * Endpoint principal que recibe mensajes desde Teams
     */
    @PostMapping("/messages")
    public ResponseEntity<?> receiveMessage(@RequestBody TeamsActivity activity) {

        if (!"message".equalsIgnoreCase(activity.getType())) {
            return ResponseEntity.ok().build();
        }

        String userMessage = activity.getText();
        String sessionId = activity.getConversation() != null
                ? activity.getConversation().getId()
                : UUID.randomUUID().toString();

        if (userMessage == null || userMessage.isBlank()) {
            return ResponseEntity.ok(new TeamsMessageResponse("El mensaje no puede estar vacío."));
        }

        try {

            // Construir UserContext desde Teams
            UserContext userContext = UserContext.builder()
                    .userId(activity.getFrom() != null ? activity.getFrom().getId() : "teams-user")
                    .username(activity.getFrom() != null ? activity.getFrom().getName() : "Teams User")
                    .email(null)
                    .roles(Set.of("user"))
                    .requestTimestamp(System.currentTimeMillis())
                    .build();

            // Construir AgentRequest
            AgentOrchestrator.AgentRequest agentRequest = new AgentOrchestrator.AgentRequest();
            agentRequest.setMessage(userMessage);
            agentRequest.setSessionId(sessionId);
            agentRequest.setUserContext(userContext);
            agentRequest.setMetadata(new HashMap<>());

            // Procesar con el mismo motor que usa /agent
            AgentOrchestrator.AgentResponse result = orchestrator.process(agentRequest);

            // Si el agente retorna múltiples mensajes, concatenarlos
            String responseText;
            if (result.getMessage() != null) {
                responseText = result.getMessage();
            } else {
                responseText = "No se pudo generar una respuesta.";
            }

            return ResponseEntity.ok(new TeamsMessageResponse(responseText));

        } catch (Exception e) {
            log.error("Error processing Teams message", e);
            return ResponseEntity.ok(
                    new TeamsMessageResponse("Ocurrió un error procesando tu solicitud. Intenta nuevamente.")
            );
        }
    }
}
