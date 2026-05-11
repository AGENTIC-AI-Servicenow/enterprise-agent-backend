package com.enterprise.agent;

import com.enterprise.agent.agent.AgentOrchestrator;
import com.enterprise.agent.context.UserContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;

/**
 * Console runner for testing the Enterprise Agent.
 * Disabled by default - uncomment @Component to enable.
 * 
 * Para testing local con la nueva arquitectura AgentOrchestrator.
 * Usa un usuario de prueba hardcodeado.
 */
//@Component
public class ConsoleAgentRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleAgentRunner.class);

    private final AgentOrchestrator orchestrator;

    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";
    
    private final String sessionId = UUID.randomUUID().toString();

    public ConsoleAgentRunner(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void run(String... args) {
        
        printWelcomeMessage();
        
        // Usuario de prueba
        UserContext testUser = UserContext.builder()
            .userId("console-test-user")
            .username("test.console")
            .email("test@company.com")
            .fullName("Console Test User")
            .roles(Set.of("user", "analyst"))
            .requestTimestamp(System.currentTimeMillis())
            .build();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\n" + GREEN + "Tú: " + RESET);
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
                // Crear AgentRequest
                AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest();
                request.setMessage(input);
                request.setSessionId(sessionId);
                request.setUserContext(testUser);
                request.setMetadata(new HashMap<>());
                
                // Procesar con el orquestador
                AgentOrchestrator.AgentResponse response = orchestrator.process(request);
                
                // Mostrar respuesta
                if (response.isSuccess()) {
                    System.out.println(YELLOW + "\n🤖 Agente: " + RESET + response.getMessage());
                    System.out.println(GREEN + "   [Intent: " + response.getIntent() + 
                                     ", Time: " + response.getExecutionTimeMs() + "ms]" + RESET);
                } else {
                    System.out.println(RED + "\n❌ Error: " + RESET + response.getMessage());
                }

            } catch (Exception e) {
                logger.error("Error processing console input", e);
                System.out.println(RED + "\n❌ Error inesperado: " + e.getMessage() + RESET);
            }
        }
        
        scanner.close();
    }

    private void printWelcomeMessage() {
        String welcomeMessage = """
╔══════════════════════════════════════════════════════════════╗
║            Enterprise Agent - Console Test Mode              ║
║              Nueva Arquitectura Agéntica                     ║
╚══════════════════════════════════════════════════════════════╝

🤖 Sistema agéntico con:
  • 🧠 Clasificación inteligente de intenciones
  • 🎯 Enrutamiento automático de acciones
  • 💾 Memoria conversacional
  • 🔍 Búsqueda semántica
  • 📊 Análisis de incidentes

✨ Ejemplos de comandos:
  "¿Cuál es el estado del incidente INC0010001?"
  "Busca mis tickets abiertos"
  "Analiza el ticket INC0010002"
  "Resume el incidente INC0010003"
  "¿Qué incidentes están críticos?"

💬 También puedes conversar naturalmente

🚪 Escribe 'exit' o 'salir' para terminar
""";

        System.out.println(YELLOW + welcomeMessage + RESET);
    }
}
