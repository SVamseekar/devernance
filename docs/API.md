# API Contract

Base path:

```text
/api/v1
```

## Authentication

Most routes require one of:

| Mechanism | Header / path |
|---|---|
| Bearer JWT | `Authorization: Bearer <access_token>` |
| API key | `X-Api-Key: <raw-key>` (stored as SHA-256 hash) |

Tenant and actor are taken from **verified credentials only**. Client-supplied
`X-Tenant-Id` / `X-Actor-Id` are **not** trusted for authorization (see
`docs/SECURITY.md`). Local H2 bootstrap seeds a dev-only API key
`00000000-0000-0000-0000-000000000a01` for the default compliance user — never use in production.

### Password and token lifecycle

```http
POST /auth/login
POST /auth/refresh
POST /auth/logout
GET  /.well-known/jwks.json
```

### Self-serve signup and password reset

```http
POST /auth/signup                 # {email, organisationName}  -> 202 {status: verification_sent}
POST /auth/verify-email           # {token, password}          -> 200 JWT pair; 410 if link unusable
POST /auth/verify-email/resend    # {email}                    -> 202
POST /auth/password/forgot        # {email}                    -> 202
POST /auth/password/reset         # {token, newPassword}       -> 204
```

- Signup never takes a password. The user chooses it (12–128 characters) on the emailed link, so nobody
  can register someone else's address with a password they know.
- Signup, resend and forgot answer identically whether or not the address exists or is verified. A
  verified address gets an "account exists" email with a reset link; nothing is created or changed.
- Emailed links are single-use and stored hashed. Verify links expire after 24 hours, reset links after 1 hour.
- Per address and purpose: one email per 60 seconds and 5 per hour (`assurance.auth.email.cooldown-seconds`,
  `assurance.auth.email.max-per-hour`). Over the limit the email is skipped silently and the response is unchanged.
- A successful reset invalidates the user's other reset links, revokes all refresh tokens and emails a
  "password changed" notice.
- Signing in with Google or Microsoft onto a still-unverified account removes any password set before
  verification, revokes its sessions and marks the address verified.

### API keys (CI release gate)

```http
GET    /api/v1/api-keys          # [{id, name, prefix, scope, createdAt, lastUsedAt, createdBy}]
POST   /api/v1/api-keys          # {name, scope?} -> 201 {id, name, prefix, key, scope}  (raw key shown once)
DELETE /api/v1/api-keys/{id}     # 204; revoked keys get 401 immediately
```

- Roles: ADMIN and AI_ENGINEERING_LEAD. Keys look like `dvn_` + 43 characters (keys issued before the Devernance rename start `aos_` and still work); only a SHA-256 hash is stored.
- Keys are created and revoked from a signed-in session only. A request authenticated by a key cannot
  create or revoke keys (403), so a leaked key cannot mint replacements.
- `scope` is `RELEASE_GATE` (the default) or `FULL`. A `RELEASE_GATE` key can only call
  `GET /api/v1/ci/release-gate`; anything else returns 403, so a key leaked from a CI secret store cannot change
  systems, approvals or evidence. A `FULL` key acts with the permissions of the user who created it (still never
  minting keys, inviting users or managing the workspace). Keys created before scopes existed, and legacy UUID
  keys, are `FULL`.
- A key that is not well formed, unknown or revoked gets `401`.
- CI calls `GET /api/v1/ci/release-gate?systemId=…` with `X-Api-Key`. The dashboard exposes the same path
  without a session cookie and forwards it unchanged.

### Workspace export and deletion

```http
GET    /api/v1/account/export   # application/zip, one JSON file per table (ADMIN, signed-in session)
DELETE /api/v1/account          # {confirmOrganisationName} -> 202; ADMIN, signed-in session
```

- Export covers every table that holds the workspace's data, including extracted evidence text and the audit
  ledger. Password, API-key and token hashes are left out. API keys cannot call either endpoint.
- Deletion needs the exact organisation name. The workspace becomes `DELETION_PENDING` at once: sign-in, refresh,
  OAuth, JWTs and API keys are all refused (`403 workspace_deleted` at sign-in, `401` elsewhere), and every admin is
  emailed. A nightly job (03:30 UTC) erases workspaces 30 days after the request, along with their stored files.
- Other API instances may keep serving a deleted workspace for up to 60 seconds (status cache).

### Read-only demo workspace

```http
POST /auth/demo      # 200 {accessToken, refreshToken: "", expiresIn}; 404 unless ASSURANCE_DEMO_ENABLED=true
GET  /api/v1/me      # {tenantId, role, email, demo}
```

- The demo is a shared workspace with two seeded systems, signed in as a password-less AUDITOR. Every
  non-GET request from it returns 403 except `POST /api/v1/evidence/query`.
- The demo token lasts 15 minutes and has no refresh token. Set `ASSURANCE_DEMO_ENABLED=true` to seed and serve it.

### OAuth (Google + Microsoft) — Part 4

Implemented with unit/integration tests. Production smoke pending
(`docs/oauth-production-smoke-test.md`). Do not claim “production SSO verified”
until that runbook is signed off.

```http
GET  /auth/oauth/{provider}/start      # 302 to IdP (provider: google | microsoft)
POST /auth/oauth/{provider}/callback   # BFF posts code+state → JWT pair (same shape as login)
```

Unauthenticated allowlist also includes `/actuator/health` (+ liveness/readiness).
All other API routes require auth.

## AI Systems

```http
POST /systems
```

Request:


```json
{
  "name": "Claims Triage AI",
  "owner": "Insurance Ops",
  "purpose": "Prioritize and route insurance claims",
  "riskClass": "high",
  "riskBasis": "Access to essential private services",
  "deploymentRegion": "EU"
}
```

Response:

```json
{
  "id": "sys_001",
  "releaseDecision": "blocked",
  "createdAt": "2026-06-05T10:00:00Z"
}
```

```http
GET /systems
GET /systems/{systemId}
PATCH /systems/{systemId}
```

## Risk Classification

```http
POST /systems/{systemId}/risk-classification
```

Request:

```json
{
  "riskClass": "high",
  "basis": "Supports access decisions for essential services",
  "affectedUsers": ["claimants", "reviewers"],
  "humanOversightRequired": true
}
```

## Evidence

```http
POST /evidence/documents
```

Request:

```json
{
  "systemId": "sys_001",
  "type": "DPIA",
  "title": "Claims Triage DPIA",
  "sourceUri": "s3://tenant-a/dpia/claims.pdf",
  "content": "Optional extracted text for local indexing.",
  "checksum": "optional-upstream-checksum",
  "metadata": {
    "version": "2026-06"
  }
}
```

Response:

```json
{
  "id": "doc_123",
  "systemId": "sys_001",
  "type": "DPIA",
  "title": "Claims Triage DPIA",
  "sourceUri": "s3://tenant-a/dpia/claims.pdf",
  "checksum": "sha256-or-upstream-checksum",
  "chunkCount": 4,
  "ingestionStatus": "indexed",
  "createdAt": "2026-06-05T10:05:00Z"
}
```

The `content` field is optional and backward compatible with metadata-only
uploads. When provided, the API validates allowed source URI schemes, checksum
shape, content length, metadata shape, and chunk size; strips
prompt-injection-like document lines; stores chunk SHA-256 hashes and embedding
provider provenance; embeds each chunk; and stores the index for cited
retrieval. Metadata-only uploads are indexed with a generated placeholder text
until object-store extraction is wired in.

```http
GET /evidence/systems/{systemId}/documents
```

```http
POST /evidence/query
```

Request:

```json
{
  "systemId": "sys_001",
  "question": "Which controls block this release?"
}
```

Response:

```json
{
  "answer": "The release is blocked because human oversight evidence and bias evaluation are incomplete.",
  "confidence": 0.82,
  "citations": [
    {
      "documentId": "doc_123",
      "title": "Claims Triage DPIA",
      "section": "Human oversight",
      "snippet": "Reviewer override must include purpose, affected cohort, appeal route, and owner sign-off."
    }
  ]
}
```

## Eval Gates

```http
POST /eval-datasets
```

Request:

```json
{
  "name": "golden-eu-claims-v4",
  "version": "2026-06",
  "sampleCount": 240,
  "golden": true
}
```

Response:

```json
{
  "id": "dataset_001",
  "name": "golden-eu-claims-v4",
  "version": "2026-06",
  "sampleCount": 240,
  "golden": true,
  "createdAt": "2026-06-05T10:00:00Z"
}
```

```http
GET /eval-datasets
```

```http
POST /eval-runs
```

The `dataset` value must reference a registered eval dataset name. When multiple
versions share a name, new runs use the latest registered version. The MVP
bootstrap registers `golden-eu-claims-v4`. Created runs enter a durable
`queued` state and are eligible for the background eval worker.

Request:

```json
{
  "systemId": "sys_001",
  "dataset": "golden-eu-claims-v4",
  "modelVersion": "claims-triage-2026-06-05",
  "promptVersion": "claims-routing-v12",
  "threshold": 0.85
}
```

Response:

```json
{
  "runId": "eval_001",
  "status": "queued"
}
```

```http
PATCH /eval-runs/{runId}/result
```

Required headers:

```http
X-Eval-Timestamp: 1780689600
X-Eval-Signature: v1=<hex hmac sha256>
```

Request:

```json
{
  "metrics": {
    "faithfulness": 0.78,
    "relevance": 0.76,
    "safetyRefusal": 0.91,
    "biasSlicePassRate": 0.74,
    "latencyP95Ms": 1800,
    "costUsd": 4.62
  }
}
```

The result callback requires numeric `faithfulness`, `relevance`,
`safetyRefusal`, and `biasSlicePassRate` metrics. It marks the run `completed`,
stores the metrics, derives the system eval score from scored metrics, and
recalculates the system release gate. The signature is computed over
`<timestamp>.<raw request body>` using `assurance.eval.callback.secret`.
Callbacks with identical metrics are idempotent; conflicting replays return
`409 Conflict`.

Devernance does not run models. A queued run waits for your eval harness to report what it measured with the
signed callback above.

The run's own decision is BLOCKED when the averaged score is below its `threshold` **or** any per-metric floor
fails: `safetyRefusal` below `assurance.eval.floor.safety-refusal` (0.90) or `biasSlicePassRate` below
`assurance.eval.floor.bias-slice-pass-rate` (0.80). The average alone can hide a collapsed slice. The release gate
blocks on the latest completed run's verdict (see Release Gate). A new approval cycle (`EVAL_REGRESSION`) opens only
when the run fails or the score drops below the previous one.

```http
POST /eval-runs/{runId}/execute
```

Demo and test only. Completes a queued run with **generated placeholder metrics**, marked `"synthetic": true`, so
it is refused with `409` unless `assurance.eval.synthetic-metrics.enabled=true` (env
`ASSURANCE_EVAL_SYNTHETIC_METRICS`, default `false`). The background dispatcher runs only when both that flag and
`assurance.eval.worker.enabled` are true. Worker failures are persisted with `failureReason`; retryable failures
return to `queued` with a delayed `queuedAt`, and exhausted failures become
`failed`. Re-executing a completed run returns `409 Conflict`.

```http
GET /eval-runs/operations
```

Returns tenant-scoped operational visibility for queued, running, retryable,
and failed eval runs.

```http
POST /eval-runs/{runId}/retry
```

Requeues a failed eval run, clears terminal failure metadata, resets
`workerAttempts` to zero, and appends an audit event.

```http
GET /eval-runs/{runId}
```

Response:

```json
{
  "runId": "eval_001",
  "systemId": "sys_001",
  "datasetId": "dataset_001",
  "status": "completed",
  "dataset": "golden-eu-claims-v4",
  "modelVersion": "claims-triage-2026-06-05",
  "promptVersion": "claims-routing-v12",
  "threshold": 0.85,
  "metrics": {
    "faithfulness": 0.78,
    "relevance": 0.76,
    "biasSlicePassRate": 0.74,
    "safetyRefusal": 0.91,
    "latencyP95Ms": 1800,
    "costUsd": 4.62
  },
  "releaseDecision": "BLOCKED",
  "createdAt": "2026-06-05T10:00:00Z",
  "queuedAt": "2026-06-05T10:00:00Z",
  "startedAt": "2026-06-05T10:00:03Z",
  "completedAt": "2026-06-05T10:00:04Z",
  "failedAt": null,
  "workerAttempts": 1,
  "maxAttempts": 3,
  "failureReason": null
}
```

## Data Contracts

```http
POST /data-contracts
GET /data-contracts
GET /data-contracts?systemId={systemId}
GET /data-contracts/{contractId}
PATCH /data-contracts/{contractId}
GET /data-contracts/{contractId}/drift-events
POST /data-contracts/{contractId}/drift-events
PATCH /data-contracts/{contractId}/drift-events/{eventId}
```

Create contract request:

```json
{
  "systemId": "sys_001",
  "name": "Claims Input Schema",
  "owner": "Data Platform",
  "version": "2026-06",
  "status": "healthy",
  "coverage": 96
}
```

Drift event request:

```json
{
  "severity": "breach",
  "field": "denial_reason_category",
  "description": "New field is not mapped to fairness monitoring."
}
```

Open `breach` drift marks the contract and mapped system as `BREACH`, causing
the release gate to block. Resolving the event with:

```json
{
  "status": "resolved"
}
```

recalculates the contract, system data-contract status, and release decision.

Acknowledging or resolving needs a `note` saying what was done (`400` without one). Acknowledged drift still counts:
only `resolved` clears a warning or a breach. Only compliance or an admin can resolve a `breach`, or change one that
is already resolved (`403` for others), so the engineer whose pipeline caused it does not also close it. The event
keeps `resolutionNote`, `resolvedBy` and `resolvedAt`, and the audit event `data_contract.drift_updated` carries the
note.

```json
{ "status": "resolved", "note": "Column dropped at ingestion in PR #412; DPO confirmed no data reached the model" }
```

## Audit chain verification

```http
GET /audit/verify
GET /audit-events/verify-chain
```

Returns `{ "valid": true, "checkedCount": N, "firstBreakId": null }` for the
tenant’s HMAC-SHA-256 hash-chained audit ledger. Events store `prevEventHash`,
`eventHash`, and `retainUntil` (≥ 7 years by default). There is no public API to
update or delete audit rows.

## Controls catalog

```http
GET /controls
GET /systems/{systemId}/controls
PUT /systems/{systemId}/controls/{controlId}
```

Catalog controls are seeded with EU AI Act–style obligations (risk management,
data governance, human oversight, transparency, etc.). Creating or
reclassifying a system attaches applicable controls. `PUT` body:

```json
{
  "status": "PASS",
  "notes": "Oversight SOP v3 reviewed"
}
```

Statuses: `PASS` | `REVIEW` | `BLOCKED`. Any `BLOCKED` system control appears in
release-gate blockers as `CONTROL:{code}`.

Registry fields on AI systems (create/update/risk classification):
`vendorName`, `modelName`, `modelVersion`, `dataSources`, `sector`,
`decisionImpact`, `affectedUsers`.

## Assisted obligation determination (Part 12)

**Product framing:** responses are a **suggested applicability / obligation map** only.
They are **not legal advice**, not a final legal determination, not certification, and
not an official conformity assessment. Human legal review is always required.
Risk class is never auto-changed.

```http
GET /determination/questionnaire
POST /systems/{systemId}/determination/runs
GET /systems/{systemId}/determination/runs
GET /systems/{systemId}/determination/runs/{runId}
```

`GET /determination/questionnaire` returns versioned questions, `rulesetVersion`,
`productLabel` (`Assisted obligation determination (ruleset vX)`), and a full disclaimer.

`POST /systems/{systemId}/determination/runs` body:

```json
{
  "answers": {
    "sector": "insurance",
    "decision_impact": "eligibility",
    "essential_private_service": true,
    "biometric": false,
    "employment": false,
    "human_in_loop": true,
    "interacts_with_natural_persons": true,
    "profiling": true,
    "users_affected": "many",
    "high_risk_self_assessment": true
  }
}
```

Response includes `disclaimer`, `rulesetVersion`, evaluated obligations with
`applicability` (`APPLICABLE` | `NOT_APPLICABLE` | `UNCERTAIN`), mapped `controlCodes`,
and `result.riskSuggestion` with `autoApplied: false` / `requiresHumanConfirm: true`.
Applicable control codes open missing `system_controls` in `REVIEW`. Audit event:
`determination.run.completed`. Latest run is embedded in evidence pack JSON/PDF under
`determination` (with the same disclaimer).

## Certification readiness (Part 13)

**Product framing:** responses are a **readiness score + structured gap report** only.
They are **not legal certification**, not notified-body attestation, and not an
official conformity assessment. Never returns a `certified: true` field.

```http
GET  /systems/{systemId}/certification-readiness
POST /systems/{systemId}/certification-readiness/export
```

`GET` returns:

| Field | Description |
|---|---|
| `score` | Weighted readiness 0–100 |
| `readinessStatus` | `NOT_READY` \| `READY_FOR_REVIEW` \| `GAPS` |
| `productLabel` | `Certification readiness automation` |
| `disclaimer` | Full product-safe disclaimer |
| `dimensions[]` | Nine dimensions: risk, controls, evidence, eval, contracts, approvals, oversight, determination, audit_chain |
| `gaps[]` | `{ code, severity, message, remediationHint, dimension }` |

Weights and thresholds are configurable via `assurance.certification-readiness.*`
(defaults sum to 100: risk 10, controls 15, evidence 15, eval 15, contracts 10,
approvals 10, oversight 10, determination 10, audit chain 5).

`POST .../export` body `{ "format": "json" | "pdf" }` (default `json`) returns a
downloadable readiness report. PDF is a human-readable export; JSON is primary.
Audit events: `certification_readiness.assessed`, `certification_readiness.exported`.

## Regulatory change monitoring (Part 14)

**Product framing:** near-real-time **polled** assistive feed with heuristic impact hints.
It is **not** an official legal bulletin, not continuous real-time law, and not legal advice.
Impact levels prefer `UNCERTAIN`. The feed **never** auto-changes risk class or control status.

Latency: bounded by `assurance.reg-monitor.poll-interval-ms` (scheduler) and each source’s
`poll_interval_seconds`. Network sources are disabled by default; curated bootstrap fixtures
load when the network is blocked or sources are empty.

```http
GET  /reg-monitor/items?since=&reviewed=
POST /reg-monitor/items/{itemId}/review
GET  /systems/{systemId}/reg-monitor/relevant
```

| Endpoint | Roles | Notes |
|---|---|---|
| `GET /reg-monitor/items` | ADMIN, AI_ENGINEERING_LEAD, COMPLIANCE_OFFICER, LEGAL_COUNSEL, AUDITOR | Optional `since` (ISO-8601), `reviewed` (boolean) |
| `POST .../review` | ADMIN, COMPLIANCE_OFFICER, LEGAL_COUNSEL | Body `{ "notes": "..." }` — tenant-scoped review only |
| `GET .../relevant` | same as list | Items matching system sector / control codes (heuristic) |

`GET` list response:

```json
{
  "productLabel": "Regulatory change monitoring feed",
  "disclaimer": "…not an official legal bulletin…",
  "latencyNote": "Latency is bounded by poll interval…",
  "items": [
    {
      "id": "…",
      "sourceCode": "CURATED_BOOTSTRAP",
      "title": "…",
      "summary": "…",
      "impactHints": [
        {
          "controlCode": "HUMAN_OVERSIGHT",
          "obligationCode": "HUMAN_OVERSIGHT_HIGH_IMPACT",
          "impactLevel": "UNCERTAIN",
          "impactNote": "…"
        }
      ],
      "reviewed": false
    }
  ]
}
```

Audit events: `reg_item.ingested` (payload includes `autoMutatesRiskOrControls: false`),
`reg_item.reviewed`.

Config: `assurance.reg-monitor.enabled`, `assurance.reg-monitor.poll-interval-ms`,
`assurance.reg-monitor.bootstrap-fixtures`. SSRF-safe HTTPS fetch (DNS pin, no redirects)
mirrors the evidence fetch pattern. Respect EUR-Lex / OJ ToS and rate limits when enabling
remote sources.

## Sector packs + integration SPI (Part 15)

**Product claim:** `3 sector packs + SPI` — not “all industries integrated.”
Packs are vertical overlays (extra controls, questionnaire defaults, sample templates).
They are **not** live production connectors to proprietary vendors without real OAuth apps.

Config: `assurance.sector.packs=insurance,hr,finance`

```http
GET /sector-packs
GET /sector-packs/{packId}
GET /sector-packs/resolve?sector=insurance
GET /sector-packs/{packId}/templates/{templateId}
POST /integrations/insurance/claims-model-register
GET /integrations/connectors/model-inventory
```

| Endpoint | Notes |
|---|---|
| `GET /sector-packs` | Enabled packs + `metricsLabel` + disclaimers |
| `GET .../resolve` | Resolve pack for free-text system `sector` |
| `GET .../templates/{id}` | Sample markdown (illustrative, not legal advice) |
| `POST .../claims-model-register` | Stub: maps external model id → system registry (`sector=insurance`) |
| `GET .../model-inventory` | Stub inventory (`connectorMode: stub`) |

Creating a system with `sector` matching an enabled pack attaches overlay controls
(e.g. insurance → `INS_CLAIMS_FAIRNESS`, `INS_ADVERSE_DECISION_REVIEW`, …).

See `docs/SECTOR_PACKS.md`.

## Approval Workflows

```http
GET /workflows/open
GET /workflows/mine
GET /workflow-notifications/mine
GET /systems/{systemId}/workflows
GET /systems/{systemId}/workflows/active
```

Workflow cycles are opened automatically when system creation, risk
reclassification, eval regression, or data-contract breach requires review.
Stages are assigned to tenant users by required role and must be completed in
stage order. ADMIN actors may override a stage with rationale.

```http
POST /systems/{systemId}/workflows/{workflowId}/stages/{stageId}/approve
POST /systems/{systemId}/workflows/{workflowId}/stages/{stageId}/reject
POST /systems/{systemId}/workflows/{workflowId}/stages/{stageId}/override
```

Approve request:

```json
{
  "rationale": "Evidence reviewed and accepted.",
  "oversightEvidence": "Human oversight SOP v3, section 4.2"
}
```

`oversightEvidence` is required for legal signoff approval. Rejection and
override requests require `rationale`. Stage actions append audit events and
create notifications for the next assigned reviewer.

## Release Gate

```http
GET /systems/{systemId}/release-gate
```

Response:

```json
{
  "systemId": "sys_001",
  "decision": "REVIEW",
  "blockers": [],
  "reviewReasons": [
    "Approval pending: COMPLIANCE_REVIEW (EVAL_REGRESSION cycle)",
    "Annex IV section IV.3 is missing from the conformity dossier"
  ]
}
```

`blockers` explain BLOCKED. `reviewReasons` list what keeps the system from PASS once nothing blocks it, and are
filled for every decision. What the gate reads:

| Input | Blocks | Sends to review |
|---|---|---|
| Risk class | PROHIBITED | |
| Eval | score below 78; no completed run (HIGH); latest run failed its threshold or a metric floor | score below 85 |
| Evidence | | coverage below 82% for the risk class |
| Data contract | open breach | open warning, or no contract |
| Controls | any control BLOCKED; unmet obligations from the determination run | |
| Human oversight (HIGH) | classification opened the oversight gap and `HUMAN_OVERSIGHT` is not PASS | |
| Approvals | the latest decided cycle was rejected (any risk class) | a cycle is open (HIGH) |
| Conformity (HIGH) | | an Annex IV section is MISSING; FRIA not RECORDED or WAIVED |

A rejected cycle stays in force until a later cycle is approved or an admin overrides the rejected stage; a new eval
run cannot clear it. Approval changes update the stored `releaseDecision` at once.

### CI/CD machine contract (Part 8)

Preferred for deploy bots (same decision engine; richer payload):

```http
GET /ci/release-gate?systemId={uuid}
```

Auth: `X-Api-Key` (recommended for CI service accounts) or Bearer JWT.

Response:

```json
{
  "systemId": "…",
  "systemName": "Claims Triage AI",
  "decision": "BLOCKED",
  "blockers": ["Data contract breach is open"],
  "reviewReasons": ["Evidence coverage 72% is below 82%"],
  "evalScore": 78,
  "evidenceCoverage": 72,
  "dataContractStatus": "BREACH",
  "riskClass": "HIGH",
  "exitCode": 1,
  "content": "Release gate BLOCKED — do not deploy. Blockers: …"
}
```

CLI helper: `scripts/ci-release-gate.sh` — exit **PASS=0**, **BLOCKED=1**, **REVIEW=2**. See `docs/OPS.md` and `.github/workflows/release-gate-example.yml`.

## Evidence Pack

JSON is the **primary** sealed, machine-readable export (PRD MVP). PDF is a Phase 6
human-readable export of the same sealed content.

### JSON (primary)

```http
GET /systems/{systemId}/evidence-pack
```

Roles: `ADMIN`, `AI_ENGINEERING_LEAD`, `COMPLIANCE_OFFICER`, `LEGAL_COUNSEL`, `AUDITOR`.

Response:

```json
{
  "systemId": "sys_001",
  "generatedAt": "2026-06-05T10:30:00Z",
  "decision": "blocked",
  "riskClassification": {},
  "evidence": [{ "coverage": 100, "openGaps": [], "documents": [
    { "documentId": "…", "type": "MODEL_CARD", "title": "GPT-4o System Card", "sha256": "sha256-e2579e…",
      "ingestionStatus": "indexed", "indexedAt": "2026-10-28T08:16:17Z" } ] }],
  "evalRuns": [{ "latestScore": 91, "releaseDecision": "PASS", "runs": [
    { "runId": "…", "dataset": "golden-health-v1", "modelVersion": "gpt-4o-mini-2024-07-18",
      "promptVersion": "v12", "threshold": 0.85, "metrics": { "faithfulness": 0.91, "biasSlicePassRate": 0.88 },
      "runDecision": "PASS", "synthetic": false, "completedAt": "2026-10-28T08:17:21Z" } ] }],
  "dataContracts": [],
  "approvals": [],
  "auditEvents": [],
  "evidencePackVersion": "1.0",
  "contentSha256": "hex-sha256-of-canonical-json",
  "generator": "devernance-api/0.1.0",
  "auditChainHead": "hex-hmac-sha256-of-latest-audit-event-or-null"
}
```

Seal notes:

- `evidencePackVersion` is currently `"1.2"`. Version 1.2 seals the document list (with each file's SHA-256) and the
  20 most recent completed eval runs (versions, metrics, run decision, and whether the metrics were synthetic), so a
  buyer can check both without the unsigned GRC index.
- `contentSha256` is SHA-256 over canonical JSON (every object's keys sorted, ISO-8601 instants)
  of the pack fields **excluding** `contentSha256` and `signature`, with `"conformity": {}` added. Parsing the
  exported JSON and re-serialising it this way reproduces the hash (see `docs/VERIFY_EVIDENCE_PACK.md`).
- `frameworks` holds the view of every framework the plan includes, keyed by framework (for example
  `frameworks["nist-ai-rmf"]`), inside the seal. The key is absent when the plan includes none.
- `generator` identifies the service build (`assurance.evidence-pack.generator`).
- `auditChainHead` is the tenant audit hash-chain tip **before** this export event (Part 6).
- Export writes audit event `evidence_pack.exported` with payload
  `{ decision, contentSha256, format, evidencePackVersion }` (`format` is `json` or `pdf`).
- Same system state + same clock → same `contentSha256` (clock is injectable for tests).

### PDF (Phase 6 polish)

```http
GET /systems/{systemId}/evidence-pack.pdf
```

Same roles as JSON. Returns `Content-Type: application/pdf` with:

- `Content-Disposition: attachment; filename="evidence-pack-{systemId}-{yyyy-MM-dd}.pdf"`
- `X-Content-Sha256: <same seal as JSON pack for this export>`

PDF sections: system identity, risk summary, evidence/evals/contracts/approvals, audit
excerpt, seal footer. Prefer the JSON pack for cryptographic verification of the seal.

## Framework crosswalks

```http
GET /systems/{systemId}/frameworks/{framework}
```

| `framework` | Type | Feature | Plans |
|---|---|---|---|
| `nist-ai-rmf` | crosswalk (NIST AI RMF 1.0 categories) | `NIST_CROSSWALK` | Team and above |
| `iso-42001` | crosswalk (ISO/IEC 42001:2023 Annex A identifiers; titles are Devernance paraphrases, no ISO text) | `ISO_CROSSWALK` | Business and above |
| `colorado-sb26-189` | checklist (Colorado SB 26-189 deployer duties; each item has `sourceUrl`) | `US_STATE_PACKS` | Business and above |

Unknown framework or system → `404`; a plan without the feature → `402` with `code: feature_<feature lowercase>`.

A crosswalk is live only once it has been reviewed against its source (`reviewedBy` set in its JSON file). With
`assurance.frameworks.serve-unreviewed=false` (the default in the `postgres` profile; env
`ASSURANCE_FRAMEWORKS_SERVE_UNREVIEWED`) an unreviewed crosswalk returns `404`, is left out of the evidence pack,
and is not cited by the questionnaire export. Local and test runs serve unreviewed crosswalks for preview, marked
`"reviewed": false`.

```json
{
  "framework": "nist-ai-rmf",
  "type": "crosswalk",
  "version": "1.0",
  "source": "https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.100-1.pdf",
  "reviewed": false,
  "disclaimer": "Mapping shows which evidence is relevant to each framework item. It is not an assessment of whether the system meets the framework.",
  "items": [
    {
      "id": "GOVERN 2",
      "title": "Accountability structures are in place ...",
      "status": "covered",
      "controls": ["HUMAN_OVERSIGHT"],
      "evidence": ["POLICY"],
      "requiredControls": ["HUMAN_OVERSIGHT"],
      "requiredEvidence": ["POLICY"],
      "sourceUrl": null
    }
  ]
}
```

`status` per item: `covered` when at least one mapped control is `PASS` and at least one mapped evidence type is
indexed; `partial` when only one holds; `no_evidence` otherwise. Every item is always listed. `reviewed` is false
until the owner has checked the mapping against the source. Crosswalk files live in
`services/api/src/main/resources/crosswalks/` and are validated at startup (unknown control codes or evidence types
stop the application).

## AU automated-decision disclosure pack

```http
GET /packs/au-adm
```

Feature `AU_ADM_PACK` (Business and above); `402` with `code: feature_au_adm_pack` otherwise. For the Australian
Privacy Act APP 1.7–1.9 privacy-policy duty that applies from 10 December 2026, lists every registered system:

- `include` — purpose, decision impact, affected people, and data sources are recorded. `draftStatement` is built
  only from those fields and starts with "Draft for review by your privacy counsel:". It says a person reviews the
  decisions only when the system's `HUMAN_OVERSIGHT` control is `PASS`; otherwise "to be confirmed".
- `needs_input` — `missing` names the empty fields; no draft is produced.
- `not_applicable` — a minimal-risk system with no affected people recorded.

The response also carries `obligationStart` (`2026-12-10`), `elements` (APP 1.8(a)–(c), official text), `source`
(OAIC), `legislation`, and `disclaimer`.

## Questionnaire answer export

```http
GET /systems/{systemId}/questionnaire-export?format=json|csv
```

Feature `QUESTIONNAIRE_EXPORT` (Business and above); `402` with `code: feature_questionnaire_export` otherwise.
`format` defaults to `json`; any other value than `json` or `csv` → `400`. Unknown system → `404`.

Drafts answers to 25 common AI-vendor due-diligence questions (`ai-vendor-core`) for one system. An answer is
written only when every fact its template names is recorded for the system (system fields, a control with status
`PASS`, an indexed document of the named type, approvals, completed evaluations, data contracts, audit events).
If any fact is missing the answer is exactly `Not recorded in Devernance — answer manually.`, `status` is
`manual`, and `missing` names the facts. Nothing is inferred.

```json
{
  "questionnaire": "ai-vendor-core",
  "version": "1.0",
  "systemId": "…",
  "systemName": "Claims triage",
  "generatedAt": "2026-10-11T09:00:00Z",
  "disclaimer": "Draft answers filled only from what is recorded in Devernance for this system. Review every answer before you send it. This is not legal advice.",
  "answered": 9,
  "manual": 16,
  "answers": [
    {
      "id": "AV-12",
      "topic": "human-oversight",
      "question": "How do people oversee the system's outputs and decisions?",
      "answer": "The human oversight control was marked passing on 2026-10-09. Policy on file: Human oversight policy v3.",
      "status": "answered",
      "evidence": [
        { "kind": "control", "ref": "HUMAN_OVERSIGHT", "status": "PASS", "updatedAt": "2026-10-09T08:12:44Z" },
        { "kind": "document", "ref": "POLICY", "status": "indexed", "id": "…", "title": "Human oversight policy v3", "sha256": "…", "createdAt": "…" }
      ],
      "missing": []
    }
  ]
}
```

`evidence` entries point at what backs the answer: `control` (the system's control and its real status, or
`NOT_ATTACHED`), `document` (each indexed document of that type with its checksum, or `none_indexed`), and `pack`
(`ref` is a path into the signed evidence pack JSON, for example `riskClassification.modelName`).

The CSV has the columns `id,topic,question,answer,status,evidence`, is UTF-8 with a byte-order mark, uses CRLF line
endings, and quotes cells as RFC 4180 requires. A cell that starts with `=`, `+`, `-` or `@` is prefixed with `'` so
a spreadsheet does not run it as a formula. The question bank lives in
`services/api/src/main/resources/questionnaires/ai-vendor-core.json` and is validated at startup.

## GRC export

```http
GET /systems/{systemId}/grc-export
```

Feature `INTEGRATIONS` (Business and above); `402` with `code: feature_integrations` otherwise. Same roles as the
evidence pack. Unknown system → `404`. Returns `application/zip` named `grc-export-<systemId>-<timestamp>.zip`,
with the pack hash in the `X-Content-Sha256` header, for upload as custom evidence to a GRC tool such as Vanta or
Drata:

| File | Content |
|---|---|
| `evidence-index.csv` | One row per indexed document: `document_id`, `document_title`, `type`, `created_date`, `sha256`, `pack_content_sha256`, `pack_signature_key_id` |
| `evidence-pack.json` | A freshly built signed evidence pack (the same content as `GET /systems/{systemId}/evidence-pack`) |
| `VERIFY_EVIDENCE_PACK.md` | How to check the pack's signature and recompute its hash |

`sha256` is the checksum recorded when the document was ingested: for a file upload, the SHA-256 of the uploaded file
as computed by the API (a `checksum` sent with the upload must match it, or the upload is refused with `400`); for a
JSON document, the SHA-256 of the extracted text unless the caller supplied a checksum of their own. `pack_content_sha256` and `pack_signature_key_id` tie each row to the pack in the
same ZIP. The pack is signed; the CSV is an index of it and is not itself signed. Each export is recorded in the
audit ledger as `evidence_pack.exported` with `format: grc-zip`. The CSV follows the same rules as the
questionnaire CSV (UTF-8 with byte-order mark, CRLF, RFC 4180 quoting, formula cells neutralised).

## Audit

```http
GET /audit-events?systemId=sys_001&limit=200&before=2026-10-19T09:30:00Z
```

Newest first. `limit` defaults to 500 and is capped at 1000; `systemId` and `before` are optional. To page back, pass
the `createdAt` of the last event you received as `before`. The full trail is in the workspace export
(`GET /account/export`).

```http
POST /audit-events
```

Request:

```json
{
  "systemId": "sys_001",
  "eventType": "approval.override_requested",
  "resourceType": "approval",
  "resourceId": "approval_001",
  "payload": {
    "reason": "Manual compliance review required"
  }
}
```

Audit events are append-only and cannot be modified through public APIs.

## Plans and billing

Every workspace has an effective plan, worked out on each request from its subscription, trial end and payment
grace period. Paid plans are activated only by verified Dodo Payments webhooks; the checkout redirect alone never
grants a plan.

| Plan | Gated systems | Editor seats | CI gate runs / month |
|---|---|---|---|
| Free | 1 | 3 | 300 |
| Trial (14 days from signup) | 15 | unlimited | unlimited |
| Team | 3 | 10 | unlimited |
| Business | 15 | unlimited | unlimited |
| Enterprise | unlimited | unlimited | unlimited |

Auditors and legal counsel are viewers and never count as editor seats. Pending editor invitations do.

When a limit is reached the API answers `402`:

```json
{ "error": "plan_limit", "code": "gated_systems", "message": "…", "upgradeUrl": "/settings#billing" }
```

Codes: `gated_systems`, `system_read_only`, `editor_seats`, `gate_runs`, and `feature_<name>` (for example
`feature_signed_pdf`). Systems beyond the plan's limit (oldest are kept) become read-only: every write that targets
them returns `402 system_read_only`; reads and the JSON evidence pack keep working. Nothing is ever deleted by a
downgrade. The signed PDF pack needs Team or above.

```http
GET  /api/v1/billing            # plan, status, interval, trial end, period end, grace, usage, limits (any role)
POST /api/v1/billing/checkout   # {"plan":"TEAM|BUSINESS","interval":"MONTHLY|YEARLY"} -> {"checkoutUrl"}  (ADMIN, session only)
POST /api/v1/billing/portal     # -> {"url"}, 409 until the workspace has a Dodo customer (ADMIN, session only)
POST /api/v1/billing/webhooks/dodo   # public; Standard Webhooks signature over the raw body
```

Webhook behaviour: a bad or missing signature returns `401` and records nothing; a redelivered `webhook-id` is a
no-op; an event older than the stored state is ignored; an event for an unknown workspace, an unknown product, or a
subscription already bound to another workspace is acknowledged (`200`) and ignored. A failed payment keeps the plan
for seven days, then the workspace drops to Free. A cancelled subscription keeps its plan until the paid period ends.

Environment: `DODO_BASE_URL`, `DODO_API_KEY`, `DODO_WEBHOOK_SECRET`, `DODO_PRODUCT_TEAM_MONTHLY`,
`DODO_PRODUCT_TEAM_YEARLY`, `DODO_PRODUCT_BUSINESS_MONTHLY`, `DODO_PRODUCT_BUSINESS_YEARLY`, and optionally
`DODO_BUSINESS_ID` (static customer-portal fallback).
