# Governance manifest reference

Every field the governance runtime accepts, with its type, default and meaning. The server rejects unknown keys
(a misspelled key must never silently become a missing rule), so this list is complete.

Manifests are YAML or JSON. A file may hold several YAML documents separated by `---`. The CLI reads every
`.yaml`, `.yml` and `.json` file in the folder you give it (default `./governance`).

Concepts and architecture: [GOVERNANCE_PLATFORM.md](GOVERNANCE_PLATFORM.md). Parser source of truth:
`services/api/src/main/java/os/assurance/eu/api/governance/SystemSpec.java` and `PolicySpec.java`.

## Envelope (all kinds)

```yaml
apiVersion: devernance.io/v1      # required, exactly this value
kind: AISystem                    # AISystem | GovernancePolicy | PolicyTests
metadata:
  name: claims-agent              # required: lowercase letters, digits, . _ -, start with a letter or digit, max 128
  systemId: 6f1c...               # optional, AISystem only: bind to an already registered system (UUID)
spec: { ... }                     # required for AISystem and GovernancePolicy
```

A `spec` may be at most 64 KB. One `apply` request may hold at most 50 manifests, and it is all or nothing: if any
manifest is invalid, nothing is applied.

## AISystem

The approved state of one AI system. Applying it also creates or updates the system in the AI system registry.

| Field | Type | Required | Meaning |
|---|---|---|---|
| `spec.owner` | string ≤ 255 | yes | Team or person accountable |
| `spec.purpose` | string ≤ 255 | yes | What the system does |
| `spec.riskClass` | `minimal` \| `limited` \| `high` \| `prohibited` | yes | EU AI Act risk class (any case). Lowering it needs a reviewer role, as in the dashboard |
| `spec.riskBasis` | string ≤ 255 | yes | Why that class applies |
| `spec.deploymentRegion` | string ≤ 255 | yes | e.g. `EU` |
| `spec.sector` | string ≤ 255 | no | e.g. `insurance`; selects sector controls |
| `spec.model.provider` | string | no | e.g. `openai` |
| `spec.model.name` | string ≤ 200, no `@` | yes if `model` is given | e.g. `gpt-4.1` |
| `spec.model.version` | string ≤ 55 | no | e.g. `"2025-04-14"`. Quote it in YAML. Without a version, any version of the model counts as approved for drift |
| `spec.promptHash` | string ≤ 128 | no | Hash of the approved system prompt, e.g. `sha256:...`. Compared with what deployments report |
| `spec.tools` | list of names | no | Every tool the agent can call. Names: letters, digits, `_ . : -`, max 128, each once, at most 200 |

The approved model reference used everywhere is `name@version` (or `name` when there is no version).

## GovernancePolicy

What a system may do.

```yaml
apiVersion: devernance.io/v1
kind: GovernancePolicy
metadata:
  name: insurance-claims-policy
spec:
  target:
    system: claims-agent                # required: metadata.name of an applied (or same-request) AISystem
  models:
    approved: [gpt-4.1@2025-04-14]      # optional
  defaultDecision: deny                 # optional: deny (default) | allow
  tools:                                # tool name -> action
    process_refund: payment.refund
  rules: [ ... ]                        # see below
  enforcement:
    deployment: blocking                # blocking (default) | advisory
    runtime: enforce                    # enforce (default) | monitor
    onDrift: continue                   # continue (default) | block
```

### `spec.models.approved`

List of model references (at most 100). An entry `gpt-4.1@2025-04-14` approves exactly that version; an entry
`gpt-4.1` approves every version of `gpt-4.1`. When the list is empty or absent, the model is not checked.

At runtime the model is only checked when the caller sends `model`. The SDK sends it when you pass
`GovernanceClient(model=...)`. Drift detection checks the model a deployment reports.

### `spec.defaultDecision`

What an action gets when no rule covers it. Keep `deny` (the default) unless you are rolling a policy out and want
to see unknown actions first; for that, `enforcement.runtime: monitor` is usually better.

### `spec.tools`

Maps each tool name (as listed in the AISystem's `spec.tools`) to the action it performs. Actions are lowercase
words joined by dots: `payment.refund`, `customer.read`. `devernance validate` uses this map:

| Finding | Severity | When |
|---|---|---|
| `TOOL_UNGOVERNED` | ERROR | The system has a tool the policy does not map |
| `TOOL_PROHIBITED` | ERROR | A tool's action is denied by a rule, or by the default |
| `MODEL_NOT_APPROVED` | ERROR | The system's model is not on `models.approved` |
| `POLICY_MISSING` | ERROR | No policy targets the system |
| `TOOL_NOT_DECLARED` | WARNING | The policy maps a tool the system does not declare |
| `MANIFEST_INVALID` | ERROR | A manifest sent to validate could not be parsed (dry run only) |
| `POLICY_CONFLICT` | ERROR | Two policies in one request target the same system (dry run only) |
| `TARGET_UNKNOWN` | ERROR | A policy targets a system that is neither sent nor applied (dry run only) |

### `spec.rules`

At most 200 rules, one per action pattern.

| Field | Type | Meaning |
|---|---|---|
| `action` | `payment.refund` \| `payment.*` \| `*` | Which actions the rule covers. Exact beats prefix; longer prefix beats shorter; `*` is last. `pay.*` matches `pay.refund`, not `payment.refund` |
| `decision` | `allow` \| `deny` | Required |
| `reason` | string ≤ 512 | Shown in the decision (for deny rules) and in the dashboard |
| `requirement` | string ≤ 512 | The business rule in words. Shown in the closure report. Write it for an auditor |
| `require` | list of conditions | `allow` rules only. All must hold, otherwise DENY |
| `review` | list of reviews | `allow` rules only. The first review whose `when` holds turns the answer into REVIEW |

A `deny` rule cannot have `require` or `review`.

### Conditions

```yaml
- {field: context.amount, op: gt, value: 50000}
- {field: resource.customer_id, op: eq, valueFrom: context.authorized_customer_id}
- {field: context.currency, op: in, value: [INR, EUR]}
- {field: context.ticket_id, op: exists}
```

| Key | Meaning |
|---|---|
| `field` | Path into the request: `resource.<key>` or `context.<key>`, nested with dots (`context.customer.tier`) |
| `op` | `eq`, `ne`, `gt`, `gte`, `lt`, `lte`, `in`, `exists` |
| `value` | A literal. Numbers for `gt/gte/lt/lte`; a list of 1–100 scalars for `in`; none for `exists` |
| `valueFrom` | Compare with another field instead of a literal (not with `in` or `exists`) |

Give exactly one of `value` and `valueFrom` (except `in`, which needs `value`, and `exists`, which takes neither).

How conditions evaluate:

- A missing field, or a field that is an object or list, makes the condition **unknown**. Unknown in `require` or
  in a review's `when` → **DENY**. Information that was left out never allows anything.
- `gt/gte/lt/lte` need numbers on both sides; text such as `"75000"` is unknown → DENY. Send numbers as numbers.
- Numbers compare by value: `50000` equals `50000.0`.
- `eq`/`ne` between text and a number are never equal (`"5"` ≠ `5`).
- `exists` is true when the field is present and is a string, number or boolean.

**Where facts come from.** `resource` is what is acted on (customer id, document id). `context` is facts about the
request (amount, currency, the customer the agent's session is authorised for). Fill both from your application's
own trusted state, never from model output: the model chooses *which* action, your code supplies *the facts*.

### Reviews

```yaml
review:
  - when:
      - {field: context.amount, op: gt, value: 50000}
    approverRoles: [COMPLIANCE_OFFICER]
    reason: High-value refund
    expiresAfterMinutes: 240
```

| Field | Default | Meaning |
|---|---|---|
| `when` | empty = always | Conditions that must all hold for the review to apply |
| `approverRoles` | `[ADMIN, COMPLIANCE_OFFICER]` | Who may answer: `ADMIN`, `AI_ENGINEERING_LEAD`, `COMPLIANCE_OFFICER`, `LEGAL_COUNSEL` (any case) |
| `reason` | `Human approval required` | Shown to the approver and in the decision |
| `expiresAfterMinutes` | 1440 (24 h) | 1 to 43200 (30 days). Unanswered requests expire; approved but unused ones expire too |

At most 20 reviews per rule and 20 conditions per list.

### `spec.enforcement`

| Field | Values | Effect |
|---|---|---|
| `deployment` | `blocking` (default) | Validation errors or drift make `release-check` BLOCKED |
| | `advisory` | They make it REVIEW instead (never better than the release gate's own answer) |
| `runtime` | `enforce` (default) | DENY and unapproved REVIEW are not authorized |
| | `monitor` | Every decision is recorded but `authorized` is always true; no approvals are created |
| `onDrift` | `continue` (default) | Drift is reported; runtime decisions are unaffected |
| | `block` | While the requesting environment has drifted, every runtime decision is DENY |

## PolicyTests

Not applied. `devernance test` sends these cases to `POST /simulate` together with the manifests in the same
folder, so a pull request can test a policy before anything is applied.

```yaml
apiVersion: devernance.io/v1
kind: PolicyTests
cases:
  - name: a 75,000 refund needs approval      # optional, shown in output
    system: claims-agent                     # required
    action: payment.refund                   # required
    resource: {customer_id: CUST-102}        # optional
    context: {authorized_customer_id: CUST-102, amount: 75000}
    model: gpt-4.1@2025-04-14                # optional
    expect: REVIEW                           # optional: ALLOW | DENY | REVIEW
```

A case without `expect` is reported but neither passes nor fails. At most 200 cases per run. Simulation runs the
same evaluator as runtime but ignores drift (`onDrift`) and monitor mode, so it shows what the rules themselves say.

## Recipes

**Group actions with prefixes.** Prefixes go from the left: `records.*` is valid, `*.read` is not. So name actions
resource first (`customer.read`, `customer.export`), and one rule can cover a family:

```yaml
rules:
  - {action: claim.*, decision: allow}                 # claim.search, claim.read, ...
  - {action: claim.delete, decision: deny}             # exact beats the prefix above
  - {action: payment.*, decision: deny, reason: Payments go through payment.refund only}
  - action: payment.refund                             # exact beats payment.*
    decision: allow
```

**Two-tier approval by amount:**

```yaml
- action: payment.refund
  decision: allow
  review:
    - when: [{field: context.amount, op: gt, value: 500000}]
      approverRoles: [ADMIN]
      reason: Very high-value refund
    - when: [{field: context.amount, op: gt, value: 50000}]
      approverRoles: [COMPLIANCE_OFFICER]
      reason: High-value refund
```

Reviews are checked in order; put the strictest first.

**Only during a support ticket:**

```yaml
- action: customer.update
  decision: allow
  require:
    - {field: context.ticket_id, op: exists}
    - {field: context.ticket_status, op: eq, value: open}
```

**Roll out safely:** apply with `enforcement.runtime: monitor`, watch the Governance page for a few days, then switch
to `enforce`.
