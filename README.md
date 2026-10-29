# Devernance

[![CI](https://github.com/SVamseekar/devernance/actions/workflows/ci.yml/badge.svg)](https://github.com/SVamseekar/devernance/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)

**Live product:** [https://euassuranceai.souravamseekar.com](https://euassuranceai.souravamseekar.com)
**Repo:** [github.com/SVamseekar/devernance](https://github.com/SVamseekar/devernance)

Devernance (formerly EU AI Assurance OS) is a multi-tenant **governance control plane** for teams shipping AI systems into the EU market. Development and governance, hand in hand. Production surface:

| Surface | What it is |
|---|---|
| **Next.js dashboard + landing** | Authenticated product shell + public marketing/legal pages |
| **Spring Boot API** | Registry, evidence RAG, evals, contracts, workflows, audit, release gates |
| **Live URL** | Landing + dashboard shell on Vercel (API may be hosted separately) |

It helps teams validate AI releases against EU AI Act–oriented controls by combining:

- AI system registry and **guided** risk classification (`MINIMAL` / `LIMITED` / `HIGH` / `PROHIBITED`)
- Control catalog + system-control status tracking
- Cited compliance evidence RAG (local-hash in dev; DJL/ONNX all-MiniLM-L6-v2 + pgvector on postgres)
- Eval gates with durable worker queue and **HMAC-SHA-256** signed result callbacks
- Data-contract drift monitoring and lineage graph
- Multi-stage approval workflows (reviewer assignment, human oversight evidence, notifications)
- **Hash-chained** append-only audit ledger with verify endpoints
- Sealed evidence packs (**JSON** primary + **PDF**)
- CI release-gate contract for deploy pipelines
- **Pinned legal corpus**, a human **proposal queue** for control mappings (a person accepts them; they are not auto-applied), and release-gate modes **INFORMATIONAL**, **WARNING**, **APPROVAL_REQUIRED**, and **BLOCKING**
- **Pinned Evgraph scan** (`evgraph-cli` **0.1.3**): the evidence pack and the live scan share the same current gap. Missing approval timestamps stay inconclusive
- **Assisted** obligation determination, certification **readiness** scoring, polled reg-monitor feed, and 3 sector packs (insurance · HR · finance)

> This product assists governance and release readiness. It is **not** a notified body, does **not** issue legal certifications, and does **not** replace qualified legal counsel.

**Commercial path:** scoped readiness work for one named AI system after a demo — no public list price. See [`/pricing`](https://euassuranceai.souravamseekar.com/pricing). Evgraph is a **separate** PyPI library — this repo exports the files it already reads.

## Stack

| Layer | Technology |
|---|---|
| Dashboard + landing | **Next.js 16**, React 19, TypeScript, Tailwind CSS v4, TanStack Query, shadcn/ui |
| API | **Spring Boot 4.1.1**, Java 17, **Tika 4.0.0**, Flyway **V1–V20** (+ postgres V4), Spring Data JPA |
| Auth | Password JWT + refresh · API keys (`X-Api-Key`) · JWKS · Google/Microsoft OAuth **implemented** (prod smoke pending) |
| Data | H2 (local default) or PostgreSQL (full-text BM25 + optional pgvector) |
| Deploy | Dashboard on Vercel; API via Docker Compose or host (Flyway on boot); Terraform skeleton in `infra/terraform/` |

## Repository layout

```text
apps/dashboard/   Production Next.js app (landing + authenticated dashboard)
apps/web/         Legacy static HTML/CSS/JS prototype (reference only — not the product)
services/api/     Spring Boot API
docs/             PRD, architecture, API, schema, roadmap, security, metrics, ops
infra/            Docker Compose, API/dashboard Dockerfiles, Terraform skeleton
scripts/          Local, Compose smoke, CI release-gate helpers
```

## Quick start

### Option A — Docker Compose (Postgres + API)

```bash
docker compose -f infra/docker-compose.yml --env-file .env.example up --build
# → API http://localhost:8080  ·  Postgres localhost:5432
```

See [`infra/README.md`](./infra/README.md) and [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md).

### Option B — native tooling

#### 1. API (H2 default)

```bash
cd services/api
mvn test
mvn spring-boot:run
# → http://localhost:8080  (Flyway through V20)
```

Postgres profile:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/eu_ai_assurance \
DATABASE_USERNAME=eu_ai_assurance \
DATABASE_PASSWORD=eu_ai_assurance \
EVAL_CALLBACK_SECRET=replace-me \
AUDIT_CHAIN_SECRET=replace-me \
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

#### 2. Dashboard

```bash
cd apps/dashboard
npm ci
npm run dev
# → http://localhost:3000  (proxies /api/v1/* → localhost:8080)
```

Local H2 runs seed demo users (`compliance@example.com`, `admin@example.com`, …) because
`assurance.bootstrap.seed-demo-users=true` in the default profile. The `postgres` profile never seeds them and
refuses to start if an old database still has them with the development password — run
`scripts/disable-seeded-users.sql` once on such databases.

### Legacy prototype (optional, not production)

```bash
python3 -m http.server 4173 --directory apps/web
# → http://localhost:4173
```

## Environment variables

Full template: [`.env.example`](./.env.example). Deploy matrix: [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md). NFR secrets: [`docs/NFR.md`](./docs/NFR.md).

| Variable | Where | Purpose |
|---|---|---|
| `EVAL_CALLBACK_SECRET` | API | HMAC secret for eval result callbacks (required in non-local envs) |
| `AUDIT_CHAIN_SECRET` | API | Hash-chain HMAC for immutable audit ledger |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | API | Postgres profile |
| `ASSURANCE_API_BASE_URL` | Dashboard | Upstream API base (default `http://localhost:8080`) |
| `NEXT_PUBLIC_SITE_URL` | Dashboard | Canonical site URL for SEO/metadata |
| `DISCORD_WEBHOOK_URL` / `DISCORD_DEMO_WEBHOOK_URL` | Dashboard | Server-only: demo form → Discord |
| `ASSURANCE_STORAGE_*` | API | Optional S3/MinIO object store for evidence uploads |
| `EVIDENCE_EMBEDDING_PROVIDER` | API | `local-hash` (H2) or `djl-sentence` (postgres default) |
| `NEXT_PUBLIC_USE_MOCK_DATA` | Dashboard | Production must stay false |
| OAuth client id/secret (Google/Microsoft) | API + dashboard | Part 4 — see `.env.example` and `docs/oauth-production-smoke-test.md` |

## Roadmap status (honest)

| Phase | Status on `main` |
|---|---|
| 0–4 Core product (systems, evidence, evals, contracts) | **Complete** |
| 5 Approval workflows + notifications + oversight | **Complete** |
| 6 Enterprise (OAuth, hash-chained audit, PDF packs, CI gate, Docker/TF, tenant NFRs) | **Complete** (OAuth **prod smoke pending**) |
| 7 Assisted determination, readiness, reg-monitor, sector packs | **Complete** (assisted / readiness naming only) |
| Landing legal + demo + SEO (Part 3) | **Complete** |
| Metrics freeze (Part 2) | **Complete** — [`docs/METRICS_CANONICAL.md`](./docs/METRICS_CANONICAL.md) |
| Docs alignment (Part 11) | **Complete** (PR #40) |
| CV / pitch / portfolio binaries (Part 16) | Planned |

See [`docs/ROADMAP.md`](./docs/ROADMAP.md) and [`docs/superpowers/plans/2026-07-20-INDEX.md`](./docs/superpowers/plans/2026-07-20-INDEX.md).

## Documentation

- [`docs/METRICS_CANONICAL.md`](./docs/METRICS_CANONICAL.md) — **authoritative** measured platform numbers
- [`docs/BRAND.md`](./docs/BRAND.md) — name rules, identifiers that keep the old name, domain-day checklist
- [`docs/PRD.md`](./docs/PRD.md) — product requirements
- [`docs/ROADMAP.md`](./docs/ROADMAP.md) — phased delivery and honesty matrix
- [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) — system design
- [`docs/API.md`](./docs/API.md) — HTTP contract
- [`docs/SCHEMA.md`](./docs/SCHEMA.md) — SQL tables (Flyway V1–V20)
- [`docs/SECURITY.md`](./docs/SECURITY.md) — threat model
- [`docs/DEPLOYMENT.md`](./docs/DEPLOYMENT.md) — Docker, Vercel + API host, migrations, rollback
- [`docs/OPS.md`](./docs/OPS.md) — CI release gate + observability
- [`docs/NFR.md`](./docs/NFR.md) — latency/uptime targets and secrets
- [`docs/SECTOR_PACKS.md`](./docs/SECTOR_PACKS.md) — sector pack claims
- [`SECURITY.md`](./SECURITY.md) — vulnerability reporting
- [`CONTRIBUTING.md`](./CONTRIBUTING.md) — PR and branch conventions

## Contributing & CI

PRs to `main` run GitHub Actions CI:

1. **Secret scan** (Gitleaks)
2. **API tests** — Java 17, `mvn -B test` (path-filtered; always on `main` pushes)
3. **Dashboard checks** — Node 22, `tsc --noEmit`, `npm run build` (path-filtered)
4. **Terraform validate** when `infra/terraform/**` changes

Never commit secrets. Prefer feature branches and conventional commits.

## License

MIT — see [LICENSE](./LICENSE).
