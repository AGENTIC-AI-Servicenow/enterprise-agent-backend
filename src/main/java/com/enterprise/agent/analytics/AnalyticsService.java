package com.enterprise.agent.analytics;

import com.enterprise.agent.client.ServiceNowClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * AnalyticsService
 *
 * Ejecuta consultas analíticas estructuradas sin depender
 * de condicionales por frases específicas.
 */
@Service
public class AnalyticsService {

    private final ServiceNowClient serviceNowClient;

    public AnalyticsService(ServiceNowClient serviceNowClient) {
        this.serviceNowClient = serviceNowClient;
    }

    public Map<String, Object> execute(AnalyticsQuery query) {

        JsonNode data = serviceNowClient.getAllIncidents(
                null,
                null,
                null,
                query.getLimit() != null ? query.getLimit() : 200,
                0
        );

        JsonNode results = data.has("result")
                ? data.get("result")
                : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();

        List<JsonNode> filtered = new ArrayList<>();

        for (JsonNode node : results) {

            if (!matchesFilters(node, query.getFilters())) continue;
            if (!matchesDateRange(node, query.getDateRange())) continue;

            filtered.add(node);
        }

        Map<String, Object> response = new HashMap<>();

        if ("count".equalsIgnoreCase(query.getMetric())) {
            response.put("count", filtered.size());
        }

        if ("group".equalsIgnoreCase(query.getMetric()) && query.getGroupBy() != null) {

            Map<String, Integer> grouped = new HashMap<>();

            for (JsonNode node : filtered) {

                String key = extractGroupValue(node, query.getGroupBy());
                grouped.merge(key, 1, Integer::sum);
            }

            response.put("grouped", grouped);
        }

        if ("list".equalsIgnoreCase(query.getMetric())) {
            List<Map<String, String>> list = new ArrayList<>();

            for (JsonNode node : filtered) {
                Map<String, String> item = new HashMap<>();
                item.put("number", node.path("number").asText());
                item.put("short_description", node.path("short_description").asText());
                list.add(item);
            }

            response.put("list", list);
        }

        return response;
    }

    private boolean matchesFilters(JsonNode node, Map<String, String> filters) {

        if (filters == null || filters.isEmpty()) return true;

        for (Map.Entry<String, String> entry : filters.entrySet()) {

            String field = entry.getKey();
            String expected = entry.getValue();

            String actual = node.path(field).asText();

            if (!expected.equalsIgnoreCase(actual)) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesDateRange(JsonNode node, String range) {

        if (range == null) return true;

        String createdStr = node.path("sys_created_on").asText(null);
        if (createdStr == null) return true;

        java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        java.time.LocalDateTime localDateTime =
                java.time.LocalDateTime.parse(createdStr, formatter);

        OffsetDateTime created =
                localDateTime.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime();

        OffsetDateTime now = OffsetDateTime.now();

        switch (range) {

            case "today":
                return created.toLocalDate().equals(now.toLocalDate());

            case "yesterday":
                return created.toLocalDate().equals(now.minusDays(1).toLocalDate());

            case "last_week":
                OffsetDateTime start = now.minusWeeks(1);
                return created.isAfter(start);

            case "until_now":
                return created.isBefore(now);

            default:
                return true;
        }
    }

    private String extractGroupValue(JsonNode node, String groupBy) {

        if ("assigned_to".equalsIgnoreCase(groupBy)) {
            return node.path("assigned_to").path("display_value")
                    .asText(node.path("assigned_to").asText("Sin asignar"));
        }

        if ("category".equalsIgnoreCase(groupBy)) {
            return node.path("category").asText("N/A");
        }

        if ("state".equalsIgnoreCase(groupBy)) {
            return node.path("state").asText("N/A");
        }

        return "N/A";
    }
}
