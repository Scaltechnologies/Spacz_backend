#!/bin/bash
# Runs once, on the first start of an empty PostgreSQL volume.
# Creates one database and one login role per service. Each role owns only its own database and
# PUBLIC is denied CONNECT, so a service's credentials cannot open another service's database.
set -euo pipefail

create_service_db() {
  local db="$1" role="$2" password="$3"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
       -v db="$db" -v role="$role" -v password="$password" <<-'EOSQL'
	CREATE ROLE :"role" LOGIN PASSWORD :'password';
	CREATE DATABASE :"db" OWNER :"role";
	REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
EOSQL
  echo "created database $db owned by $role"
}

create_service_db spacz_auth      spacz_auth      "$AUTH_DB_PASSWORD"
create_service_db spacz_user      spacz_user      "$USER_DB_PASSWORD"
create_service_db spacz_studyhall spacz_studyhall "$STUDYHALL_DB_PASSWORD"
create_service_db spacz_admin     spacz_admin     "$ADMIN_DB_PASSWORD"
