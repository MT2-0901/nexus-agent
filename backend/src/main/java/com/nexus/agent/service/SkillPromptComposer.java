package com.nexus.agent.service;

import com.nexus.agent.skills.SkillDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SkillPromptComposer {

    public String compose(List<SkillDefinition> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("\n\nActivated skills:\n");
        for (SkillDefinition skill : skills) {
            prompt.append("- ").append(skill.getName());
            if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
                prompt.append(": ").append(skill.getDescription());
            }
            prompt.append("\n");
            if (skill.getInstruction() != null && !skill.getInstruction().isBlank()) {
                prompt.append("  Instruction: ").append(skill.getInstruction()).append("\n");
            }
        }
        return prompt.toString();
    }
}
