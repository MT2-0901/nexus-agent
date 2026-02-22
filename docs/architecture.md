# Architecture Notes

## Why this structure

The backend is split by concerns so agent mode expansion, skill policies, and API integration can evolve independently:
- `AgentTopologyFactory` controls topology wiring.
- `ModeRegistry` controls topology configuration source-of-truth.
- `SkillRegistry` controls dynamic skill source-of-truth.
- `AgentOrchestratorService` executes ADK runs and normalizes response extraction.
- `AgUiProtocolService` translates AG-UI `run` requests into ADK execution and emits AG-UI event stream.
- `ChatHistoryStore` defines persistence abstraction, with `JdbcChatHistoryStore` as the default relational implementation.

The frontend now acts as an operator console with AG-UI streaming, runtime agent configuration, multimodal upload, and session/history management.

## Extension points

- Add new topology: extend `AgentMode` and add a new mode file under `backend/modes`.
- Add new tool: implement method in `ToolCatalog` and reference it in skill files.
- Add skill governance: enrich `SkillDefinition` with policy fields and apply in registry filtering.
- Tune role behavior: update instructions/descriptions in mode files without changing Java code.
- Switch storage backend: keep `ChatHistoryStore` contract unchanged, provide a new RDB implementation and datasource config (e.g., MySQL/PostgreSQL).
- Extend AG-UI behavior: keep event types and run payload structure AG-UI compatible, add richer events progressively without changing basic run contract.
