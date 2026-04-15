package com.enterprise.agent.agent;

public class AgentDecision {

    private String action;
    private String number;
    private String shortDescription;
    private String description;
    private String priority; // ✅ NUEVO
    private String originalInput;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    // Para mapear JSON del LLM si viene como short_description
    public void setShort_description(String short_description) {
        this.shortDescription = short_description;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // ✅ PRIORITY
    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getOriginalInput() {
        return originalInput;
    }

    public void setOriginalInput(String originalInput) {
        this.originalInput = originalInput;
    }
}
