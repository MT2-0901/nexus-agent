package com.nexus.agent.api.dto.agui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgUiRunRequest(
        String threadId,
        String runId,
        List<AgUiMessage> messages,
        Map<String, Object> forwardedProps
) {
}
