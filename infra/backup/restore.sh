#!/usr/bin/env bash
# Restore one encrypted backup from R2 into the configured PGDATABASE.
# Usage: restore.sh <object-path-in-bucket> <path-to-age-private-key>
#   e.g. restore.sh daily/aos-daily-20261101T021500Z.sql.gz.age /tmp/k
# Run only with the api container stopped and PGDATABASE freshly created (see docs/OPS.md).
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: restore.sh <object-path-in-bucket> <path-to-age-private-key>" >&2
  exit 2
fi
object="$1"
key="$2"
[[ -r "$key" ]] || { echo "restore.sh: cannot read key file $key" >&2; exit 1; }

export RCLONE_CONFIG_R2_TYPE=s3
export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ACCESS_KEY_ID="$BACKUP_R2_ACCESS_KEY_ID"
export RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$BACKUP_R2_SECRET_ACCESS_KEY"
export RCLONE_CONFIG_R2_ENDPOINT="$BACKUP_R2_ENDPOINT"

rclone cat "r2:${BACKUP_R2_BUCKET}/${object}" | age -d -i "$key" | gunzip | psql --quiet --set ON_ERROR_STOP=1
echo "restore ok: ${object}"
