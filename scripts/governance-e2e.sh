#!/usr/bin/env bash
# End-to-end proof of the governance runtime: a real API, the real CLI, the real Python SDK.
#
# Runs the claims-agent walkthrough from docs/GOVERNANCE_PLATFORM.md and fails on the first step that does
# not behave as documented:
#   1. validate + policy tests on manifests that are not applied yet (CI key)
#   2. a pull request that adds an ungoverned tool fails validation
#   3. apply (editor key)
#   4. the agent: 20,000 refund allowed and paid; 75,000 refund waits until a compliance officer approves;
#      another customer's record denied; an ungoverned tool denied; a forged token refused by the payment service
#   5. drift: the deployment reports another model; drift is detected and the release check blocks
#   6. evidence: export a signed bundle, verify it offline, show that a one-byte edit is caught
#   7. the audit chain still verifies
#
# Usage:
#   scripts/governance-e2e.sh                      # starts the API itself (H2, port 18080), stops it after
#   DEVERNANCE_E2E_SERVER=http://localhost:8080 scripts/governance-e2e.sh   # use an API that is already up
#                                                  # (local H2 profile with seeded demo users only)
# Needs: java 17 + maven (unless DEVERNANCE_E2E_SERVER is set), python3 with PyYAML, curl.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${DEVERNANCE_E2E_PORT:-18080}"
SERVER="${DEVERNANCE_E2E_SERVER:-}"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/devernance-e2e.XXXXXX")"
API_PID=""
PASSWORD="dev-local-password-only"   # seeded local H2 demo users only (DemoCredentialGuard)

cleanup() {
  if [[ -n "$API_PID" ]]; then
    kill "$API_PID" 2>/dev/null || true
    wait "$API_PID" 2>/dev/null || true
  fi
  rm -rf "$WORK"
}
trap cleanup EXIT

step() { printf '\n==> %s\n' "$*"; }
fail() { printf '\nFAILED: %s\n' "$*" >&2; exit 1; }

python3 -c 'import yaml' 2>/dev/null || fail "python3 needs PyYAML (pip install pyyaml)"

# Run the CLI from the repository, no install needed.
dvn() { PYTHONPATH="$ROOT/sdk/python" python3 -m devernance "$@"; }

# expect_exit <code> <command...>: runs the command and fails unless it exits with <code>.
expect_exit() {
  local want="$1"; shift
  set +e
  "$@"
  local got=$?
  set -e
  [[ "$got" == "$want" ]] || fail "expected exit $want, got $got: $*"
}

# json_get <key>: prints one top-level field of the JSON on stdin.
json_get() { python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"; }

if [[ -z "$SERVER" ]]; then
  step "Starting the API (H2, port $PORT); log: $WORK/api.log"
  # The API needs the evgraph CLI at startup (evidence-pack scans). Without EVGRAPH_BIN or evgraph on PATH,
  # let it install its pinned copy, as the test suite does.
  if [[ -z "${EVGRAPH_BIN:-}" ]] && ! command -v evgraph >/dev/null 2>&1; then
    export ASSURANCE_EVGRAPH_ALLOW_RUNTIME_INSTALL=true
  fi
  (cd "$ROOT/services/api" && EVAL_CALLBACK_SECRET=e2e-callback-secret \
    mvn -q spring-boot:run -Dspring-boot.run.arguments="--server.port=$PORT" >"$WORK/api.log" 2>&1) &
  API_PID=$!
  SERVER="http://localhost:$PORT"
  for _ in $(seq 1 180); do
    if curl -fsS "$SERVER/actuator/health" >/dev/null 2>&1; then break; fi
    kill -0 "$API_PID" 2>/dev/null || { tail -40 "$WORK/api.log"; fail "API exited during startup"; }
    sleep 1
  done
  curl -fsS "$SERVER/actuator/health" >/dev/null || fail "API did not become healthy"
fi
echo "API: $SERVER"

login() {
  curl -fsS -X POST "$SERVER/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}" | json_get accessToken
}

new_key() { # <bearer> <name> <scope>
  curl -fsS -X POST "$SERVER/api/v1/api-keys" -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -d "{\"name\":\"$2\",\"scope\":\"$3\"}" | json_get key
}

step "Signing in as the seeded admin and compliance officer; creating one key per workload"
ADMIN_JWT="$(login admin@example.com)"
COMPLIANCE_JWT="$(login compliance@example.com)"
EDITOR_KEY="$(new_key "$ADMIN_JWT" platform-cli FULL)"
CI_KEY="$(new_key "$ADMIN_JWT" github-actions RELEASE_GATE)"
AGENT_KEY="$(new_key "$ADMIN_JWT" claims-agent RUNTIME)"
echo "keys: platform-cli (FULL), github-actions (RELEASE_GATE), claims-agent (RUNTIME)"

# Each run uses a fresh system name, so the script can run again against the same server.
SUFFIX="$(python3 -c 'import uuid; print(uuid.uuid4().hex[:6])')"
SYSTEM="claims-agent-$SUFFIX"
GOV="$WORK/governance"
mkdir -p "$GOV"
for file in system.yaml policy.yaml tests.yaml; do
  sed -e "s/claims-agent\$/$SYSTEM/" \
    -e "s/insurance-claims-policy/insurance-claims-policy-$SUFFIX/" \
    "$ROOT/examples/claims-agent/governance/$file" >"$GOV/$file"
done
grep -q "name: $SYSTEM" "$GOV/system.yaml" || fail "could not rename the example system"

export DEVERNANCE_SERVER="$SERVER"

step "1. CI validates and tests the manifests before anything is applied"
DEVERNANCE_API_KEY="$CI_KEY" expect_exit 0 dvn validate -f "$GOV"
DEVERNANCE_API_KEY="$CI_KEY" expect_exit 0 dvn test -f "$GOV"

step "2. A pull request adds export_all_customers without a policy rule: validation fails"
PR="$WORK/pr"
mkdir -p "$PR" && cp "$GOV"/*.yaml "$PR"/
python3 - "$PR/system.yaml" <<'PY'
import sys
path = sys.argv[1]
text = open(path).read().replace("    - process_refund\n", "    - process_refund\n    - export_all_customers\n")
open(path, "w").write(text)
PY
DEVERNANCE_API_KEY="$CI_KEY" expect_exit 1 dvn validate -f "$PR"

step "3. The reviewed manifests are applied"
DEVERNANCE_API_KEY="$EDITOR_KEY" expect_exit 0 dvn apply -f "$GOV"
DEVERNANCE_API_KEY="$EDITOR_KEY" expect_exit 0 dvn validate --applied "$SYSTEM"
# Applying again changes nothing.
DEVERNANCE_API_KEY="$EDITOR_KEY" dvn apply -f "$GOV" | grep -q "unchanged" || fail "second apply was not a no-op"
CHECK="$(DEVERNANCE_API_KEY="$CI_KEY" dvn release-check "$SYSTEM" -o json || true)"
echo "$CHECK" | python3 -c 'import json,sys; d=json.load(sys.stdin); sys.exit(any(f["severity"]=="ERROR" for f in d["findings"]))' \
  || fail "release check reports governance errors after a valid apply"
echo "release check: governance part clean; overall $(echo "$CHECK" | json_get decision) (the release gate also needs evidence and evals)"

step "4. The agent runs; a compliance officer approves the high-value refund while it waits"
RESULT="$WORK/scenarios.json"
(cd "$ROOT/examples/claims-agent" && \
  PYTHONPATH="$ROOT/sdk/python:$ROOT/examples/claims-agent" DEVERNANCE_API_KEY="$AGENT_KEY" \
  python3 - "$SYSTEM" "$RESULT" <<'PY'
import runpy, sys
from agent import tools
system, result = sys.argv[1], sys.argv[2]
original = tools.GovernanceClient
tools.GovernanceClient = lambda **kw: original(**{**kw, "system": system})
sys.argv = ["run_scenarios.py", "--wait", "90", "--result-file", result]
runpy.run_path("run_scenarios.py", run_name="__main__")
PY
) >"$WORK/scenarios.log" 2>&1 &
AGENT_PID=$!

APPROVAL_ID=""
for _ in $(seq 1 60); do
  APPROVAL_ID="$(curl -fsS "$SERVER/api/v1/governance/approvals?status=PENDING" \
    -H "Authorization: Bearer $COMPLIANCE_JWT" \
    | python3 -c "import json,sys; print(next((a['id'] for a in json.load(sys.stdin) if a['system']=='$SYSTEM'), ''))")"
  [[ -n "$APPROVAL_ID" ]] && break
  kill -0 "$AGENT_PID" 2>/dev/null || break
  sleep 1
done
[[ -n "$APPROVAL_ID" ]] || { cat "$WORK/scenarios.log"; fail "no approval request appeared for the 75,000 refund"; }

# An agent's key can never approve its own request.
code="$(curl -s -o /dev/null -w '%{http_code}' -X POST "$SERVER/api/v1/governance/approvals/$APPROVAL_ID/approve" \
  -H "X-Api-Key: $AGENT_KEY" -H 'Content-Type: application/json' -d '{}')"
[[ "$code" == "403" ]] || fail "an API key could approve (HTTP $code)"
curl -fsS -X POST "$SERVER/api/v1/governance/approvals/$APPROVAL_ID/approve" \
  -H "Authorization: Bearer $COMPLIANCE_JWT" -H 'Content-Type: application/json' \
  -d '{"rationale":"Claim documents and bank details checked"}' >/dev/null
echo "approved $APPROVAL_ID as compliance@example.com"

wait "$AGENT_PID" || { cat "$WORK/scenarios.log"; fail "the agent run failed"; }
cat "$WORK/scenarios.log"
python3 - "$RESULT" <<'PY' || fail "a scenario did not behave as documented"
import json, sys
r = json.load(open(sys.argv[1]))
assert r["A"]["paid"], "A: the 20,000 refund was not paid"
assert r["B"]["approvalWasRequired"], "B: the 75,000 refund did not ask for approval"
assert r["B"]["paid"], "B: the approved refund was not paid: %s" % r["B"]
assert r["C"]["denied"], "C: another customer's record was readable"
assert r["export"]["denied"], "the ungoverned export tool ran"
assert r["bypass"]["refused"], "the payment service accepted a forged token"
assert len(r["payments"]) == 2, "expected exactly 2 payments, got %s" % r["payments"]
PY

step "5. Someone deploys another model outside the governed process: drift"
DEVERNANCE_API_KEY="$AGENT_KEY" expect_exit 0 dvn report-state "$SYSTEM" --model gpt-4.1@2025-04-14 \
  --prompt-hash sha256:claims-prompt-v1 --tools claim_search,customer_database,process_refund --source e2e
DEVERNANCE_API_KEY="$AGENT_KEY" expect_exit 1 dvn report-state "$SYSTEM" --model gpt-4.1@2026-01-15 --source e2e
DEVERNANCE_API_KEY="$EDITOR_KEY" expect_exit 1 dvn drift "$SYSTEM"
DEVERNANCE_API_KEY="$CI_KEY" expect_exit 1 dvn release-check "$SYSTEM"

step "6. Evidence: export a signed bundle and verify it offline"
DEVERNANCE_API_KEY="$EDITOR_KEY" dvn decisions "$SYSTEM"
DEVERNANCE_API_KEY="$EDITOR_KEY" dvn status "$SYSTEM"
DEVERNANCE_API_KEY="$EDITOR_KEY" expect_exit 0 dvn evidence export "$SYSTEM" --out "$WORK/bundle"
expect_exit 0 dvn evidence verify "$WORK/bundle" --jwks "$SERVER/.well-known/jwks.json"
FIRST="$(ls "$WORK/bundle/decisions" | head -1)"
python3 - "$WORK/bundle/decisions/$FIRST" <<'PY'
import sys
path = sys.argv[1]
text = open(path, encoding="utf-8").read()
open(path, "w", encoding="utf-8").write(text.replace('"reason":"', '"reason":"(edited) ', 1))
PY
expect_exit 1 dvn evidence verify "$WORK/bundle" --jwks "$SERVER/.well-known/jwks.json"
echo "(the edited bundle was rejected, as it must be)"
set +e
DEVERNANCE_API_KEY="$EDITOR_KEY" dvn closure "$SYSTEM"
set -e

step "7. The audit chain still verifies"
VALID="$(curl -fsS "$SERVER/api/v1/audit-events/verify-chain" -H "Authorization: Bearer $ADMIN_JWT" | json_get valid)"
[[ "$VALID" == "True" ]] || fail "audit chain does not verify"

printf '\nALL GOVERNANCE E2E STEPS PASSED\n'
