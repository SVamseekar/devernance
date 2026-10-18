#!/usr/bin/env bash
# Deploy / refresh the Spring API (+ Postgres) on the Dell lab host via Docker Compose.
#
# Defaults match MaSoVa platform SSH (OpenSSH on Windows):
#   Host: 192.168.50.88
#   User: Vamsee
#
# Usage (from Mac, repo root or any cwd):
#   ./scripts/deploy-dell.sh              # sync + up -d --build
#   ./scripts/deploy-dell.sh status       # compose ps + health
#   ./scripts/deploy-dell.sh logs         # follow api logs
#   ./scripts/deploy-dell.sh down         # stop stack
#   ./scripts/deploy-dell.sh ssh          # interactive shell
#
# Env overrides:
#   DELL_HOST=192.168.50.88
#   DELL_USER=Vamsee
#   DELL_SSH="Vamsee@192.168.50.88"   # full target (wins over host/user)
#   DELL_DIR="C:/Users/Vamsee/Projects/eu-ai-assurance-os"
#   SKIP_SYNC=1                       # only run remote compose (no file transfer)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Local secrets from MaSoVa lab notes (gitignored) — optional
if [[ -f "$ROOT/.local/dell-ssh.env" ]]; then
  # shellcheck disable=SC1091
  set -a
  # shellcheck source=/dev/null
  source "$ROOT/.local/dell-ssh.env"
  set +a
fi

# Defaults match masova-platform Dell lab (AGENTS.md / shell history / D:\Projects\...)
DELL_HOST="${DELL_HOST:-192.168.50.88}"
DELL_USER="${DELL_USER:-Vamsee}"
DELL_SSH="${DELL_SSH:-${DELL_USER}@${DELL_HOST}}"
DELL_DIR="${DELL_DIR:-D:/Projects/eu-ai-assurance-os}"
ACTION="${1:-up}"

SSH_OPTS=(
  -o ConnectTimeout=10
  -o ServerAliveInterval=30
  -o ServerAliveCountMax=3
)

ssh_dell() {
  ssh "${SSH_OPTS[@]}" "$DELL_SSH" "$@"
}

remote_ps() {
  # Prefer PowerShell so paths like C:/Users/... work on Windows OpenSSH.
  # Falls back to bash if the host is WSL/Linux.
  local cmd="$1"
  ssh_dell "powershell -NoProfile -Command \"${cmd}\"" 2>/dev/null \
    || ssh_dell "bash -lc $(printf '%q' "$cmd")"
}

compose_remote() {
  local subcmd="$1"
  # docker compose from the synced tree; --env-file must exist on Dell
  local ps="Set-Location '${DELL_DIR}'; docker compose -f infra/docker-compose.yml -f infra/docker-compose.dell.yml --env-file .env.dell ${subcmd}"
  remote_ps "$ps"
}

ensure_reachable() {
  if ! ssh_dell "echo ok" >/dev/null 2>&1; then
    echo "error: cannot SSH to ${DELL_SSH}" >&2
    echo "  • Is the Dell on the LAN at ${DELL_HOST}?" >&2
    echo "  • Try: ssh ${DELL_SSH}" >&2
    echo "  • Optional: Host dell in ~/.ssh/config (see docs/DEPLOYMENT.md § Dell)" >&2
    exit 1
  fi
}

ensure_local_env_dell() {
  if [[ ! -f "$ROOT/.env.dell" ]]; then
    if [[ -f "$ROOT/.env.dell.example" ]]; then
      echo "==> Creating local .env.dell from .env.dell.example (edit secrets if needed)"
      cp "$ROOT/.env.dell.example" "$ROOT/.env.dell"
    else
      echo "error: missing .env.dell and .env.dell.example" >&2
      exit 1
    fi
  fi
}

sync_env_file() {
  ensure_local_env_dell
  # Always push secrets/env file (not in git)
  scp "${SSH_OPTS[@]}" "$ROOT/.env.dell" "${DELL_SSH}:${DELL_DIR}/.env.dell"
  scp "${SSH_OPTS[@]}" "$ROOT/.env.dell.example" "${DELL_SSH}:${DELL_DIR}/.env.dell.example" 2>/dev/null || true
}

sync_tree() {
  if [[ "${SKIP_SYNC:-0}" == "1" ]]; then
    echo "==> SKIP_SYNC=1 — not copying files"
    return 0
  fi

  local git_url="${DELL_GIT_URL:-https://github.com/SVamseekar/devernance.git}"
  local git_ref="${DELL_GIT_REF:-main}"

  echo "==> Ensuring remote directory ${DELL_DIR}"
  remote_ps "New-Item -ItemType Directory -Force -Path '${DELL_DIR}' | Out-Null"

  # 1) Prefer git pull/clone on Dell (best on Windows when Git is installed)
  #    set-url moves a checkout cloned before the repo rename onto the current URL.
  if remote_ps "if (Get-Command git -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"; then
    echo "==> Sync via git (${git_ref}) → ${DELL_DIR}"
    remote_ps "
      Set-Location '${DELL_DIR}'
      if (Test-Path '.git') {
        git remote set-url origin '${git_url}'
        git fetch origin
        git checkout ${git_ref}
        git pull --ff-only origin ${git_ref}
      } else {
        if ((Get-ChildItem -Force | Measure-Object).Count -eq 0) {
          git clone -b ${git_ref} '${git_url}' .
        } else {
          Write-Host 'Remote dir not empty and not a git repo; falling back is handled on Mac'
          exit 42
        }
      }
    " && sync_env_file && echo "==> Sync complete (git)" && return 0
  fi

  echo "==> Git unavailable or non-empty non-git dir — streaming tarball"
  ensure_local_env_dell
  # 2) Tar stream (Windows 10+ includes tar.exe)
  tar -C "$ROOT" -czf - \
    --exclude 'services/api/target' \
    --exclude 'apps/dashboard/node_modules' \
    --exclude 'apps/dashboard/.next' \
    infra \
    services/api/pom.xml \
    services/api/src \
    .env.dell \
    .env.dell.example \
  | ssh_dell "powershell -NoProfile -Command \"
      \$dir = '${DELL_DIR}'
      New-Item -ItemType Directory -Force -Path \$dir | Out-Null
      \$tar = Join-Path \$dir '_sync.tgz'
      \$in = [Console]::OpenStandardInput()
      \$fs = [System.IO.File]::Create(\$tar)
      \$in.CopyTo(\$fs)
      \$fs.Close()
      Push-Location \$dir
      tar -xzf \$tar
      Pop-Location
      Remove-Item \$tar -Force
    \""
  echo "==> Sync complete (tar)"
}

health_check() {
  local url="http://${DELL_HOST}:9080/actuator/health"
  echo "==> Health: ${url}"
  local i
  for i in $(seq 1 36); do
    if curl -fsS --connect-timeout 3 "$url" 2>/dev/null; then
      echo
      echo "==> API healthy on Dell (:9080)"
      return 0
    fi
    sleep 5
  done
  echo "warn: health not ready after ~3 minutes — check: $0 logs" >&2
  return 1
}

case "$ACTION" in
  up|deploy)
    ensure_reachable
    sync_tree
    echo "==> docker compose up -d --build (postgres + api)"
    compose_remote "up -d --build"
    health_check || true
    echo
    echo "Mac dashboard (BFF → Dell API):"
    echo "  export ASSURANCE_API_BASE_URL=http://${DELL_HOST}:9080"
    echo "  cd apps/dashboard && npm run dev"
    ;;
  status)
    ensure_reachable
    compose_remote "ps"
    curl -fsS "http://${DELL_HOST}:9080/actuator/health" && echo || echo "(API not reachable on :9080)"
    ;;
  logs)
    ensure_reachable
    compose_remote "logs -f --tail=200 api"
    ;;
  down)
    ensure_reachable
    compose_remote "down"
    ;;
  ssh)
    exec ssh "${SSH_OPTS[@]}" "$DELL_SSH"
    ;;
  sync)
    ensure_reachable
    sync_tree
    ;;
  *)
    echo "Usage: $0 {up|status|logs|down|ssh|sync}" >&2
    exit 2
    ;;
esac
