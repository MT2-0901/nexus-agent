---
name: git-auto-commit-push
description: Automatically stage local git changes, generate a commit message from the current diff, create the commit, and push to the remote branch. Use when the user asks to auto-fill commit messages, commit current work, or push code directly to remote without manual git message writing.
---

# Git Auto Commit Push

Use this skill to complete git delivery in one pass: stage -> message -> commit -> push.

## Workflow

1. Confirm repository state with `git status --short --branch`.
2. If there are no changes, stop and report "nothing to commit".
3. Run `scripts/auto_commit_push.sh`.
4. Report back:
   - branch
   - commit hash
   - commit title
   - push destination

## Message Strategy

Generate commit title from staged diff summary:
- Default format: `chore(repo): update <N> files`
- If change type is obvious from diff, prefer one of:
  - `feat(scope): ...`
  - `fix(scope): ...`
  - `refactor(scope): ...`
  - `docs(scope): ...`

Commit body rules:
- First section: short summary of change intent.
- Second section: bullet list of key changed paths (top 10).

## Command Contract

Primary command:

```bash
scripts/auto_commit_push.sh
```

Optional overrides:

```bash
scripts/auto_commit_push.sh --type feat --scope agent --summary "add multi-agent scheduler"
scripts/auto_commit_push.sh --dry-run
```

## Constraints

- Do not use interactive git commands.
- Do not modify remote or branch config.
- If `origin` or current branch is missing, stop and report actionable error.
- If push is rejected, stop and report git output; do not force push.
