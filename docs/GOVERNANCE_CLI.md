# `devernance` CLI and Python SDK

The command line and the Python library ship in one package: `sdk/python`. They need Python 3.10+ and PyYAML,
nothing else. Concepts: [GOVERNANCE_PLATFORM.md](GOVERNANCE_PLATFORM.md). Manifest fields:
[GOVERNANCE_POLICY_REFERENCE.md](GOVERNANCE_POLICY_REFERENCE.md).

## Install

```bash
pip install ./sdk/python            # from a checkout of this repository
devernance --version
```

Without installing: `PYTHONPATH=sdk/python python3 -m devernance --help`.

## Which key

Create keys in the dashboard (Workspace → API keys) or with `POST /api/v1/api-keys` from a signed-in session.
**Name each key after the workload that holds it**: the name is recorded as the `agent` on every decision.

| Scope | Give it to | It may | It may not |
|---|---|---|---|
| `RELEASE_GATE` | CI pipelines | `GET /ci/release-gate`, `release-check`, `validate`, `test` | Apply, authorize, read decisions |
| `RUNTIME` | A running agent / enforcement point | `authorize`, read one decision, report its outcome, verify a token, report state | Apply, approve, list decisions, read evidence |
| `FULL` | A person's own terminal, platform automation | Everything the creator's role allows | Mint keys, invite users, **approve** |

Approvals always need a signed-in person in the dashboard.

## Configure

Order of precedence: command-line flags (`--server`, `--api-key`), then environment (`DEVERNANCE_SERVER`,
`DEVERNANCE_API_KEY`), then the file written by `devernance login` (`~/.config/devernance/config.json`, mode 600;
override the path with `DEVERNANCE_CONFIG`).

```bash
devernance login --server https://api.devernance.example
# prompts for the API key (not echoed)
```

## Commands

Every command takes `--server`, `--api-key` and `-o json` (machine-readable output).

| Command | What it does | Exit codes |
|---|---|---|
| `devernance init [dir] [--name N]` | Writes `system.yaml`, `policy.yaml`, `tests.yaml` (keeps existing files) | 0 |
| `devernance validate [-f path]` | Dry run: do the local manifests fit together (against what is applied)? | 0 valid, 1 errors |
| `devernance validate --applied [SYSTEM]` | Same, for what is applied | 0, 1 |
| `devernance test [-f path] [--applied]` | Runs `PolicyTests` cases; nothing is applied or recorded | 0 all pass, 1 a case failed |
| `devernance apply [-f path]` | Makes the manifests the approved state; prints `created` / `configured` / `unchanged` | 0 |
| `devernance release-check SYSTEM [--fail-on-review]` | Governance validation + drift + release gate | 0 PASS, 1 BLOCKED, 2 REVIEW |
| `devernance status [SYSTEM]` | Table of governed systems: model, policy, validation, drift, pending approvals, 24 h counts | 0 |
| `devernance get SYSTEM [--all]` | Applied manifests and their versions | 0 |
| `devernance graph [SYSTEM]` | System → model / tools → actions → rules | 0 |
| `devernance authorize SYSTEM ACTION [--resource k=v] [--context k=v] [--model M] [--dry-run]` | Ask for a decision by hand | 0 authorized, 1 denied, 2 review |
| `devernance explain DECISION_ID` | Why a decision was made, with approval and outcome | 0 |
| `devernance decisions [SYSTEM] [--action A] [--status S] [--from T] [--to T] [--limit N]` | Recorded decisions, newest first | 0 |
| `devernance approvals [--status S]` | Approval requests | 0 |
| `devernance report-state SYSTEM --model M [--prompt-hash H] [--tools a,b] [--policy-version N] [--environment E]` | Report what a deployment runs | 0 in sync, 1 drift |
| `devernance drift [SYSTEM]` | Approved vs observed, per environment | 0 in sync, 1 drift |
| `devernance closure SYSTEM [--from T] [--to T]` | Evidence closure per rule | 0 CLOSED, 1 OPEN |
| `devernance evidence export SYSTEM --out DIR [--from T] [--to T]` | Signed bundle into a new folder, then verifies it | 0 |
| `devernance evidence verify DIR [--jwks URL-or-file]` | Offline verification; no server or key needed | 0 verified, 1 not |

Common exit codes: **3** usage or configuration error (also HTTP 400/404), **4** server unreachable or other
server error. `--resource` and `--context` values are JSON when they parse as JSON (`amount=75000` is a number,
`ok=true` a boolean) and text otherwise (`customer_id=CUST-102`).

## CI: GitHub Actions

```yaml
name: Governance
on: [pull_request, push]
jobs:
  governance:
    runs-on: ubuntu-latest
    env:
      DEVERNANCE_SERVER: ${{ vars.DEVERNANCE_SERVER }}
      DEVERNANCE_API_KEY: ${{ secrets.DEVERNANCE_CI_KEY }}   # a RELEASE_GATE key named after the pipeline
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: {python-version: "3.12"}
      - run: pip install "git+https://github.com/SVamseekar/devernance.git#subdirectory=sdk/python"
      - run: devernance validate          # fails the PR when a tool has no governing rule
      - run: devernance test              # fails the PR when the policy answers a case differently
      - run: devernance release-check claims-agent
        if: github.ref == 'refs/heads/main'
```

Apply on merge from a separate job with a `FULL` key that belongs to a platform account, after review.

## Reporting drift from a deployment

The approved state is the applied `AISystem`. Report what actually runs, from wherever it is known:

```bash
# CI deploy step, after rollout
devernance report-state claims-agent --environment production \
  --model "$MODEL_REF" --prompt-hash "sha256:$(sha256sum prompts/system.txt | cut -d' ' -f1)" \
  --tools claim_search,customer_database,process_refund --source github-actions
```

```python
# Inside the agent, at startup and every few minutes
governance.report_state(model=MODEL, prompt_hash=PROMPT_HASH, tools=TOOL_NAMES, source="claims-agent")
```

## Python SDK

```python
from devernance import GovernanceClient, ActionDenied, ApprovalPending

governance = GovernanceClient(
    system="claims-agent",
    model="gpt-4.1@2025-04-14",      # sent with each request, checked against models.approved
    # server= and api_key= default to DEVERNANCE_SERVER / DEVERNANCE_API_KEY / devernance login
)
```

### `guard`: the usual way

```python
with governance.guard("payment.refund",
                      resource={"customer_id": customer_id},
                      context={"amount": amount, "currency": "INR",
                               "authorized_customer_id": session.customer_id}) as decision:
    payment_id = payments.refund(customer_id, amount, authorization=decision.token)
```

- Asks Devernance first. Runs the block only when `decision.authorized`.
- DENY → raises `ActionDenied` (also a `PermissionError`); the block never runs.
- REVIEW → raises `ApprovalPending` at once (default), or waits `wait_for_approval=` seconds and then runs, or
  raises `ApprovalRejected` / `ApprovalExpired`.
- After the block: reports `EXECUTED`; if the block raises, reports `FAILED` and re-raises.
- Devernance unreachable → `GovernanceUnavailable`; the block never runs.
- Resume later: `governance.guard(..., resume=pending.decision.id)` with the same request.

### `governed`: decorate a tool function

```python
@governance.governed(
    "customer.read",
    resource=lambda session, customer_id: {"customer_id": customer_id},
    context=lambda session, customer_id: {"authorized_customer_id": session.customer_id})
def get_customer(session, customer_id):
    return db.customers.get(customer_id)
```

Works for any plain function: a LangGraph node, a LangChain tool, an MCP tool handler. If the function accepts a
`governance_decision` keyword argument, it receives the decision (for `decision.token`).

### In the protected service

```python
check = governance.verify_authorization(token, "payment.refund", resource=..., context=...,
                                       system="claims-agent")   # the system this service protects
if not check["valid"]:
    raise PermissionError(check["reason"])
```

Send exactly the `resource` and `context` the agent sent, with the same types (a number stays a number). The check
redeems the token: it works once, so verify right before acting. A token issued for another system is refused. The
token expires after 5 minutes.

### Other calls

| Method | Purpose |
|---|---|
| `authorize(action, resource, context, dry_run=False)` | Returns a `Decision`; never raises for DENY |
| `decision(id)` | Read a decision again |
| `record_execution(decision, "EXECUTED" or "FAILED", detail, reference)` | Report an outcome once |
| `report_state(...)`, `drift()` | Reconciliation |
| `apply(manifests)`, `validate(...)`, `simulate(cases, manifests)`, `release_check()` | Define and check |
| `decisions(...)`, `approvals(...)`, `evidence_bundle(...)`, `closure(...)`, `status()`, `graph()`, `jwks()` | Read and verify |

`Decision` fields: `id`, `status`, `authorized`, `enforced`, `reason`, `rule`, `token`, `approval`,
`approval_status`, `policy`, `execution`, `raw`.

## Calling the API from other languages

There is no Java or TypeScript SDK yet. The runtime path is three HTTP calls; see [API.md](API.md#governance-runtime).

```java
// Java 17, java.net.http
var body = """
    {"system":"claims-agent","action":"payment.refund",
     "resource":{"customer_id":"%s"},"context":{"amount":%d,"currency":"INR"}}""".formatted(customerId, amount);
var response = http.send(HttpRequest.newBuilder(URI.create(server + "/api/v1/governance/authorize"))
        .header("X-Api-Key", runtimeKey).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
    HttpResponse.BodyHandlers.ofString());
JsonNode decision = mapper.readTree(response.body());
if (response.statusCode() != 200 || !decision.path("authorized").asBoolean(false)) {
  throw new SecurityException(decision.path("reason").asText("not authorized"));   // fail closed
}
```

## Developing the SDK

```bash
cd sdk/python
python3 -m unittest discover -s tests -t .      # unit tests, no server
../../scripts/governance-e2e.sh                  # end to end against a real API (starts one on port 18080)
```
