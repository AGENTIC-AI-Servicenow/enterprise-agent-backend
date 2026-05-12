package com.enterprise.agent.controller;

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
            
            // Build response
            ObjectNode response = objectMapper.createObjectNode();
            response.set("result", incidents);
            response.put("total", incidents != null ? incidents.size() : 0);
            response.put("limit", limit);
            response.put("offset", offset);
            response.put("mock_data", false);
            
            logger.info("Retrieved {} incidents from ServiceNow", incidents != null ? incidents.size() : 0);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error fetching incidents: {}", e.getMessage(), e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "Failed to fetch incidents");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
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
            ObjectNode stats = objectMapper.createObjectNode();
            
            // Mock statistics for MVP
            stats.put("total", 145);
            stats.put("total_change", "+12%");
            stats.put("open", 38);
            stats.put("open_change", "-5%");
            stats.put("resolved", 107);
            stats.put("resolved_change", "+8%");
            stats.put("high_priority", 12);
            stats.put("high_priority_critical", 2);
            stats.put("mock_data", true);
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            logger.error("Error fetching incident summary: {}", e.getMessage(), e);
            ObjectNode error = objectMapper.createObjectNode();
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
        
        try {
            ObjectNode stats = objectMapper.createObjectNode();
            
            // Mock statistics for MVP
            stats.put("total", 145);
            stats.put("total_change", "+12%");
            stats.put("open", 38);
            stats.put("open_change", "-5%");
            stats.put("resolved", 107);
            stats.put("resolved_change", "+8%");
            stats.put("high_priority", 12);
            stats.put("high_priority_critical", 2);
            stats.put("mock_data", true);
            
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            logger.error("Error fetching incident stats: {}", e.getMessage(), e);
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "Failed to fetch incident statistics");
            error.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
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
     * Helper method to create mock incident data for MVP testing.
     */
    private ObjectNode createMockIncident(
            String number,
            String shortDescription,
            String priority,
            String state,
            String assignedTo,
            String category,
            String createdAt
    ) {
        ObjectNode incident = objectMapper.createObjectNode();
        incident.put("sys_id", "mock_" + number.toLowerCase());
        incident.put("number", number);
        incident.put("short_description", shortDescription);
        incident.put("priority", priority);
        incident.put("state", state);
        incident.put("assigned_to", assignedTo);
        incident.put("category", category);
        incident.put("sys_created_on", createdAt);
        incident.put("sys_updated_on", createdAt);
        
        return incident;
    }
}
