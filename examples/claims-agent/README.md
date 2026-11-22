# Example: an insurance claims agent under governance

An agent reads insurance claims, looks up the claimant and issues refunds. The company rules:

1. The agent reads only the customer of its authorised claim.
2. Refunds above 50,000 need approval by a compliance officer.
3. Customer data is never exported.
4. Only the approved model version runs.
5. Every payment action is on an audit trail.

| File | What it shows |
|---|---|
| `governance/system.yaml` | The approved state: model, prompt hash, tools |
| `governance/policy.yaml` | The five rules as a `GovernancePolicy` |
| `governance/tests.yaml` | Ten policy test cases, run with `devernance test` before anything is applied |
| `agent/tools.py` | The agent's tools, each behind `governance.guard(...)` (the enforcement point) |
| `agent/services.py` | Stand-in payment service that verifies the authorization token itself (second enforcement point) |
| `run_scenarios.py` | Runs the production situations: 20,000 refund, 75,000 refund, another customer's record, an ungoverned tool, a forged token |

No LLM or agent framework is needed to run it: the tools are what an LLM would call, and governance sits in the
tools, not in the prompt. In a LangGraph or MCP agent, the tool functions look exactly like `agent/tools.py`.

## Run it

The quickest way runs everything, including starting the API and approving the refund:

```bash
scripts/governance-e2e.sh        # from the repository root
```

By hand, with the API running locally (`cd services/api && mvn spring-boot:run`):

```bash
pip install ./sdk/python
# In the dashboard (Workspace → API keys) create a FULL key "platform-cli" and a RUNTIME key "claims-agent".
export DEVERNANCE_SERVER=http://localhost:8080
cd examples/claims-agent

DEVERNANCE_API_KEY=<platform-cli key> devernance validate
DEVERNANCE_API_KEY=<platform-cli key> devernance test
DEVERNANCE_API_KEY=<platform-cli key> devernance apply

DEVERNANCE_API_KEY=<claims-agent key> python3 run_scenarios.py --wait 300
# Situation B waits: sign in as compliance@example.com, open Governance, approve the refund.

DEVERNANCE_API_KEY=<platform-cli key> devernance decisions claims-agent
DEVERNANCE_API_KEY=<platform-cli key> devernance evidence export claims-agent --out /tmp/claims-evidence
devernance evidence verify /tmp/claims-evidence --jwks http://localhost:8080/.well-known/jwks.json
```

Try breaking it: add `export_all_customers` to `governance/system.yaml` and run `devernance validate`; or report a
different model with `devernance report-state claims-agent --model gpt-4.1@2026-01-15` and run
`devernance release-check claims-agent`.
