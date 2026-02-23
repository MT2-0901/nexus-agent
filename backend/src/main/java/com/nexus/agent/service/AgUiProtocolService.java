package com.nexus.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.nexus.agent.api.dto.agui.AgUiMessage;
import com.nexus.agent.api.dto.agui.AgUiRunRequest;
import com.nexus.agent.config.AdkProperties;
import com.nexus.agent.domain.AgentMode;
import com.nexus.agent.persistence.ChatHistoryRecord;
import com.nexus.agent.persistence.ChatHistoryStore;
import com.nexus.agent.skills.SkillDefinition;
import com.nexus.agent.skills.SkillRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class AgUiProtocolService {

    private final AdkProperties adkProperties;
    private final AgentTopologyFactory topologyFactory;
    private final SkillRegistry skillRegistry;
    private final ChatHistoryStore chatHistoryStore;
    private final BaseSessionService sessionService;

    public AgUiProtocolService(AdkProperties adkProperties,
                               AgentTopologyFactory topologyFactory,
                               SkillRegistry skillRegistry,
                               ChatHistoryStore chatHistoryStore,
                               BaseSessionService sessionService) {
        this.adkProperties = adkProperties;
        this.topologyFactory = topologyFactory;
        this.skillRegistry = skillRegistry;
        this.chatHistoryStore = chatHistoryStore;
        this.sessionService = sessionService;
    }

    public AgUiRunResult run(AgUiRunRequest request,
                             String threadId,
                             Consumer<String> deltaSink) {
        Map<String, Object> props = request.forwardedProps() == null ? Map.of() : request.forwardedProps();

        AgentMode mode = AgentMode.from(readString(props, "mode"));
        String model = readString(props, "model");
        String userId = hasText(readString(props, "userId"))
                ? readString(props, "userId").trim()
                : adkProperties.getDefaultUserId();
        String sessionId = hasText(readString(props, "sessionId"))
                ? readString(props, "sessionId").trim()
                : threadId;
        String llmBaseUrl = readString(props, "llmBaseUrl");
        String llmApiKey = readString(props, "llmApiKey");
        if (!hasText(sessionId)) {
            sessionId = adkProperties.getDefaultSessionPrefix() + "-" + UUID.randomUUID();
        }

        Set<String> requiredSkills = normalizeSkillNames(readStringList(props, "skillNames"));
        ParsedUserMessage userMessage = parseLatestUserMessage(request.messages());

        List<SkillDefinition> activeSkills = skillRegistry.resolve(mode, requiredSkills);
        BaseAgent root = topologyFactory.create(mode, activeSkills, model, llmBaseUrl, llmApiKey);
        Runner runner = Runner.builder()
                .agent(root)
                .appName(adkProperties.getAppName())
                .sessionService(sessionService)
                .build();
        ensureSessionExists(userId, sessionId);

        List<Event> events = new ArrayList<>();
        StringBuilder streamedText = new StringBuilder();

        runner.runAsync(userId, sessionId, userMessage.content(), RunConfig.builder().build())
                .blockingForEach(event -> {
                    events.add(event);
                    String text = readText(event);
                    if (!hasText(text)) {
                        return;
                    }
                    String delta = toDelta(streamedText.toString(), text);
                    if (hasText(delta)) {
                        streamedText.append(delta);
                        deltaSink.accept(delta);
                    }
                });

        String response = extractResponse(events);
        if (!hasText(streamedText.toString()) && hasText(response)) {
            deltaSink.accept(response);
        }

        List<String> activeSkillNames = activeSkills.stream().map(SkillDefinition::getName).toList();
        Instant timestamp = Instant.now();

        chatHistoryStore.save(new ChatHistoryRecord(
                null,
                sessionId,
                userId,
                mode.name(),
                userMessage.persistenceText(),
                response,
                activeSkillNames,
                events.size(),
                timestamp
        ));

        return new AgUiRunResult(
                sessionId,
                mode.name(),
                response,
                activeSkillNames,
                events.size(),
                timestamp
        );
    }

    private ParsedUserMessage parseLatestUserMessage(List<AgUiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages is required");
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgUiMessage message = messages.get(i);
            if (!"user".equalsIgnoreCase(message.role())) {
                continue;
            }
            return parseUserContent(message.content());
        }
        throw new IllegalArgumentException("At least one user message is required");
    }

    private ParsedUserMessage parseUserContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isNull()) {
            throw new IllegalArgumentException("User message content is required");
        }

        List<Part> parts = new ArrayList<>();
        List<String> persistenceChunks = new ArrayList<>();

        if (contentNode.isTextual()) {
            appendTextPart(parts, persistenceChunks, contentNode.asText());
            return finalizeParsedMessage(parts, persistenceChunks);
        }

        if (!contentNode.isArray()) {
            throw new IllegalArgumentException("Unsupported AG-UI message content format");
        }

        for (JsonNode item : contentNode) {
            if (item == null || item.isNull()) {
                continue;
            }
            if (item.isTextual()) {
                appendTextPart(parts, persistenceChunks, item.asText());
                continue;
            }
            if (!item.isObject()) {
                continue;
            }

            String type = readNodeText(item, "type").toLowerCase(Locale.ROOT);
            if ("text".equals(type) || "input_text".equals(type)) {
                appendTextPart(parts, persistenceChunks, firstNonBlank(
                        readNodeText(item, "text"),
                        readNodeText(item, "content"),
                        readNodeText(item, "value")
                ));
                continue;
            }

            if (type.contains("image")) {
                appendImagePart(parts, persistenceChunks, item);
                continue;
            }

            // Graceful fallback for content blocks without explicit type but with text.
            appendTextPart(parts, persistenceChunks, firstNonBlank(
                    readNodeText(item, "text"),
                    readNodeText(item, "content")
            ));
        }

        return finalizeParsedMessage(parts, persistenceChunks);
    }

    private void appendImagePart(List<Part> parts, List<String> persistenceChunks, JsonNode imageNode) {
        String mimeType = firstNonBlank(
                readNodeText(imageNode, "mimeType"),
                readNodeText(imageNode, "mediaType"),
                "image/png"
        );
        String payload = firstNonBlank(
                readNodeText(imageNode, "data"),
                readNodeText(imageNode, "base64"),
                readNodeText(imageNode, "imageBase64")
        );
        String fileName = firstNonBlank(
                readNodeText(imageNode, "name"),
                readNodeText(imageNode, "fileName"),
                "image"
        );

        String url = firstNonBlank(readNodeText(imageNode, "url"), readNodeText(imageNode, "imageUrl"));
        if (!hasText(payload) && hasText(url) && url.startsWith("data:")) {
            int commaIndex = url.indexOf(',');
            if (commaIndex > 0 && commaIndex + 1 < url.length()) {
                String header = url.substring(5, commaIndex);
                payload = url.substring(commaIndex + 1);
                int semicolonIndex = header.indexOf(';');
                if (semicolonIndex > 0) {
                    mimeType = header.substring(0, semicolonIndex);
                } else if (!header.isBlank()) {
                    mimeType = header;
                }
            }
        }

        if (!hasText(payload)) {
            return;
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid image base64 payload", ex);
        }
        parts.add(Part.fromBytes(bytes, mimeType));
        persistenceChunks.add("[image:" + fileName + "]");
    }

    private ParsedUserMessage finalizeParsedMessage(List<Part> parts, List<String> persistenceChunks) {
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("User message must contain text or image content");
        }
        Content content = Content.fromParts(parts.toArray(Part[]::new));
        String persistenceText = String.join("\n", persistenceChunks).trim();
        if (!hasText(persistenceText)) {
            persistenceText = "[multimodal-content]";
        }
        return new ParsedUserMessage(content, persistenceText);
    }

    private void appendTextPart(List<Part> parts, List<String> persistenceChunks, String text) {
        if (!hasText(text)) {
            return;
        }
        String normalized = text.trim();
        parts.add(Part.fromText(normalized));
        persistenceChunks.add(normalized);
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

    private String toDelta(String previous, String current) {
        if (!hasText(current)) {
            return "";
        }
        if (!hasText(previous)) {
            return current;
        }
        if (current.startsWith(previous)) {
            return current.substring(previous.length());
        }
        if (previous.startsWith(current)) {
            return "";
        }
        return current;
    }

    private String readText(Event event) {
        return event.content()
                .map(Content::text)
                .orElseGet(event::stringifyContent);
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

    private List<String> readStringList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof List<?> values) {
            return values.stream()
                    .map(item -> item == null ? null : String.valueOf(item))
                    .filter(this::hasText)
                    .toList();
        }
        return List.of();
    }

    private String readString(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return hasText(text) ? text : null;
    }

    private String readNodeText(JsonNode source, String field) {
        JsonNode node = source.get(field);
        if (node == null || node.isNull() || node.isContainerNode()) {
            return "";
        }
        String text = node.asText("");
        return text == null ? "" : text.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    private record ParsedUserMessage(Content content, String persistenceText) {
    }
}
