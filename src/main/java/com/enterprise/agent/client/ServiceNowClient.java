package com.enterprise.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * ServiceNow REST API Client using OAuth 2.0 authentication only.
 * All Basic Authentication has been removed for security compliance.
 */
@Service
public class ServiceNowClient {

    private static final Logger logger = LoggerFactory.getLogger(ServiceNowClient.class);
    
    private final WebClient webClient;

    public ServiceNowClient(@Qualifier("serviceNowWebClient") WebClient serviceNowWebClient) {
        this.webClient = serviceNowWebClient;
    }

    /**
     * Gets the current authenticated user information.
     * Used to validate OAuth authentication and confirm user is 'smartiso'.
     */
    public JsonNode getCurrentUser() {
        logger.info("Retrieving current authenticated user information");
        
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                                .path("/api/now/v1/table/sys_user")
                            .queryParam("sysparm_limit", "1")
                            .queryParam("sysparm_fields", "sys_id,user_name,first_name,last_name,email")
                            .build())
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> handleClientError(response, "get current user")
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> handleServerError(response, "get current user")
                    )
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isRetryableException))
                    .block();
                    
        } catch (Exception e) {
            logger.error("Failed to retrieve current user", e);
            throw new RuntimeException("Failed to retrieve current user: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves an incident by sys_id using the Table API direct record access.
     */
    public JsonNode getIncidentBySysId(String sysId) {
        logger.info("Retrieving incident by sys_id: {}", sysId);

        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/now/v1/table/incident/" + sysId)
                            .queryParam("sysparm_fields",
                                       "sys_id,number,short_description,description,state,priority," +
                                       "caller_id,assigned_to,category,urgency,impact," +
                                       "sys_created_on,sys_updated_on")
                            .build())
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> handleClientError(response, "get incident by sys_id " + sysId)
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> handleServerError(response, "get incident by sys_id " + sysId)
                    )
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isRetryableException))
                    .block();

        } catch (Exception e) {
            logger.error("Failed to retrieve incident by sys_id {}", sysId, e);
            throw new RuntimeException("Failed to retrieve incident by sys_id " + sysId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves an incident by number using OAuth authentication.
     */
    public JsonNode getIncidentByNumber(String number) {
        logger.info("Retrieving incident by number: {}", number);
        
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/now/v1/table/incident")
                            .queryParam("sysparm_query", "number=" + number)
                            .queryParam("sysparm_fields", 
                                       "number,short_description,state,priority,urgency,caller_id,assigned_to,opened_at,sys_created_on")
                            .queryParam("sysparm_display_value", "true")
                            .queryParam("sysparm_limit", "1")
                            .build())
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> handleClientError(response, "get incident " + number)
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> handleServerError(response, "get incident " + number)
                    )
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isRetryableException))
                    .block();
                    
        } catch (Exception e) {
            logger.error("Failed to retrieve incident {}", number, e);
            throw new RuntimeException("Failed to retrieve incident " + number + ": " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all incidents with optional filtering and pagination.
     * For admin/reporting use cases where caller filtering is not needed.
     * 
     * @param state Filter by state (optional)
     * @param priority Filter by priority (optional)
     * @param assignedTo Filter by assigned_to (optional)
     * @param limit Maximum number of results
     * @param offset Pagination offset
     * @return JsonNode with incident list
     */
    public JsonNode getAllIncidents(String state, Integer priority, String assignedTo, 
                                    int limit, int offset) {
        logger.info("Retrieving all incidents - state={}, priority={}, limit={}, offset={}", 
                    state, priority, limit, offset);
        
        try {
            StringBuilder queryBuilder = new StringBuilder();
            
            // Build query string with filters
            if (state != null && !state.isEmpty()) {
                queryBuilder.append("state=").append(state);
            }
            if (priority != null) {
                if (queryBuilder.length() > 0) queryBuilder.append("^");
                queryBuilder.append("priority=").append(priority);
            }
            if (assignedTo != null && !assignedTo.isEmpty()) {
                if (queryBuilder.length() > 0) queryBuilder.append("^");
                queryBuilder.append("assigned_to.user_name=").append(assignedTo);
            }
            
            String query = queryBuilder.length() > 0 ? queryBuilder.toString() : null;
            
            var uriSpec = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/api/now/v1/table/incident")
                                .queryParam("sysparm_fields", 
                                           "sys_id,number,short_description,state,priority," +
                                           "assigned_to.user_name,category,sys_created_on,sys_updated_on," +
                                           "caller_id.name,urgency,impact")
                                .queryParam("sysparm_limit", limit)
                                .queryParam("sysparm_offset", offset)
                                .queryParam("sysparm_order_by", "sys_created_on DESC");
                        
                        if (query != null) {
                            builder.queryParam("sysparm_query", query);
                        }
                        
                        return builder.build();
                    });
            
            return uriSpec
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> handleClientError(response, "get all incidents")
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> handleServerError(response, "get all incidents")
                    )
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isRetryableException))
                    .block();
                    
        } catch (Exception e) {
            logger.error("Failed to retrieve all incidents", e);
            throw new RuntimeException("Failed to retrieve all incidents: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves incidents filtered by caller (for security).
     */
    public JsonNode getIncidentsByCallerId(String callerSysId) {
        logger.info("Retrieving incidents for caller: {}", callerSysId);
        
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/now/v1/table/incident")
                            .queryParam("sysparm_query", "caller_id=" + callerSysId)
                            .queryParam("sysparm_fields", 
                                       "number,short_description,state,priority,sys_created_on")
                            .queryParam("sysparm_limit", "10")
                            .queryParam("sysparm_order_by", "sys_created_on DESC")
                            .build())
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> handleClientError(response, "get incidents for caller " + callerSysId)
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> handleServerError(response, "get incidents for caller " + callerSysId)
                    )
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isRetryableException))
                    .block();
                    
        } catch (Exception e) {
            logger.error("Failed to retrieve incidents for caller {}", callerSysId, e);
            throw new RuntimeException("Failed to retrieve incidents for caller: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves a specific incident if it belongs to the specified caller.
     */
    public JsonNode getIncidentSecureByNumberAndCaller(String number, String callerSysId) {
        logger.info("Retrieving incident {} for caller {}", number, callerSysId);
        
        try {
            String query = "number=" + number + "^caller_id=" + callerSysId;
            
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/now/v1/table/incident")
                            .queryParam("sysparm_query", query)
                            .queryParam("sysparm_fields", 
                                       "number,short_description,description,state,priority,caller_id,assigned_to,sys_created_on")
                            .queryParam("sysparm_limit", "1")
                            .build())
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> handleClientError(response, "get secure incident " + number)
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> handleServerError(response, "get secure incident " + number)
                    )
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isRetryableException))
                    .block();
                    
        } catch (Exception e) {
            logger.error("Failed to retrieve secure incident {} for caller {}", number, callerSysId, e);
            throw new RuntimeException("Failed to retrieve secure incident: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new incident using OAuth authentication.
     */
    public JsonNode createIncident(String shortDescription, String description, 
                                   String priority, String callerSysId) {
        logger.info("Creating new incident for caller: {}", callerSysId);
        
        try {
            Map<String, Object> incidentData = new HashMap<>();
            incidentData.put("short_description", shortDescription);
            incidentData.put("description", description);
            incidentData.put("priority", priority);
            incidentData.put("caller_id", callerSysId);
            incidentData.put("state", "1"); // New
            
            return webClient.post()
                    .uri("/api/now/v1/table/incident")
                    .bodyValue(incidentData)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> handleClientError(response, "create incident")
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> handleServerError(response, "create incident")
                    )
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isRetryableException))
                    .block();
                    
        } catch (Exception e) {
            logger.error("Failed to create incident", e);
            throw new RuntimeException("Failed to create incident: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing incident.
     */
    public JsonNode updateIncident(String incidentSysId, Map<String, Object> updateData) {
        logger.info("Updating incident: {}", incidentSysId);
        
        try {
            return webClient.put()
                    .uri("/api/now/v1/table/incident/" + incidentSysId)
                    .bodyValue(updateData)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> handleClientError(response, "update incident " + incidentSysId)
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> handleServerError(response, "update incident " + incidentSysId)
                    )
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isRetryableException))
                    .block();
                    
        } catch (Exception e) {
            logger.error("Failed to update incident {}", incidentSysId, e);
            throw new RuntimeException("Failed to update incident: " + e.getMessage(), e);
        }
    }

    /**
     * Searches users by username or email.
     */
    public JsonNode searchUsers(String searchTerm) {
        logger.info("Searching users with term: {}", searchTerm);
        
        try {
            String query = "user_nameLIKE" + searchTerm + "^ORemailLIKE" + searchTerm;
            
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/now/v1/table/sys_user")
                            .queryParam("sysparm_query", query)
                            .queryParam("sysparm_fields", "sys_id,user_name,email,first_name,last_name")
                            .queryParam("sysparm_limit", "10")
                            .build())
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError(),
                            response -> handleClientError(response, "search users")
                    )
                    .onStatus(
                            status -> status.is5xxServerError(),
                            response -> handleServerError(response, "search users")
                    )
                    .bodyToMono(JsonNode.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                            .filter(this::isRetryableException))
                    .block();
                    
        } catch (Exception e) {
            logger.error("Failed to search users", e);
            throw new RuntimeException("Failed to search users: " + e.getMessage(), e);
        }
    }

    /**
     * Handles 4xx client errors.
     */
    private Mono<Throwable> handleClientError(org.springframework.web.reactive.function.client.ClientResponse response, String operation) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(errorBody -> {
                    HttpStatus status = HttpStatus.resolve(response.statusCode().value());
                    if (status == null) {
                        status = HttpStatus.BAD_GATEWAY;
                    }

                    logger.error("Client error during {}: {} - {}", operation, status, errorBody);
                    return new ServiceNowApiException(status, operation, errorBody);
                });
    }

    /**
     * Handles 5xx server errors.
     */
    private Mono<Throwable> handleServerError(org.springframework.web.reactive.function.client.ClientResponse response, String operation) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(errorBody -> {
                    HttpStatus status = HttpStatus.resolve(response.statusCode().value());
                    if (status == null) {
                        status = HttpStatus.BAD_GATEWAY;
                    }

                    logger.error("Server error during {}: {} - {}", operation, status, errorBody);
                    return new ServiceNowApiException(status, operation, errorBody);
                });
    }

    /**
     * Determines if an exception is retryable.
     */
    private boolean isRetryableException(Throwable throwable) {
        return throwable instanceof WebClientResponseException.InternalServerError ||
               throwable instanceof WebClientResponseException.BadGateway ||
               throwable instanceof WebClientResponseException.ServiceUnavailable ||
               throwable instanceof WebClientResponseException.GatewayTimeout;
    }
}
