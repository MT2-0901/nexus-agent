package com.nexus.agent.modes;

import java.util.ArrayList;
import java.util.List;

public class ModeNodeDefinition {

    private ModeNodeKind kind = ModeNodeKind.LLM;
    private String name;
    private String description;
    private String instruction;
    private List<String> subAgents = new ArrayList<>();

    public ModeNodeKind getKind() {
        return kind;
    }

    public void setKind(ModeNodeKind kind) {
        this.kind = kind;
    }

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

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public List<String> getSubAgents() {
        return subAgents;
    }

    public void setSubAgents(List<String> subAgents) {
        this.subAgents = subAgents == null ? new ArrayList<>() : new ArrayList<>(subAgents);
    }
}
