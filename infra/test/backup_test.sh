#!/usr/bin/env bash
# Behaviour tests for infra/backup/{entrypoint.sh,backup.sh,restore.sh,crontab}.
# pg_dump, psql, age, rclone and curl are stubbed on PATH; gzip is real. R2 is a local directory.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKUP_DIR="$ROOT/infra/backup"
passed=0
failed=0

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

make_stubs() {
  local bin="$1"
  mkdir -p "$bin"
  cat > "$bin/pg_dump" <<'SH'
#!/usr/bin/env bash
[[ "${STUB_PG_DUMP_FAIL:-0}" == "1" ]] && { echo "pg_dump: connection refused" >&2; exit 1; }
head -c "${STUB_DUMP_BYTES:-200000}" /dev/urandom | base64
SH
  cat > "$bin/psql" <<'SH'
#!/usr/bin/env bash
cat > "$STUB_PSQL_INPUT"
SH
  # age: -r <recipient> encrypts, -d -i <key> decrypts. The stub tags the stream so a decrypt of plain data fails.
  cat > "$bin/age" <<'SH'
#!/usr/bin/env bash
if [[ "$1" == "-r" ]]; then printf 'AGE:%s\n' "$2"; cat; exit 0; fi
if [[ "$1" == "-d" ]]; then IFS= read -r header; [[ "$header" == AGE:* ]] || exit 1; cat; exit 0; fi
exit 2
SH
  cat > "$bin/rclone" <<'SH'
#!/usr/bin/env bash
[[ -n "${RCLONE_CONFIG_R2_ACCESS_KEY_ID:-}" ]] || { echo "rclone: no credentials" >&2; exit 1; }
case "$1" in
  copyto) dest="$STUB_R2/${3#r2:}"; mkdir -p "$(dirname "$dest")"; cp "$2" "$dest" ;;
  cat) cat "$STUB_R2/${2#r2:}" ;;
  *) exit 2 ;;
esac
SH
  cat > "$bin/curl" <<'SH'
#!/usr/bin/env bash
for arg in "$@"; do last="$arg"; done
echo "$last" >> "$STUB_CURL_LOG"
SH
  cat > "$bin/crond" <<'SH'
#!/usr/bin/env bash
echo "crond $*" >> "$STUB_CURL_LOG"
SH
  chmod +x "$bin"/*
}

# Fresh sandbox per test: stubs, fake R2, temp dir, and the backup environment.
setup() {
  sandbox="$work/$1"
  mkdir -p "$sandbox/r2" "$sandbox/tmp"
  make_stubs "$sandbox/bin"
  export PATH="$sandbox/bin:$ORIGINAL_PATH"
  export STUB_R2="$sandbox/r2" STUB_CURL_LOG="$sandbox/curl.log" STUB_PSQL_INPUT="$sandbox/psql.in"
  export TMPDIR="$sandbox/tmp"
  export PGHOST=postgres PGDATABASE=aos PGUSER=aos PGPASSWORD=pw
  export BACKUP_AGE_RECIPIENT=age1testrecipient BACKUP_R2_ENDPOINT=https://r2.example BACKUP_R2_BUCKET=aos-backups
  export BACKUP_R2_ACCESS_KEY_ID=key BACKUP_R2_SECRET_ACCESS_KEY=secret BACKUP_HEARTBEAT_URL=https://heartbeat.example/ping
  unset STUB_PG_DUMP_FAIL STUB_DUMP_BYTES
  : > "$STUB_CURL_LOG"
}

ok() { passed=$((passed + 1)); echo "  ok   $1"; }
bad() { failed=$((failed + 1)); echo "  FAIL $1: $2"; }
objects() { (cd "$STUB_R2" && find . -type f | sed 's|^\./||' | sort); }

ORIGINAL_PATH="$PATH"

test_daily_backup_uploads_encrypted_dump_and_pings_heartbeat() {
  local name="${FUNCNAME[0]}"; setup "$name"
  local out; out="$(bash "$BACKUP_DIR/backup.sh" daily 2>&1)" || { bad "$name" "exit non-zero: $out"; return; }
  local objs; objs="$(objects)"
  [[ "$objs" =~ ^aos-backups/daily/aos-daily-[0-9]{8}T[0-9]{6}Z\.sql\.gz\.age$ ]] || { bad "$name" "objects: $objs"; return; }
  head -n1 "$STUB_R2/$objs" | grep -q '^AGE:age1testrecipient$' || { bad "$name" "object not encrypted to recipient"; return; }
  [[ "$(cat "$STUB_CURL_LOG")" == "https://heartbeat.example/ping" ]] || { bad "$name" "heartbeat log: $(cat "$STUB_CURL_LOG")"; return; }
  [[ -z "$(ls -A "$TMPDIR")" ]] || { bad "$name" "temp files left: $(ls "$TMPDIR")"; return; }
  [[ "$out" == *"backup ok: daily"* ]] || { bad "$name" "output: $out"; return; }
  ok "$name"
}

test_pre_deploy_backup_skips_heartbeat() {
  local name="${FUNCNAME[0]}"; setup "$name"
  bash "$BACKUP_DIR/backup.sh" pre-deploy > /dev/null 2>&1 || { bad "$name" "exit non-zero"; return; }
  objects | grep -q '^aos-backups/pre-deploy/aos-pre-deploy-' || { bad "$name" "objects: $(objects)"; return; }
  [[ ! -s "$STUB_CURL_LOG" ]] || { bad "$name" "heartbeat pinged: $(cat "$STUB_CURL_LOG")"; return; }
  ok "$name"
}

test_suspiciously_small_dump_fails_without_upload_or_heartbeat() {
  local name="${FUNCNAME[0]}"; setup "$name"
  export STUB_DUMP_BYTES=0
  if bash "$BACKUP_DIR/backup.sh" daily > "$sandbox/out" 2>&1; then bad "$name" "exit zero"; return; fi
  grep -q "suspiciously small" "$sandbox/out" || { bad "$name" "output: $(cat "$sandbox/out")"; return; }
  [[ -z "$(objects)" ]] || { bad "$name" "uploaded: $(objects)"; return; }
  [[ ! -s "$STUB_CURL_LOG" ]] || { bad "$name" "heartbeat pinged"; return; }
  [[ -z "$(ls -A "$TMPDIR")" ]] || { bad "$name" "temp files left: $(ls "$TMPDIR")"; return; }
  ok "$name"
}

test_failed_pg_dump_fails_without_upload() {
  local name="${FUNCNAME[0]}"; setup "$name"
  export STUB_PG_DUMP_FAIL=1
  if bash "$BACKUP_DIR/backup.sh" daily > "$sandbox/out" 2>&1; then bad "$name" "exit zero"; return; fi
  [[ -z "$(objects)" ]] || { bad "$name" "uploaded: $(objects)"; return; }
  [[ ! -s "$STUB_CURL_LOG" ]] || { bad "$name" "heartbeat pinged"; return; }
  [[ -z "$(ls -A "$TMPDIR")" ]] || { bad "$name" "temp files left: $(ls "$TMPDIR")"; return; }
  ok "$name"
}

test_missing_recipient_fails_before_dumping() {
  local name="${FUNCNAME[0]}"; setup "$name"
  unset BACKUP_AGE_RECIPIENT
  if bash "$BACKUP_DIR/backup.sh" daily > "$sandbox/out" 2>&1; then bad "$name" "exit zero"; return; fi
  grep -q "BACKUP_AGE_RECIPIENT" "$sandbox/out" || { bad "$name" "output: $(cat "$sandbox/out")"; return; }
  [[ -z "$(objects)" ]] || { bad "$name" "uploaded: $(objects)"; return; }
  ok "$name"
}

test_unknown_kind_is_rejected() {
  local name="${FUNCNAME[0]}"; setup "$name"
  if bash "$BACKUP_DIR/backup.sh" hourly > "$sandbox/out" 2>&1; then bad "$name" "exit zero"; return; fi
  [[ -z "$(objects)" ]] || { bad "$name" "uploaded: $(objects)"; return; }
  ok "$name"
}

test_restore_round_trips_the_dump_into_psql() {
  local name="${FUNCNAME[0]}"; setup "$name"
  bash "$BACKUP_DIR/backup.sh" daily > /dev/null 2>&1 || { bad "$name" "backup failed"; return; }
  local obj; obj="$(objects)"; obj="${obj#aos-backups/}"
  # Reproduce the dump the stub produced by decrypting the stored object directly.
  local expected="$sandbox/expected"; tail -n +2 "$STUB_R2/aos-backups/$obj" | gunzip > "$expected"
  echo "AGE-SECRET-KEY-TEST" > "$sandbox/key"
  bash "$BACKUP_DIR/restore.sh" "$obj" "$sandbox/key" > "$sandbox/out" 2>&1 || { bad "$name" "restore failed: $(cat "$sandbox/out")"; return; }
  cmp -s "$expected" "$STUB_PSQL_INPUT" || { bad "$name" "psql input differs from dump"; return; }
  grep -q "restore ok: $obj" "$sandbox/out" || { bad "$name" "output: $(cat "$sandbox/out")"; return; }
  ok "$name"
}

test_restore_refuses_missing_arguments() {
  local name="${FUNCNAME[0]}"; setup "$name"
  if bash "$BACKUP_DIR/restore.sh" > "$sandbox/out" 2>&1; then bad "$name" "exit zero"; return; fi
  grep -q "Usage" "$sandbox/out" || { bad "$name" "output: $(cat "$sandbox/out")"; return; }
  ok "$name"
}

# crond starts jobs with an empty environment. The entrypoint must persist the backup variables, and every
# crontab command must work from a clean environment.
test_cron_jobs_run_with_an_empty_environment() {
  local name="${FUNCNAME[0]}"; setup "$name"
  local envfile="$sandbox/aos-backup.env"
  AOS_BACKUP_ENV_FILE="$envfile" sh "$BACKUP_DIR/entrypoint.sh" > /dev/null 2>&1 || { bad "$name" "entrypoint failed"; return; }
  grep -q "^crond -f" "$STUB_CURL_LOG" || { bad "$name" "entrypoint did not exec crond: $(cat "$STUB_CURL_LOG")"; return; }
  [[ "$(stat -c %a "$envfile" 2>/dev/null || stat -f %Lp "$envfile")" == "600" ]] || { bad "$name" "env file mode not 600"; return; }
  if grep -qE "PATH=|HOME=|STUB_" "$envfile"; then bad "$name" "env file holds unrelated variables"; return; fi
  : > "$STUB_CURL_LOG"
  local count=0 cmd
  while IFS= read -r line; do
    [[ -z "$line" || "$line" == \#* ]] && continue
    cmd="$(echo "$line" | awk '{ $1=$2=$3=$4=$5=""; sub(/^ +/, ""); print }')"
    cmd="${cmd//\/run\/aos-backup.env/$envfile}"
    cmd="${cmd//\/usr\/local\/bin\/backup.sh/bash $BACKUP_DIR/backup.sh}"
    cmd="${cmd//\/proc\/1\/fd\/1/$sandbox/cron.out}"
    env -i PATH="$sandbox/bin:/usr/bin:/bin" TMPDIR="$TMPDIR" STUB_R2="$STUB_R2" STUB_CURL_LOG="$STUB_CURL_LOG" \
      sh -c "$cmd" || { bad "$name" "cron command failed: $cmd ($(cat "$sandbox/cron.out" 2>/dev/null))"; return; }
    count=$((count + 1))
  done < "$BACKUP_DIR/crontab"
  [[ "$count" == "2" ]] || { bad "$name" "expected 2 cron jobs, found $count"; return; }
  objects | grep -q '^aos-backups/daily/' || { bad "$name" "no daily object"; return; }
  objects | grep -q '^aos-backups/weekly/' || { bad "$name" "no weekly object"; return; }
  ok "$name"
}

echo "backup_test:"
for t in $(declare -F | awk '{print $3}' | grep '^test_'); do "$t"; done
echo "backup_test: $passed passed, $failed failed"
[[ "$failed" == "0" ]]
