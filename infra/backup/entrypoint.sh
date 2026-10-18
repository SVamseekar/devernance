#!/bin/sh
# crond starts jobs with an empty environment, so persist the backup variables where the crontab can source them.
set -eu

env_file="${AOS_BACKUP_ENV_FILE:-/run/aos-backup.env}"
umask 077
export -p | grep -E "^export (PG[A-Z]+|BACKUP_[A-Z0-9_]+)=" > "$env_file"
chmod 600 "$env_file"

exec crond -f -l 8
