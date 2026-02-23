package com.nexus.agent.service;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.Gemini;
import com.google.adk.tools.BaseTool;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
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
        return create(mode, activeSkills, null, null, null);
    }

    public BaseAgent create(AgentMode mode, List<SkillDefinition> activeSkills, String modelOverride) {
        return create(mode, activeSkills, modelOverride, null, null);
    }

    public BaseAgent create(AgentMode mode,
                            List<SkillDefinition> activeSkills,
                            String modelOverride,
                            String llmBaseUrl,
                            String llmApiKey) {
        String skillPrompt = skillPromptComposer.compose(activeSkills);
        List<BaseTool> tools = toolCatalog.resolve(activeSkills);
        String model = resolveModel(modelOverride);
        RuntimeLlmOptions runtimeLlmOptions = new RuntimeLlmOptions(
                normalizeOptional(llmBaseUrl),
                normalizeOptional(llmApiKey)
        );
        return createWithFallback(mode, skillPrompt, tools, model, runtimeLlmOptions);
    }

    private BaseAgent createWithFallback(AgentMode requestedMode,
                                         String skillPrompt,
                                         List<BaseTool> tools,
                                         String model,
                                         RuntimeLlmOptions runtimeLlmOptions) {
        AgentMode current = requestedMode;
        Set<AgentMode> visited = EnumSet.noneOf(AgentMode.class);
        RuntimeException lastError = null;

        while (current != null && visited.add(current)) {
            ModeDefinition definition = modeRegistry.getRequired(current);
            try {
                return buildNode(definition, definition.getRoot(), skillPrompt, tools, model, runtimeLlmOptions, new ArrayList<>());
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
                                RuntimeLlmOptions runtimeLlmOptions,
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
                .map(ref -> buildNode(definition, ref, skillPrompt, tools, model, runtimeLlmOptions, stack))
                .toList();

        stack.remove(stack.size() - 1);

        return switch (node.getKind()) {
            case LLM -> buildLlmNode(node, children, skillPrompt, tools, model, runtimeLlmOptions);
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
                                   String model,
                                   RuntimeLlmOptions runtimeLlmOptions) {
        String instructionBase = node.getInstruction() == null ? "" : node.getInstruction();
        String instruction = instructionBase + skillPrompt;

        LlmAgent.Builder builder = LlmAgent.builder()
                .name(node.getName())
                .description(node.getDescription())
                .instruction(instruction)
                .tools(tools);
        applyModel(builder, model, runtimeLlmOptions);

        if (!children.isEmpty()) {
            builder.subAgents(children.toArray(BaseAgent[]::new));
        }
        return builder.build();
    }

    private String resolveModel(String modelOverride) {
        if (modelOverride == null || modelOverride.isBlank()) {
            return adkProperties.getModel();
        }
        return modelOverride.trim();
    }

    private void applyModel(LlmAgent.Builder builder, String modelName, RuntimeLlmOptions runtimeLlmOptions) {
        if (!runtimeLlmOptions.hasOverrides()) {
            builder.model(modelName);
            return;
        }
        builder.model(buildRuntimeModel(modelName, runtimeLlmOptions));
    }

    private BaseLlm buildRuntimeModel(String modelName, RuntimeLlmOptions runtimeLlmOptions) {
        if (hasText(runtimeLlmOptions.baseUrl())) {
            Client.Builder clientBuilder = Client.builder()
                    .httpOptions(HttpOptions.builder().baseUrl(runtimeLlmOptions.baseUrl()).build());
            if (hasText(runtimeLlmOptions.apiKey())) {
                clientBuilder.apiKey(runtimeLlmOptions.apiKey());
            }
            return Gemini.builder()
                    .modelName(modelName)
                    .apiClient(clientBuilder.build())
                    .build();
        }
        if (hasText(runtimeLlmOptions.apiKey())) {
            return Gemini.builder()
                    .modelName(modelName)
                    .apiKey(runtimeLlmOptions.apiKey())
                    .build();
        }
        return Gemini.builder().modelName(modelName).build();
    }

    private String normalizeOptional(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized.replaceAll("/+$", "");
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RuntimeLlmOptions(String baseUrl, String apiKey) {
        private boolean hasOverrides() {
            return (baseUrl != null && !baseUrl.isBlank()) || (apiKey != null && !apiKey.isBlank());
        }
    }
}
