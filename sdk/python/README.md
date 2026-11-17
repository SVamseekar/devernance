# devernance (CLI and Python SDK)

Governance for AI agents, enforced where the agent acts.

- **CLI** `devernance`: write governance manifests, validate and test them in CI, apply them, check releases,
  inspect decisions and drift, export and verify signed evidence.
- **SDK** `devernance.GovernanceClient`: ask before every sensitive action; run it only when authorized.

Needs Python 3.9+ and PyYAML. Talks to a Devernance API (`services/api` in this repository).

```bash
pip install ./sdk/python
devernance login --server http://localhost:8080
devernance init && devernance validate && devernance test && devernance apply
```

```python
from devernance import GovernanceClient

governance = GovernanceClient(system="claims-agent", model="gpt-4.1@2025-04-14")

with governance.guard("payment.refund",
                      resource={"customer_id": "CUST-102"},
                      context={"amount": 75000, "currency": "INR", "authorized_customer_id": "CUST-102"}) as decision:
    payments.refund("CUST-102", 75000, authorization=decision.token)
```

`guard` raises `ActionDenied` when the policy says no, `ApprovalPending` when a person must approve first, and
`GovernanceUnavailable` when the server cannot be reached. In every case the block does not run.

Full guide: [`docs/GOVERNANCE_CLI.md`](../../docs/GOVERNANCE_CLI.md). Design: [`docs/GOVERNANCE_PLATFORM.md`](../../docs/GOVERNANCE_PLATFORM.md).

## Layout

| File | Purpose |
|---|---|
| `devernance/client.py` | `GovernanceClient`: HTTP calls, `guard`, `governed`, `wait_for_approval` |
| `devernance/cli.py` | The `devernance` command (argparse); exit codes at the top of the file |
| `devernance/manifests.py` | Loads YAML/JSON manifests; templates for `devernance init` |
| `devernance/verify.py` | Offline evidence-bundle verification (RS256 + SHA-256, standard library only) |
| `devernance/config.py` | Server and key from flags, environment, or `~/.config/devernance/config.json` |
| `devernance/errors.py`, `models.py` | Exceptions and the `Decision` object |

## Tests

```bash
python3 -m unittest discover -s tests -t .     # no server needed
../../scripts/governance-e2e.sh                 # against a real API
```
