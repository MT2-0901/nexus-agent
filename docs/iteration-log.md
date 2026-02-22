# Iteration Log

## 2026-02-22 - Improve IntelliJ Project Recognition and One-Click Backend Start

### Summary
Added root Maven aggregator metadata and shared IntelliJ run configuration so IDEA can import the repository root as a Maven project and start backend directly.

### Scope
- Feature / module: project bootstrap and IDE startup workflow
- Problem solved: opening repository root in IDEA could load only a plain `.iml` module, causing poor dependency/model recognition and no direct backend run entry
- User-visible behavior change: root project is now Maven-importable (`pom.xml` at root) and includes shared `Backend Spring Boot` run config

### Implementation
- Key design decisions:
  - Add a minimal root aggregator `pom.xml` to declare module boundary (`backend`) without altering backend dependency management.
  - Use Maven run configuration (`.run/Backend-SpringBoot.run.xml`) so startup does not depend on local module naming.
  - Keep backend build entry unchanged (`backend/pom.xml`) for compatibility.
- Main files changed:
  - `pom.xml`
  - `.run/Backend-SpringBoot.run.xml`
  - `README.md`
  - `README.zh-CN.md`
  - `docs/iteration-log.md`
- Backward compatibility notes:
  - Existing backend commands remain valid (`cd backend && mvn spring-boot:run`).

### Validation
- Tests run:
  - `mvn -q -DskipTests compile`
  - `mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.arguments=--server.port=19090`
- Manual verification:
  - Confirmed root Maven reactor recognizes backend module.
  - Confirmed backend still starts successfully after aggregator/config additions.

### Architecture Impact
- Architecture changed: No

## 2026-02-22 - Add Spring Boot Backend Entry Point

### Summary
Added a dedicated Spring Boot application entry class so the backend can be started through standard Boot lifecycle commands.

### Scope
- Feature / module: backend bootstrap and application startup
- Problem solved: backend module had no discoverable `main` class, causing `spring-boot:run` to fail
- User-visible behavior change: `cd backend && mvn spring-boot:run` now has a valid startup entry

### Implementation
- Key design decisions:
  - Place entry class at package root (`com.nexus.agent`) to keep default component scanning aligned with existing package layout.
  - Enable `@ConfigurationPropertiesScan("com.nexus.agent.config")` so existing `@ConfigurationProperties` classes are registered without per-class wiring.
- Main files changed:
  - `backend/src/main/java/com/nexus/agent/NexusAgentBackendApplication.java`
  - `docs/iteration-log.md`
- Backward compatibility notes:
  - No API or data model change; startup wiring only.

### Validation
- Tests run:
  - `mvn -f backend/pom.xml -q -DskipTests compile`
  - `mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.arguments=--server.port=19090`
- Manual verification:
  - Confirmed previous error `Unable to find a suitable main class` is resolved.

### Architecture Impact
- Architecture changed: No

## 2026-02-22 - Deliver AG-UI Streaming Console and Runtime Agent Configuration

### Summary
Implemented AG-UI protocol based streaming communication and upgraded the frontend into an extensible agent console with runtime config, multimodal upload, and session persistence capabilities.

### Scope
- Feature / module: AG-UI backend protocol adapter + frontend operator console
- Problem solved: frontend previously used a simple request/response placeholder and lacked streaming, multimodal input, runtime configuration, and session management
- User-visible behavior change: users can configure mode/model/skills, stream chat via AG-UI events, upload images, switch models, and persist/reload conversation sessions

### Implementation
- Key design decisions:
  - Introduce `POST /api/v1/agui/run` as AG-UI-compatible SSE stream endpoint.
  - Translate AG-UI `messages` content blocks (`text`/`image`) into ADK `Content` parts.
  - Keep runtime control app-specific fields in AG-UI `forwardedProps` for `mode/model/userId/sessionId/skillNames`.
  - Refactor topology creation to support per-request model override while enforcing configured model allowlist.
- Main files changed:
  - `backend/src/main/java/com/nexus/agent/api/AgUiController.java`
  - `backend/src/main/java/com/nexus/agent/api/dto/agui/**`
  - `backend/src/main/java/com/nexus/agent/service/AgUiProtocolService.java`
  - `backend/src/main/java/com/nexus/agent/service/AgUiRunResult.java`
  - `backend/src/main/java/com/nexus/agent/service/AgentTopologyFactory.java`
  - `backend/src/main/java/com/nexus/agent/config/AdkProperties.java`
  - `backend/src/main/java/com/nexus/agent/api/ChatController.java`
  - `backend/src/main/java/com/nexus/agent/config/WebCorsConfig.java`
  - `backend/src/main/resources/application.yaml`
  - `frontend/src/App.vue`
  - `frontend/src/styles.css`
  - `frontend/vite.config.js`
  - `README.md`
  - `README.zh-CN.md`
  - `docs/architecture.md`
  - `docs/iteration-log.md`
- Backward compatibility notes:
  - Existing `POST /api/v1/chat` API remains available.
  - Added AG-UI endpoint (`/api/v1/agui/run`) and model metadata endpoint (`/api/v1/models`) without removing old paths.

### Validation
- Tests run:
  - `cd backend && mvn -q -DskipTests compile`
- Manual verification:
  - Confirmed backend compiles with AG-UI service and controller wiring.
  - Confirmed frontend implements SSE parsing for AG-UI events and renders stream output.
- Unresolved:
  - Frontend build not executed because local `frontend` dependencies were not installed (`vite: command not found`).

### Architecture Impact
- Architecture changed: Yes
- README sections updated:
  - Technical Architecture
  - API list
  - Frontend section

## 2026-02-22 - Add Relational Persistence with SQLite Default

### Summary
Added chat persistence based on a relational storage abstraction, with SQLite as the default backend and extension points for MySQL/PostgreSQL.

### Scope
- Feature / module: backend chat persistence and history query
- Problem solved: chat request/response data was in-memory only and not queryable after execution
- User-visible behavior change: chat turns are now persisted and can be queried by session via API

### Implementation
- Key design decisions:
  - Introduce `ChatHistoryStore` as storage abstraction to decouple orchestration flow from concrete database engine.
  - Provide `JdbcChatHistoryStore` default implementation and SQLite schema bootstrap script.
  - Keep persistence configurable via `nexus.persistence` and `spring.datasource` to support future RDB migration.
- Main files changed:
  - `backend/src/main/java/com/nexus/agent/persistence/**`
  - `backend/src/main/java/com/nexus/agent/config/PersistenceProperties.java`
  - `backend/src/main/java/com/nexus/agent/domain/PersistenceProvider.java`
  - `backend/src/main/java/com/nexus/agent/service/AgentOrchestratorService.java`
  - `backend/src/main/java/com/nexus/agent/api/ChatController.java`
  - `backend/src/main/java/com/nexus/agent/api/dto/ChatHistoryItem.java`
  - `backend/src/main/resources/sql/schema-sqlite.sql`
  - `backend/src/main/resources/application.yaml`
  - `backend/pom.xml`
  - `README.md`
  - `README.zh-CN.md`
  - `docs/architecture.md`
  - `docs/persistence-er.md`
  - `docs/persistence-flow.md`
  - `docs/iteration-log.md`
- Backward compatibility notes:
  - Existing `POST /api/v1/chat` contract remains unchanged.
  - Added new read-only endpoint `GET /api/v1/chat/history`.

### Validation
- Tests run:
  - `cd backend && mvn -q -DskipTests compile`
- Manual verification:
  - Verified chat service writes history records through persistence abstraction.
  - Verified history API path and session/limit parameter handling in controller-service flow.

### Architecture Impact
- Architecture changed: Yes
- README sections updated:
  - Technical Architecture
  - Code Organization

## 2026-02-22 - Enhance Project Governance for SQL Changes

### Summary
Updated the `project-change-governance` skill to require change-mode judgment and add explicit governance for SQL-related changes.

### Scope
- Feature / module: delivery governance skill rules
- Problem solved: SQL additions were not previously enforced with script location, comments, ER updates, and data-flow documentation
- User-visible behavior change: future SQL additions must include `sql/` scripts with comments and corresponding ER/data-flow documentation updates

### Implementation
- Key design decisions:
  - Introduce explicit change modes (`CODE_ONLY`, `CONFIG_OR_MODE`, `DATA_CHANGE`) in workflow and checklist.
  - Enforce SQL-specific documentation hygiene under `sql/` and `docs/`.
  - Keep skill content synchronized between runtime-local (`.codex`) and repository (`skills`) copies.
- Main files changed:
  - `.codex/skills/project-change-governance/SKILL.md`
  - `skills/project-change-governance/SKILL.md`
  - `docs/iteration-log.md`
- Backward compatibility notes:
  - No runtime code changes; governance policy update only.

### Validation
- Tests run:
  - None (documentation/policy-only update)
- Manual verification:
  - Confirmed both skill copies contain identical new workflow/checklist requirements.

### Architecture Impact
- Architecture changed: No

## 2026-02-22 - Externalize Mode Topology Definitions

### Summary
Refactored agent topology construction to be configuration-driven, moving role composition and workflow wiring from hardcoded Java branches into runtime-loaded mode definition files.

### Scope
- Feature / module: backend mode topology orchestration
- Problem solved: reduce uncertainty and change cost for mode-specific agent design by decoupling topology/instructions from code
- User-visible behavior change: existing modes continue to work, and mode fallback chain is now configurable (`MULTI_WORKFLOW -> MASTER_SUB -> SINGLE`)

### Implementation
- Key design decisions:
  - Introduce `ModeRegistry` to load mode definitions from `backend/modes`.
  - Represent mode topology as node graphs (`LLM` / `PARALLEL` / `SEQUENTIAL`) with `root` entry.
  - Add fallback handling in topology creation so failed mode assembly can degrade to a safer mode.
- Main files changed:
  - `backend/src/main/java/com/nexus/agent/service/AgentTopologyFactory.java`
  - `backend/src/main/java/com/nexus/agent/modes/**`
  - `backend/src/main/java/com/nexus/agent/config/ModeProperties.java`
  - `backend/src/main/resources/application.yaml`
  - `backend/modes/**`
  - `README.md`
  - `README.zh-CN.md`
  - `docs/architecture.md`
  - `docs/iteration-log.md`
- Backward compatibility notes:
  - API contract and `AgentMode` enum stay unchanged.
  - Existing mode names remain valid.

### Validation
- Tests run:
  - `cd backend && mvn -q -DskipTests compile`
- Manual verification:
  - Verified topology and role prompts are resolved from YAML mode files.
  - Verified fallback metadata is present in definitions.

### Architecture Impact
- Architecture changed: Yes
- README sections updated:
  - Technical Architecture
  - Code Organization

## 2026-02-22 - Add Chinese README

### Summary
Added a Chinese documentation counterpart for the project root README and linked it from the default English README for bilingual discoverability.

### Scope
- Feature / module: project root documentation
- Problem solved: provide Chinese onboarding documentation without replacing the canonical English README
- User-visible behavior change: users can open `README.zh-CN.md` directly from `README.md`

### Implementation
- Key design decisions:
  - Keep `README.md` as the default entry and add a lightweight language link.
  - Mirror the existing architecture/quick-start structure in Chinese for consistency.
- Main files changed:
  - `README.md`
  - `README.zh-CN.md`
  - `docs/iteration-log.md`
- Backward compatibility notes:
  - No runtime code changes; documentation-only update.

### Validation
- Tests run:
  - None (documentation-only change)
- Manual verification:
  - Checked markdown structure and section parity between English and Chinese README files.

### Architecture Impact
- Architecture changed: No

## 2026-02-22 - Initial Multi-Agent Scaffold

### Summary
Built a baseline scaffold for a Java + Google ADK backend and Vue3 frontend, with three agent runtime topologies and dynamic skill loading.

### Scope
- Feature / module: backend agent runtime foundation, dynamic skills, frontend placeholder shell
- Problem solved: establish an extensible starting architecture for single-agent and multi-agent execution modes
- User-visible behavior change: new backend APIs and placeholder UI are available for local integration

### Implementation
- Key design decisions:
  - Separate agent topology construction from runtime execution.
  - Load skills from file system to avoid code-level coupling for instruction policies.
  - Keep frontend minimal while preserving API contract testing path.
- Main files changed:
  - `backend/**`
  - `frontend/**`
  - `README.md`
  - `docs/architecture.md`
  - `docs/iteration-log.md`
- Backward compatibility notes:
  - Existing root sample code remains untouched; new scaffold lives under `backend` and `frontend`.

### Validation
- Tests run:
  - `cd backend && mvn -q -DskipTests compile`
- Manual verification:
  - Confirmed skill file parsing path and API endpoint mappings in code.

### Architecture Impact
- Architecture changed: Yes
- README sections updated:
  - Technical Architecture
  - Dynamic Skill Loading
  - Code Organization
  - Quick Start
