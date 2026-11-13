#!/usr/bin/env bash
# Backs up everything SPACZ cannot rebuild from git: all PostgreSQL databases AND the two upload
# volumes (vendor hall photos, student documents). Scheduled nightly by vps-setup.sh; run by hand with
#   bash /opt/spacz/deploy/backup.sh [label]          # label is optional, e.g. "pre-deploy"
#
# Every run writes a NEW directory and never overwrites an existing backup:
#   /opt/spacz/backups/<YYYYmmdd-HHMMSS>[-label]/
#     db/<database>.sql.gz   pg_dump --create --clean --if-exists, one per SPACZ database
#     db/globals.sql.gz      roles (pg_dumpall --globals-only; for disaster recovery, not auto-applied)
#     hall-images.tar.gz     volume spacz_hallimages   (/data/hall-images in studyhall-service)
#     user-documents.tar.gz  volume spacz_userdocs     (/data/user-documents in user-service)
#     MANIFEST, SHA256SUMS
# The set is built in a hidden ".incomplete-*" directory, verified (gzip/tar integrity, checksums)
# and only then renamed into place, so a listed set is always complete.
# Restore with deploy/restore.sh (dry run by default). Copy the backups off the server regularly.
set -euo pipefail
umask 077   # student documents are personal data

PROJECT="${SPACZ_PROJECT:-spacz}"                    # docker compose project name (docker-compose.prod.yml)
BACKUP_DIR="${SPACZ_BACKUP_DIR:-/opt/spacz/backups}"
RETENTION_DAYS="${SPACZ_BACKUP_RETENTION_DAYS:-14}"
POSTGRES_CONTAINER="${PROJECT}-postgres-1"
HELPER_IMAGE="${SPACZ_BACKUP_HELPER_IMAGE:-postgres:16-alpine}"   # already on the server; has tar + gzip
LABEL="${1:-}"

if [[ -n "$LABEL" && ! "$LABEL" =~ ^[A-Za-z0-9._-]{1,64}$ ]]; then
  echo "Invalid label: $LABEL" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"

# One backup at a time (nightly cron, a manual run and a pre-deploy run must not interleave).
exec 9>"$BACKUP_DIR/.backup.lock"
if ! flock -w 600 9; then
  echo "Another backup is still running (lock $BACKUP_DIR/.backup.lock)" >&2
  exit 1
fi

NAME="$(date +%Y%m%d-%H%M%S)${LABEL:+-$LABEL}"
FINAL="$BACKUP_DIR/$NAME"
WORK="$BACKUP_DIR/.incomplete-$NAME"
if [[ -e "$FINAL" || -e "$WORK" ]]; then
  echo "Backup $FINAL already exists; refusing to overwrite it." >&2
  exit 1
fi
mkdir "$WORK"
trap 'rm -rf -- "$WORK"' EXIT

# 1. Databases first: files uploaded after the dump are harmless extras in the file archives, whereas
#    the reverse order could leave database rows pointing at files that are not in the backup.
mkdir "$WORK/db"
mapfile -t DATABASES < <(docker exec "$POSTGRES_CONTAINER" psql -U postgres -d postgres -AtX -c \
  "SELECT datname FROM pg_database WHERE datallowconn AND datname NOT IN ('postgres', 'template0', 'template1') ORDER BY 1")
if ((${#DATABASES[@]} == 0)); then
  echo "No databases found in $POSTGRES_CONTAINER" >&2
  exit 1
fi
for db in "${DATABASES[@]}"; do
  docker exec "$POSTGRES_CONTAINER" pg_dump -U postgres --create --clean --if-exists "$db" | gzip > "$WORK/db/$db.sql.gz"
  if ! gunzip -c "$WORK/db/$db.sql.gz" | tail -n 5 | grep -q 'PostgreSQL database dump complete'; then
    echo "Dump of $db is incomplete" >&2
    exit 1
  fi
done
docker exec "$POSTGRES_CONTAINER" pg_dumpall -U postgres --globals-only | gzip > "$WORK/db/globals.sql.gz"
gzip -t "$WORK/db/globals.sql.gz"

# 2. Upload volumes, archived read-only through a throwaway container (numeric owners preserved).
archive_volume() {  # archive_volume <volume> <archive name>
  local volume="$1" archive="$2"
  if ! docker volume inspect "$volume" >/dev/null 2>&1; then
    # deploy.sh allows this for the backup taken before the first deploy that creates the volume.
    if [[ "${SPACZ_BACKUP_ALLOW_MISSING_VOLUMES:-}" == 1 ]]; then
      echo "Volume $volume does not exist yet; storing an empty archive" >&2
      MISSING_VOLUMES+=("$volume")
      docker run --rm --network none -v "$WORK":/backup "$HELPER_IMAGE" sh -c \
        'mkdir /empty && tar -czf "/backup/$1" -C /empty . && chown "$2" "/backup/$1" && chmod 600 "/backup/$1"' \
        sh "$archive" "$(id -u):$(id -g)"
      return
    fi
    echo "Volume $volume does not exist (is the stack deployed with docker-compose.prod.yml?)" >&2
    exit 1
  fi
  docker run --rm --network none -v "$volume":/src:ro -v "$WORK":/backup "$HELPER_IMAGE" \
    sh -c 'tar -czf "/backup/$1" --numeric-owner -C /src . && chown "$2" "/backup/$1" && chmod 600 "/backup/$1"' \
    sh "$archive" "$(id -u):$(id -g)"
  docker run --rm --network none -v "$WORK":/backup:ro "$HELPER_IMAGE" tar -tzf "/backup/$archive" >/dev/null
}
MISSING_VOLUMES=()
archive_volume "${PROJECT}_hallimages" hall-images.tar.gz
archive_volume "${PROJECT}_userdocs" user-documents.tar.gz

# 3. Manifest + checksums, then publish the set atomically.
{
  echo "created=$(date -Is)"
  echo "project=$PROJECT"
  echo "image_tag=$(cat "${SPACZ_APP_DIR:-/opt/spacz}/.image-tag" 2>/dev/null || echo unknown)"
  echo "label=${LABEL:-}"
  echo "databases=${DATABASES[*]}"
  echo "missing_volumes=${MISSING_VOLUMES[*]:-}"
} > "$WORK/MANIFEST"
(cd "$WORK" && sha256sum db/*.sql.gz hall-images.tar.gz user-documents.tar.gz MANIFEST > SHA256SUMS \
  && sha256sum -c --quiet SHA256SUMS)

if [[ -e "$FINAL" ]]; then
  echo "Backup $FINAL appeared meanwhile; refusing to overwrite it." >&2
  exit 1
fi
mv -n -T "$WORK" "$FINAL"
if [[ -e "$WORK" ]]; then
  echo "Could not move the backup into $FINAL" >&2
  exit 1
fi
trap - EXIT

# 4. Retention: only complete sets (and the old single-file dumps) older than RETENTION_DAYS.
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '20[0-9][0-9][0-1][0-9][0-3][0-9]-*' \
  -mtime +"$RETENTION_DAYS" -exec rm -rf -- {} +
find "$BACKUP_DIR" -maxdepth 1 -type f -name 'spacz-*.sql.gz' -mtime +"$RETENTION_DAYS" -delete

echo "$(date -Is) backup ok: $FINAL ($(du -sh "$FINAL" | cut -f1))"
