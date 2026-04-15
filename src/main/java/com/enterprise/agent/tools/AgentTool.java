package com.enterprise.agent.tools;

import com.enterprise.agent.model.*;

public interface AgentTool {

    boolean supports(String action);

    AgentResponse execute(AgentDecision decision);
}
