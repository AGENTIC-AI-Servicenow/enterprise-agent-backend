package com.enterprise.agent.client;

import org.springframework.http.HttpStatus;

/**
 * Exception representing an error response from ServiceNow API.
 * Keeps the original HTTP status code so controllers can propagate it properly.
 */
public class ServiceNowApiException extends RuntimeException {

    private final HttpStatus status;
    private final String operation;
    private final String responseBody;

    public ServiceNowApiException(HttpStatus status, String operation, String responseBody) {
        super(buildMessage(status, operation, responseBody));
        this.status = status;
        this.operation = operation;
        this.responseBody = responseBody;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getOperation() {
        return operation;
    }

    public String getResponseBody() {
        return responseBody;
    }

    private static String buildMessage(HttpStatus status, String operation, String responseBody) {
        String body = responseBody == null ? "" : responseBody;
        return "ServiceNow API error during " + operation + ": " + status + " - " + body;
    }
}
