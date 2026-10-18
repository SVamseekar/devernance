#!/usr/bin/env bash
# Behaviour tests for infra/deploy/aos-update.sh against a stubbed docker CLI.
# The stub keeps image tags and running containers as files, so each test can stage a registry state.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
UPDATER="$ROOT/infra/deploy/aos-update.sh"
passed=0
failed=0
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
ORIGINAL_PATH="$PATH"

write_docker_stub() {
  cat > "$1/docker" <<'SH'
#!/usr/bin/env bash
# State: $STUB/tags/<ref> = image id; $STUB/registry/<ref> = id a pull yields; $STUB/running/<svc> = image id;
# $STUB/health/<image id> = health status; every call is appended to $STUB/log.
set -euo pipefail
S="$STUB"
echo "docker $*" >> "$S/log"
key() { echo "$1" | tr '/:' '__'; }
ref_for() { echo "ghcr.io/svamseekar/aos-$1:$(cat "$S/aos_tag")"; }
if [[ "$1" == "compose" ]]; then
  shift
  while [[ "$1" == -* ]]; do shift 2; done
  sub="$1"; shift
  case "$sub" in
    pull)
      for svc in "$@"; do
        [[ "$svc" == --* ]] && continue
        r="$(key "$(ref_for "$svc")")"
        [[ -f "$S/registry/$r" ]] && cp "$S/registry/$r" "$S/tags/$r"
      done ;;
    up)
      svcs=()
      for a in "$@"; do [[ "$a" == -* ]] || svcs+=("$a"); done
      [[ ${#svcs[@]} -eq 0 ]] && svcs=(api dashboard backup)
      for svc in "${svcs[@]}"; do
        r="$(key "$(ref_for "$svc")")"
        [[ -f "$S/tags/$r" ]] && cp "$S/tags/$r" "$S/running/$svc"
      done ;;
    ps) [[ "$*" == *"-q api"* ]] && echo "container-api" ;;
    exec)
      [[ -f "$S/backup_fails" ]] && { echo "backup.sh: upload failed" >&2; exit 1; }
      echo "backup ok: pre-deploy" ;;
    *) echo "stub: unhandled compose $sub" >&2; exit 2 ;;
  esac
  exit 0
fi
case "$1" in
  image)
    [[ "$2" == "prune" ]] && exit 0
    # docker image inspect <ref> --format '{{.Id}}'
    r="$(key "$3")"; [[ -f "$S/tags/$r" ]] || exit 1; cat "$S/tags/$r" ;;
  inspect)
    fmt="$3"; cid="$4"
    svc="${cid#container-}"
    if [[ "$fmt" == *Health* ]]; then
      id="$(cat "$S/running/$svc")"; cat "$S/health/$id" 2>/dev/null || echo healthy
    else
      cat "$S/running/$svc"
    fi ;;
  tag) echo "$2" > "$S/tags/$(key "$3")" ;;
  *) : ;;
esac
SH
  chmod +x "$1/docker"
  cat > "$1/curl" <<'SH'
#!/usr/bin/env bash
echo "curl $*" >> "$STUB/alerts"
SH
  chmod +x "$1/curl"
  printf '#!/usr/bin/env bash\nexit 0\n' > "$1/sleep"
  chmod +x "$1/sleep"
}

# Stage: running images, local tags, what the registry now serves, and health per image id.
setup() {
  local dir="$work/$1"
  mkdir -p "$dir/bin" "$dir/stub/tags" "$dir/stub/registry" "$dir/stub/running" "$dir/stub/health" "$dir/aos"
  write_docker_stub "$dir/bin"
  export PATH="$dir/bin:$ORIGINAL_PATH" STUB="$dir/stub" AOS_DIR="$dir/aos"
  export DEPLOY_ALERT_URL="https://alerts.example/hook" AOS_HEALTH_TIMEOUT_SECONDS=10
  printf 'AOS_TAG=main\nPOSTGRES_DB=aos\n' > "$AOS_DIR/.env"
  echo main > "$STUB/aos_tag"
  : > "$STUB/log"; : > "$STUB/alerts"
  for svc in api dashboard backup; do
    echo "sha256:${svc}-old" > "$STUB/running/$svc"
    echo "sha256:${svc}-old" > "$STUB/tags/ghcr.io_svamseekar_aos-${svc}_main"
  done
}
registry() { echo "$2" > "$STUB/registry/ghcr.io_svamseekar_aos-$1_$(cat "$STUB/aos_tag")"; }
running() { cat "$STUB/running/$1"; }
tag_of() { cat "$STUB/tags/ghcr.io_svamseekar_aos-$1_$(cat "$STUB/aos_tag")"; }
called() { grep -q -- "$1" "$STUB/log"; }
ok() { passed=$((passed + 1)); echo "  ok   $1"; }
bad() { failed=$((failed + 1)); echo "  FAIL $1: $2"; }
run_updater() { bash "$UPDATER" > "$STUB/out" 2>&1; }

test_no_new_images_changes_nothing() {
  local n="${FUNCNAME[0]}"; setup "$n"
  run_updater || { bad "$n" "exit non-zero: $(cat "$STUB/out")"; return; }
  if called "compose.* exec"; then bad "$n" "took a backup"; return; fi
  if called "compose.* up"; then bad "$n" "ran up"; return; fi
  if called "image prune"; then bad "$n" "pruned"; return; fi
  ok "$n"
}

test_new_healthy_api_is_backed_up_then_deployed() {
  local n="${FUNCNAME[0]}"; setup "$n"
  registry api sha256:api-new
  run_updater || { bad "$n" "exit non-zero: $(cat "$STUB/out")"; return; }
  [[ "$(running api)" == "sha256:api-new" ]] || { bad "$n" "api runs $(running api)"; return; }
  local backup_line up_line
  backup_line="$(grep -n "exec -T backup backup.sh pre-deploy" "$STUB/log" | head -n1 | cut -d: -f1)"
  up_line="$(grep -n "compose.* up -d --no-deps api" "$STUB/log" | head -n1 | cut -d: -f1)"
  [[ -n "$backup_line" && -n "$up_line" && "$backup_line" -lt "$up_line" ]] || { bad "$n" "backup must precede api up"; return; }
  called "image prune -f" || { bad "$n" "did not prune"; return; }
  [[ ! -s "$STUB/alerts" ]] || { bad "$n" "alerted: $(cat "$STUB/alerts")"; return; }
  [[ ! -f "$AOS_DIR/.rejected-api-image" ]] || { bad "$n" "rejected marker left"; return; }
  ok "$n"
}

test_unhealthy_api_rolls_back_alerts_and_remembers_the_image() {
  local n="${FUNCNAME[0]}"; setup "$n"
  registry api sha256:api-bad; registry dashboard sha256:dashboard-new
  echo unhealthy > "$STUB/health/sha256:api-bad"
  if run_updater; then bad "$n" "exit zero"; return; fi
  [[ "$(running api)" == "sha256:api-old" ]] || { bad "$n" "api runs $(running api)"; return; }
  [[ "$(tag_of api)" == "sha256:api-old" ]] || { bad "$n" "tag points at $(tag_of api)"; return; }
  [[ "$(cat "$AOS_DIR/.rejected-api-image" 2>/dev/null)" == "sha256:api-bad" ]] || { bad "$n" "rejected marker missing"; return; }
  grep -q "alerts.example/hook" "$STUB/alerts" || { bad "$n" "no alert"; return; }
  [[ "$(running dashboard)" == "sha256:dashboard-old" ]] || { bad "$n" "dashboard deployed after a failed API"; return; }
  if called "image prune"; then bad "$n" "pruned during rollback"; return; fi
  ok "$n"
}

test_rejected_image_is_not_retried() {
  local n="${FUNCNAME[0]}"; setup "$n"
  registry api sha256:api-bad
  echo "sha256:api-bad" > "$AOS_DIR/.rejected-api-image"
  run_updater || { bad "$n" "exit non-zero: $(cat "$STUB/out")"; return; }
  if called "compose.* exec"; then bad "$n" "took a backup for a rejected image"; return; fi
  if called "compose.* up -d --no-deps api"; then bad "$n" "redeployed a rejected image"; return; fi
  [[ "$(running api)" == "sha256:api-old" ]] || { bad "$n" "api runs $(running api)"; return; }
  [[ "$(tag_of api)" == "sha256:api-old" ]] || { bad "$n" "tag left on the rejected image"; return; }
  grep -q "rejected" "$STUB/out" || { bad "$n" "no log line: $(cat "$STUB/out")"; return; }
  [[ ! -s "$STUB/alerts" ]] || { bad "$n" "alerted again"; return; }
  ok "$n"
}

test_newer_image_after_a_rejection_is_deployed() {
  local n="${FUNCNAME[0]}"; setup "$n"
  registry api sha256:api-fixed
  echo "sha256:api-bad" > "$AOS_DIR/.rejected-api-image"
  run_updater || { bad "$n" "exit non-zero: $(cat "$STUB/out")"; return; }
  [[ "$(running api)" == "sha256:api-fixed" ]] || { bad "$n" "api runs $(running api)"; return; }
  [[ ! -f "$AOS_DIR/.rejected-api-image" ]] || { bad "$n" "rejected marker not cleared"; return; }
  ok "$n"
}

test_dashboard_only_change_skips_backup_and_api() {
  local n="${FUNCNAME[0]}"; setup "$n"
  registry dashboard sha256:dashboard-new
  run_updater || { bad "$n" "exit non-zero: $(cat "$STUB/out")"; return; }
  [[ "$(running dashboard)" == "sha256:dashboard-new" ]] || { bad "$n" "dashboard runs $(running dashboard)"; return; }
  if called "compose.* exec"; then bad "$n" "took a backup"; return; fi
  if called "compose.* up -d --no-deps api"; then bad "$n" "recreated api"; return; fi
  ok "$n"
}

test_failed_pre_deploy_backup_aborts_before_touching_the_api() {
  local n="${FUNCNAME[0]}"; setup "$n"
  registry api sha256:api-new
  touch "$STUB/backup_fails"
  if run_updater; then bad "$n" "exit zero"; return; fi
  if called "compose.* up -d --no-deps api"; then bad "$n" "deployed without a backup"; return; fi
  [[ "$(running api)" == "sha256:api-old" ]] || { bad "$n" "api runs $(running api)"; return; }
  [[ "$(tag_of api)" == "sha256:api-old" ]] || { bad "$n" "tag moved to the undeployed image"; return; }
  grep -q "alerts.example/hook" "$STUB/alerts" || { bad "$n" "no alert"; return; }
  ok "$n"
}

test_pinned_tag_from_env_file_is_used() {
  local n="${FUNCNAME[0]}"; setup "$n"
  printf 'AOS_TAG=abc1234\n' > "$AOS_DIR/.env"
  echo abc1234 > "$STUB/aos_tag"
  for svc in api dashboard backup; do echo "sha256:${svc}-old" > "$STUB/tags/ghcr.io_svamseekar_aos-${svc}_abc1234"; done
  run_updater || { bad "$n" "exit non-zero: $(cat "$STUB/out")"; return; }
  called "image inspect ghcr.io/svamseekar/aos-api:abc1234" || { bad "$n" "did not inspect the pinned tag"; return; }
  ok "$n"
}

echo "updater_test:"
for t in $(declare -F | awk '{print $3}' | grep '^test_'); do "$t"; done
echo "updater_test: $passed passed, $failed failed"
[[ "$failed" == "0" ]]
