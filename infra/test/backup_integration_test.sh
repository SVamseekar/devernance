#!/usr/bin/env bash
# Real round trip with the backup image: pgvector Postgres -> backup.sh -> S3 (`rclone serve s3`, stands in for R2)
# -> drop the database -> restore.sh -> same rows and vectors back. Needs a Docker daemon.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
run_id="aosbk$$"
net="$run_id-net"
image="aos-backup:$run_id"
pg_image="pgvector/pgvector:0.8.6-pg16"
work="$(mktemp -d)"

cleanup() {
  docker rm -fv "$run_id-pg" "$run_id-s3" > /dev/null 2>&1 || true
  docker network rm "$net" > /dev/null 2>&1 || true
  docker image rm "$image" > /dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

fail() { echo "backup_integration_test: FAIL $*" >&2; exit 1; }

docker build -q -f "$ROOT/infra/backup/Dockerfile" -t "$image" "$ROOT" > /dev/null
docker network create "$net" > /dev/null
docker run -d --name "$run_id-pg" --network "$net" \
  -e POSTGRES_DB=aos -e POSTGRES_USER=aos -e POSTGRES_PASSWORD=pw "$pg_image" > /dev/null
docker run -d --name "$run_id-s3" --network "$net" --entrypoint sh "$image" \
  -c 'mkdir -p /data/aos-backups && exec rclone serve s3 --auth-key s3key,s3secret --addr :9000 /data' > /dev/null

for _ in $(seq 1 60); do
  docker exec "$run_id-pg" pg_isready -U aos -d aos > /dev/null 2>&1 && break
  sleep 1
done
docker exec "$run_id-pg" pg_isready -U aos -d aos > /dev/null || fail "postgres not ready"

psql_pg() { docker exec -i "$run_id-pg" psql -U aos -d "${2:-aos}" -v ON_ERROR_STOP=1 -qtAc "$1"; }
psql_pg "create extension if not exists vector;
         create table evidence (id serial primary key, title text, embedding vector(3));
         insert into evidence (title, embedding)
           select 'doc ' || g, ('[' || g || ',' || g * 2 || ',' || g * 3 || ']')::vector from generate_series(1, 500) g;" \
  || fail "seed"
before="$(psql_pg "select count(*) || ':' || md5(string_agg(title || embedding::text, ',' order by id)) from evidence")"

# A real age key pair, generated inside the image; the private key never leaves $work.
docker run --rm --entrypoint age-keygen "$image" > "$work/key" 2> /dev/null
recipient="$(grep -o 'age1[0-9a-z]*' "$work/key" | head -n1)"
[[ -n "$recipient" ]] || fail "age-keygen produced no recipient"

backup_env=(
  -e PGHOST="$run_id-pg" -e PGDATABASE=aos -e PGUSER=aos -e PGPASSWORD=pw
  -e BACKUP_AGE_RECIPIENT="$recipient" -e BACKUP_R2_ENDPOINT="http://$run_id-s3:9000"
  -e BACKUP_R2_BUCKET=aos-backups -e BACKUP_R2_ACCESS_KEY_ID=s3key -e BACKUP_R2_SECRET_ACCESS_KEY=s3secret
)
in_backup() { docker run --rm --network "$net" "${backup_env[@]}" -v "$work:/work:ro" --entrypoint bash "$image" -c "$1"; }

r2() { in_backup "RCLONE_CONFIG_R2_TYPE=s3 RCLONE_CONFIG_R2_PROVIDER=Cloudflare RCLONE_CONFIG_R2_ENDPOINT=\$BACKUP_R2_ENDPOINT \
  RCLONE_CONFIG_R2_ACCESS_KEY_ID=s3key RCLONE_CONFIG_R2_SECRET_ACCESS_KEY=s3secret rclone $1"; }
for _ in $(seq 1 30); do
  r2 "lsd r2:" 2> /dev/null | grep -q aos-backups && break
  sleep 1
done

out="$(in_backup "backup.sh pre-deploy")" || fail "backup.sh: $out"
echo "$out" | grep -q "^backup ok: pre-deploy" || fail "backup output: $out"
object="$(r2 "lsf -R r2:aos-backups" | grep '\.age$')"
[[ "$object" =~ ^pre-deploy/aos-pre-deploy-[0-9T]+Z\.sql\.gz\.age$ ]] || fail "object name: $object"

# The stored object must not contain plaintext from the database.
plain="$(r2 "cat r2:aos-backups/$object" | head -c 32)"
[[ "$plain" == "age-encryption.org/v1"* ]] || fail "object is not age-encrypted"

# Runbook steps 2-4: drop and recreate the database from the maintenance database, then restore.
psql_pg "drop database aos" postgres > /dev/null
psql_pg "create database aos" postgres > /dev/null
out="$(in_backup "restore.sh '$object' /work/key")" || fail "restore.sh: $out"
echo "$out" | grep -q "restore ok: $object" || fail "restore output: $out"

after="$(psql_pg "select count(*) || ':' || md5(string_agg(title || embedding::text, ',' order by id)) from evidence")"
[[ "$before" == "$after" ]] || fail "restored data differs: before=$before after=$after"
echo "backup_integration_test: ok (500 rows with vectors restored from $object)"
