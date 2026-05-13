package com.enterprise.agent.controller;

import com.enterprise.agent.client.ServiceNowApiException;
import com.enterprise.agent.client.ServiceNowClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for ServiceNow Incident Management.
 * 
 * Provides endpoints for:
 * - Listing incidents with filtering and pagination
 * - Getting individual incident details
 * - Incident statistics and metrics
 * 
 * @author Enterprise Agent Team
 */
@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private static final Logger logger = LoggerFactory.getLogger(IncidentController.class);
    private final ServiceNowClient serviceNowClient;
    private final ObjectMapper objectMapper;

    public IncidentController(ServiceNowClient serviceNowClient, ObjectMapper objectMapper) {
        this.serviceNowClient = serviceNowClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Get list of incidents with optional filtering.
     * 
     * MVP Mode: Returns mock data when ServiceNow integration is not available.
     * Production: Will fetch real data from ServiceNow API.
     * 
     * Query Parameters:
     * - state: Filter by incident state (new, in_progress, resolved, closed)
     * - priority: Filter by priority (1-5)
     * - assigned_to: Filter by assignee
     * - limit: Number of results (default: 50)
     * - offset: Pagination offset (default: 0)
     */
    @GetMapping
    public ResponseEntity<?> getIncidents(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) String assigned_to,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        logger.info("GET /api/incidents - state={}, priority={}, limit={}, offset={}", 
                    state, priority, limit, offset);
        
        try {
            // Get real incidents from ServiceNow
            JsonNode serviceNowResponse = serviceNowClient.getAllIncidents(
                state, priority, assigned_to, limit, offset
            );

            // Extract result array
            JsonNode incidents = serviceNowResponse.get("result");
            int total = incidents != null ? incidents.size() : 0;

            // Build ApiResponse-compatible wrapper { success, data, total, limit, offset }
            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.set("data", incidents);
            response.put("total", total);
            response.put("limit", limit);
            response.put("offset", offset);
            response.put("mock_data", false);

            logger.info("Retrieved {} incidents from ServiceNow", total);
            return ResponseEntity.ok(response);

        } catch (ServiceNowApiException e) {
            logger.error("ServiceNow API error while retrieving incidents", e);

            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "servicenow_error");
            error.put("message", e.getMessage());
            error.put("operation", e.getOperation());
            error.put("status", e.getStatus().value());
            error.put("mock_data", false);

            return ResponseEntity.status(e.getStatus()).body(error);

        } catch (Exception e) {
            // IMPORTANT: do not hide failures by returning mock data.
            // The frontend must show the real status (401/403/connectivity errors)
            // so we can properly validate OAuth and roles.
            logger.error("Failed to retrieve incidents from ServiceNow", e);

            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "servicenow_unavailable");
            error.put("message", e.getMessage());
            error.put("mock_data", false);

            return ResponseEntity.status(502).body(error);
        }
    }

    /**
     * Get incident summary/statistics for dashboard metrics.
     * 
     * Returns:
     * - Total incidents count
     * - Open incidents count
     * - Resolved incidents count
     * - High priority incidents count
     * - Trends and percentages
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getIncidentSummary() {
        logger.info("GET /api/incidents/summary");
        
        try {
            // Fetch real incidents from ServiceNow (limited) and compute summary
            JsonNode serviceNowResponse = serviceNowClient.getAllIncidents(
                    null, null, null, 200, 0
            );

            JsonNode incidentsNode = serviceNowResponse.get("result");
            if (incidentsNode == null || !incidentsNode.isArray()) {
                throw new IllegalStateException("ServiceNow response does not contain a result array");
            }

            int total = incidentsNode.size();

            // Compute counts based on ServiceNow numeric state/priority when possible.
            // ServiceNow incident.state is usually numeric:
            // 1=New, 2=In Progress, 3=On Hold, 6=Resolved, 7=Closed
            ObjectNode byState = objectMapper.createObjectNode();
            byState.put("New", 0);
            byState.put("In Progress", 0);
            byState.put("On Hold", 0);
            byState.put("Resolved", 0);
            byState.put("Closed", 0);

            // ServiceNow incident.priority is usually numeric:
            // 1=Critical, 2=High, 3=Moderate/Medium, 4=Low, 5=Planning (map to Low)
            ObjectNode byPriority = objectMapper.createObjectNode();
            byPriority.put("Critical", 0);
            byPriority.put("High", 0);
            byPriority.put("Medium", 0);
            byPriority.put("Low", 0);

            for (JsonNode inc : incidentsNode) {
                String stateRaw = inc.path("state").asText("");
                switch (stateRaw) {
                    case "1" -> byState.put("New", byState.path("New").asInt() + 1);
                    case "2" -> byState.put("In Progress", byState.path("In Progress").asInt() + 1);
                    case "3" -> byState.put("On Hold", byState.path("On Hold").asInt() + 1);
                    case "6" -> byState.put("Resolved", byState.path("Resolved").asInt() + 1);
                    case "7" -> byState.put("Closed", byState.path("Closed").asInt() + 1);
                    default -> { /* ignore other states */ }
                }

                String priorityRaw = inc.path("priority").asText("");
                switch (priorityRaw) {
                    case "1" -> byPriority.put("Critical", byPriority.path("Critical").asInt() + 1);
                    case "2" -> byPriority.put("High", byPriority.path("High").asInt() + 1);
                    case "3" -> byPriority.put("Medium", byPriority.path("Medium").asInt() + 1);
                    case "4", "5" -> byPriority.put("Low", byPriority.path("Low").asInt() + 1);
                    default -> { /* ignore */ }
                }
            }

            int openIncidents = byState.path("New").asInt()
                    + byState.path("In Progress").asInt()
                    + byState.path("On Hold").asInt();

            ObjectNode summaryData = objectMapper.createObjectNode();
            summaryData.put("total", total);
            summaryData.put("openIncidents", openIncidents);
            summaryData.put("avgResolutionTime", 0); // TODO compute with resolved_at/opened_at if needed
            summaryData.set("byState", byState);
            summaryData.set("byPriority", byPriority);

            ObjectNode response = objectMapper.createObjectNode();
            response.put("success", true);
            response.set("data", summaryData);
            response.put("mock_data", false);

            return ResponseEntity.ok(response);

        } catch (ServiceNowApiException e) {
            logger.error("ServiceNow API error fetching incident summary", e);

            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "servicenow_error");
            error.put("message", e.getMessage());
            error.put("operation", e.getOperation());
            error.put("status", e.getStatus().value());

            return ResponseEntity.status(e.getStatus()).body(error);

        } catch (Exception e) {
            logger.error("Error fetching incident summary from ServiceNow: {}", e.getMessage(), e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("success", false);
            error.put("error", "Failed to fetch incident summary");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Alias endpoint for backward compatibility.
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getIncidentStats() {
        logger.info("GET /api/incidents/stats");
        // Delegate to summary for consistent response format
        return getIncidentSummary();
    }

    /**
     * Get incident by incident number (e.g. INC0010197) or sys_id.
     * 
     * - If the id matches the pattern INC/RITM/CHG + digits, it queries by number.
     * - Otherwise it is treated as a sys_id and queries the record directly.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getIncidentById(@PathVariable String id) {
        logger.info("GET /api/incidents/{}", id);

        try {
            JsonNode serviceNowResponse;
            JsonNode incident;

            if (id.matches("(?i)^(INC|RITM|CHG|PRB|TASK)\\d+$")) {
                // Query by incident number → response is { "result": [ {...} ] }
                serviceNowResponse = serviceNowClient.getIncidentByNumber(id.toUpperCase());
                JsonNode resultArray = serviceNowResponse.get("result");

                if (resultArray == null || !resultArray.isArray() || resultArray.isEmpty()) {
                    ObjectNode notFound = objectMapper.createObjectNode();
                    notFound.put("error", "not_found");
                    notFound.put("message", "No incident found with number: " + id.toUpperCase());
                    return ResponseEntity.status(404).body(notFound);
                }
                incident = resultArray.get(0);
            } else {
                // Query by sys_id → response is { "result": { ... } }
                serviceNowResponse = serviceNowClient.getIncidentBySysId(id);
                incident = serviceNowResponse.get("result");

                if (incident == null || incident.isNull()) {
                    ObjectNode notFound = objectMapper.createObjectNode();
                    notFound.put("error", "not_found");
                    notFound.put("message", "No incident found with sys_id: " + id);
                    return ResponseEntity.status(404).body(notFound);
                }
            }

            logger.info("Successfully retrieved incident: {}", id);
            return ResponseEntity.ok(incident);

        } catch (ServiceNowApiException e) {
            logger.error("ServiceNow API error fetching incident {}", id, e);

            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "servicenow_error");
            error.put("message", e.getMessage());
            error.put("operation", e.getOperation());
            error.put("status", e.getStatus().value());

            return ResponseEntity.status(e.getStatus()).body(error);

        } catch (Exception e) {
            logger.error("Error fetching incident {}: {}", id, e.getMessage(), e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "Failed to fetch incident");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    // Legacy endpoint - kept for backward compatibility
    @GetMapping("/test/incident")
    public JsonNode getIncidentByNumber(@RequestParam String number) {
        return serviceNowClient.getIncidentByNumber(number);
    }

    /**
     * Builds a full mock incidents response with realistic data for MVP/fallback mode.
     * Returns ApiResponse-compatible format: { success, data: [...], total, limit, offset }
     * All field names and enum values match the frontend Incident type exactly.
     */
    private ObjectNode buildMockIncidentsResponse(int limit, int offset, String stateFilter, Integer priorityFilter) {

        // priority: "Critical"|"High"|"Medium"|"Low"
        // state:    "New"|"In Progress"|"On Hold"|"Resolved"|"Closed"
        // impact / urgency: "High"|"Medium"|"Low"
        record MockRow(String number, String desc, String priority, String state,
                       String assigned, String category, String impact, String urgency) {}

        List<MockRow> mockData = List.of(
            new MockRow("INC0010001","Unable to login to VPN","Critical","New","John Smith","Network","High","High"),
            new MockRow("INC0010002","Email server not responding","Critical","In Progress","Maria Garcia","Email","High","High"),
            new MockRow("INC0010003","Laptop screen flickering issue","Medium","In Progress","Carlos Lopez","Hardware","Medium","Medium"),
            new MockRow("INC0010004","Printer offline in 3rd floor","Low","New","Ana Martinez","Hardware","Low","Low"),
            new MockRow("INC0010005","ERP system slow performance","High","In Progress","Luis Torres","Software","High","Medium"),
            new MockRow("INC0010006","Password reset request","Low","Resolved","Pedro Ramirez","Access","Low","Low"),
            new MockRow("INC0010007","Network connectivity drop in office B","High","New","Sofia Chen","Network","High","High"),
            new MockRow("INC0010008","CRM integration failing","Critical","In Progress","David Kim","Software","High","High"),
            new MockRow("INC0010009","Monitor not detected after update","Medium","Resolved","Emma Wilson","Hardware","Medium","Low"),
            new MockRow("INC0010010","Teams call quality degraded","Medium","New","Oliver Brown","Communication","Medium","Medium")
        );

        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        ArrayNode allIncidents = objectMapper.createArrayNode();

        for (MockRow row : mockData) {
            // Apply filters (match frontend state values)
            if (stateFilter != null && !stateFilter.isBlank()
                    && !stateFilter.equalsIgnoreCase(row.state())) {
                continue;
            }
            if (priorityFilter != null) {
                String[] pMap = {"", "Critical", "High", "Medium", "Low", "Low"};
                String mappedPriority = (priorityFilter >= 1 && priorityFilter <= 5) ? pMap[priorityFilter] : "";
                if (!mappedPriority.equalsIgnoreCase(row.priority())) {
                    continue;
                }
            }

            ObjectNode incident = objectMapper.createObjectNode();
            incident.put("sys_id",            "mock_" + row.number().toLowerCase());
            incident.put("number",             row.number());
            incident.put("short_description",  row.desc());
            incident.put("description",        "Detailed description for " + row.desc());
            incident.put("priority",           row.priority());
            incident.put("state",              row.state());
            incident.put("impact",             row.impact());
            incident.put("urgency",            row.urgency());
            incident.put("assigned_to",        row.assigned());
            incident.put("assignment_group",   "IT Support");
            incident.put("caller_id",          "end.user@company.com");
            incident.put("category",           row.category());
            incident.put("subcategory",        "General");
            incident.put("opened_at",          now);
            incident.put("updated_at",         now);
            allIncidents.add(incident);
        }

        // Apply pagination
        int total = allIncidents.size();
        ArrayNode paginated = objectMapper.createArrayNode();
        for (int i = offset; i < Math.min(offset + limit, total); i++) {
            paginated.add(allIncidents.get(i));
        }

        // Return ApiResponse-compatible format expected by the frontend
        ObjectNode response = objectMapper.createObjectNode();
        response.put("success", true);
        response.set("data", paginated);
        response.put("total", total);
        response.put("limit", limit);
        response.put("offset", offset);
        response.put("mock_data", true);
        return response;
    }
}
