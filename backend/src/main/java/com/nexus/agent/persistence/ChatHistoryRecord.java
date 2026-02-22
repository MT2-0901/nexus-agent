package com.nexus.agent.persistence;

import java.time.Instant;
import java.util.List;

public record ChatHistoryRecord(
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
