package com.nexus.agent.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ModelDiscoverRequest(
        @NotBlank(message = "baseUrl is required")
        String baseUrl,
        String apiKey
) {
}
