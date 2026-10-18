#!/usr/bin/env bash
# Query Devernance release gate for CI/CD deploy decisions.
#
# Exit codes (stable contract for bots):
#   0  PASS
#   1  BLOCKED (or unreadable decision)
#   2  REVIEW
#   3  usage / missing env
#   4  HTTP / transport / parse failure
#
# Required env:
#   ASSURANCE_API_BASE  e.g. https://api.example.com  (no trailing slash required)
#   SYSTEM_ID           UUID of the AI system
#   API_KEY             X-Api-Key for a service account (recommended for CI bots)
#
# Optional:
#   ASSURANCE_API_KEY   alias for API_KEY
#   FAIL_ON_REVIEW=1    treat REVIEW as exit 1 (strict mode)
#   CURL_CONNECT_TIMEOUT (default 10)
#   CURL_MAX_TIME        (default 30)
#
# Example:
#   ASSURANCE_API_BASE=http://localhost:8080 \
#   API_KEY=00000000-0000-0000-0000-000000000a01 \
#   SYSTEM_ID=<uuid> \
#   ./scripts/ci-release-gate.sh

set -euo pipefail

API_BASE="${ASSURANCE_API_BASE:-}"
SYSTEM_ID="${SYSTEM_ID:-}"
KEY="${API_KEY:-${ASSURANCE_API_KEY:-}}"
FAIL_ON_REVIEW="${FAIL_ON_REVIEW:-0}"
CURL_CONNECT_TIMEOUT="${CURL_CONNECT_TIMEOUT:-10}"
CURL_MAX_TIME="${CURL_MAX_TIME:-30}"

usage() {
  cat >&2 <<'EOF'
Usage: ASSURANCE_API_BASE=... API_KEY=... SYSTEM_ID=... scripts/ci-release-gate.sh

Exit: PASS=0, BLOCKED=1, REVIEW=2 (or 1 if FAIL_ON_REVIEW=1),
      usage=3, HTTP/parse=4
EOF
}

if [[ -z "$API_BASE" || -z "$SYSTEM_ID" || -z "$KEY" ]]; then
  usage
  exit 3
fi

# Trim trailing slash
API_BASE="${API_BASE%/}"
URL="${API_BASE}/api/v1/ci/release-gate?systemId=${SYSTEM_ID}"

TMP_BODY="$(mktemp)"
TMP_HDR="$(mktemp)"
cleanup() {
  rm -f "$TMP_BODY" "$TMP_HDR"
}
trap cleanup EXIT

HTTP_CODE="$(
  curl -sS \
    --connect-timeout "$CURL_CONNECT_TIMEOUT" \
    --max-time "$CURL_MAX_TIME" \
    -H "X-Api-Key: ${KEY}" \
    -H "Accept: application/json" \
    -D "$TMP_HDR" \
    -o "$TMP_BODY" \
    -w "%{http_code}" \
    "$URL" || true
)"

if [[ -z "$HTTP_CODE" || "$HTTP_CODE" == "000" ]]; then
  echo "ci-release-gate: transport failure calling ${URL}" >&2
  exit 4
fi

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "ci-release-gate: HTTP ${HTTP_CODE} from ${URL}" >&2
  head -c 2000 "$TMP_BODY" >&2 || true
  echo >&2
  exit 4
fi

# Prefer jq when available; fall back to python3
extract() {
  local field="$1"
  if command -v jq >/dev/null 2>&1; then
    jq -r --arg f "$field" '.[$f] // empty' "$TMP_BODY"
  else
    python3 - "$field" "$TMP_BODY" <<'PY'
import json, sys
field, path = sys.argv[1], sys.argv[2]
with open(path) as f:
    data = json.load(f)
val = data.get(field, "")
if val is None:
    val = ""
print(val if not isinstance(val, (dict, list)) else json.dumps(val))
PY
  fi
}

DECISION="$(extract decision | tr '[:lower:]' '[:upper:]')"
EXIT_CODE_FIELD="$(extract exitCode)"
CONTENT="$(extract content)"
BLOCKERS="$(extract blockers)"

if [[ -n "$CONTENT" ]]; then
  echo "$CONTENT"
else
  echo "decision=${DECISION} exitCode=${EXIT_CODE_FIELD} blockers=${BLOCKERS}"
fi

# Prefer server-provided exitCode when present and numeric
if [[ "$EXIT_CODE_FIELD" =~ ^[012]$ ]]; then
  CODE="$EXIT_CODE_FIELD"
else
  case "$DECISION" in
    PASS) CODE=0 ;;
    BLOCKED) CODE=1 ;;
    REVIEW) CODE=2 ;;
    *)
      echo "ci-release-gate: unreadable decision '${DECISION}'" >&2
      exit 4
      ;;
  esac
fi

if [[ "$FAIL_ON_REVIEW" == "1" && "$CODE" == "2" ]]; then
  echo "ci-release-gate: FAIL_ON_REVIEW=1 treating REVIEW as failure" >&2
  exit 1
fi

exit "$CODE"
