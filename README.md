# Nexus Agent Scaffold

This repository is now scaffolded for a Java-based multi-agent project using **Google ADK** on the backend and **Vue3** on the frontend.

中文文档: [`README.zh-CN.md`](README.zh-CN.md)

## Technical Architecture

### Backend (`backend/`)
- Language/runtime: Java 17
- Frameworks: Spring Boot + Google ADK (`com.google.adk:google-adk:0.5.0`)
- Responsibility: agent orchestration, skill loading, and API exposure

Supported agent topologies:
- `SINGLE`: one `LlmAgent` handles the full request
- `MASTER_SUB`: one master `LlmAgent` delegates to sub-agents
- `MULTI_WORKFLOW`: `ParallelAgent` + `SequentialAgent` pipeline for staged collaboration

### Dynamic Skill Loading
- Skill files are loaded from `backend/skills` at runtime.
- Supported formats: `.yaml`, `.yml`, `.json`
- Each skill can control:
  - `enabled`
  - `appliesTo` (which topology mode it applies to)
  - `instruction` (prompt overlay)
  - `tools` (bind local ADK function tools)

APIs:
- `POST /api/v1/chat`
- `GET /api/v1/skills`
- `POST /api/v1/skills/reload`
- `GET /api/v1/modes`

### Frontend (`frontend/`)
- Vue3 + Vite placeholder shell
- Current state: pre-reserved interaction shell with mode selection and chat request panel
- Purpose: keep integration path ready while product form is undecided

## Code Organization

- `backend/src/main/java/com/nexus/agent/config`: configuration properties
- `backend/src/main/java/com/nexus/agent/domain`: domain enums and shared types
- `backend/src/main/java/com/nexus/agent/skills`: dynamic skill loading + tool registry
- `backend/src/main/java/com/nexus/agent/service`: orchestration and topology factory
- `backend/src/main/java/com/nexus/agent/api`: REST controllers and exception mapping
- `backend/skills`: runtime skill definitions
- `frontend/src`: Vue application entry and placeholder UI
- `docs`: architecture and iteration records

## Quick Start

### Backend
```bash
cd backend
mvn spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

## Notes

- Configure model/auth according to your Google ADK environment before real usage.
- The scaffold focuses on architecture and extensibility, not final business workflow.
