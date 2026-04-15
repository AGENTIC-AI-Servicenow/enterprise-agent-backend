package com.enterprise.agent.controller;

import com.enterprise.agent.client.ServiceNowClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IncidentController {

    private final ServiceNowClient serviceNowClient;

    public IncidentController(ServiceNowClient serviceNowClient) {
        this.serviceNowClient = serviceNowClient;
    }

    // Endpoint controlado: buscar incidente por número exacto
    @GetMapping("/test/incident")
    public JsonNode getIncidentByNumber(@RequestParam String number) {
        return serviceNowClient.getIncidentByNumber(number);
    }
}
