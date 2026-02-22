#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<USAGE
Usage: scripts/auto_commit_push.sh [--type TYPE] [--scope SCOPE] [--summary TEXT] [--dry-run]

Options:
  --type TYPE      Commit type (feat|fix|refactor|docs|chore), default: chore
  --scope SCOPE    Commit scope, default: repo
  --summary TEXT   Commit summary; auto-generated if omitted
  --dry-run        Print commit message and target branch, do not commit or push
USAGE
}

commit_type="chore"
scope="repo"
summary=""
dry_run="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --type)
      commit_type="${2:-}"
      shift 2
      ;;
    --scope)
      scope="${2:-}"
      shift 2
      ;;
    --summary)
      summary="${2:-}"
      shift 2
      ;;
    --dry-run)
      dry_run="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Not inside a git repository." >&2
  exit 1
fi

if ! git remote get-url origin >/dev/null 2>&1; then
  echo "Remote 'origin' is not configured." >&2
  exit 1
fi

branch="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$branch" == "HEAD" ]]; then
  echo "Detached HEAD detected. Switch to a branch before committing." >&2
  exit 1
fi

# Stage all tracked and untracked changes.
git add -A

if [[ -z "$(git status --porcelain)" ]]; then
  echo "Nothing to commit."
  exit 0
fi

changed_files=()
while IFS= read -r file; do
  changed_files+=("$file")
done < <(git diff --cached --name-only)
file_count="${#changed_files[@]}"

if [[ -z "$summary" ]]; then
  summary="update ${file_count} files"
fi

title="${commit_type}(${scope}): ${summary}"

body="Auto-generated commit from current staged diff.\n\nChanged paths:"
limit=$(( file_count < 10 ? file_count : 10 ))
for ((i=0; i<limit; i++)); do
  body+="\n- ${changed_files[$i]}"
done
if (( file_count > 10 )); then
  body+="\n- ... and $((file_count - 10)) more"
fi

if [[ "$dry_run" == "true" ]]; then
  echo "[DRY RUN] Branch: ${branch}"
  echo "[DRY RUN] Title: ${title}"
  echo "[DRY RUN] Body:"
  printf "%b\n" "$body"
  exit 0
fi

git commit -m "$title" -m "$body"
commit_hash="$(git rev-parse --short HEAD)"
git push origin "$branch"

echo "Committed and pushed successfully."
echo "Branch: ${branch}"
echo "Commit: ${commit_hash}"
echo "Title: ${title}"
