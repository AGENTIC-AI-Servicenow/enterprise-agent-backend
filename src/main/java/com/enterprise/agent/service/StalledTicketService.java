package com.enterprise.agent.service;

import com.enterprise.agent.client.ServiceNowClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Log4j2
public class StalledTicketService {

    private static final DateTimeFormatter SERVICE_NOW_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US);

    private final ServiceNowClient serviceNowClient;
    private final LLMProvider llmProvider;

    public StalledTicketService(ServiceNowClient serviceNowClient, LLMProvider llmProvider) {
        this.serviceNowClient = serviceNowClient;
        this.llmProvider = llmProvider;
    }

    public StalledTicketResponse analyzeTicket(String ticketNumber) {
        JsonNode response = serviceNowClient.getIncidentByNumber(ticketNumber);
        JsonNode results = response.path("result");

        if (!results.isArray() || results.isEmpty()) {
            throw new IllegalArgumentException("No encontré el ticket " + ticketNumber);
        }

        JsonNode incident = results.get(0);

        String number = incident.path("number").asText(ticketNumber);
        String shortDescription = incident.path("short_description").asText("");
        String description = incident.path("description").asText("");
        String stateRaw = incident.path("state").asText("");
        String priorityRaw = incident.path("priority").asText("");
        String assignedTo = extractDisplayValue(incident.path("assigned_to"));
        String caller = extractDisplayValue(incident.path("caller_id"));

        String openedAt = firstNonBlank(
                incident.path("opened_at").asText(""),
                incident.path("sys_created_on").asText(""));
        String updatedAt = firstNonBlank(
                incident.path("sys_updated_on").asText(""),
                incident.path("updated_at").asText(""));

        long ageHours = hoursSince(openedAt);
        long daysWithoutUpdate = daysSince(updatedAt);
        int contactAttempts = estimateContactAttempts(shortDescription + " " + description);
        boolean onHold = isOnHold(stateRaw);
        boolean stalled = onHold || daysWithoutUpdate >= 2;
        String holdReason = inferHoldReason(shortDescription, description, stateRaw);
        String nextAction = buildNextAction(contactAttempts, daysWithoutUpdate, onHold);
        List<String> validationFlags = buildValidationFlags(onHold, daysWithoutUpdate, contactAttempts, priorityRaw);

        String draft = generateDraft(number, shortDescription, holdReason, nextAction, contactAttempts, daysWithoutUpdate);

        OperationalDiagnosis diagnosis = OperationalDiagnosis.builder()
                .ticketNumber(number)
                .shortDescription(shortDescription)
                .state(mapState(stateRaw))
                .priority(mapPriority(priorityRaw))
                .assignedTo(assignedTo)
                .caller(caller)
                .ageHours(ageHours)
                .daysWithoutUpdate(daysWithoutUpdate)
                .contactAttempts(contactAttempts)
                .stalled(stalled)
                .holdReason(holdReason)
                .nextAction(nextAction)
                .validationFlags(validationFlags)
                .build();

        DraftProposal proposal = DraftProposal.builder()
                .flowId("stalled-" + number.toLowerCase(Locale.ROOT))
                .draftVersion(1)
                .approvalState("PROPOSED")
                .channel("work_notes")
                .content(draft)
                .build();

        return StalledTicketResponse.builder()
                .success(true)
                .data(StalledTicketData.builder()
                        .diagnosis(diagnosis)
                        .draft(proposal)
                        .build())
                .build();
    }

    private String generateDraft(String ticketNumber,
                                 String shortDescription,
                                 String holdReason,
                                 String nextAction,
                                 int contactAttempts,
                                 long daysWithoutUpdate) {
        String prompt = """
                Eres un copiloto operativo de Mesa de Ayuda.
                Redacta un borrador breve en español para seguimiento de ticket estancado.
                Debe sonar como work note interna, máximo 90 palabras, tono profesional y accionable.
                Incluye:
                - referencia al ticket
                - diagnóstico breve
                - siguiente paso concreto
                - si aplica, mención de intentos de contacto
                
                CONTEXTO:
                Ticket: %s
                Descripción: %s
                Motivo de espera: %s
                Días sin actualizar: %d
                Intentos de contacto detectados: %d
                Siguiente acción sugerida: %s
                """.formatted(
                ticketNumber,
                shortDescription,
                holdReason,
                daysWithoutUpdate,
                contactAttempts,
                nextAction
        );

        try {
            return llmProvider.generate(prompt, 0.2, 140);
        } catch (Exception ex) {
            log.warn("Falling back to deterministic stalled draft for {}: {}", ticketNumber, ex.getMessage());
            return """
                    Seguimiento %s: ticket con diagnóstico de "%s". Se identifica %s y %d intento(s) de contacto registrados.
                    Siguiente paso recomendado: %s. Si no hay respuesta en la siguiente ventana operativa, escalar o cerrar por procedimiento según política vigente.
                    """.formatted(ticketNumber, shortDescription, holdReason, contactAttempts, nextAction).trim();
        }
    }

    private List<String> buildValidationFlags(boolean onHold,
                                              long daysWithoutUpdate,
                                              int contactAttempts,
                                              String priorityRaw) {
        List<String> flags = new ArrayList<>();

        if (onHold) {
            flags.add("ticket actualmente en estado On Hold");
        }
        if (daysWithoutUpdate >= 2) {
            flags.add(daysWithoutUpdate + " día(s) sin actualización");
        }
        if (contactAttempts >= 3) {
            flags.add("regla de 3 intentos potencialmente cumplida");
        } else {
            flags.add("faltan " + Math.max(0, 3 - contactAttempts) + " intento(s) para llegar a la regla de 3 contactos");
        }
        if ("1".equals(priorityRaw) || "2".equals(priorityRaw) || "Critical".equalsIgnoreCase(priorityRaw) || "High".equalsIgnoreCase(priorityRaw)) {
            flags.add("ticket de alta prioridad requiere seguimiento acelerado");
        }

        return flags;
    }

    private String buildNextAction(int contactAttempts, long daysWithoutUpdate, boolean onHold) {
        if (contactAttempts >= 3) {
            return "documentar último intento, validar escalamiento y preparar resolución por procedimiento si no hay respuesta";
        }
        if (onHold && daysWithoutUpdate >= 2) {
            return "emitir nuevo seguimiento hoy y dejar siguiente checkpoint comprometido";
        }
        return "actualizar contexto del ticket, contactar responsable y registrar siguiente paso";
    }

    private String inferHoldReason(String shortDescription, String description, String stateRaw) {
        String text = (shortDescription + " " + description).toLowerCase(Locale.ROOT);

        if (text.contains("espera") || text.contains("pendiente")) {
            return "dependencia pendiente de tercero o usuario";
        }
        if (text.contains("aprob")) {
            return "espera aprobación operativa";
        }
        if (text.contains("acceso") || text.contains("auth") || text.contains("autentic")) {
            return "validación de acceso o evidencia pendiente";
        }
        if (isOnHold(stateRaw)) {
            return "ticket detenido sin actualización reciente";
        }
        return "seguimiento pendiente por falta de movimiento reciente";
    }

    private int estimateContactAttempts(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        int score = 0;

        if (normalized.contains("llamada")) score++;
        if (normalized.contains("correo")) score++;
        if (normalized.contains("email")) score++;
        if (normalized.contains("contact")) score++;
        if (normalized.contains("seguimiento")) score++;

        return Math.min(score, 3);
    }

    private boolean isOnHold(String stateRaw) {
        return "3".equals(stateRaw) || "On Hold".equalsIgnoreCase(stateRaw);
    }

    private String mapState(String stateRaw) {
        return switch (stateRaw) {
            case "1" -> "New";
            case "2" -> "In Progress";
            case "3" -> "On Hold";
            case "6" -> "Resolved";
            case "7" -> "Closed";
            default -> stateRaw;
        };
    }

    private String mapPriority(String priorityRaw) {
        return switch (priorityRaw) {
            case "1" -> "Critical";
            case "2" -> "High";
            case "3" -> "Medium";
            case "4", "5" -> "Low";
            default -> priorityRaw;
        };
    }

    private String extractDisplayValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isObject()) {
            String display = node.path("display_value").asText("");
            if (!display.isBlank()) return display;
            String name = node.path("name").asText("");
            if (!name.isBlank()) return name;
            return node.path("value").asText("");
        }
        return node.asText("");
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private long hoursSince(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return 0;
        }

        try {
            OffsetDateTime timestamp;
            if (rawDate.contains("T")) {
                timestamp = OffsetDateTime.parse(rawDate.endsWith("Z") ? rawDate : rawDate + "Z");
            } else {
                timestamp = OffsetDateTime.of(
                        java.time.LocalDateTime.parse(rawDate, SERVICE_NOW_DATE),
                        ZoneOffset.UTC
                );
            }
            return Math.max(0, Duration.between(timestamp, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
        } catch (Exception ex) {
            log.debug("Unable to parse date '{}': {}", rawDate, ex.getMessage());
            return 0;
        }
    }

    private long daysSince(String rawDate) {
        return hoursSince(rawDate) / 24;
    }

    @Data
    @Builder
    public static class StalledTicketResponse {
        private boolean success;
        private StalledTicketData data;
    }

    @Data
    @Builder
    public static class StalledTicketData {
        private OperationalDiagnosis diagnosis;
        private DraftProposal draft;
    }

    @Data
    @Builder
    public static class OperationalDiagnosis {
        private String ticketNumber;
        private String shortDescription;
        private String state;
        private String priority;
        private String assignedTo;
        private String caller;
        private long ageHours;
        private long daysWithoutUpdate;
        private int contactAttempts;
        private boolean stalled;
        private String holdReason;
        private String nextAction;
        private List<String> validationFlags;
    }

    @Data
    @Builder
    public static class DraftProposal {
        private String flowId;
        private int draftVersion;
        private String approvalState;
        private String channel;
        private String content;
    }
}
