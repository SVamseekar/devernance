#!/usr/bin/env bash
# Pull-based deploy, run every 5 minutes by aos-update.timer.
# Rolls forward when the registry has new images for AOS_TAG (from .env). A new API image is deployed only after a
# pre-deploy backup and only kept when its container turns healthy; otherwise the previous image is restored, the
# rejected image id is remembered (so it is not retried every run), and DEPLOY_ALERT_URL is notified.
# Flyway migrations are forward-only: a rollback after a new migration ran may need the restore runbook (docs/OPS.md).
set -euo pipefail

AOS_DIR="${AOS_DIR:-/opt/aos}"
HEALTH_TIMEOUT_SECONDS="${AOS_HEALTH_TIMEOUT_SECONDS:-300}"
REJECTED_FILE="$AOS_DIR/.rejected-api-image"
cd "$AOS_DIR"

if command -v flock > /dev/null 2>&1; then
  exec 9> "$AOS_DIR/.update.lock"
  flock -n 9 || { echo "aos-update: another run holds the lock"; exit 0; }
fi

compose() { docker compose -f "$AOS_DIR/docker-compose.prod.yml" --env-file "$AOS_DIR/.env" "$@"; }
log() { echo "aos-update: $*"; }
# DEPLOY_ALERT_URL is a Discord webhook (set in /opt/aos/deploy.env). Messages hold only image ids and states.
alert() {
  echo "aos-update: $*" >&2
  if [[ -n "${DEPLOY_ALERT_URL:-}" ]]; then
    curl -fsS -m 10 -o /dev/null -H "Content-Type: application/json" \
      -d "{\"content\": \"aos deploy on $(hostname): $*\"}" "$DEPLOY_ALERT_URL" || true
  fi
}

tag="$(sed -n 's/^AOS_TAG=//p' "$AOS_DIR/.env" | tail -n1)"
tag="${tag:-main}"
ref() { echo "ghcr.io/svamseekar/aos-$1:$tag"; }
image_id() { docker image inspect "$1" --format '{{.Id}}' 2> /dev/null || echo none; }
running_id() {
  local cid
  cid="$(compose ps -q "$1" 2> /dev/null || true)"
  if [[ -n "$cid" ]]; then docker inspect --format '{{.Image}}' "$cid"; else image_id "$(ref "$1")"; fi
}

before_api="$(running_id api)"
before_dashboard="$(running_id dashboard)"
before_backup="$(running_id backup)"
compose pull --quiet api dashboard backup
after_api="$(image_id "$(ref api)")"
after_dashboard="$(image_id "$(ref dashboard)")"
after_backup="$(image_id "$(ref backup)")"

api_changed=false
if [[ "$after_api" != "$before_api" ]]; then
  if [[ -f "$REJECTED_FILE" && "$(cat "$REJECTED_FILE")" == "$after_api" ]]; then
    log "api image $after_api was rejected earlier; keeping $before_api"
    [[ "$before_api" != "none" ]] && docker tag "$before_api" "$(ref api)"
  else
    api_changed=true
  fi
fi
others=()
[[ "$after_dashboard" != "$before_dashboard" ]] && others+=(dashboard)
[[ "$after_backup" != "$before_backup" ]] && others+=(backup)

if [[ "$api_changed" == "false" && ${#others[@]} -eq 0 ]]; then
  exit 0
fi

if [[ "$api_changed" == "true" ]]; then
  log "new api image $after_api (was $before_api)"
  if ! compose exec -T backup backup.sh pre-deploy; then
    [[ "$before_api" != "none" ]] && docker tag "$before_api" "$(ref api)"
    alert "pre-deploy backup failed; api $after_api not deployed"
    exit 1
  fi

  compose up -d --no-deps api
  status=starting
  deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  while (( SECONDS < deadline )); do
    status="$(docker inspect --format '{{.State.Health.Status}}' "$(compose ps -q api)" 2> /dev/null || echo missing)"
    [[ "$status" == "healthy" || "$status" == "unhealthy" ]] && break
    sleep 5
  done

  if [[ "$status" != "healthy" ]]; then
    echo "$after_api" > "$REJECTED_FILE"
    if [[ "$before_api" != "none" ]]; then
      docker tag "$before_api" "$(ref api)"
      compose up -d --no-deps api
    fi
    alert "api $after_api was $status; rolled back to $before_api"
    exit 1
  fi
  rm -f "$REJECTED_FILE"
  log "api $after_api healthy"
fi

if [[ ${#others[@]} -gt 0 ]]; then
  log "updating ${others[*]}"
  compose up -d --no-deps "${others[@]}"
fi
compose up -d
docker image prune -f > /dev/null
log "deploy complete"
