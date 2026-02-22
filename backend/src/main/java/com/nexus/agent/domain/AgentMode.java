package com.nexus.agent.domain;

import java.util.Arrays;

public enum AgentMode {
    SINGLE,
    MASTER_SUB,
    MULTI_WORKFLOW;

    public static AgentMode from(String value) {
        if (value == null || value.isBlank()) {
            return SINGLE;
        }
        return Arrays.stream(values())
                .filter(mode -> mode.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported mode: " + value));
    }
}
