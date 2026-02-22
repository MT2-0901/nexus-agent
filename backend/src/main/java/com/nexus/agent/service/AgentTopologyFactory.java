package com.nexus.agent.service;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.tools.BaseTool;
import com.nexus.agent.config.AdkProperties;
import com.nexus.agent.domain.AgentMode;
import com.nexus.agent.modes.ModeDefinition;
import com.nexus.agent.modes.ModeNodeDefinition;
import com.nexus.agent.modes.ModeRegistry;
import com.nexus.agent.skills.SkillDefinition;
import com.nexus.agent.skills.ToolCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class AgentTopologyFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentTopologyFactory.class);

    private final AdkProperties adkProperties;
    private final SkillPromptComposer skillPromptComposer;
    private final ToolCatalog toolCatalog;
    private final ModeRegistry modeRegistry;

    public AgentTopologyFactory(AdkProperties adkProperties,
                                SkillPromptComposer skillPromptComposer,
                                ToolCatalog toolCatalog,
                                ModeRegistry modeRegistry) {
        this.adkProperties = adkProperties;
        this.skillPromptComposer = skillPromptComposer;
        this.toolCatalog = toolCatalog;
        this.modeRegistry = modeRegistry;
    }

    public BaseAgent create(AgentMode mode, List<SkillDefinition> activeSkills) {
        return create(mode, activeSkills, null);
    }

    public BaseAgent create(AgentMode mode, List<SkillDefinition> activeSkills, String modelOverride) {
        String skillPrompt = skillPromptComposer.compose(activeSkills);
        List<BaseTool> tools = toolCatalog.resolve(activeSkills);
        String model = resolveModel(modelOverride);
        return createWithFallback(mode, skillPrompt, tools, model);
    }

    private BaseAgent createWithFallback(AgentMode requestedMode,
                                         String skillPrompt,
                                         List<BaseTool> tools,
                                         String model) {
        AgentMode current = requestedMode;
        Set<AgentMode> visited = EnumSet.noneOf(AgentMode.class);
        RuntimeException lastError = null;

        while (current != null && visited.add(current)) {
            ModeDefinition definition = modeRegistry.getRequired(current);
            try {
                return buildNode(definition, definition.getRoot(), skillPrompt, tools, model, new ArrayList<>());
            } catch (RuntimeException ex) {
                lastError = ex;
                AgentMode fallbackMode = definition.getFallbackMode();
                if (fallbackMode == null) {
                    break;
                }
                log.warn("Failed building mode {} from definitions, fallback to {}", current, fallbackMode, ex);
                current = fallbackMode;
            }
        }

        throw new IllegalStateException("Failed to build topology for mode " + requestedMode, lastError);
    }

    private BaseAgent buildNode(ModeDefinition definition,
                                String nodeRef,
                                String skillPrompt,
                                List<BaseTool> tools,
                                String model,
                                List<String> stack) {
        if (stack.contains(nodeRef)) {
            throw new IllegalStateException("Cycle detected in mode " + definition.getMode() + ": " + stack + " -> " + nodeRef);
        }
        stack.add(nodeRef);

        ModeNodeDefinition node = definition.getNodes().get(nodeRef);
        if (node == null) {
            throw new IllegalStateException("Node " + nodeRef + " not found for mode " + definition.getMode());
        }

        List<BaseAgent> children = node.getSubAgents().stream()
                .map(ref -> buildNode(definition, ref, skillPrompt, tools, model, stack))
                .toList();

        stack.remove(stack.size() - 1);

        return switch (node.getKind()) {
            case LLM -> buildLlmNode(node, children, skillPrompt, tools, model);
            case PARALLEL -> ParallelAgent.builder()
                    .name(node.getName())
                    .description(node.getDescription())
                    .subAgents(children.toArray(BaseAgent[]::new))
                    .build();
            case SEQUENTIAL -> SequentialAgent.builder()
                    .name(node.getName())
                    .description(node.getDescription())
                    .subAgents(children.toArray(BaseAgent[]::new))
                    .build();
        };
    }

    private BaseAgent buildLlmNode(ModeNodeDefinition node,
                                   List<BaseAgent> children,
                                   String skillPrompt,
                                   List<BaseTool> tools,
                                   String model) {
        String instructionBase = node.getInstruction() == null ? "" : node.getInstruction();
        String instruction = instructionBase + skillPrompt;

        LlmAgent.Builder builder = LlmAgent.builder()
                .name(node.getName())
                .description(node.getDescription())
                .model(model)
                .instruction(instruction)
                .tools(tools);

        if (!children.isEmpty()) {
            builder.subAgents(children.toArray(BaseAgent[]::new));
        }
        return builder.build();
    }

    private String resolveModel(String modelOverride) {
        if (modelOverride == null || modelOverride.isBlank()) {
            return adkProperties.getModel();
        }
        String normalized = modelOverride.trim();
        boolean allowed = adkProperties.getAvailableModels().stream()
                .map(item -> item == null ? "" : item.trim().toLowerCase(Locale.ROOT))
                .anyMatch(item -> item.equals(normalized.toLowerCase(Locale.ROOT)));
        if (!allowed) {
            throw new IllegalArgumentException("Unsupported model: " + normalized);
        }
        return normalized;
    }
}
