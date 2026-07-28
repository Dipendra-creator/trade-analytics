#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backup_dir="${TRADE_BACKUP_DIR:-${project_dir}/backups}"
container_name="${TRADE_MYSQL_CONTAINER:-demo-mysql}"
database_name="${MYSQL_DATABASE:-demo}"
retention_days="${BACKUP_RETENTION_DAYS:-30}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="${backup_dir}/${database_name}-${timestamp}.sql.gz"

mkdir -p "${backup_dir}"
umask 077
docker exec "${container_name}" sh -ec 'exec mysqldump --single-transaction --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  | gzip -9 > "${backup_file}"
gzip -t "${backup_file}"
test "$(gzip -dc "${backup_file}" | wc -c)" -gt 1024
find "${backup_dir}" -maxdepth 1 -type f -name "${database_name}-*.sql.gz" -mtime "+${retention_days}" -delete
sha256sum "${backup_file}" > "${backup_file}.sha256"
echo "Verified MySQL backup: ${backup_file}"
