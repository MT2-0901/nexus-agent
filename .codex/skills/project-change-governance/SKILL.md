---
name: project-change-governance
description: Enforce architecture-aware code delivery and documentation hygiene for this repository. Use when adding or modifying code, especially for new features or refactors. Always read the project's technical architecture and code organization first, keep changes readable and maintainable, record added functionality in the iteration log document, and update README when architecture changes.
---

# Project Change Governance

## Workflow

1. Review project architecture and code organization before writing any code.
2. Identify existing module boundaries, naming conventions, and dependency direction.
3. Implement changes by following existing patterns unless there is a clear improvement.
4. Keep code readable and maintainable:
   - Use clear names and small, focused functions/classes.
   - Avoid duplicated logic; extract reusable units when needed.
   - Add concise comments only for non-obvious decisions.
5. After coding, update the iteration log with the newly delivered functionality.
6. If the change modifies architecture, module boundaries, or technical stack, update `README.md` accordingly.

## Execution Checklist

1. Read architecture context in this order:
   - `README.md`
   - architecture docs under `docs/` (if present)
   - source tree and key entry points
2. Define affected components and expected behavior change.
3. Implement and validate code changes.
4. Update iteration record:
   - Prefer `docs/iteration-log.md`.
   - If missing, create it using `references/iteration-log-template.md`.
5. Update `README.md` when architecture-level changes are introduced.
6. In final delivery notes, explicitly list:
   - architecture/context reviewed
   - code files changed
   - iteration log update location
   - whether `README.md` was updated (and why)

## Resource

- Iteration log template: `references/iteration-log-template.md`
