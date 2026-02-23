---
name: git-auto-commit-push
description: Automatically stage local git changes, classify them by commit type, create grouped commits, and push to the remote branch. Use when the user asks to auto-fill commit messages, commit current work, or push code directly to remote while keeping commits logically separated by change type.
---

# Git Auto Commit Push

Use this skill to complete git delivery in one pass: classify -> grouped commits -> push.

## Workflow

1. Confirm repository state with `git status --short --branch`.
2. If there are no changes, stop and report "nothing to commit".
3. Run `scripts/auto_commit_push.sh`.
4. Report back:
   - branch
   - commit hashes
   - commit titles
   - push destination

## Message Strategy

Default behavior generates multiple commits, not one all-in commit.

Type inference priority:
- `docs`: paths under `docs/`, markdown files (`*.md`), README changes
- `fix`: paths containing `fix` or `bug`
- `refactor`: paths containing `refactor`
- `feat`: application source and runtime topology/skill definitions (`backend/src`, `frontend/src`, `backend/modes`, `backend/skills`)
- `chore`: remaining tooling/config/infra files

Title format per commit:
- `<type>(<scope>): update <N> files`
- scope is inferred by module (`backend`, `frontend`, `docs`, `skills`), fallback `repo`

Body rules:
- First section: short summary of change intent.
- Second section: bullet list of key changed paths (top 10).

## Command Contract

Primary command:

```bash
scripts/auto_commit_push.sh
```

Optional overrides:

```bash
scripts/auto_commit_push.sh --dry-run
scripts/auto_commit_push.sh --single
scripts/auto_commit_push.sh --type feat --scope backend --summary "add multi-agent scheduler"
```

Notes:
- `--single` forces one commit for all files.
- `--type` or `--summary` implies `--single`.

## Constraints

- Do not use interactive git commands.
- Do not modify remote or branch config.
- Do not collapse all files into one commit unless user explicitly asks or passes `--single`/`--type`.
- If `origin` or current branch is missing, stop and report actionable error.
- If push is rejected, stop and report git output; do not force push.
