#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
TARGET="${DEPLOY_TARGET:-oracle}"
REMOTE_DIR="${CAFES_REMOTE_DIR:-/home/ubuntu/cafes}"
HOST="${CAFES_HOST:-cafes.164.152.20.248.nip.io}"
RELEASE="$(date -u +%Y%m%d%H%M%S)-$$"
DIST_DIR="$REPO_DIR/webApp/build/dist/wasmJs/productionExecutable"

if [[ -z "$HOST" ]]; then
  echo "Set CAFES_HOST to the domain Traefik should serve (for example, cafes.example.com)." >&2
  exit 2
fi

if [[ ! "$HOST" =~ ^[A-Za-z0-9.-]+$ ]]; then
  echo "CAFES_HOST must contain only letters, numbers, dots, and hyphens." >&2
  exit 2
fi

if [[ "$REMOTE_DIR" != /* || "$REMOTE_DIR" == *..* || ! "$REMOTE_DIR" =~ ^/[A-Za-z0-9._/-]+$ ]]; then
  echo "CAFES_REMOTE_DIR must be a safe absolute path without '..' components." >&2
  exit 2
fi

for command in ssh rsync scp; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Required command not found: $command" >&2
    exit 2
  fi
done

echo "Building the production web app..."
"$REPO_DIR/gradlew" -p "$REPO_DIR" :webApp:wasmJsBrowserDistribution --rerun-tasks

if [[ ! -f "$DIST_DIR/index.html" ]]; then
  echo "Web distribution not found at $DIST_DIR" >&2
  exit 1
fi

sed -i -E "s|src=\"webApp\\.js(\\?v=[^\"]*)?\"|src=\"webApp.js?v=$RELEASE\"|g" "$DIST_DIR/index.html"
sed -i -E "s|food-classifier-worker\\.js(\\?v=[^']*)?|food-classifier-worker.js?v=$RELEASE|g" "$DIST_DIR/index.html"

REMOTE_RELEASE="$REMOTE_DIR/releases/$RELEASE"
echo "Uploading release $RELEASE to $TARGET:$REMOTE_RELEASE ..."
ssh "$TARGET" "mkdir -p '$REMOTE_RELEASE'"
rsync --archive --chmod=Du=rwx,Dgo=rx,Fu=rw,Fgo=r --delete "$DIST_DIR/" "$TARGET:$REMOTE_RELEASE/"

scp "$SCRIPT_DIR/compose.yaml" "$SCRIPT_DIR/nginx.conf" "$TARGET:$REMOTE_DIR/"
printf 'CAFES_HOST=%s\n' "$HOST" | ssh "$TARGET" "cat > '$REMOTE_DIR/.env'"

# Switch the static content atomically, recreate Nginx, and prune old releases (keep latest 5).
ssh "$TARGET" \
  "ln -sfn 'releases/$RELEASE' '$REMOTE_DIR/current.next' && mv -Tf '$REMOTE_DIR/current.next' '$REMOTE_DIR/current' && cd '$REMOTE_DIR' && docker compose pull && docker compose up -d --force-recreate && ls -1dt '$REMOTE_DIR/releases/'* | tail -n +6 | xargs -r rm -rf"

echo "Published https://$HOST"
