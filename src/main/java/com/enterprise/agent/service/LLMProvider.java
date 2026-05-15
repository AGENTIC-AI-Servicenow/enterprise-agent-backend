package com.enterprise.agent.service;

public interface LLMProvider {

    /**
     * Agentic chat interaction.
     * Must return strict JSON response from the model.
     */
    String agentChat(String systemPrompt, String context);

    /**
     * Generic text generation.
     */
    String generate(String prompt, double temperature, int maxTokens);
}
