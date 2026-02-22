package com.nexus.agent.skills;

import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ToolCatalog {

    private final Map<String, BaseTool> indexedTools;

    public ToolCatalog() {
        Map<String, BaseTool> tools = new HashMap<>();
        tools.put("echo", FunctionTool.create(this, "echo"));
        tools.put("now", FunctionTool.create(this, "now"));
        this.indexedTools = Map.copyOf(tools);
    }

    public List<BaseTool> resolve(List<SkillDefinition> skills) {
        Set<String> toolNames = skills.stream()
                .flatMap(skill -> skill.getTools().stream())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .collect(Collectors.toSet());

        return toolNames.stream()
                .map(name -> indexedTools.get(name.toLowerCase()))
                .filter(Objects::nonNull)
                .toList();
    }

    public Map<String, Object> echo(String input) {
        return Map.of("echo", input == null ? "" : input);
    }

    public Map<String, Object> now() {
        return Map.of("timestamp", Instant.now().toString());
    }
}
