package com.nexus.agent.api.dto;

import java.util.List;

public record SkillView(
        String name,
        String description,
        boolean enabled,
        List<String> appliesTo,
        List<String> tools
) {
}
