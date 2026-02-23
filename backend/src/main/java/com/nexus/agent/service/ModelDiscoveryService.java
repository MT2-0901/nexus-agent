package com.nexus.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.agent.api.dto.ModelDiscoverRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class ModelDiscoveryService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ModelDiscoveryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public List<String> discover(ModelDiscoverRequest request) {
        URI modelsUri = buildModelsUri(request.baseUrl());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(modelsUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET();

        if (hasText(request.apiKey())) {
            builder.header("Authorization", "Bearer " + request.apiKey().trim());
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to load models from upstream URL", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Model discovery was interrupted", ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = readErrorDetail(response.body());
            throw new IllegalArgumentException("Model discovery failed (" + response.statusCode() + "): " + detail);
        }

        List<String> models = parseModelNames(response.body());
        if (models.isEmpty()) {
            throw new IllegalArgumentException("No model IDs found in upstream response");
        }
        return models;
    }

    private URI buildModelsUri(String baseUrl) {
        if (!hasText(baseUrl)) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        String normalized = baseUrl.trim();
        if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            throw new IllegalArgumentException("baseUrl must start with http:// or https://");
        }
        normalized = normalized.replaceAll("/+$", "");
        if (normalized.endsWith("/models")) {
            return URI.create(normalized);
        }
        if (!normalized.endsWith("/v1")) {
            normalized = normalized + "/v1";
        }
        return URI.create(normalized + "/models");
    }

    private List<String> parseModelNames(String body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Upstream models response is not valid JSON", ex);
        }

        JsonNode listNode = root;
        if (root.isObject()) {
            if (root.has("data")) {
                listNode = root.get("data");
            } else if (root.has("models")) {
                listNode = root.get("models");
            }
        }
        if (!listNode.isArray()) {
            return List.of();
        }

        Set<String> deduplicated = new LinkedHashSet<>();
        for (JsonNode item : listNode) {
            if (item == null || item.isNull()) {
                continue;
            }
            if (item.isTextual()) {
                addModel(deduplicated, item.asText());
                continue;
            }
            if (!item.isObject()) {
                continue;
            }
            String candidate = readFirstText(item.get("id"), item.get("name"), item.get("model"));
            addModel(deduplicated, candidate);
        }
        return deduplicated.stream().toList();
    }

    private String readErrorDetail(String body) {
        if (!hasText(body)) {
            return "upstream request rejected";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String message = readFirstText(root.get("message"), root.path("error").get("message"));
            if (hasText(message)) {
                return message;
            }
        } catch (IOException ignored) {
            // Fall back to raw text.
        }
        String condensed = body.trim().replaceAll("\\s+", " ");
        if (condensed.length() <= 180) {
            return condensed;
        }
        return condensed.substring(0, 177) + "...";
    }

    private String readFirstText(JsonNode... nodes) {
        return Stream.of(nodes)
                .filter(node -> node != null && node.isTextual())
                .map(JsonNode::asText)
                .filter(this::hasText)
                .map(String::trim)
                .findFirst()
                .orElse("");
    }

    private void addModel(Set<String> models, String model) {
        if (!hasText(model)) {
            return;
        }
        models.add(model.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
