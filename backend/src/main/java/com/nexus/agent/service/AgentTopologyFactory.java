package com.nexus.agent.service;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.tools.BaseTool;
import com.nexus.agent.config.AdkProperties;
import com.nexus.agent.domain.AgentMode;
import com.nexus.agent.skills.SkillDefinition;
import com.nexus.agent.skills.ToolCatalog;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentTopologyFactory {

    private final AdkProperties adkProperties;
    private final SkillPromptComposer skillPromptComposer;
    private final ToolCatalog toolCatalog;

    public AgentTopologyFactory(AdkProperties adkProperties,
                                SkillPromptComposer skillPromptComposer,
                                ToolCatalog toolCatalog) {
        this.adkProperties = adkProperties;
        this.skillPromptComposer = skillPromptComposer;
        this.toolCatalog = toolCatalog;
    }

    public BaseAgent create(AgentMode mode, List<SkillDefinition> activeSkills) {
        String skillPrompt = skillPromptComposer.compose(activeSkills);
        List<BaseTool> tools = toolCatalog.resolve(activeSkills);

        return switch (mode) {
            case SINGLE -> buildSingle(skillPrompt, tools);
            case MASTER_SUB -> buildMasterSub(skillPrompt, tools);
            case MULTI_WORKFLOW -> buildMultiWorkflow(skillPrompt, tools);
        };
    }

    private BaseAgent buildSingle(String skillPrompt, List<BaseTool> tools) {
        String instruction = "Handle the entire request independently with clear reasoning and concise delivery."
                + skillPrompt;

        return LlmAgent.builder()
                .name("single-agent")
                .description("Single-agent execution")
                .model(adkProperties.getModel())
                .instruction(instruction)
                .tools(tools)
                .build();
    }

    private BaseAgent buildMasterSub(String skillPrompt, List<BaseTool> tools) {
        BaseAgent planner = LlmAgent.builder()
                .name("planner-agent")
                .description("Plan decomposition specialist")
                .model(adkProperties.getModel())
                .instruction("Break tasks into executable steps and constraints." + skillPrompt)
                .tools(tools)
                .build();

        BaseAgent executor = LlmAgent.builder()
                .name("executor-agent")
                .description("Execution specialist")
                .model(adkProperties.getModel())
                .instruction("Execute implementation details and return concrete outputs." + skillPrompt)
                .tools(tools)
                .build();

        return LlmAgent.builder()
                .name("master-agent")
                .description("Master agent coordinating sub-agents")
                .model(adkProperties.getModel())
                .instruction("Delegate to planner-agent and executor-agent when useful, then synthesize a final answer."
                        + skillPrompt)
                .subAgents(planner, executor)
                .tools(tools)
                .build();
    }

    private BaseAgent buildMultiWorkflow(String skillPrompt, List<BaseTool> tools) {
        BaseAgent researcher = LlmAgent.builder()
                .name("researcher-agent")
                .description("Collect requirements and assumptions")
                .model(adkProperties.getModel())
                .instruction("Focus on analysis, requirements, and risks." + skillPrompt)
                .tools(tools)
                .build();

        BaseAgent builder = LlmAgent.builder()
                .name("builder-agent")
                .description("Design and build solution details")
                .model(adkProperties.getModel())
                .instruction("Draft implementation-oriented outputs based on research." + skillPrompt)
                .tools(tools)
                .build();

        BaseAgent reviewer = LlmAgent.builder()
                .name("reviewer-agent")
                .description("Quality and consistency reviewer")
                .model(adkProperties.getModel())
                .instruction("Review and reconcile outputs into production-ready response." + skillPrompt)
                .tools(tools)
                .build();

        BaseAgent parallelPhase = ParallelAgent.builder()
                .name("parallel-phase")
                .description("Parallel analysis and build")
                .subAgents(researcher, builder)
                .build();

        return SequentialAgent.builder()
                .name("multi-workflow-root")
                .description("Multi-agent sequential orchestration")
                .subAgents(parallelPhase, reviewer)
                .build();
    }
}
