# Nexus Agent Scaffold

This repository is now scaffolded for a Java-based multi-agent project using **Google ADK** on the backend and **Vue3** on the frontend.

中文文档: [`README.zh-CN.md`](README.zh-CN.md)

## Technical Architecture

### Backend (`backend/`)
- Language/runtime: Java 17
- Frameworks: Spring Boot + Google ADK (`com.google.adk:google-adk:0.5.0`) + JDBC
- Responsibility: agent orchestration, skill loading, and API exposure

Supported agent topologies:
- `SINGLE`: one `LlmAgent` handles the full request
- `MASTER_SUB`: one master `LlmAgent` delegates to sub-agents
- `MULTI_WORKFLOW`: `ParallelAgent` + `SequentialAgent` pipeline for staged collaboration

### Dynamic Mode Topology Loading
- Mode topology files are loaded from `backend/modes` at runtime.
- Supported formats: `.yaml`, `.yml`, `.json`
- Each mode definition controls:
  - `root` node and node graph (`LLM` / `PARALLEL` / `SEQUENTIAL`)
  - role-level instruction/description
  - optional `fallbackMode` for degraded execution when a mode build fails

### Dynamic Skill Loading
- Skill files are loaded from `backend/skills` at runtime.
- Supported formats: `.yaml`, `.yml`, `.json`
- Each skill can control:
  - `enabled`
  - `appliesTo` (which topology mode it applies to)
  - `instruction` (prompt overlay)
  - `tools` (bind local ADK function tools)

### Relational Persistence (SQLite default)
- Chat exchanges are persisted via `ChatHistoryStore` abstraction.
- Default provider is SQLite (`jdbc:sqlite:./nexus-agent.db`).
- The storage implementation is RDB-oriented and can be extended to MySQL/PostgreSQL by replacing datasource and store implementation.

### AG-UI Protocol Streaming
- Added AG-UI compatible HTTP streaming endpoint: `POST /api/v1/agui/run` (SSE event stream).
- Implemented event sequence with `RUN_STARTED`, `TEXT_MESSAGE_START`, `TEXT_MESSAGE_CONTENT`, `TEXT_MESSAGE_END`, `RUN_FINISHED`, and `RUN_ERROR`.
- Supports multimodal user input (`text` + `image` content blocks with base64 payload).
- Supports runtime agent config via `forwardedProps` (mode/model/userId/sessionId/skillNames).

APIs:
- `POST /api/v1/chat`
- `GET /api/v1/chat/history`
- `POST /api/v1/agui/run`
- `GET /api/v1/skills`
- `POST /api/v1/skills/reload`
- `GET /api/v1/modes`
- `GET /api/v1/models`

### Frontend (`frontend/`)
- Vue3 + Vite AG-UI client console
- Current state:
  - agent runtime config panel (mode/model/user/skills)
  - AG-UI stream chat UI
  - image upload and multimodal send
  - local session persistence and backend history sync
- Purpose: provide an extensible operator console for multi-agent runtime experiments

## Code Organization

- `backend/src/main/java/com/nexus/agent/config`: configuration properties
- `backend/src/main/java/com/nexus/agent/domain`: domain enums and shared types
- `backend/src/main/java/com/nexus/agent/modes`: mode definition model + registry
- `backend/src/main/java/com/nexus/agent/skills`: dynamic skill loading + tool registry
- `backend/src/main/java/com/nexus/agent/persistence`: persistence abstraction + relational implementation
- `backend/src/main/java/com/nexus/agent/service`: orchestration and topology factory
- `backend/src/main/java/com/nexus/agent/api`: REST controllers and exception mapping
- `backend/src/main/resources/sql`: database schema scripts
- `backend/modes`: runtime mode topology definitions
- `backend/skills`: runtime skill definitions
- `frontend/src`: Vue application entry and placeholder UI
- `docs`: architecture and iteration records

## Quick Start

### Backend
```bash
cd backend
mvn spring-boot:run
```

Or run from repository root:
```bash
mvn -f backend/pom.xml spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

### IntelliJ IDEA
- Open the repository root as a Maven project (`pom.xml` at root).
- Use shared run config `Backend Spring Boot` (from `.run/Backend-SpringBoot.run.xml`) for direct backend startup.

## Notes

- Configure model/auth according to your Google ADK environment before real usage.
- The scaffold focuses on architecture and extensibility, not final business workflow.
