#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

database="${BACKUP_DATABASE:-elderly_study}"
backup_dir="${BACKUP_DIR:-/var/backups/elderly-study}"
uploads_dir="${UPLOAD_DIR:-/var/lib/elderly-study/uploads}"
mysql_config="${MYSQL_BACKUP_CONFIG:-/etc/elderly-study/backup.cnf}"
[[ "$database" =~ ^[a-zA-Z0-9_]+$ ]] || { echo 'Invalid database name' >&2; exit 1; }
[[ "$backup_dir" = /* && "$uploads_dir" = /* && "$mysql_config" = /* ]] || { echo 'Absolute paths required' >&2; exit 1; }
[[ -f "$mysql_config" && -d "$uploads_dir" ]] || { echo 'Missing backup config or uploads directory' >&2; exit 1; }
mkdir -p -- "$backup_dir"
stamp="$(date -u +%Y%m%dT%H%M%SZ)-$$"
db_archive="$backup_dir/database-$stamp.sql.gz"
uploads_archive="$backup_dir/uploads-$stamp.tar.gz"
# 密码由私有文件读取，不出现在进程参数中。失败保留 .partial 以便排查。
mysqldump --defaults-extra-file="$mysql_config" --single-transaction --quick \
  --no-tablespaces --skip-lock-tables --set-gtid-purged=OFF --hex-blob \
  --databases "$database" | gzip > "$db_archive.partial"
gzip -t "$db_archive.partial"
tar -czf "$uploads_archive.partial" -C "$uploads_dir" .
tar -tzf "$uploads_archive.partial" > /dev/null
mv -- "$db_archive.partial" "$db_archive"
mv -- "$uploads_archive.partial" "$uploads_archive"
printf 'Backup completed: %s\n' "$stamp"
