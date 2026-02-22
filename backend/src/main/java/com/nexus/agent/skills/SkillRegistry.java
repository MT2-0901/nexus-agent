package com.nexus.agent.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.nexus.agent.config.SkillProperties;
import com.nexus.agent.domain.AgentMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Component
public class SkillRegistry {

    private static final Logger log = LoggerFactory.getLogger(SkillRegistry.class);

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Path skillDirectory;

    private final AtomicReference<List<SkillDefinition>> cache = new AtomicReference<>(List.of());

    public SkillRegistry(SkillProperties skillProperties) {
        this.skillDirectory = Paths.get(skillProperties.getPath());
    }

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized List<SkillDefinition> reload() {
        if (!Files.exists(skillDirectory)) {
            log.warn("Skill directory {} not found, skipping load.", skillDirectory.toAbsolutePath());
            cache.set(List.of());
            return cache.get();
        }

        try (Stream<Path> files = Files.list(skillDirectory)) {
            List<SkillDefinition> loaded = files
                    .filter(Files::isRegularFile)
                    .filter(this::isSupported)
                    .map(this::readSkill)
                    .filter(Objects::nonNull)
                    .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
                    .toList();
            cache.set(loaded);
            log.info("Loaded {} skill files from {}", loaded.size(), skillDirectory.toAbsolutePath());
            return loaded;
        } catch (IOException e) {
            throw new IllegalStateException("Failed loading skills from " + skillDirectory.toAbsolutePath(), e);
        }
    }

    public List<SkillDefinition> listAll() {
        return cache.get();
    }

    public List<SkillDefinition> resolve(AgentMode mode, Set<String> requiredSkillNames) {
        Set<String> normalized = requiredSkillNames == null
                ? Set.of()
                : requiredSkillNames.stream().filter(Objects::nonNull).map(String::trim).map(String::toLowerCase).collect(java.util.stream.Collectors.toSet());

        return cache.get().stream()
                .filter(SkillDefinition::isEnabled)
                .filter(skill -> skill.supports(mode))
                .filter(skill -> normalized.isEmpty() || normalized.contains(skill.getName().toLowerCase()))
                .toList();
    }

    private boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json");
    }

    private SkillDefinition readSkill(Path path) {
        try {
            SkillDefinition definition;
            String fileName = path.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".json")) {
                definition = jsonMapper.readValue(path.toFile(), SkillDefinition.class);
            } else {
                definition = yamlMapper.readValue(path.toFile(), SkillDefinition.class);
            }

            if (definition.getName() == null || definition.getName().isBlank()) {
                throw new IllegalArgumentException("Skill name is required: " + path.getFileName());
            }
            return definition;
        } catch (Exception e) {
            throw new IllegalStateException("Failed parsing skill file: " + path.toAbsolutePath(), e);
        }
    }
}
