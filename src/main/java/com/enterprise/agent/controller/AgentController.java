package com.enterprise.agent.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for ServiceNow Widget integration.
 * This endpoint receives the authenticated ServiceNow user
 * directly from the widget (session-based).
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> payload) {

        logger.info("Incoming chat request from ServiceNow widget");

        Map<String, Object> user = (Map<String, Object>) payload.get("user");

        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "User context missing"
            ));
        }

        String sysId = (String) user.get("sys_id");
        String username = (String) user.get("username");
        String email = (String) user.get("email");

        logger.info("User context received - sys_id: {}, username: {}", sysId, username);

        // TODO: Add your business logic here
        // Example: Create incident, retrieve data, call LLM, etc.

        return ResponseEntity.ok(Map.of(
                "message", "Hello " + username + ", your request was processed successfully.",
                "userSysId", sysId,
                "email", email
        ));
    }
}
