# Implementation Roadmap

Honest status against `origin/main`. Program index: `docs/superpowers/plans/2026-07-20-INDEX.md`. Metrics: `docs/METRICS_CANONICAL.md`. Signoff: `docs/investigations/2026-07-20-roadmap-completion-signoff.md`.

## Phase 0: Prototype

Status: **Complete**.

- Static dashboard.
- Simulated system registry.
- Simulated RAG answer.
- Simulated eval gate.
- Simulated data-contract drift.
- Evidence pack export.
- README and engineering docs.

Legacy static UI retained at `apps/web/` for reference only.

## Phase 1: Backend Foundation

Status: **Complete**.

- Backend stack: Spring Boot 3.3 / Java 17 (`services/api`).
- PostgreSQL schema with Flyway (H2 for local/default tests).
- Tenant, user, and role model.
- AI system registry APIs.
- Audit event append API.
- Release gate calculation service.
- Backend tests for release gate logic.

## Phase 2: Evidence and RAG

Status: **Complete** (production-hardened foundation).

- Evidence document upload metadata: implemented.
- Text extraction pipeline: Apache Tika 4.0.0 for uploaded files, with metadata fallback for provided text.
- Chunking and embedding: implemented with deterministic local embeddings (`local-hash`).
- Embedding provider seam: implemented; postgres profile default `djl-sentence` (DJL + ONNX all-MiniLM-L6-v2).
- Hybrid search: BM25 keywords + embedding similarity fused by reciprocal rank; on Postgres, keyword ranking runs in SQL (`db/postgresql/V32_1`) and meaning ranking runs in SQL only with pgvector, otherwise in the API; fusion always runs in the API. On H2 everything runs in memory. Sentence-aligned chunks; PDF chunks cite their page.
- Citation-required answer generation: implemented.
- Prompt injection safeguards and upload/query guardrails: implemented.
- Dashboard evidence upload/list/query flow: Next.js production UI.

### Phase 2 leftovers (resolved or deferred with links)

| Leftover | Status |
|---|---|
| Object-store / binary upload path | **Resolved (Part 9)** — optional S3/MinIO via `ASSURANCE_STORAGE_*`; Compose MinIO profile |
| Non-local embedding provider | **Resolved** — DJL/ONNX on postgres profile |
| Production auth / RBAC | **Resolved** — JWT + API keys (V7/V9); roles in `ROLE_MATRIX.md`; OAuth Part 4 |
| Operational monitoring | **Resolved (Part 8)** — Actuator metrics + product counters; see `docs/OPS.md` |
| PDF binary extraction from object store | **Deferred** — text/content upload + synthetic URI path remain primary |

## Phase 3: Eval Gates

Status: **Complete**.

- Eval dataset registry: create/list with tenant scope.
- Eval run creation and status tracking for registered datasets.
- Metrics capture via API result callback (HMAC-SHA-256).
- Threshold-based release decision update and gate recalculation.
- Worker-owned metric execution (deterministic MVP metrics).
- Async worker queue: durable DB state, background dispatch, retry metadata, failure capture (`SELECT … FOR UPDATE SKIP LOCKED`). Kafka is **not** required.

## Phase 4: Data Contracts

Status: **Complete**.

- Data contract CRUD with tenant scope.
- Drift event ingestion (open / acknowledged / resolved).
- Contract-to-system mapping via `system_id`.
- Lineage display: React Flow DAG in Next.js dashboard at `/contracts`.
- Release gate integration for warning and breach rollups.
- Frontend rewrite: Next.js 16 + TypeScript + Tailwind + shadcn/ui + TanStack Query at `apps/dashboard/`.

## Phase 5: Workflow

Status: **Complete** (V8/V10; Part 0 finish).

- Approval stages: engineering lead, compliance, legal signoff.
- Reviewer assignment by tenant role.
- Override workflow for ADMIN with rationale + audit.
- Human oversight evidence capture for legal signoff.
- Notifications: persisted reviewer workflow notifications.
- Approvals dashboard against live workflow APIs.

Evidence: PR #1 (Part 0), workflow migrations V8/V10.

## Phase 6: Enterprise Readiness

Status: **Complete** (items landed on `main`; residual: OAuth **production smoke** not signed off).

| Item | Status | Evidence |
|---|---|---|
| SSO/OIDC (Google + Microsoft) | **Complete (code + tests)**; prod smoke pending | Part 4 · PR #27 · `docs/oauth-production-smoke-test.md` |
| Tenant isolation hardening | **Complete** | Part 10 · PR #7 · `TenantIsolationTest` |
| Immutable audit storage (hash-chained) | **Complete** | Part 6 · PR #5 · Flyway V13 · verify endpoints |
| Evidence pack PDF/JSON export | **Complete** | Part 7 · PR #9 · sealed `contentSha256` |
| CI/CD release gate integration | **Complete** | Part 8 · PR #24 · `GET /api/v1/ci/release-gate` · `scripts/ci-release-gate.sh` |
| Observability (metrics / health) | **Complete** | Part 8 · Actuator + product counters · `docs/OPS.md` |
| Deployment with Docker and Terraform | **Complete** | Part 9 · PR #26 · `infra/docker-compose.yml` · `infra/terraform/` |

## Phase 7: Program expansions (assisted + readiness + reg + sector)

Status: **Complete** (assisted naming forever — never final legal authority).

| Item | Status | Evidence |
|---|---|---|
| Assisted obligation determination | **Complete** | Part 12 · PR #28 · Flyway V15 · `/determination/*` |
| Certification readiness score + gaps | **Complete** | Part 13 · PR #30 · **not** legal certification |
| Polled regulatory change monitoring feed | **Complete** | Part 14 · PR #32 · Flyway V16 · not an official legal bulletin |
| Sector packs: insurance, HR, finance + SPI | **Complete** | Part 15 · PR #34 · stubs only; see `docs/SECTOR_PACKS.md` |

## Phase 8: Governance runtime (policy-as-code, runtime enforcement, reconciliation)

Status: **Built and tested** (API, CLI, SDK, dashboard, end-to-end script in CI). Not yet deployed to the hosted API.
Design: `docs/GOVERNANCE_PLATFORM.md`.

| Item | Status | Evidence |
|---|---|---|
| Versioned `AISystem` / `GovernancePolicy` manifests, declarative apply | **Built** | Flyway V33 · `GovernanceApplyService` |
| Validation (ungoverned / prohibited tools, unapproved model) and policy tests | **Built** | `GovernanceValidator`, `POST /governance/simulate` |
| Runtime decisions ALLOW / DENY / REVIEW, fail closed | **Built** | `PolicyEvaluator`, `POST /governance/authorize` |
| Action approvals (session only, policy roles, no self-approval, expiry) | **Built** | `GovernanceAuthorizationService` · dashboard `/governance` |
| Signed, single-use, request-bound authorization tokens | **Built** | `POST /governance/authorizations/verify` |
| `RUNTIME` API key scope for agents | **Built** | `ApiKeyScope` |
| Drift reconciliation (model, prompt, tools, policy version) | **Built** | `GovernanceDriftService` |
| Release check combining gate, validation and drift | **Built** | `GET /governance/release-check` |
| Signed evidence bundle + offline verifier; evidence closure report | **Built** | `GovernanceEvidenceService`, `sdk/python/devernance/verify.py` |
| `devernance` CLI and Python SDK (`guard`, `governed`) | **Built** | `sdk/python` |
| Runnable insurance claims example + end-to-end script in CI | **Built** | `examples/claims-agent`, `scripts/governance-e2e.sh` |
| Local decision point (sidecar) with signed policy distribution | **Planned** | see GOVERNANCE_PLATFORM.md §10 |
| Kubernetes operator for drift observation | **Planned** | |
| OPA / Cedar adapter, MCP gateway, Java and TypeScript SDKs | **Planned** | |
| Trusted timestamps on bundles, N-of-M approvals | **Planned** | |

## Cross-cutting delivery (Wave F and docs)

| Item | Status | Evidence |
|---|---|---|
| Industry-grade GitHub (CI, license, CONTRIBUTING) | **Complete** | Part 1 · PR #2 |
| Landing legal pages + demo form + SEO | **Complete** | Part 3 · PR #36 |
| Metrics canon freeze | **Complete** | Part 2 · PR #38 · `docs/METRICS_CANONICAL.md` |
| Docs alignment (PRD / ROADMAP / README / arch) | **Complete** | Part 11 · PR #40 |
| CV / pitch / portfolio binaries | **Planned** | Part 16 — no binary edits in Part 11 |

## Still not claimed

- Notified-body / official conformity assessment / “you are certified”.
- Production OAuth smoke verified until smoke runbook is executed.
- Live proprietary vendor connectors (Workday, Guidewire, core banking, …).
- Customer counts, ARR, or invented commercial metrics.
- That the governance runtime sees every action of an agent: it governs only actions that ask for authorization.
- Kubernetes-native enforcement, OPA/Cedar integration or a local decision point (all planned, none built).
