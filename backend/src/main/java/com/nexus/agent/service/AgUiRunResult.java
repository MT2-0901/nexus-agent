package com.nexus.agent.service;

import java.time.Instant;
import java.util.List;

public record AgUiRunResult(
        String sessionId,
        String mode,
        String response,
        List<String> activatedSkills,
        int eventCount,
        Instant timestamp
) {
}
