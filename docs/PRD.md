# Product Requirements Document

## 1. Vision

Devernance helps organizations release AI systems in Europe with evidence, controls, and auditability. It acts as a release control plane that validates each AI system against EU AI Act-style risk obligations, GDPR privacy expectations, data governance checks, model evaluation gates, and human oversight workflows.

## 2. Target Users

- AI Engineering Lead: needs release gates for model and prompt changes.
- Compliance Officer: needs evidence that AI systems meet regulatory obligations.
- Legal Counsel: needs documented risk classification, oversight, and audit trails.
- Data Platform Lead: needs data-contract drift and lineage checks.
- Product Owner: needs a clear pass/review/block decision before launch.
- Auditor: needs immutable evidence of who approved what and why.

## 3. Primary Use Case

A European insurance provider wants to deploy Claims Triage AI. The system helps prioritize claims, recommend next actions, and route cases to reviewers. Because it can affect access to an essential private service, it is treated as high-risk.

Before release, the team must:

- Register the system and ownership.
- Classify risk and record the basis.
- Attach evidence such as DPIA, model card, policy docs, vendor docs, and data contracts.
- Run eval gates for faithfulness, bias, refusal behavior, accuracy, latency, and cost.
- Confirm data inputs have approved contracts and no drift.
- Require human oversight approval.
- Export an evidence pack for audit review.

## 4. MVP Scope

In scope (shipped on `main`):

- Multi-tenant AI system registry with guided risk classification.
- Control checklist mapped to EU AI Act-style obligations (`GET /controls`, system-control status).
- Evidence document inventory and cited evidence answers (RAG).
- Eval run records and release gate decisioning (`PASS` / `REVIEW` / `BLOCKED`).
- Data contract monitor status and drift events.
- Multi-stage approval workflows and in-app notifications.
- Hash-chained append-only audit event timeline.
- Evidence pack export (sealed JSON primary; PDF polish).

Auth for MVP delivery:

- Password JWT + refresh tokens and API keys (`X-Api-Key`) are the day-to-day MVP auth model.
- **Production SSO (Google / Microsoft OAuth)** is an **Enterprise / Phase 6** capability (Part 4). Code and tests are on `main`; production smoke remains pending (`docs/oauth-production-smoke-test.md`). Do not treat OAuth as MVP-blocking.

Out of scope for MVP (as final authority / overclaim):

- Full legal determination engine as legal advice or official conformity assessment.
- Automated certification claims (“you are certified”).
- Real-time regulatory change monitoring as a live official legal database.
- Complex live vendor sector connectors without SPI stubs.
- Notified-body status or official EU AI Act conformity assessment.

Phase 7 product expansions (assisted — never final legal authority; shipped as Parts 12–15):

- **Assisted obligation determination** (Part 12): questionnaire + deterministic ruleset → suggested applicability / obligation map mapped to control codes. Always labeled not legal advice; requires human legal review; never auto-changes risk class without human confirm.
- **Certification readiness score** (Part 13): weighted readiness + structured gaps only — never legal certification.
- **Regulatory change monitoring feed** (Part 14): near-real-time polled assistive feed with `UNCERTAIN` impact hints — not an official legal bulletin; never auto-mutates risk/controls.
- **Sector SPI packs** (Part 15): insurance, HR, finance overlays + connector stubs only.

## 5. Core Features

### AI System Registry

Records every AI system with owner, purpose, risk class, deployment context, vendor/model info, data sources, sector, decision impact, affected users, and release status.

### Risk Classification

Guided workflow records risk class (`MINIMAL` / `LIMITED` / `HIGH` / `PROHIBITED`), rationale, affected users, sector, decision impact, and required controls. Classification is **guided / recorded** (caller supplies class + basis), not ML auto-inference.

### Controls Catalog

Seeded EU AI Act-style control library with per-system status (`PASS` / `REVIEW` / `BLOCKED`). Blocked controls surface in release-gate blockers.

### Evidence RAG

Retrieves cited answers from policy docs, DPIAs, model cards, vendor docs, incident records, data contracts, and control mappings. Dev embeddings: `local-hash`; postgres default: DJL/ONNX all-MiniLM-L6-v2, ranked by hybrid search (Postgres BM25 + pgvector cosine, reciprocal rank fusion). PDF citations name the page.

### Eval Gates

Stores eval datasets, model/prompt versions, scores, thresholds, guard metrics, and release decisions. Durable DB-backed worker queue (not Kafka-required). Result callbacks use HMAC-SHA-256 signatures.

### Data Contract Monitor

Tracks input schemas, semantic contracts, lineage, drift events, severity, and remediation state. Open breaches roll into the release gate.

### Approval Workflow

Routes blocked or review systems through engineering lead, compliance, legal, and human oversight approvals with reviewer assignment and notifications.

### Audit Ledger

Append-only, **hash-chained** (HMAC-SHA-256) log of risk decisions, evidence answers, eval runs, approvals, overrides, exports, determination runs, and data drift events. Verify endpoints and ≥7-year retention hooks.

### Assisted Determination, Readiness, Reg Monitor, Sector Packs

See Phase 7 naming in §4. Product routes: determination APIs, certification readiness score/export, reg-monitor feed, sector pack resolve + insurance connector stubs.

## 6. MVP Acceptance Criteria

Verified primarily by `ClaimsTriageAcceptanceTest` (API integration) plus domain tests. Criteria and representative surfaces:

| Criterion | Evidence |
|---|---|
| A user can create an AI system and assign risk class | `POST /api/v1/systems`, `POST /api/v1/systems/{id}/risk-classification` · `ClaimsTriageAcceptanceTest` |
| The system calculates release decision as Pass, Review, or Blocked | `GET /api/v1/systems/{id}/release-gate` · `ReleaseGateService` tests |
| A high-risk blocked system explains the blocking controls | Gate `blockers[]` including `CONTROL:{code}` · `ClaimsTriageAcceptanceTest` |
| An evidence query returns answer text with citations | `POST /api/v1/evidence/query` · evidence tests |
| An eval run updates the release gate | `POST /api/v1/eval-runs`, callback / execute · eval tests |
| A data-contract breach blocks or reopens a release review | Drift `BREACH` → gate `BLOCKED` · contract tests |
| An evidence pack can be exported as sealed JSON (`contentSha256`) | `GET /api/v1/systems/{id}/evidence-pack` · pack tests + `ClaimsTriageAcceptanceTest` |
| PDF evidence pack export (Phase 6 polish; same seal) | `GET /api/v1/systems/{id}/evidence-pack.pdf` |
| Audit entries are created for all critical actions | `GET /api/v1/audit-events` · hash-chain verify · acceptance test |

**MVP acceptance status:** verified by `os.assurance.eu.api.ClaimsTriageAcceptanceTest` (Claims Triage high-risk control block + evidence pack + audit) as of 2026-07-20 on `main`. Full §6 matrix in `docs/investigations/2026-07-20-roadmap-completion-signoff.md`.

## 7. Non-Functional Requirements

- p95 API latency under 300 ms for registry reads (**target** — instrumented; not production-certified).
- p95 RAG answer latency under 4 seconds for MVP (**target**).
- 99.9% uptime target for production (**ops SLO**, not claimed from unit tests).
- Tenant data isolation (enforced; `TenantIsolationTest` in CI).
- Encryption in transit and at rest (documented; provider disk encryption for DBs).
- Audit event retention configurable for at least 7 years (implemented Part 6).
- Evidence pack export must be deterministic and traceable (JSON + PDF seal Part 7).

See `docs/NFR.md` for measurement hooks and honest limits.

## 8. Success Metrics

- 80% of AI systems have complete owner, risk, and evidence metadata.
- 95% of releases have documented gate decisions.
- 100% of high-risk systems have audit trail coverage.
- Eval regression catch rate above 90% on critical datasets.
- Mean time to produce audit evidence under 30 minutes.

Platform scale numbers for external copy: **only** from `docs/METRICS_CANONICAL.md` (do not invent customer counts or ARR).

## 9. Monetization

Potential SaaS plans:

- Starter: limited systems, evidence inventory, manual exports.
- Business: eval gates, data contracts, workflows, integrations.
- Enterprise: SSO/OAuth, custom controls, on-prem or VPC deployment, immutable hash-chained audit, advanced reporting.

## 10. Source PRD Mapping

- ComplianceGuard RAG contributes cited evidence and EU compliance readiness.
- EvalForge contributes eval gates, LLM-as-judge concepts, CI/CD decisioning.
- Data Contracts AI contributes drift and lineage controls.
- Java Spring Boot regulated-system PRDs contribute auditability, SAGA workflows, idempotency, security, and multi-tenancy.

---

*Document status: aligned to product on `main` as of Part 11 (2026-07-20). Never claims notified-body status, legal certification, or official conformity assessment.*
