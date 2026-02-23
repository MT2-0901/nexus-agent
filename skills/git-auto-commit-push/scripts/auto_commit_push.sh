#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<USAGE
Usage: scripts/auto_commit_push.sh [--single] [--type TYPE] [--scope SCOPE] [--summary TEXT] [--dry-run]

Options:
  --single         Create one commit for all changed files
  --type TYPE      Commit type (feat|fix|refactor|docs|chore); implies --single
  --scope SCOPE    Commit scope override for generated commit title(s)
  --summary TEXT   Commit summary; implies --single
  --dry-run        Show planned commits, do not commit or push
USAGE
}

commit_type=""
scope=""
summary=""
single_mode="false"
dry_run="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --single)
      single_mode="true"
      shift
      ;;
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

if [[ -n "$commit_type" || -n "$summary" ]]; then
  single_mode="true"
fi

if [[ -n "$commit_type" ]]; then
  case "$commit_type" in
    feat|fix|refactor|docs|chore)
      ;;
    *)
      echo "Invalid --type: $commit_type" >&2
      exit 1
      ;;
  esac
fi

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

if [[ -z "$(git status --porcelain)" ]]; then
  echo "Nothing to commit."
  exit 0
fi

declare -a all_files=()
append_unique_file() {
  local candidate="$1"
  local item
  [[ -z "$candidate" ]] && return 0
  if (( ${#all_files[@]} > 0 )); then
    for item in "${all_files[@]}"; do
      if [[ "$item" == "$candidate" ]]; then
        return 0
      fi
    done
  fi
  all_files+=("$candidate")
}

collect_changed_files() {
  local file
  while IFS= read -r file; do
    append_unique_file "$file"
  done < <(git diff --name-only)
  while IFS= read -r file; do
    append_unique_file "$file"
  done < <(git diff --cached --name-only)
  while IFS= read -r file; do
    append_unique_file "$file"
  done < <(git ls-files --others --exclude-standard)
}

collect_changed_files
file_count="${#all_files[@]}"
if (( file_count == 0 )); then
  echo "Nothing to commit."
  exit 0
fi

infer_type() {
  local file="$1"
  local lower
  lower="$(printf '%s' "$file" | tr '[:upper:]' '[:lower:]')"

  case "$lower" in
    docs/*|*.md|readme|readme.*)
      echo "docs"
      return 0
      ;;
  esac

  case "$lower" in
    *fix*|*bug*)
      echo "fix"
      return 0
      ;;
    *refactor*)
      echo "refactor"
      return 0
      ;;
  esac

  case "$lower" in
    backend/src/*|frontend/src/*|backend/modes/*|backend/skills/*)
      echo "feat"
      ;;
    *)
      echo "chore"
      ;;
  esac
}

infer_scope_from_file() {
  local file="$1"
  local lower
  lower="$(printf '%s' "$file" | tr '[:upper:]' '[:lower:]')"
  case "$lower" in
    backend/*)
      echo "backend"
      ;;
    frontend/*)
      echo "frontend"
      ;;
    docs/*|*.md)
      echo "docs"
      ;;
    skills/*|.codex/skills/*)
      echo "skills"
      ;;
    *)
      echo "repo"
      ;;
  esac
}

infer_scope_for_files() {
  local inferred=""
  local file
  local candidate
  for file in "$@"; do
    candidate="$(infer_scope_from_file "$file")"
    if [[ -z "$inferred" ]]; then
      inferred="$candidate"
      continue
    fi
    if [[ "$inferred" != "$candidate" ]]; then
      echo "repo"
      return 0
    fi
  done
  echo "${inferred:-repo}"
}

build_body() {
  local type="$1"
  shift
  local files=("$@")
  local count="${#files[@]}"
  local body_text
  body_text="Auto-generated ${type} commit grouped by change category.\n\nChanged paths:"
  local limit=$(( count < 10 ? count : 10 ))
  local i
  for ((i=0; i<limit; i++)); do
    body_text+="\n- ${files[$i]}"
  done
  if (( count > 10 )); then
    body_text+="\n- ... and $((count - 10)) more"
  fi
  printf "%b" "$body_text"
}

commit_hashes=()
commit_titles=()
planned_commits=0

create_commit() {
  local type="$1"
  local scope_value="$2"
  local summary_value="$3"
  shift 3
  local files=("$@")
  local count="${#files[@]}"

  if (( count == 0 )); then
    return 0
  fi

  if [[ -z "$scope_value" ]]; then
    scope_value="$(infer_scope_for_files "${files[@]}")"
  fi
  if [[ -z "$summary_value" ]]; then
    summary_value="update ${count} files"
  fi

  local title="${type}(${scope_value}): ${summary_value}"
  local body
  body="$(build_body "$type" "${files[@]}")"

  if [[ "$dry_run" == "true" ]]; then
    planned_commits=$((planned_commits + 1))
    echo "[DRY RUN] Title: ${title}"
    echo "[DRY RUN] Body:"
    printf "%b\n" "$body"
    echo
    return 0
  fi

  git reset --quiet
  git add -A -- "${files[@]}"
  if git diff --cached --quiet; then
    return 0
  fi

  git commit -m "$title" -m "$body"
  commit_hashes+=("$(git rev-parse --short HEAD)")
  commit_titles+=("$title")
}

if [[ "$single_mode" == "true" ]]; then
  if [[ -z "$commit_type" ]]; then
    commit_type="chore"
  fi
  single_scope="${scope:-repo}"
  create_commit "$commit_type" "$single_scope" "$summary" "${all_files[@]}"
else
  declare -a feat_files=()
  declare -a fix_files=()
  declare -a refactor_files=()
  declare -a docs_files=()
  declare -a chore_files=()

  file=""
  for file in "${all_files[@]}"; do
    case "$(infer_type "$file")" in
      feat)
        feat_files+=("$file")
        ;;
      fix)
        fix_files+=("$file")
        ;;
      refactor)
        refactor_files+=("$file")
        ;;
      docs)
        docs_files+=("$file")
        ;;
      *)
        chore_files+=("$file")
        ;;
    esac
  done

  declare -a group_files=()
  for group in feat fix refactor docs chore; do
    group_files=()
    case "$group" in
      feat)
        if (( ${#feat_files[@]} > 0 )); then
          group_files=("${feat_files[@]}")
        fi
        ;;
      fix)
        if (( ${#fix_files[@]} > 0 )); then
          group_files=("${fix_files[@]}")
        fi
        ;;
      refactor)
        if (( ${#refactor_files[@]} > 0 )); then
          group_files=("${refactor_files[@]}")
        fi
        ;;
      docs)
        if (( ${#docs_files[@]} > 0 )); then
          group_files=("${docs_files[@]}")
        fi
        ;;
      chore)
        if (( ${#chore_files[@]} > 0 )); then
          group_files=("${chore_files[@]}")
        fi
        ;;
    esac
    if (( ${#group_files[@]} > 0 )); then
      create_commit "$group" "$scope" "" "${group_files[@]}"
    fi
  done
fi

if [[ "$dry_run" == "true" ]]; then
  echo "[DRY RUN] Branch: ${branch}"
  echo "[DRY RUN] Planned commits: ${planned_commits}"
  echo "[DRY RUN] Push target: origin/${branch}"
  exit 0
fi

if [[ "${#commit_hashes[@]}" -eq 0 ]]; then
  echo "Nothing to commit."
  exit 0
fi

git push origin "$branch"

echo "Committed and pushed successfully."
echo "Branch: ${branch}"
index=0
for hash in "${commit_hashes[@]}"; do
  index=$((index + 1))
  echo "Commit ${index}: ${hash}"
  echo "Title ${index}: ${commit_titles[$((index - 1))]}"
done
echo "Push: origin/${branch}"
