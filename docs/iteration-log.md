# Iteration Log

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
