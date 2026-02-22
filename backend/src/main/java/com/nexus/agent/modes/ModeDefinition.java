package com.nexus.agent.modes;

import com.nexus.agent.domain.AgentMode;

import java.util.HashMap;
import java.util.Map;

public class ModeDefinition {

    private AgentMode mode;
    private AgentMode fallbackMode;
    private String root;
    private Map<String, ModeNodeDefinition> nodes = new HashMap<>();

    public AgentMode getMode() {
        return mode;
    }

    public void setMode(AgentMode mode) {
        this.mode = mode;
    }

    public AgentMode getFallbackMode() {
        return fallbackMode;
    }

    public void setFallbackMode(AgentMode fallbackMode) {
        this.fallbackMode = fallbackMode;
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public Map<String, ModeNodeDefinition> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, ModeNodeDefinition> nodes) {
        this.nodes = nodes == null ? new HashMap<>() : new HashMap<>(nodes);
    }
}
