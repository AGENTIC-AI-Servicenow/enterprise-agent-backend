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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Log4j2
public class BriefingService {

    private static final DateTimeFormatter SERVICE_NOW_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US);

    private final ServiceNowClient serviceNowClient;
    private final LLMProvider llmProvider;

    public BriefingService(ServiceNowClient serviceNowClient, LLMProvider llmProvider) {
        this.serviceNowClient = serviceNowClient;
        this.llmProvider = llmProvider;
    }

    public BriefingResponse buildBriefing(String technician) {
        JsonNode response = serviceNowClient.getAllIncidents(null, null, technician, 200, 0);
        JsonNode incidentsNode = response.path("result");

        List<BriefingTicket> openTickets = new ArrayList<>();
        List<BriefingTicket> attentionToday = new ArrayList<>();
        List<BriefingTicket> complianceWatch = new ArrayList<>();

        if (incidentsNode.isArray()) {
            for (JsonNode incident : incidentsNode) {
                BriefingTicket ticket = mapTicket(incident);
                if (!ticket.isOpen()) {
                    if (ticket.isComplianceWindow()) {
                        complianceWatch.add(ticket);
                    }
                    continue;
                }

                openTickets.add(ticket);
                if (ticket.isAttentionToday()) {
                    attentionToday.add(ticket);
                }
            }
        }

        attentionToday.sort(Comparator
                .comparing(BriefingTicket::getRiskRank)
                .reversed()
                .thenComparing(BriefingTicket::getDaysWithoutUpdate, Comparator.reverseOrder()));

        complianceWatch.sort(Comparator
                .comparing(BriefingTicket::getDaysSinceClosed, Comparator.reverseOrder()));

        List<BriefingTicket> topAttention = attentionToday.stream().limit(3).toList();
        List<String> patterns = detectPatterns(openTickets, complianceWatch);
        BriefingMetrics metrics = computeMetrics(openTickets, complianceWatch);

        String narrative = generateNarrative(metrics, topAttention, patterns);

        BriefingContext context = BriefingContext.builder()
                .technician(technician)
                .generatedAt(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .metrics(metrics)
                .attentionToday(topAttention)
                .complianceWatch(complianceWatch.stream().limit(3).toList())
                .patterns(patterns)
                .build();

        return BriefingResponse.builder()
                .summary(narrative)
                .context(context)
                .build();
    }

    private String generateNarrative(BriefingMetrics metrics,
                                     List<BriefingTicket> attentionToday,
                                     List<String> patterns) {
        String prompt = """
                Eres el copiloto de operación de la Mesa de Ayuda TI. Recibes un contexto calculado de forma determinística.
                Tu tarea es redactar un briefing matutino en español, máximo 120 palabras, con esta estructura exacta:
                1) Una línea de estado general (tono directo, sin saludos largos).
                2) "Atención hoy:" máximo 3 ítems, cada uno con número de ticket, motivo y la acción sugerida.
                3) Si hay patrones detectados, una línea final "Detecté: ...".
                No inventes tickets ni datos fuera del contexto.
                No uses viñetas anidadas.
                
                CONTEXTO:
                Tickets abiertos: %d
                Riesgo ANS: %d
                Estancados > 2 días sin actualizar: %d
                Conformidad por vencer: %d
                
                TICKETS A PRIORIZAR:
                %s
                
                PATRONES:
                %s
                """.formatted(
                metrics.getOpenTickets(),
                metrics.getSlaRiskTickets(),
                metrics.getStalledTickets(),
                metrics.getPendingComplianceTickets(),
                attentionToday.isEmpty() ? "- Sin tickets priorizados" : attentionToday.stream()
                        .map(ticket -> "- %s | %s | acción: %s".formatted(
                                ticket.getNumber(),
                                ticket.getReason(),
                                ticket.getSuggestedAction()))
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("- Sin tickets priorizados"),
                patterns.isEmpty() ? "- Sin patrones relevantes" : String.join("\n", patterns.stream()
                        .map(pattern -> "- " + pattern)
                        .toList()));

        try {
            return llmProvider.generate(prompt, 0.2, 220);
        } catch (Exception ex) {
            log.warn("Falling back to deterministic briefing narrative: {}", ex.getMessage());
            return buildFallbackNarrative(metrics, attentionToday, patterns);
        }
    }

    private String buildFallbackNarrative(BriefingMetrics metrics,
                                          List<BriefingTicket> attentionToday,
                                          List<String> patterns) {
        StringBuilder builder = new StringBuilder();
        builder.append("Cola activa con ")
                .append(metrics.getOpenTickets())
                .append(" tickets abiertos; ")
                .append(metrics.getSlaRiskTickets())
                .append(" requieren atención por riesgo de ANS.\n");
        builder.append("Atención hoy:\n");

        if (attentionToday.isEmpty()) {
            builder.append("- Sin tickets críticos inmediatos; revisa conformidades y tickets sin actualizar.\n");
        } else {
            for (BriefingTicket ticket : attentionToday) {
                builder.append("- ")
                        .append(ticket.getNumber())
                        .append(": ")
                        .append(ticket.getReason())
                        .append(". Acción: ")
                        .append(ticket.getSuggestedAction())
                        .append("\n");
            }
        }

        if (!patterns.isEmpty()) {
            builder.append("Detecté: ")
                    .append(String.join(" | ", patterns));
        }

        return builder.toString().trim();
    }

    private BriefingMetrics computeMetrics(List<BriefingTicket> openTickets, List<BriefingTicket> complianceWatch) {
        int slaRisk = (int) openTickets.stream().filter(BriefingTicket::isSlaRisk).count();
        int stalled = (int) openTickets.stream().filter(ticket -> ticket.getDaysWithoutUpdate() >= 2).count();

        return BriefingMetrics.builder()
                .openTickets(openTickets.size())
                .slaRiskTickets(slaRisk)
                .stalledTickets(stalled)
                .pendingComplianceTickets(complianceWatch.size())
                .build();
    }

    private List<String> detectPatterns(List<BriefingTicket> openTickets, List<BriefingTicket> complianceWatch) {
        List<String> patterns = new ArrayList<>();

        long auth = openTickets.stream()
                .filter(ticket -> ticket.getTopic().contains("autentic") || ticket.getTopic().contains("access"))
                .count();
        long payments = openTickets.stream()
                .filter(ticket -> ticket.getTopic().contains("pago") || ticket.getTopic().contains("payment"))
                .count();
        long stale = openTickets.stream()
                .filter(ticket -> ticket.getDaysWithoutUpdate() >= 2)
                .count();

        if (auth >= 2) {
            patterns.add("se repiten incidencias ligadas a autenticación/accesos");
        }
        if (payments >= 2) {
            patterns.add("hay concentración de incidentes vinculados a pagos");
        }
        if (stale >= 2) {
            patterns.add("varios tickets llevan más de 48h sin actualización");
        }
        if (!complianceWatch.isEmpty()) {
            patterns.add("hay cierres con conformidad próxima a vencer");
        }

        return patterns;
    }

    private BriefingTicket mapTicket(JsonNode incident) {
        String number = incident.path("number").asText("");
        String shortDescription = incident.path("short_description").asText("");
        String stateRaw = incident.path("state").asText("");
        String priorityRaw = incident.path("priority").asText("");
        String openedRaw = firstNonBlank(
                incident.path("opened_at").asText(""),
                incident.path("sys_created_on").asText(""));
        String updatedRaw = incident.path("sys_updated_on").asText("");
        String closedRaw = incident.path("closed_at").asText("");
        String topic = (shortDescription + " " + incident.path("description").asText("")).toLowerCase(Locale.ROOT);

        long ageHours = hoursSince(openedRaw);
        long daysWithoutUpdate = daysSince(updatedRaw);
        long daysSinceClosed = daysSince(closedRaw);

        boolean open = isOpenState(stateRaw);
        boolean slaRisk = open && computeSlaRisk(priorityRaw, ageHours);
        boolean complianceWindow = !open && daysSinceClosed >= 13 && daysSinceClosed <= 15;

        String reason;
        String suggestedAction;

        if (complianceWindow) {
            reason = "conformidad por vencer en %d día(s)".formatted(Math.max(0, 15 - daysSinceClosed));
            suggestedAction = "confirmar con usuario final o cerrar por procedimiento";
        } else if (slaRisk) {
            reason = "riesgo ANS por %d hora(s) de antigüedad".formatted(ageHours);
            suggestedAction = "actualizar estado y priorizar resolución";
        } else if (daysWithoutUpdate >= 2) {
            reason = "%d día(s) sin actualización".formatted(daysWithoutUpdate);
            suggestedAction = "contactar responsable y destrabar siguiente paso";
        } else {
            reason = "seguimiento operativo estándar";
            suggestedAction = "mantener monitoreo";
        }

        int riskRank = computeRiskRank(priorityRaw, ageHours, daysWithoutUpdate, topic, complianceWindow);

        return BriefingTicket.builder()
                .number(number)
                .shortDescription(shortDescription)
                .state(mapState(stateRaw))
                .priority(mapPriority(priorityRaw))
                .ageHours(ageHours)
                .daysWithoutUpdate(daysWithoutUpdate)
                .daysSinceClosed(daysSinceClosed)
                .slaRisk(slaRisk)
                .complianceWindow(complianceWindow)
                .reason(reason)
                .suggestedAction(suggestedAction)
                .riskRank(riskRank)
                .topic(topic)
                .build();
    }

    private int computeRiskRank(String priorityRaw,
                                long ageHours,
                                long daysWithoutUpdate,
                                String topic,
                                boolean complianceWindow) {
        int score = 0;

        score += switch (priorityRaw) {
            case "1", "Critical" -> 50;
            case "2", "High" -> 35;
            case "3", "Medium" -> 20;
            default -> 10;
        };

        if (ageHours >= 8) score += 20;
        if (daysWithoutUpdate >= 2) score += 15;
        if (daysWithoutUpdate >= 4) score += 10;
        if (complianceWindow) score += 12;
        if (topic.contains("pago") || topic.contains("payment")) score += 15;
        if (topic.contains("autentic") || topic.contains("auth")) score += 15;
        if (topic.contains("producci")) score += 10;

        return score;
    }

    private boolean computeSlaRisk(String priorityRaw, long ageHours) {
        long thresholdHours = switch (priorityRaw) {
            case "1", "Critical" -> 2;
            case "2", "High" -> 4;
            case "3", "Medium" -> 8;
            default -> 16;
        };

        return ageHours >= thresholdHours;
    }

    private boolean isOpenState(String stateRaw) {
        return "1".equals(stateRaw)
                || "2".equals(stateRaw)
                || "3".equals(stateRaw)
                || "New".equalsIgnoreCase(stateRaw)
                || "In Progress".equalsIgnoreCase(stateRaw)
                || "On Hold".equalsIgnoreCase(stateRaw);
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
                        ZoneOffset.UTC);
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
    public static class BriefingResponse {
        private String summary;
        private BriefingContext context;
    }

    @Data
    @Builder
    public static class BriefingContext {
        private String technician;
        private String generatedAt;
        private BriefingMetrics metrics;
        private List<BriefingTicket> attentionToday;
        private List<BriefingTicket> complianceWatch;
        private List<String> patterns;
    }

    @Data
    @Builder
    public static class BriefingMetrics {
        private int openTickets;
        private int slaRiskTickets;
        private int stalledTickets;
        private int pendingComplianceTickets;
    }

    @Data
    @Builder
    public static class BriefingTicket {
        private String number;
        private String shortDescription;
        private String state;
        private String priority;
        private long ageHours;
        private long daysWithoutUpdate;
        private long daysSinceClosed;
        private boolean slaRisk;
        private boolean complianceWindow;
        private String reason;
        private String suggestedAction;
        private int riskRank;
        private String topic;

        public boolean isOpen() {
            return "New".equalsIgnoreCase(state)
                    || "In Progress".equalsIgnoreCase(state)
                    || "On Hold".equalsIgnoreCase(state);
        }

        public boolean isAttentionToday() {
            return slaRisk || daysWithoutUpdate >= 2;
        }
    }
}
