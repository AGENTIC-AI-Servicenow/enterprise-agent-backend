package com.enterprise.agent.controller;

import com.enterprise.agent.service.StalledTicketService;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@Log4j2
public class StalledTicketController {

    private final StalledTicketService stalledTicketService;

    public StalledTicketController(StalledTicketService stalledTicketService) {
        this.stalledTicketService = stalledTicketService;
    }

    @GetMapping("/stalled/{ticketNumber}")
    public ResponseEntity<?> getStalledTicketAnalysis(@PathVariable String ticketNumber) {
        try {
            return ResponseEntity.ok(stalledTicketService.analyzeTicket(ticketNumber.toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        } catch (Exception ex) {
            log.error("Failed to analyze stalled ticket {}", ticketNumber, ex);
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "degraded", true,
                    "message", "No pude analizar el ticket en tiempo real. Revisa conectividad con ServiceNow o usa un ticket visible cargado en sesión."
            ));
        }
    }
}
