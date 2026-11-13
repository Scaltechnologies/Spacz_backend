#!/usr/bin/env bash
# Restores a backup set written by backup.sh. Run on the VPS as the deploy user:
#   bash /opt/spacz/deploy/restore.sh /opt/spacz/backups/<set>                 # dry run: verify + show plan
#   bash /opt/spacz/deploy/restore.sh /opt/spacz/backups/<set> --all --yes     # restore everything
#   bash /opt/spacz/deploy/restore.sh <set> --db | --hall-images | --user-docs  [--yes]
#
# Safety:
#   - nothing changes without --yes; checksums must match before anything is touched
#   - a fresh backup (label "pre-restore") of the CURRENT state is taken first, so a restore can be undone
#   - the SPACZ application containers are stopped during the restore (postgres keeps running) and
#     started again afterwards
#   - each database is dropped and recreated from its own dump with ON_ERROR_STOP (any error aborts)
# Roles are not touched (init-databases.sh creates them from .env); db/globals.sql.gz holds them for
# disaster recovery on a new server.
set -euo pipefail

PROJECT="${SPACZ_PROJECT:-spacz}"
POSTGRES_CONTAINER="${PROJECT}-postgres-1"
HELPER_IMAGE="${SPACZ_BACKUP_HELPER_IMAGE:-postgres:16-alpine}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() { sed -n '2,6p' "$0" | sed 's/^# \{0,1\}//' >&2; exit 2; }

SET_DIR="${1:-}"
[[ -n "$SET_DIR" && "$SET_DIR" != --* ]] || usage
shift
DO_DB=false; DO_IMAGES=false; DO_DOCS=false; CONFIRMED=false
for arg in "$@"; do
  case "$arg" in
    --all) DO_DB=true; DO_IMAGES=true; DO_DOCS=true ;;
    --db) DO_DB=true ;;
    --hall-images) DO_IMAGES=true ;;
    --user-docs) DO_DOCS=true ;;
    --yes) CONFIRMED=true ;;
    *) usage ;;
  esac
done
if ! $DO_DB && ! $DO_IMAGES && ! $DO_DOCS; then
  DO_DB=true; DO_IMAGES=true; DO_DOCS=true   # dry run / plan defaults to everything
  $CONFIRMED && { echo "Say what to restore: --all, --db, --hall-images and/or --user-docs" >&2; exit 2; }
fi

SET_DIR="$(cd "$SET_DIR" && pwd)"
echo "==> Verifying $SET_DIR"
for f in hall-images.tar.gz user-documents.tar.gz MANIFEST SHA256SUMS; do
  [[ -f "$SET_DIR/$f" ]] || { echo "Missing $f - not a complete backup set" >&2; exit 1; }
done
DB_DUMPS=()
for path in "$SET_DIR"/db/*.sql.gz; do
  [[ -f "$path" && "$(basename "$path")" != globals.sql.gz ]] && DB_DUMPS+=("$(basename "$path")")
done
((${#DB_DUMPS[@]})) || { echo "No database dumps in $SET_DIR/db - not a complete backup set" >&2; exit 1; }
(cd "$SET_DIR" && sha256sum -c --quiet SHA256SUMS) || { echo "Checksum mismatch - refusing to restore" >&2; exit 1; }
for dump in "${DB_DUMPS[@]}"; do
  grep -q " db/$dump\$" "$SET_DIR/SHA256SUMS" || { echo "db/$dump is not covered by SHA256SUMS" >&2; exit 1; }
done
sed 's/^/    /' "$SET_DIR/MANIFEST"

echo "==> Plan (project $PROJECT):"
for dump in "${DB_DUMPS[@]}"; do
  $DO_DB && echo "    database ${dump%.sql.gz} <- db/$dump (dropped and recreated)"
done
$DO_IMAGES && echo "    ${PROJECT}_hallimages <- hall-images.tar.gz    (volume contents are replaced)"
$DO_DOCS && echo "    ${PROJECT}_userdocs   <- user-documents.tar.gz (volume contents are replaced)"
if ! $CONFIRMED; then
  echo "Dry run only. Re-run with the parts to restore and --yes to apply."
  exit 0
fi

echo "==> Safety backup of the current state"
bash "$SCRIPT_DIR/backup.sh" pre-restore

# Application containers of this compose project, except postgres.
mapfile -t APP_CONTAINERS < <(docker ps -q --filter "label=com.docker.compose.project=$PROJECT" \
  | xargs -r docker inspect --format '{{index .Config.Labels "com.docker.compose.service"}} {{.ID}}' \
  | awk '$1 != "postgres" {print $2}')
restart_apps() {
  if ((${#APP_CONTAINERS[@]})); then
    echo "==> Starting application containers again"
    docker start "${APP_CONTAINERS[@]}" >/dev/null
  fi
}
if ((${#APP_CONTAINERS[@]})); then
  echo "==> Stopping ${#APP_CONTAINERS[@]} application container(s)"
  docker stop "${APP_CONTAINERS[@]}" >/dev/null
fi
trap restart_apps EXIT

restore_volume() {  # restore_volume <volume> <archive>
  local volume="$1" archive="$2"
  docker volume inspect "$volume" >/dev/null 2>&1 || docker volume create "$volume" >/dev/null
  echo "==> Restoring volume $volume"
  docker run --rm --network none -v "$volume":/dst -v "$SET_DIR":/backup:ro "$HELPER_IMAGE" \
    sh -c 'find /dst -mindepth 1 -delete && tar -xzpf "/backup/$1" --numeric-owner -C /dst' sh "$archive"
}

if $DO_DB; then
  for dump in "${DB_DUMPS[@]}"; do
    db="${dump%.sql.gz}"
    echo "==> Restoring database $db"
    # The application containers are stopped; close any other session so DROP DATABASE can run.
    echo "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = :'db' AND pid <> pg_backend_pid();" \
      | docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d postgres -qAtX -v ON_ERROR_STOP=1 -v db="$db" >/dev/null
    gunzip -c "$SET_DIR/db/$dump" \
      | docker exec -i "$POSTGRES_CONTAINER" psql -U postgres -d postgres -q -X -v ON_ERROR_STOP=1 >/dev/null
  done
fi
$DO_IMAGES && restore_volume "${PROJECT}_hallimages" hall-images.tar.gz
$DO_DOCS && restore_volume "${PROJECT}_userdocs" user-documents.tar.gz

echo "==> Restore finished from $SET_DIR"
