#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
umask 077
export PGPASSWORD="$DB_PASSWORD"
: "${PGHOST:=127.0.0.1}" "${PGPORT:=5432}" "${PGDATABASE:=growthhub}"
export PGHOST PGPORT PGDATABASE PGUSER="$DB_USER"
if [[ "$DB_URL" != "jdbc:postgresql://$PGHOST:$PGPORT/$PGDATABASE" ]]; then
 echo 'DB_URL 与 PGHOST/PGPORT/PGDATABASE 不一致，请显式配置同一目标再备份。' >&2; exit 1
fi
mkdir -p "$ROOT/backups"
file="$ROOT/backups/growthhub-$(date -u +%Y%m%dT%H%M%SZ)-$$.dump"
pg_dump --format=custom --no-owner --file="$file"
pg_restore --list "$file" >/dev/null
printf '备份已生成：%s
' "$file"
