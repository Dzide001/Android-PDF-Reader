#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BACKUP_DIR="${ROOT_DIR}/backups"
LABEL="snapshot"
INCLUDE_BUILD=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dest)
      BACKUP_DIR="$2"
      shift 2
      ;;
    --label)
      LABEL="$2"
      shift 2
      ;;
    --include-build)
      INCLUDE_BUILD=true
      shift
      ;;
    *)
      echo "Unknown argument: $1"
      echo "Usage: $0 [--dest /path/to/backups] [--label custom] [--include-build]"
      exit 1
      ;;
  esac
done

mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "no-git")"
COMMIT="$(git rev-parse --short HEAD 2>/dev/null || echo "no-commit")"
SNAPSHOT_NAME="${LABEL}-${TIMESTAMP}-${BRANCH}-${COMMIT}"
STAGING_DIR="$(mktemp -d)"
PROJECT_STAGE="${STAGING_DIR}/project"
mkdir -p "$PROJECT_STAGE"

echo "==> Creating project snapshot staging area"
RSYNC_EXCLUDES=(
  ".git"
  ".gradle"
  "build"
  "app/build"
  "core/**/build"
  "**/.cxx"
  "*.iml"
  ".idea"
  ".DS_Store"
  "backups"
  "*.log"
  ".env"
  ".env.local"
)

if [[ "$INCLUDE_BUILD" == false ]]; then
  RSYNC_EXCLUDES+=("**/build")
fi

RSYNC_EXCLUDE_ARGS=()
for item in "${RSYNC_EXCLUDES[@]}"; do
  RSYNC_EXCLUDE_ARGS+=(--exclude="$item")
done

rsync -a "${RSYNC_EXCLUDE_ARGS[@]}" "$ROOT_DIR/" "$PROJECT_STAGE/"

META_FILE="${PROJECT_STAGE}/SNAPSHOT_INFO.txt"
{
  echo "Snapshot: ${SNAPSHOT_NAME}"
  echo "Created: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo "Branch: ${BRANCH}"
  echo "Commit: ${COMMIT}"
  echo "Dirty: $(git status --porcelain 2>/dev/null | wc -l | tr -d ' ') files"
  echo "Include build outputs: ${INCLUDE_BUILD}"
} > "$META_FILE"

TAR_PATH="${BACKUP_DIR}/${SNAPSHOT_NAME}.tar.gz"
echo "==> Writing compressed snapshot: ${TAR_PATH}"
tar -czf "$TAR_PATH" -C "$STAGING_DIR" project

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  BUNDLE_PATH="${BACKUP_DIR}/${SNAPSHOT_NAME}.bundle"
  echo "==> Writing git history bundle: ${BUNDLE_PATH}"
  git bundle create "$BUNDLE_PATH" --all
fi

rm -rf "$STAGING_DIR"

echo "Backup complete"
echo "- Snapshot archive: $TAR_PATH"
if [[ -f "${BACKUP_DIR}/${SNAPSHOT_NAME}.bundle" ]]; then
  echo "- Git bundle: ${BACKUP_DIR}/${SNAPSHOT_NAME}.bundle"
fi
