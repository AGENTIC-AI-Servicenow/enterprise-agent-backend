package com.enterprise.agent.agent;

import com.enterprise.agent.client.ServiceNowClient;
import com.enterprise.agent.service.LLMService;
import com.enterprise.agent.service.ServiceNowOAuthTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import com.enterprise.agent.memory.ConversationMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ActionRouter {

    private static final Logger logger = LoggerFactory.getLogger(ActionRouter.class);

    private final ServiceNowClient serviceNowClient;
    private final ServiceNowOAuthTokenProvider tokenProvider;
    private final LLMService llmService;
    private final ConversationMemory memory;

    private static final String YELLOW = "\u001B[33m";
    private static final String RESET = "\u001B[0m";

    private static final Pattern INCIDENT_PATTERN = Pattern.compile("INC\\d+");

    // Cache for authenticated user info
    private String authenticatedUserSysId;
    private String authenticatedUsername;

    public ActionRouter(ServiceNowClient serviceNowClient,
                        ServiceNowOAuthTokenProvider tokenProvider,
                        LLMService llmService,
                        ConversationMemory memory) {
        this.serviceNowClient = serviceNowClient;
        this.tokenProvider = tokenProvider;
        this.llmService = llmService;
        this.memory = memory;
    }

    /**
     * Execute agent decisions with OAuth authentication.
     * No longer requires user credentials as parameters.
     */
    public void execute(AgentDecision decision) {
        
        // Ensure we have authenticated user info
        ensureUserAuthentication();

        String input = decision.getOriginalInput() != null
                ? decision.getOriginalInput().toLowerCase()
                : "";

        // Detect explicit incident number
        Matcher matcher = INCIDENT_PATTERN.matcher(input.toUpperCase());
        if (matcher.find()) {
            decision.setAction("GET_INCIDENT");
            decision.setNumber(matcher.group());
        }

        // Detect request for last incident (not from memory)
        if ((input.contains("mi última")
                || input.contains("mi ultimo")
                || input.contains("más reciente")
                || input.contains("ultima incidencia")
                || input.contains("última incidencia"))
                && !input.contains("recuerdas")) {

            decision.setAction("GET_LAST_INCIDENT");
        }

        // Memory questions
        if (input.contains("recuerdas")) {
            if (memory.getSuccessfulIncidents().isEmpty()) {
                System.out.println(YELLOW +
                        "\nNo hemos consultado incidentes exitosamente todavía.\n" + RESET);
            } else {
                System.out.println(YELLOW +
                        "\nHas consultado exitosamente: "
                        + memory.getSuccessfulIncidents() + "\n" + RESET);
            }
            return;
        }

        if (decision.getAction() == null || decision.getAction().isBlank()) {
            decision.setAction("CHAT");
        }

        switch (decision.getAction()) {
            case "GET_INCIDENT" -> handleGetIncident(decision);
            case "GET_LAST_INCIDENT" -> handleGetLastIncident();
            case "CREATE_INCIDENT" -> handleCreateIncident(decision);
            case "CHAT" -> handleChat(decision);
            default -> System.out.println("Actualmente no puedo ejecutar esa acción.");
        }
    }

    private void handleChat(AgentDecision decision) {
        String response = llmService.generateChatResponse(decision.getOriginalInput());
        System.out.println(YELLOW + "\n" + response + "\n" + RESET);
    }

    private void handleGetIncident(AgentDecision decision) {
        String number = decision.getNumber();

        if (number == null || number.isBlank()) {
            System.out.println("No pude identificar un número de incidente válido.");
            return;
        }

        System.out.println("Consultando incidente " + number + "...");

        try {
            JsonNode response = serviceNowClient.getIncidentSecureByNumberAndCaller(
                    number, authenticatedUserSysId);

            if (response != null
                    && response.has("result")
                    && response.get("result").isArray()
                    && response.get("result").size() > 0) {

                memory.addSuccessful(number);

                JsonNode incident = response.get("result").get(0);

                String shortDesc = incident.path("short_description").asText("Sin descripción");
                String state = translateState(incident.path("state").asText());
                String priority = incident.path("priority").asText("No definida");

                String contextData = """
                        Número: %s
                        Descripción: %s
                        Estado: %s
                        Prioridad: %s
                        """.formatted(number, shortDesc, state, priority);

                String summary = llmService.generateIncidentSummary(contextData);
                System.out.println(YELLOW + "\n" + summary + "\n" + RESET);

            } else {
                memory.addFailed(number);
                System.out.println(YELLOW +
                        "\nNo se encontró el incidente o no tienes permisos para verlo.\n"
                        + RESET);
            }

        } catch (Exception e) {
            logger.error("Error retrieving incident {}", number, e);
            memory.addFailed(number);
            System.out.println(YELLOW +
                    "\nError al consultar el incidente: " + e.getMessage() + "\n"
                    + RESET);
        }
    }

    private void handleGetLastIncident() {
        System.out.println("Consultando tu última incidencia registrada...");

        try {
            JsonNode response = serviceNowClient.getIncidentsByCallerId(authenticatedUserSysId);

            if (response != null
                    && response.has("result")
                    && response.get("result").isArray()
                    && response.get("result").size() > 0) {

                JsonNode incident = response.get("result").get(0);

                String number = incident.path("number").asText();
                String shortDesc = incident.path("short_description").asText("Sin descripción");
                String state = translateState(incident.path("state").asText());
                String priority = incident.path("priority").asText("No definida");

                memory.addSuccessful(number);

                String contextData = """
                        Número: %s
                        Descripción: %s
                        Estado: %s
                        Prioridad: %s
                        """.formatted(number, shortDesc, state, priority);

                String summary = llmService.generateIncidentSummary(contextData);
                System.out.println(YELLOW + "\n" + summary + "\n" + RESET);

            } else {
                System.out.println(YELLOW +
                        "\nNo tienes incidencias registradas.\n"
                        + RESET);
            }

        } catch (Exception e) {
            logger.error("Error retrieving last incident", e);
            System.out.println(YELLOW +
                    "\nError al consultar la última incidencia: " + e.getMessage() + "\n"
                    + RESET);
        }
    }

    private void handleCreateIncident(AgentDecision decision) {
        System.out.println("Creando nuevo incidente...");

        try {
            JsonNode response = serviceNowClient.createIncident(
                    decision.getShortDescription(),
                    decision.getDescription(),
                    decision.getPriority() != null ? decision.getPriority() : "3",
                    authenticatedUserSysId
            );

            if (response != null && response.has("result")) {
                String number = response.get("result").get("number").asText();
                memory.addSuccessful(number);

                System.out.println(YELLOW +
                        "\n✅ Incidente creado correctamente: " + number + "\n" + RESET);
            } else {
                System.out.println(YELLOW +
                        "\n❌ No se pudo crear el incidente.\n" + RESET);
            }

        } catch (Exception e) {
            logger.error("Error creating incident", e);
            System.out.println(YELLOW +
                    "\n❌ Error al crear el incidente: " + e.getMessage() + "\n" + RESET);
        }
    }

    private String translateState(String state) {
        return switch (state) {
            case "1" -> "Nuevo";
            case "2" -> "En Progreso";
            case "3" -> "En Espera";
            case "6" -> "Resuelto";
            case "7" -> "Cerrado";
            default -> "Desconocido";
        };
    }

    /**
     * Gets the authenticated user information using OAuth.
     * This replaces the old Basic Auth user authentication.
     */
    public void ensureUserAuthentication() {
        if (authenticatedUserSysId == null || authenticatedUsername == null) {
            try {
                logger.info("Getting authenticated user information via OAuth");
                
                JsonNode userResponse = serviceNowClient.getCurrentUser();
                
                if (userResponse != null && userResponse.has("result")) {
                    JsonNode user = userResponse.get("result");
                    authenticatedUserSysId = user.path("sys_id").asText();
                    authenticatedUsername = user.path("user_name").asText();
                    
                    logger.info("Authenticated as user: {} (sys_id: {})", 
                              authenticatedUsername, authenticatedUserSysId);
                    
                    // Verify we're authenticated as the expected user
                    String expectedUsername = tokenProvider.getAuthenticatedUsername();
                    if (!expectedUsername.equals(authenticatedUsername)) {
                        logger.warn("Expected user '{}' but authenticated as '{}'", 
                                  expectedUsername, authenticatedUsername);
                    }
                    
                } else {
                    throw new RuntimeException("Failed to retrieve current user information");
                }
                
            } catch (Exception e) {
                logger.error("Failed to get authenticated user information", e);
                throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Returns the authenticated user's sys_id.
     */
    public String getAuthenticatedUserSysId() {
        ensureUserAuthentication();
        return authenticatedUserSysId;
    }

    /**
     * Returns the authenticated username.
     */
    public String getAuthenticatedUsername() {
        ensureUserAuthentication();
        return authenticatedUsername;
    }
}
