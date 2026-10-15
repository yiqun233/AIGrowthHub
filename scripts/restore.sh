#!/usr/bin/env bash
source "$(dirname "$0")/common.sh"
if [[ $# -ne 2 ]]; then echo '用法：bash scripts/restore.sh <dump文件> <新建空数据库名>'; exit 1; fi
file="$1"; target="$2"
if [[ ! "$target" =~ ^growthhub_restore_[a-z0-9_]+$ ]]; then echo '恢复目标必须为独立 growthhub_restore_* 数据库，禁止覆盖开发库。'; exit 1; fi
export PGPASSWORD="$DB_PASSWORD" PGHOST="${PGHOST:-127.0.0.1}" PGPORT="${PGPORT:-5432}" PGUSER="$DB_USER" PGDATABASE="$target"
if [[ $(psql -X -tAc "SELECT count(*) FROM pg_tables WHERE schemaname='public'") != 0 ]]; then echo '目标库不为空，停止恢复。'; exit 1; fi
pg_restore --exit-on-error --no-owner --no-privileges --dbname="$target" "$file"
echo "恢复完成：$target"
