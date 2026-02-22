package com.nexus.agent.skills;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nexus.agent.domain.AgentMode;

import java.util.ArrayList;
import java.util.List;

public class SkillDefinition {

    private String name;
    private String description;
    private boolean enabled = true;
    private List<String> appliesTo = new ArrayList<>();
    private String instruction;
    private List<String> tools = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAppliesTo() {
        return appliesTo;
    }

    public void setAppliesTo(List<String> appliesTo) {
        this.appliesTo = appliesTo == null ? new ArrayList<>() : appliesTo;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public List<String> getTools() {
        return tools;
    }

    public void setTools(List<String> tools) {
        this.tools = tools == null ? new ArrayList<>() : tools;
    }

    @JsonIgnore
    public boolean supports(AgentMode mode) {
        if (appliesTo == null || appliesTo.isEmpty()) {
            return true;
        }
        return appliesTo.stream().anyMatch(item -> mode.name().equalsIgnoreCase(item));
    }
}
