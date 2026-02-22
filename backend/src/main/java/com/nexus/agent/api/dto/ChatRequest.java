package com.nexus.agent.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ChatRequest(
        @NotBlank(message = "message is required")
        String message,
        String mode,
        String userId,
        String sessionId,
        List<String> skillNames
) {
}
