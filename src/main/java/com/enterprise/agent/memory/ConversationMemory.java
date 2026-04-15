package com.enterprise.agent.memory;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ConversationMemory {

    private final List<String> successfulIncidents = new ArrayList<>();
    private final List<String> failedIncidents = new ArrayList<>();

    public void addSuccessful(String number) {
        if (!successfulIncidents.contains(number)) {
            successfulIncidents.add(number);
        }
    }

    public void addFailed(String number) {
        if (!failedIncidents.contains(number)) {
            failedIncidents.add(number);
        }
    }

    public List<String> getSuccessfulIncidents() {
        return successfulIncidents;
    }

    public List<String> getFailedIncidents() {
        return failedIncidents;
    }

    public String getLastSuccessful() {
        if (successfulIncidents.isEmpty()) return null;
        return successfulIncidents.get(successfulIncidents.size() - 1);
    }
}
