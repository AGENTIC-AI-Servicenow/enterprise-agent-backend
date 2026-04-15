package com.enterprise.agent.model;

public class AgentResponse {

    private String correlationId;
    private String actionExecuted;
    private String summary;
    private Object data;

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getActionExecuted() { return actionExecuted; }
    public void setActionExecuted(String actionExecuted) { this.actionExecuted = actionExecuted; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
