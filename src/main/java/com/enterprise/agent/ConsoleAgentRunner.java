package com.enterprise.agent;

import com.enterprise.agent.agent.ActionRouter;
import com.enterprise.agent.agent.AgentDecision;
import com.enterprise.agent.service.LLMService;
import com.enterprise.agent.service.ServiceNowOAuthTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

/**
 * Console runner for the Enterprise Agent using OAuth 2.0 authentication.
 * No longer requires user credential input as authentication is handled via OAuth.
 */
/*
 * Console runner disabled for Authorization Code Flow.
 * Authentication is now browser-based via /oauth/callback.
 */
//@Component
public class ConsoleAgentRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleAgentRunner.class);

    private final LLMService llmService;
    private final ActionRouter actionRouter;
    private final ServiceNowOAuthTokenProvider tokenProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    public ConsoleAgentRunner(LLMService llmService, 
                             ActionRouter actionRouter,
                             ServiceNowOAuthTokenProvider tokenProvider) {
        this.llmService = llmService;
        this.actionRouter = actionRouter;
        this.tokenProvider = tokenProvider;
    }

    @Override
    public void run(String... args) {
        
        printWelcomeMessage();

        // Validate OAuth authentication on startup
        if (!validateOAuthAuthentication()) {
            System.out.println(RED + "\n❌ OAuth authentication failed. Please check your configuration.\n" + RESET);
            return;
        }

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\nTú: ");
            String input = scanner.nextLine();

            if (input == null || input.isBlank()) {
                continue;
            }

            // Handle exit commands
            if (input.toLowerCase().matches("(exit|salir|quit|bye|adiós)")) {
                System.out.println(YELLOW + "\n¡Hasta luego! 👋\n" + RESET);
                break;
            }

            try {
                String modelResponse = llmService.classifyIntent(input);

                AgentDecision decision = objectMapper.readValue(modelResponse, AgentDecision.class);
                decision.setOriginalInput(input);

                actionRouter.execute(decision);

            } catch (Exception e) {
                logger.debug("Model did not return valid JSON, treating as chat", e);
                
                // If the model didn't return valid JSON, treat as conversation
                AgentDecision fallbackChat = new AgentDecision();
                fallbackChat.setAction("CHAT");
                fallbackChat.setOriginalInput(input);

                actionRouter.execute(fallbackChat);
            }
        }
    }

    /**
     * Validates OAuth authentication and displays current user info.
     */
    private boolean validateOAuthAuthentication() {
        try {
            System.out.println(YELLOW + "🔐 Validando autenticación OAuth..." + RESET);
            
            // This will trigger OAuth token acquisition and user validation
            actionRouter.ensureUserAuthentication();
            
            String authenticatedUser = actionRouter.getAuthenticatedUsername();
            String userSysId = actionRouter.getAuthenticatedUserSysId();
            
            System.out.println(GREEN + "✅ Autenticado exitosamente como: " + authenticatedUser + RESET);
            System.out.println(GREEN + "📋 User ID: " + userSysId + RESET);
            
            // Verify we're using the expected OAuth user
            String expectedUser = tokenProvider.getAuthenticatedUsername();
            if (!expectedUser.equals(authenticatedUser)) {
                System.out.println(YELLOW + "⚠️  Warning: Expected user '" + expectedUser + 
                                 "' but authenticated as '" + authenticatedUser + "'" + RESET);
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("OAuth authentication failed", e);
            System.out.println(RED + "❌ Error de autenticación: " + e.getMessage() + RESET);
            return false;
        }
    }

    private void printWelcomeMessage() {
        String welcomeMessage = """
╔══════════════════════════════════════════════════════════════╗
║                Enterprise Service Agent                      ║
║                     OAuth 2.0 Edition                       ║
╚══════════════════════════════════════════════════════════════╝

🤖 Puedo ayudarte con:
  • 🎫 Consultar incidentes (ej: "muéstrame INC0010001")
  • 📊 Obtener estado y prioridad de tickets
  • 📝 Crear nuevos incidentes
  • 💬 Conversar de forma natural
  • 🔍 Ver tu última incidencia

✨ Ejemplos de comandos:
  "¿Cuál es el estado del incidente INC0010001?"
  "Muéstrame mi última incidencia"
  "Crear un incidente por problema de red"
  "¿Recuerdas qué incidentes hemos consultado?"

🚪 Escribe 'exit' o 'salir' para terminar
""";

        System.out.println(YELLOW + welcomeMessage + RESET);
    }
}
