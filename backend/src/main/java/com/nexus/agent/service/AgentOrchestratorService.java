package com.nexus.agent.service;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.nexus.agent.api.dto.ChatRequest;
import com.nexus.agent.api.dto.ChatResponse;
import com.nexus.agent.config.AdkProperties;
import com.nexus.agent.domain.AgentMode;
import com.nexus.agent.skills.SkillDefinition;
import com.nexus.agent.skills.SkillRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AgentOrchestratorService {

    private final AdkProperties adkProperties;
    private final AgentTopologyFactory topologyFactory;
    private final SkillRegistry skillRegistry;

    public AgentOrchestratorService(AdkProperties adkProperties,
                                    AgentTopologyFactory topologyFactory,
                                    SkillRegistry skillRegistry) {
        this.adkProperties = adkProperties;
        this.topologyFactory = topologyFactory;
        this.skillRegistry = skillRegistry;
    }

    public ChatResponse chat(ChatRequest request) {
        AgentMode mode = AgentMode.from(request.mode());
        Set<String> requiredSkills = normalizeSkillNames(request.skillNames());

        List<SkillDefinition> activeSkills = skillRegistry.resolve(mode, requiredSkills);
        BaseAgent root = topologyFactory.create(mode, activeSkills);

        Runner runner = new InMemoryRunner(root, adkProperties.getAppName());

        String userId = hasText(request.userId()) ? request.userId() : adkProperties.getDefaultUserId();
        String sessionId = hasText(request.sessionId())
                ? request.sessionId()
                : adkProperties.getDefaultSessionPrefix() + "-" + UUID.randomUUID();

        Content userMessage = Content.fromParts(Part.fromText(request.message()));
        List<Event> events = runner.runAsync(userId, sessionId, userMessage, RunConfig.builder().build())
                .toList()
                .blockingGet();

        String response = extractResponse(events);
        List<String> skillNames = activeSkills.stream().map(SkillDefinition::getName).toList();

        return new ChatResponse(
                mode.name(),
                sessionId,
                response,
                skillNames,
                events.size(),
                Instant.now()
        );
    }

    private Set<String> normalizeSkillNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        return names.stream()
                .filter(this::hasText)
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private String extractResponse(List<Event> events) {
        String finalText = events.stream()
                .filter(Event::finalResponse)
                .map(this::readText)
                .filter(this::hasText)
                .collect(Collectors.joining("\n"));

        if (hasText(finalText)) {
            return finalText;
        }

        String fallback = events.stream()
                .map(this::readText)
                .filter(this::hasText)
                .collect(Collectors.joining("\n"));

        if (hasText(fallback)) {
            return fallback;
        }

        return "No textual response was produced. Check ADK event stream for tool outputs or structured payloads.";
    }

    private String readText(Event event) {
        return event.content()
                .map(Content::text)
                .orElseGet(event::stringifyContent);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
