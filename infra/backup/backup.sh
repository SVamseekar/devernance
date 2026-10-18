#!/usr/bin/env bash
# Dump the database, compress, encrypt to the offline age key, and copy to R2.
# Usage: backup.sh daily | weekly | pre-deploy
# Only daily and weekly ping the heartbeat; a missing ping alerts within 26 hours.
set -euo pipefail

kind="${1:-daily}"
case "$kind" in
  daily | weekly | pre-deploy) ;;
  *) echo "backup.sh: unknown kind '$kind' (daily, weekly, pre-deploy)" >&2; exit 2 ;;
esac

for var in BACKUP_AGE_RECIPIENT BACKUP_R2_ENDPOINT BACKUP_R2_BUCKET BACKUP_R2_ACCESS_KEY_ID BACKUP_R2_SECRET_ACCESS_KEY; do
  if [[ -z "${!var:-}" ]]; then
    echo "backup.sh: $var is not set" >&2
    exit 1
  fi
done

export RCLONE_CONFIG_R2_TYPE=s3
export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ACCESS_KEY_ID="$BACKUP_R2_ACCESS_KEY_ID"
export RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$BACKUP_R2_SECRET_ACCESS_KEY"
export RCLONE_CONFIG_R2_ENDPOINT="$BACKUP_R2_ENDPOINT"
export RCLONE_CONFIG_R2_NO_CHECK_BUCKET=true

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
file="$(mktemp "${TMPDIR:-/tmp}/aos-${kind}-XXXXXX")"
trap 'rm -f "$file"' EXIT

pg_dump --format=plain --no-owner --no-privileges | gzip -9 | age -r "$BACKUP_AGE_RECIPIENT" > "$file"

size="$(wc -c < "$file" | tr -d ' ')"
if [[ "$size" -lt 1024 ]]; then
  echo "backup.sh: backup suspiciously small ($size bytes); not uploaded" >&2
  exit 1
fi

rclone copyto "$file" "r2:${BACKUP_R2_BUCKET}/${kind}/aos-${kind}-${stamp}.sql.gz.age"

if [[ "$kind" != "pre-deploy" && -n "${BACKUP_HEARTBEAT_URL:-}" ]]; then
  curl -fsS -m 10 -o /dev/null "$BACKUP_HEARTBEAT_URL"
fi
echo "backup ok: ${kind} ${stamp} ${size} bytes"
