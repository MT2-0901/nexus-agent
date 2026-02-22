package com.nexus.agent.api.dto;

import java.time.Instant;
import java.util.List;

public record ChatHistoryItem(
        Long id,
        String sessionId,
        String userId,
        String mode,
        String requestMessage,
        String responseMessage,
        List<String> activatedSkills,
        int eventCount,
        Instant timestamp
) {
}
