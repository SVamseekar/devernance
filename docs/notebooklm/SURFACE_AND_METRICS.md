# SURFACE AND METRICS

All numbers below are drawn directly from the codebase and canonical metric specifications (`docs/METRICS_CANONICAL.md`, measured at git tip `c0d5cd4` / `85ac589`, updated through PR #45).

---

## 1. Backend Surface (`services/api`)

| Surface Category | Metric / Value | Source / Verification Method |
|---|---|---|
| **Java Version** | Java 17 | `pom.xml` (`<java.version>17</java.version>`) |
| **Framework** | Spring Boot 3.3.7 | `pom.xml` (`spring-boot-starter-parent`) |
| **Domain Packages** | 13 primary domains + 2 cross-cutting | `services/api/src/main/java/os/assurance/eu/api/` (`system`, `control`, `evidence`, `eval`, `contract`, `workflow`, `audit`, `auth`, `tenant`, `determination`, `readiness`, `regmonitor`, `sector`, `persistence`, `observability`) |
| **REST Endpoints** | 64 `@*Mapping` endpoints | Counted across all `@RestController` classes |
| **Automated Tests** | 190 `@Test` / `@ParameterizedTest` cases | Unit + Integration test suite (`mvn test`) |
| **Java Production Source Files** | 240 files (~14.2k LOC) | `services/api/src/main/java` |
| **Java Test Source Files** | 41 files (~6.3k LOC) | `services/api/src/test/java` |
| **Flyway Migrations** | 16 core migrations (V1–V16) + 1 Postgres extension (V4) | `services/api/src/main/resources/db/` |

### Database Migration Inventory (`db/migration` & `db/postgresql`)

- **`V1`**: Core schema — `tenants`, `users`, `ai_systems`, `eval_runs` (legacy slice), `audit_events`.
- **`V2`**: Evidence RAG schema — `evidence_documents`, `evidence_chunks`, `evidence_queries`.
- **`V3`**: Evidence hardening — chunk content `sha256`, `embedding_provider`, metadata fields.
- **`V4` (Postgres only)**: `pgvector HNSW` cosine index on `evidence_chunks.embedding` (skipped gracefully on H2).
- **`V5`**: Eval dataset management — `eval_datasets`, queue worker durability columns on `eval_runs`.
- **`V6`**: Data contracts & drift — `data_contracts`, `drift_events`.
- **`V7`**: API keys — `api_keys` with hashed key storage.
- **`V8`**: Workflow engine — `approval_workflows`, `approval_stages`.
- **`V9`**: Authentication & security — `password_hash`, `refresh_tokens`, `signing_keys`.
- **`V10`**: Workflow enhancements — reviewer assignments, `oversight_evidence`, `workflow_notifications`.
- **`V11`**: Compliance controls — `controls`, `system_controls`.
- **`V12`**: System registry metadata — vendor, model, sector, data sources, decision impact, affected demographics.
- **`V13`**: Cryptographic audit chain — `prev_event_hash`, `event_hash`, retention metadata (`retain_until`).
- **`V14`**: OAuth integration — `oauth_provider` and `oauth_subject` columns on `users`.
- **`V15`**: Obligation determination — `obligation_rules`, `determination_runs`, `determination_obligations`.
- **`V16`**: Regulatory monitoring — `reg_sources`, `reg_items`, `reg_impact_hints`, `reg_item_reviews`.

### Key Backend Configuration Parameters (`application.properties`)

```properties
assurance.evidence.embedding-provider=local-hash          # Options: local-hash, djl-sentence
assurance.eval.worker.poll-interval-ms=5000               # Queue worker polling frequency
assurance.eval.callback.secret=${EVAL_CALLBACK_SECRET:}    # HMAC callback secret
assurance.eval.callback.signature-tolerance-seconds=300   # HMAC timestamp skew tolerance
assurance.reg-monitor.poll-interval-ms=60000              # Regulatory feed poll interval
assurance.audit.chain-secret=${AUDIT_CHAIN_SECRET:...}    # HMAC secret for audit hash-chain
assurance.certification-readiness.weight.*                # Dimension weights (sum to 100)
assurance.sector.packs=insurance,hr,finance               # Active sector packs
```

#### Certification Readiness Default Dimension Weights (Total = 100)
- Risk Classification: `10`
- System Controls: `15`
- Evidence Coverage: `15`
- Eval Gate Status: `15`
- Data Contracts: `10`
- Approval Workflows: `10`
- Legal Oversight Evidence: `10`
- Obligation Determination: `10`
- Audit Chain Verification: `5`

---

## 2. Frontend & Dashboard Surface (`apps/dashboard`)

| Surface Category | Metric / Value | Source / Verification Method |
|---|---|---|
| **Framework** | Next.js 16 (16.2.9) / React 19 / TypeScript | `apps/dashboard/package.json` |
| **Styling & UI** | Tailwind CSS v4, shadcn/ui (Base UI) | `apps/dashboard/package.json` |
| **State & Fetching** | TanStack Query v5 | `apps/dashboard/package.json` |
| **Lineage Visualization** | `@xyflow/react` | Interactive DAG renderer at `/contracts` |
| **TypeScript Source Files** | 93 `.ts` / `.tsx` files (~10.7k LOC) | `apps/dashboard/src/` |
| **Authenticated Routes** | 9 product routes | `/command`, `/systems`, `/approvals`, `/evidence`, `/evals`, `/contracts`, `/audit`, `/readiness`, `/reg-monitor` |
| **Public / Legal Routes** | 7 marketing & legal pages | `/` (Landing), `/login`, `/request-demo`, `/privacy`, `/terms`, `/refunds`, `/disclaimer` |

### Combined Codebase Size
- **Backend (Java Main + Test)**: ~20.5k LOC (240 main + 41 test files)
- **Frontend (TypeScript UI + BFF)**: ~10.7k LOC (93 TS/TSX files)
- **Total Monorepo Codebase**: **~31.2k LOC**

---

## 3. Data Sources & Integration Usage

| Data Source | Type / Schema | Real-World Purpose & Consumption Pattern |
|---|---|---|
| **Evidence Documents** | PDF, Markdown, Text | Uploaded DPIAs, model cards, vendor SOC2 reports, and policy docs. Chunked, embedded, and queried via cited-RAG. |
| **Eval Datasets** | JSON / Seeded Fixtures | Golden evaluation test sets (e.g. `golden-eu-claims-v4`). Used by `EvalQueueWorker` to benchmark model releases. |
| **Data Contracts & Drift** | JSON Schemas & Events | Tracks upstream data schema bounds. Open `BREACH` drift events flip contract status and trigger release gate blocks. |
| **Regulatory Feeds** | JSON / EUR-Lex Fixtures | Seeded `CURATED_BOOTSTRAP` classpath JSON fixture. Optional disabled HTTP endpoints for monitoring legislative updates. |
| **Sector Pack Templates** | Markdown classpath files | Domain-specific control templates under `classpath:sector/{packId}/` for Insurance, HR, and Finance. |

---

## 4. Honest Status Assessment — Capability Matrix

| Capability / Component | Real & Functional | Local / Stub / Simulated | Planned / Unverified |
|---|---|---|---|
| **AI Registry & Risk Classifier** | **Yes** — Fully implemented with DB persistence & guided rationales. | — | — |
| **Evidence RAG & Cited Answers** | **Yes** — Functional chunking, local-hash & DJL embeddings, citation mapping. | — | — |
| **Eval Queue Worker & HMAC Callbacks** | **Yes** — `SKIP LOCKED` DB worker polling & HMAC-SHA-256 callback validation. | Deterministic MVP synthetic eval generator included for offline testing. | — |
| **Data Contracts & Lineage Graph** | **Yes** — Contract tracking & `@xyflow/react` interactive DAG visualization. | — | — |
| **Staged Workflows & Oversight** | **Yes** — Multi-stage approvals with mandatory oversight evidence verification. | — | — |
| **Hash-Chained Audit Ledger** | **Yes** — HMAC-SHA-256 hash chaining & dual verify REST endpoints. | — | — |
| **Unified Release Gate Engine** | **Yes** — Signal fusion engine backing UI and machine `/ci/release-gate` exit codes. | — | — |
| **OAuth 2.0 (Google & Microsoft)** | **Yes** — Backend endpoints, BFF proxy, and local dev integration complete. | Tested locally via `run-local-dev.sh`. | Production IdP live verification pending sign-off (`docs/oauth-production-smoke-test.md`). |
| **Assistive Layers (Part 12–15)** | **Yes** — Obligation determination, 0-100 readiness, reg-monitor, sector packs complete. | Sector pack vendor connectors ship as `LoggingIntegrationConnector` stubs. | No live third-party vendor API sync. |
| **Docker Compose Stack** | **Yes** — Full container stack (`docker-compose.yml`) supporting API + Postgres. | MinIO & Dashboard container are optional profile flags. | — |
| **Terraform IaC** | — | — | Infrastructure skeleton only (`infra/terraform/`). Validates via CLI, no provider modules wired. |
| **SLO Latency & Uptime** | — | — | Target goals (<300ms reads, <4s RAG, 99.9% uptime) instrumented via Micrometer, not SLA-guaranteed. |

---

## 5. Documentation & Verification Gaps

- **Production Deploy Environment**: The live demo URL (`https://devernance.space`) is hosted on Cloudflare Workers (frontend) and external container hosting (API). Exact commit sync on live environments cannot be validated purely from reading git repository state.
- **Generative Synthesis Seam**: The evidence RAG implementation handles document chunking, embedding, vector similarity search, and source-citation assembly in-process. Downstream generative text synthesis over retrieved chunks relies on pluggable model interfaces without requiring external LLM API calls by default.
