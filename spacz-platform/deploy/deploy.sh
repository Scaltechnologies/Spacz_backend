#!/usr/bin/env bash
# Pulls the images for one build and (re)starts the stack. Run on the VPS.
#   bash /opt/spacz/deploy/deploy.sh            # redeploy the last deployed tag
#   bash /opt/spacz/deploy/deploy.sh <git-sha>   # deploy a specific build (also used for rollback)
set -euo pipefail

APP_DIR=/opt/spacz
TAG_FILE="$APP_DIR/.image-tag"
ENV_FILE="$APP_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE - copy deploy/.env.production.example there and fill it in." >&2
  exit 1
fi

if [[ $# -ge 1 ]]; then
  IMAGE_TAG="$1"
elif [[ -f "$TAG_FILE" ]]; then
  IMAGE_TAG="$(cat "$TAG_FILE")"
else
  IMAGE_TAG=latest
fi
export IMAGE_TAG

cd "$APP_DIR/deploy"
compose() { docker compose --env-file "$ENV_FILE" -f docker-compose.prod.yml "$@"; }

echo "==> Deploying image tag $IMAGE_TAG"
compose pull --quiet
# --wait blocks until every container with a healthcheck reports healthy (fails otherwise).
compose up -d --remove-orphans --wait --wait-timeout 420
echo "$IMAGE_TAG" > "$TAG_FILE"

docker image prune -f >/dev/null
compose ps
echo "==> Deployed $IMAGE_TAG"
