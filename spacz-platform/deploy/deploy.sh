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

# Settings added after the first deploy. A missing or empty one is generated once and saved to .env,
# so a server set up before the setting existed keeps deploying. Only safe for values nothing depends
# on yet: NOTIFICATION_DB_PASSWORD is applied when its database is first created (init-databases.sh),
# and ADMIN_SECRETS_ENCRYPTION_KEY only encrypts payment secrets saved after it exists.
# Back up .env after the first such deploy (the encryption key cannot be recovered).
ensure_setting() {  # ensure_setting <KEY> <generator command...>
  local key="$1"; shift
  if grep -qE "^${key}=.+" "$ENV_FILE"; then return 0; fi
  local value
  value="$("$@")"
  if [[ -z "$value" ]]; then
    echo "Could not generate $key - add it to $ENV_FILE by hand. Nothing was changed." >&2
    exit 1
  fi
  sed -i "/^${key}=\$/d" "$ENV_FILE"
  printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  echo "==> Generated $key and saved it to $ENV_FILE (back this file up)"
}
gen_password() { od -An -tx1 -N24 /dev/urandom | tr -d ' \n'; }
gen_aes_key() { head -c 32 /dev/urandom | base64 -w0; }
ensure_setting NOTIFICATION_DB_PASSWORD gen_password
ensure_setting ADMIN_SECRETS_ENCRYPTION_KEY gen_aes_key

# Services added after earlier builds. A build from before a service existed has no image for it, so
# deploying or rolling back to such a build starts the stack without that service.
OPTIONAL_SERVICES=(notification-service)

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
# (Called in an `if`, where `set -e` does not apply: every step checks its own result.)
start() {
  export IMAGE_TAG="$1"
  local skip=() required
  for svc in "${OPTIONAL_SERVICES[@]}"; do
    if ! compose pull --quiet "$svc" >/dev/null 2>&1; then
      echo "    No $svc image for $IMAGE_TAG; starting without it."
      skip+=(--scale "$svc=0")
    fi
  done
  required=$(compose config --services | grep -vxF -f <(printf '%s\n' "${OPTIONAL_SERVICES[@]}")) || return 1
  # shellcheck disable=SC2086  # one service name per word
  compose pull --quiet $required || return 1
  # Databases first: creates the database of a newly added service on this existing server (the
  # script leaves existing databases and passwords untouched), before that service starts.
  compose up -d --wait --wait-timeout "$WAIT_TIMEOUT" postgres || return 1
  compose exec -T postgres bash -s < "$APP_DIR/docker/postgres/init-databases.sh" || return 1
  compose up -d --remove-orphans --wait --wait-timeout "$WAIT_TIMEOUT" "${skip[@]}"
}

# Restore point: a rollback only swaps images, it cannot undo database migrations or data changes
# the new build applies on startup. Skipped on the very first deploy (no database yet).
if [[ "${SPACZ_SKIP_PREDEPLOY_BACKUP:-}" != 1 ]] && [[ -n "$(docker ps -q --filter 'name=^spacz-postgres-1$')" ]]; then
  echo "==> Backup before deploying $IMAGE_TAG"
  if ! SPACZ_BACKUP_ALLOW_MISSING_VOLUMES=1 bash "$APP_DIR/deploy/backup.sh" pre-deploy; then
    echo "!!! Pre-deploy backup failed; nothing was changed. (Set SPACZ_SKIP_PREDEPLOY_BACKUP=1 to override.)" >&2
    exit 1
  fi
fi

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
