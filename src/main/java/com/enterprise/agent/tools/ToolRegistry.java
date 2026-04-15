package com.enterprise.agent.tools;

import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ToolRegistry {

    private final List<AgentTool> tools;

    public ToolRegistry(List<AgentTool> tools) {
        this.tools = tools;
    }

    public AgentTool findTool(String action) {

        return tools.stream()
                .filter(tool -> tool.supports(action))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No tool found"));
    }
}
