#!/bin/bash
# Creates one database and one login role per service. Each role owns only its own database and
# PUBLIC is denied CONNECT, so a service's credentials cannot open another service's database.
#
# Runs automatically on the first start of an empty PostgreSQL volume. It is also safe to run again
# (deploy.sh does, before every deploy): existing roles and databases are left exactly as they are,
# so a service added later (e.g. notification-service) gets its database on an existing server.
set -euo pipefail

create_service_db() {
  local db="$1" role="$2" password="$3"
  if [[ -z "$password" ]]; then
    echo "No password given for $role; set it in .env" >&2
    return 1
  fi
  psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER:-postgres}" --dbname postgres \
       -v db="$db" -v role="$role" -v password="$password" <<-'EOSQL'
	SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'password')
	WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'role') \gexec
	SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'role')
	WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'db') \gexec
	REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
EOSQL
  echo "database $db owned by $role is ready"
}

create_service_db spacz_auth         spacz_auth         "${AUTH_DB_PASSWORD:-}"
create_service_db spacz_user         spacz_user         "${USER_DB_PASSWORD:-}"
create_service_db spacz_studyhall    spacz_studyhall    "${STUDYHALL_DB_PASSWORD:-}"
create_service_db spacz_admin        spacz_admin        "${ADMIN_DB_PASSWORD:-}"
create_service_db spacz_notification spacz_notification "${NOTIFICATION_DB_PASSWORD:-}"
