#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /absolute/path/to/backup.sql.gz" >&2
  exit 64
fi

backup_file="$1"
container_name="${TRADE_MYSQL_CONTAINER:-demo-mysql}"
restore_database="trade_restore_verification"

test -f "${backup_file}"
gzip -t "${backup_file}"
docker exec "${container_name}" sh -ec 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE IF EXISTS trade_restore_verification; CREATE DATABASE trade_restore_verification"'
gzip -dc "${backup_file}" | docker exec -i "${container_name}" sh -ec 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" trade_restore_verification'
table_count="$(docker exec "${container_name}" sh -ec 'mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=\"trade_restore_verification\""')"
if [[ "${table_count}" -lt 8 ]]; then
  echo "Restore verification failed: only ${table_count} tables were restored" >&2
  exit 1
fi
docker exec "${container_name}" sh -ec 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "DROP DATABASE trade_restore_verification"'
echo "Restore verification passed with ${table_count} tables"
