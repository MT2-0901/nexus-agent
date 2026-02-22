package com.nexus.agent.api;

import com.nexus.agent.api.dto.ChatRequest;
import com.nexus.agent.api.dto.ChatResponse;
import com.nexus.agent.api.dto.ChatHistoryItem;
import com.nexus.agent.api.dto.SkillView;
import com.nexus.agent.config.AdkProperties;
import com.nexus.agent.domain.AgentMode;
import com.nexus.agent.service.AgentOrchestratorService;
import com.nexus.agent.skills.SkillRegistry;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final AgentOrchestratorService orchestratorService;
    private final SkillRegistry skillRegistry;
    private final AdkProperties adkProperties;

    public ChatController(AgentOrchestratorService orchestratorService,
                          SkillRegistry skillRegistry,
                          AdkProperties adkProperties) {
        this.orchestratorService = orchestratorService;
        this.skillRegistry = skillRegistry;
        this.adkProperties = adkProperties;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return orchestratorService.chat(request);
    }

    @GetMapping("/chat/history")
    public List<ChatHistoryItem> history(@RequestParam String sessionId,
                                         @RequestParam(required = false) Integer limit) {
        return orchestratorService.listSessionHistory(sessionId, limit).stream()
                .map(item -> new ChatHistoryItem(
                        item.id(),
                        item.sessionId(),
                        item.userId(),
                        item.mode(),
                        item.requestMessage(),
                        item.responseMessage(),
                        item.activatedSkills(),
                        item.eventCount(),
                        item.timestamp()
                ))
                .toList();
    }

    @GetMapping("/skills")
    public List<SkillView> listSkills() {
        return skillRegistry.listAll().stream()
                .map(skill -> new SkillView(
                        skill.getName(),
                        skill.getDescription(),
                        skill.isEnabled(),
                        skill.getAppliesTo(),
                        skill.getTools()
                ))
                .toList();
    }

    @PostMapping("/skills/reload")
    public Map<String, Object> reloadSkills() {
        List<SkillView> skills = skillRegistry.reload().stream()
                .map(skill -> new SkillView(
                        skill.getName(),
                        skill.getDescription(),
                        skill.isEnabled(),
                        skill.getAppliesTo(),
                        skill.getTools()
                ))
                .toList();

        return Map.of(
                "count", skills.size(),
                "skills", skills
        );
    }

    @GetMapping("/modes")
    public List<String> modes() {
        return Arrays.stream(AgentMode.values()).map(Enum::name).toList();
    }

    @GetMapping("/models")
    public List<String> models() {
        return adkProperties.getAvailableModels();
    }
}
