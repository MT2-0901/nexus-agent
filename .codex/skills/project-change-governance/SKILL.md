---
name: project-change-governance
description: Enforce architecture-aware code delivery and documentation hygiene for this repository. Use when adding or modifying code, especially for new features or refactors. Always read the project's technical architecture and code organization first, keep changes readable and maintainable, record added functionality in the iteration log document, and update README when architecture changes.
---

# Project Change Governance

## Workflow

1. Review project architecture and code organization before writing any code.
2. Identify existing module boundaries, naming conventions, and dependency direction.
3. Determine change mode before implementation:
   - `CODE_ONLY`: code and behavior only, no data structure change
   - `CONFIG_OR_MODE`: config/prompt/agent mode or orchestration change
   - `DATA_CHANGE`: schema/table/field/index/sql behavior change
4. Implement changes by following existing patterns unless there is a clear improvement.
5. Keep code readable and maintainable:
   - Use clear names and small, focused functions/classes.
   - Avoid duplicated logic; extract reusable units when needed.
   - Add concise comments only for non-obvious decisions.
6. If the change mode is `DATA_CHANGE` and includes new SQL:
   - Add or update SQL scripts under `sql/` (create the folder if it does not exist).
   - Include clear SQL comments for intent, object scope, and rollback/compatibility notes.
   - Supplement ER structure documentation in `docs/` (prefer Mermaid ER diagram).
   - Supplement data flow structure in `docs/` (prefer Mermaid flowchart/sequence).
7. After coding, update the iteration log with the newly delivered functionality.
8. If the change modifies architecture, module boundaries, or technical stack, update `README.md` accordingly.

## Execution Checklist

1. Read architecture context in this order:
   - `README.md`
   - architecture docs under `docs/` (if present)
   - source tree and key entry points
2. Identify change mode (`CODE_ONLY` / `CONFIG_OR_MODE` / `DATA_CHANGE`) and define expected behavior change.
3. Implement and validate code changes.
4. If `DATA_CHANGE` with SQL additions:
   - ensure new SQL statements are maintained under `sql/`
   - ensure SQL comments are present and actionable
   - update or add ER diagram documentation under `docs/`
   - update or add data flow documentation under `docs/`
5. Update iteration record:
   - Prefer `docs/iteration-log.md`.
   - If missing, create it using `references/iteration-log-template.md`.
6. Update `README.md` when architecture-level changes are introduced.
7. In final delivery notes, explicitly list:
   - architecture/context reviewed
   - change mode decision
   - code files changed
   - SQL/ER/data-flow documentation changes (when applicable)
   - iteration log update location
   - whether `README.md` was updated (and why)

## Resource

- Iteration log template: `references/iteration-log-template.md`
