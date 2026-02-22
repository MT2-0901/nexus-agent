package com.nexus.agent.api.dto.agui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgUiMessage(
        String id,
        String role,
        JsonNode content
) {
}
