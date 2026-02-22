package com.nexus.agent.api.dto;

import java.time.Instant;
import java.util.List;

public record ChatResponse(
        String mode,
        String sessionId,
        String response,
        List<String> activatedSkills,
        int eventCount,
        Instant timestamp
) {
}
