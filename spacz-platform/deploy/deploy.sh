#!/usr/bin/env bash
# Pulls the images for one build and (re)starts the stack. Run on the VPS.
#   bash /opt/spacz/deploy/deploy.sh             # redeploy the last deployed tag
#   bash /opt/spacz/deploy/deploy.sh <git-sha>   # deploy a specific build
#   bash /opt/spacz/deploy/deploy.sh --rollback  # go back to the build deployed before the current one
#
# If the new containers do not become healthy, the previous build is restarted automatically and
# the script exits with an error (so the GitHub Actions run turns red).
set -euo pipefail

APP_DIR="${SPACZ_APP_DIR:-/opt/spacz}"
TAG_FILE="$APP_DIR/.image-tag"
PREVIOUS_TAG_FILE="$APP_DIR/.image-tag.previous"
ENV_FILE="$APP_DIR/.env"
WAIT_TIMEOUT=420

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE - copy deploy/.env.production.example there and fill it in." >&2
  exit 1
fi

CURRENT_TAG="$(cat "$TAG_FILE" 2>/dev/null || true)"
PREVIOUS_TAG="$(cat "$PREVIOUS_TAG_FILE" 2>/dev/null || true)"

if [[ "${1:-}" == "--rollback" ]]; then
  if [[ -z "$PREVIOUS_TAG" ]]; then
    echo "No previous deployment recorded in $PREVIOUS_TAG_FILE - pass a tag instead." >&2
    exit 1
  fi
  IMAGE_TAG="$PREVIOUS_TAG"
elif [[ $# -ge 1 ]]; then
  IMAGE_TAG="$1"
elif [[ -n "$CURRENT_TAG" ]]; then
  IMAGE_TAG="$CURRENT_TAG"
else
  IMAGE_TAG=latest
fi

if [[ ! "$IMAGE_TAG" =~ ^[A-Za-z0-9._-]{1,128}$ ]]; then
  echo "Invalid image tag: $IMAGE_TAG" >&2
  exit 1
fi

cd "$APP_DIR/deploy"
compose() { docker compose --env-file "$ENV_FILE" -f docker-compose.prod.yml "$@"; }

# Starts one tag and waits until every container with a healthcheck reports healthy.
start() {
  export IMAGE_TAG="$1"
  compose pull --quiet && compose up -d --remove-orphans --wait --wait-timeout "$WAIT_TIMEOUT"
}

echo "==> Deploying image tag $IMAGE_TAG (currently running: ${CURRENT_TAG:-none})"
if ! start "$IMAGE_TAG"; then
  echo "!!! Tag $IMAGE_TAG did not become healthy. Container state and recent logs:" >&2
  compose ps -a >&2 || true
  compose logs --tail 60 >&2 || true

  if [[ -n "$CURRENT_TAG" && "$CURRENT_TAG" != "$IMAGE_TAG" ]]; then
    echo "==> Rolling back to $CURRENT_TAG" >&2
    if start "$CURRENT_TAG"; then
      echo "==> Rolled back to $CURRENT_TAG; it is running again." >&2
    else
      echo "!!! Rollback to $CURRENT_TAG failed too - check the server manually." >&2
    fi
  fi
  exit 1
fi

if [[ -n "$CURRENT_TAG" && "$CURRENT_TAG" != "$IMAGE_TAG" ]]; then
  echo "$CURRENT_TAG" > "$PREVIOUS_TAG_FILE"
fi
echo "$IMAGE_TAG" > "$TAG_FILE"

docker image prune -f >/dev/null
# Keep only the SPACZ images of the running and the previous build (for a fast rollback).
KEEP="$IMAGE_TAG|${CURRENT_TAG:-$IMAGE_TAG}|latest"
docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '/spacz-[a-z]+-service:' \
  | grep -vE ":($KEEP)\$" | xargs -r docker rmi >/dev/null 2>&1 || true
compose ps
echo "==> Deployed $IMAGE_TAG"
