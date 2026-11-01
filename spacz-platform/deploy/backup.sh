#!/usr/bin/env bash
# Dumps all SPACZ databases to /opt/spacz/backups and keeps the last 14 days.
# Scheduled nightly by vps-setup.sh. Restore one database with:
#   gunzip -c <file>.sql.gz | docker exec -i spacz-postgres-1 psql -U postgres
# Copy the backups off the server regularly (a VPS backup alone is not enough).
set -euo pipefail

BACKUP_DIR=/opt/spacz/backups
STAMP="$(date +%Y%m%d-%H%M%S)"

docker exec spacz-postgres-1 pg_dumpall -U postgres | gzip > "$BACKUP_DIR/spacz-$STAMP.sql.gz"
find "$BACKUP_DIR" -name 'spacz-*.sql.gz' -mtime +14 -delete
echo "$(date -Is) backup ok: spacz-$STAMP.sql.gz"
