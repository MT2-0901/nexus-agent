# Architecture Notes

## Why this structure

The backend is split by concerns so agent mode expansion, skill policies, and API integration can evolve independently:
- `AgentTopologyFactory` controls topology wiring.
- `SkillRegistry` controls dynamic skill source-of-truth.
- `AgentOrchestratorService` executes ADK runs and normalizes response extraction.

The frontend is intentionally a minimal shell to preserve flexibility for future product direction.

## Extension points

- Add new topology: extend `AgentMode` and add builder branch in `AgentTopologyFactory`.
- Add new tool: implement method in `ToolCatalog` and reference it in skill files.
- Add skill governance: enrich `SkillDefinition` with policy fields and apply in registry filtering.
