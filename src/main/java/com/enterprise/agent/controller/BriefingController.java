package com.enterprise.agent.controller;

import com.enterprise.agent.service.BriefingService;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@Log4j2
public class BriefingController {

    private final BriefingService briefingService;

    public BriefingController(BriefingService briefingService) {
        this.briefingService = briefingService;
    }

    @GetMapping("/briefing")
    public ResponseEntity<?> getBriefing(
            @RequestParam(required = false) String technician,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader
    ) {
        String effectiveTechnician = technician != null && !technician.isBlank()
                ? technician
                : (userIdHeader != null && !userIdHeader.isBlank() ? userIdHeader : "web-ui-dev-user");

        try {
            var briefing = briefingService.buildBriefing(effectiveTechnician);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", briefing
            ));
        } catch (Exception ex) {
            log.error("Failed to build briefing for technician {}", effectiveTechnician, ex);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "degraded", true,
                    "data", Map.of(
                            "summary", "No pude generar el briefing en tiempo real porque la conexión con ServiceNow falló. El workspace sigue operativo para priorizar con los incidentes visibles ya cargados en la sesión.",
                            "context", Map.of(
                                    "technician", effectiveTechnician,
                                    "generatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString(),
                                    "metrics", Map.of(
                                            "openTickets", 0,
                                            "slaRiskTickets", 0,
                                            "stalledTickets", 0,
                                            "pendingComplianceTickets", 0
                                    ),
                                    "attentionToday", List.of(),
                                    "complianceWatch", List.of(),
                                    "patterns", List.of("briefing degradado por conectividad ServiceNow")
                            )
                    )
            ));
        }
    }
}
