#!/usr/bin/env bash
set -euo pipefail
umask 077
root="${BACKUP_ROOT:-/backups}"
[[ "$root" = /* && "$root" != / ]] || { echo 'Invalid backup directory'; exit 1; }
mkdir -p "$root/daily" "$root/weekly"
while true; do
 success=true
 stamp="$(date -u +%F)"
 file="$root/daily/growthhub-$stamp.dump"
 if [[ ! -s "$file" ]] || ! pg_restore --list "$file" >/dev/null 2>&1; then
  if pg_dump --format=custom --no-owner --file="$file.partial" && pg_restore --list "$file.partial" >/dev/null; then
   mv "$file.partial" "$file"
  else
   success=false
   echo 'Backup failed; existing backups retained' >&2
  fi
 fi
 if [[ "$success" == true ]]; then
  week="$root/weekly/growthhub-$(date -u +%G-W%V).dump"
  if [[ ! -s "$week" ]] || ! pg_restore --list "$week" >/dev/null 2>&1; then cp "$file" "$week"; fi
  find "$root/daily" -maxdepth 1 -type f -name 'growthhub-*.dump' -mtime +6 -delete
  find "$root/weekly" -maxdepth 1 -type f -name 'growthhub-*.dump' -mtime +27 -delete
  echo "Backup verified: $stamp"
 fi
 if [[ "${BACKUP_ONCE:-false}" == true ]]; then
  if [[ "$success" == true ]]; then exit 0; else exit 1; fi
 fi
 sleep 3600
done
