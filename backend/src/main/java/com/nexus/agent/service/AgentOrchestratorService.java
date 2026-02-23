package com.nexus.agent.service;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.nexus.agent.api.dto.ChatRequest;
import com.nexus.agent.api.dto.ChatResponse;
import com.nexus.agent.config.AdkProperties;
import com.nexus.agent.config.PersistenceProperties;
import com.nexus.agent.domain.AgentMode;
import com.nexus.agent.persistence.ChatHistoryRecord;
import com.nexus.agent.persistence.ChatHistoryStore;
import com.nexus.agent.skills.SkillDefinition;
import com.nexus.agent.skills.SkillRegistry;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class AgentOrchestratorService {

    private final AdkProperties adkProperties;
    private final AgentTopologyFactory topologyFactory;
    private final SkillRegistry skillRegistry;
    private final ChatHistoryStore chatHistoryStore;
    private final PersistenceProperties persistenceProperties;
    private final BaseSessionService sessionService;

    public AgentOrchestratorService(AdkProperties adkProperties,
                                    AgentTopologyFactory topologyFactory,
                                    SkillRegistry skillRegistry,
                                    ChatHistoryStore chatHistoryStore,
                                    PersistenceProperties persistenceProperties,
                                    BaseSessionService sessionService) {
        this.adkProperties = adkProperties;
        this.topologyFactory = topologyFactory;
        this.skillRegistry = skillRegistry;
        this.chatHistoryStore = chatHistoryStore;
        this.persistenceProperties = persistenceProperties;
        this.sessionService = sessionService;
    }

    public ChatResponse chat(ChatRequest request) {
        AgentMode mode = AgentMode.from(request.mode());
        Set<String> requiredSkills = normalizeSkillNames(request.skillNames());

        List<SkillDefinition> activeSkills = skillRegistry.resolve(mode, requiredSkills);
        BaseAgent root = topologyFactory.create(mode, activeSkills);

        Runner runner = Runner.builder()
                .agent(root)
                .appName(adkProperties.getAppName())
                .sessionService(sessionService)
                .build();

        String userId = hasText(request.userId()) ? request.userId() : adkProperties.getDefaultUserId();
        String sessionId = hasText(request.sessionId())
                ? request.sessionId()
                : adkProperties.getDefaultSessionPrefix() + "-" + UUID.randomUUID();
        ensureSessionExists(userId, sessionId);

        Content userMessage = Content.fromParts(Part.fromText(request.message()));
        List<Event> events = runner.runAsync(userId, sessionId, userMessage, RunConfig.builder().build())
                .toList()
                .blockingGet();

        String response = extractResponse(events);
        List<String> skillNames = activeSkills.stream().map(SkillDefinition::getName).toList();
        Instant timestamp = Instant.now();

        chatHistoryStore.save(new ChatHistoryRecord(
                null,
                sessionId,
                userId,
                mode.name(),
                request.message(),
                response,
                skillNames,
                events.size(),
                timestamp
        ));

        return new ChatResponse(
                mode.name(),
                sessionId,
                response,
                skillNames,
                events.size(),
                timestamp
        );
    }

    public List<ChatHistoryRecord> listSessionHistory(String sessionId, Integer requestedLimit) {
        if (!hasText(sessionId)) {
            throw new IllegalArgumentException("sessionId is required");
        }
        int limit = normalizeLimit(requestedLimit);
        return chatHistoryStore.listBySession(sessionId.trim(), limit);
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

    private int normalizeLimit(Integer requestedLimit) {
        int defaultLimit = Math.max(1, persistenceProperties.getHistoryDefaultLimit());
        int maxLimit = Math.max(defaultLimit, persistenceProperties.getHistoryMaxLimit());
        int limit = requestedLimit == null ? defaultLimit : requestedLimit;
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be greater than 0");
        }
        return Math.min(limit, maxLimit);
    }

    private void ensureSessionExists(String userId, String sessionId) {
        Session existing = sessionService
                .getSession(adkProperties.getAppName(), userId, sessionId, Optional.empty())
                .blockingGet();
        if (existing != null) {
            return;
        }
        sessionService.createSession(
                adkProperties.getAppName(),
                userId,
                new ConcurrentHashMap<>(),
                sessionId
        ).blockingGet();
    }
}
