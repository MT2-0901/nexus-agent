package com.nexus.agent.modes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.nexus.agent.config.ModeProperties;
import com.nexus.agent.domain.AgentMode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Component
public class ModeRegistry {

    private static final Logger log = LoggerFactory.getLogger(ModeRegistry.class);

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Path modeDirectory;

    private final AtomicReference<Map<AgentMode, ModeDefinition>> cache = new AtomicReference<>(Map.of());

    public ModeRegistry(ModeProperties modeProperties) {
        this.modeDirectory = Paths.get(modeProperties.getPath());
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized Map<AgentMode, ModeDefinition> reload() {
        if (!Files.exists(modeDirectory)) {
            log.warn("Mode directory {} not found, skipping load.", modeDirectory.toAbsolutePath());
            cache.set(Map.of());
            return cache.get();
        }

        try (Stream<Path> files = Files.list(modeDirectory)) {
            Map<AgentMode, ModeDefinition> loaded = files
                    .filter(Files::isRegularFile)
                    .filter(this::isSupported)
                    .map(this::readDefinition)
                    .filter(Objects::nonNull)
                    .collect(
                            () -> new EnumMap<>(AgentMode.class),
                            (map, definition) -> map.put(definition.getMode(), definition),
                            Map::putAll
                    );
            List<AgentMode> missingModes = Arrays.stream(AgentMode.values())
                    .filter(mode -> !loaded.containsKey(mode))
                    .toList();
            if (!missingModes.isEmpty()) {
                throw new IllegalStateException("Missing mode definitions for " + missingModes + " in " + modeDirectory.toAbsolutePath());
            }
            cache.set(Map.copyOf(loaded));
            log.info("Loaded {} mode definitions from {}", loaded.size(), modeDirectory.toAbsolutePath());
            return cache.get();
        } catch (IOException e) {
            throw new IllegalStateException("Failed loading mode definitions from " + modeDirectory.toAbsolutePath(), e);
        }
    }

    public Optional<ModeDefinition> find(AgentMode mode) {
        return Optional.ofNullable(cache.get().get(mode));
    }

    public ModeDefinition getRequired(AgentMode mode) {
        ModeDefinition definition = cache.get().get(mode);
        if (definition == null) {
            throw new IllegalStateException("Missing mode definition for " + mode + " in " + modeDirectory.toAbsolutePath());
        }
        return definition;
    }

    private boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json");
    }

    private ModeDefinition readDefinition(Path path) {
        try {
            ModeDefinition definition;
            String fileName = path.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".json")) {
                definition = jsonMapper.readValue(path.toFile(), ModeDefinition.class);
            } else {
                definition = yamlMapper.readValue(path.toFile(), ModeDefinition.class);
            }

            validate(path, definition);
            return definition;
        } catch (Exception e) {
            throw new IllegalStateException("Failed parsing mode file: " + path.toAbsolutePath(), e);
        }
    }

    private void validate(Path path, ModeDefinition definition) {
        if (definition.getMode() == null) {
            throw new IllegalArgumentException("Mode is required: " + path.getFileName());
        }
        if (definition.getFallbackMode() == definition.getMode()) {
            throw new IllegalArgumentException("fallbackMode cannot equal mode in " + path.getFileName());
        }
        if (definition.getRoot() == null || definition.getRoot().isBlank()) {
            throw new IllegalArgumentException("Root node is required: " + path.getFileName());
        }
        if (definition.getNodes() == null || definition.getNodes().isEmpty()) {
            throw new IllegalArgumentException("Nodes are required: " + path.getFileName());
        }
        ModeNodeDefinition rootNode = definition.getNodes().get(definition.getRoot());
        if (rootNode == null) {
            throw new IllegalArgumentException("Root node " + definition.getRoot() + " not found in " + path.getFileName());
        }

        List<String> missingRefs = definition.getNodes().values().stream()
                .map(ModeNodeDefinition::getSubAgents)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(ref -> !definition.getNodes().containsKey(ref))
                .distinct()
                .toList();

        if (!missingRefs.isEmpty()) {
            throw new IllegalArgumentException("Unknown node references " + missingRefs + " in " + path.getFileName());
        }

        List<String> invalidNodes = definition.getNodes().entrySet().stream()
                .filter(entry -> entry.getValue() == null
                        || entry.getValue().getKind() == null
                        || entry.getValue().getName() == null
                        || entry.getValue().getName().isBlank())
                .map(Map.Entry::getKey)
                .toList();
        if (!invalidNodes.isEmpty()) {
            throw new IllegalArgumentException("Invalid node definitions " + invalidNodes + " in " + path.getFileName());
        }
    }
}
