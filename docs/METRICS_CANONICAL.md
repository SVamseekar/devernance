# Devernance — Canonical Metrics

**Measured date:** 2026-07-20
**Git tip at freeze:** `c0d5cd4` (`origin/main` — Part 3 complete: live legal + demo verified)
**Use for:** CV, pitch deck, portfolio, landing strip, README, external copy
**Regenerate:** re-run measurement commands below after any PR that changes API surface, tests, or migrations; update this file and `apps/dashboard/lib/metrics-canon.ts` together.

---

## Measurement commands (do not invent)

```bash
# endpoints
grep -rnE '@(Get|Post|Put|Patch|Delete)Mapping' services/api/src/main --include='*.java' | wc -l

# tests
grep -rnE '@Test|@ParameterizedTest' services/api/src/test --include='*.java' | wc -l

# LOC
find services/api/src/main -name '*.java' | xargs wc -l | tail -1
find services/api/src/test -name '*.java' | xargs wc -l | tail -1
find apps/dashboard \( -path '*/node_modules/*' -o -path '*/.next/*' \) -prune -o \
  \( -name '*.ts' -o -name '*.tsx' \) -print | xargs wc -l | tail -1

# versions
grep -A2 spring-boot-starter-parent services/api/pom.xml
grep -E '"next"|"react"' apps/dashboard/package.json
ls services/api/src/main/resources/db/migration/ | sort
ls services/api/src/main/resources/db/postgresql/
```

---

## Platform facts (measured 2026-07-20)

| Metric | Value | Notes |
|--------|-------|--------|
| Product | Devernance (formerly EU AI Assurance OS, renamed 2026-10-18) | Governance control plane for EU AI Act release governance |
| Live URL | https://euassuranceai.souravamseekar.com | Landing + dashboard shell |
| Repo | https://github.com/SVamseekar/devernance | Public |
| Backend | Java **17** · Spring Boot **4.1.1** · Tika **4.0.0** | `services/api/pom.xml` |
| Frontend | Next.js **16.2.9** · React **19.2.4** · TypeScript | `apps/dashboard/package.json` |
| Embeddings | DJL + ONNX Runtime · `sentence-transformers/all-MiniLM-L6-v2` | Postgres profile default `djl-sentence` |
| Embeddings (dev) | `local-hash` deterministic provider | H2 / default profile |
| Vector store | pgvector HNSW | Postgres path (`V4` postgresql migration) |
| Auth | JWT access + refresh · API keys (`X-Api-Key`) · JWKS | Cookie session via Next BFF |
| OAuth | Google + Microsoft **implemented** | Unit/integration tests green; **prod smoke pending** — do not claim “production SSO verified” until `docs/oauth-production-smoke-test.md` is signed off |
| Release decisions | **PASS · REVIEW · BLOCKED** | `ReleaseGateService` |
| Risk classes | **MINIMAL · LIMITED · HIGH · PROHIBITED** | Not “Unacceptable” as enum label |
| Risk classification | **Guided / recorded** (caller supplies class + basis) | Not ML auto-inference |
| Flyway | **V1–V20** (+ postgres **V4**) | Main: V1–V3, V5–V20; no V4 on H2 path. |
| REST `@*Mapping` endpoints | **64** | All controllers under `services/api/src/main` |
| Automated tests (`@Test` / `@ParameterizedTest`) | **190** | `services/api/src/test` |
| Java production source files | **240** | |
| Java test source files | **41** | |
| Java production LOC | **~14.2k** (14,159) | |
| Java test LOC | **~6.3k** (6,321) | |
| Dashboard TS/TSX files | **93** | excl. `node_modules` / `.next` |
| Dashboard TS/TSX LOC | **~10.7k** (10,714) | |
| Approx. combined app LOC | **~31.2k** | Java main+test + dashboard TS only |
| Dashboard product routes | **11** | command, systems, approvals, evidence, evals, contracts, audit, readiness, reg-monitor, settings, public-claims |
| Public / marketing routes | `/`, `/product`, `/how-it-works`, `/who-its-for`, `/pricing`, `/method`, `/faq`, login, request-demo, privacy, terms, refunds, disclaimer, DPA/MSA/order-form | Split landing; not a single scroll |
| Capability modules (landing) | **7** | Registry, risk, evidence RAG, eval gates, contracts, approvals/audit, certification readiness |
| Sector packs | **3** — insurance · HR · finance | SPI + vertical overlays; not live vendor connectors |
| Eval callback integrity | **HMAC-SHA-256** (`X-Eval-Signature: v1=<hex>`) | Eval result callback only |
| Audit | **Hash-chained** append-only ledger (HMAC-SHA-256 chain) + verify endpoints + retention hooks | Part 6 |
| Evidence export | Evidence Pack **JSON** + **PDF** (deterministic seal) | Part 7 |
| CI / release gate | GitHub Actions CI + CI release-gate endpoint | Part 8 |
| Infra | Docker Compose + Dockerfile(s) · Terraform under `infra/terraform` | Part 9 |
| Assisted determination | Questionnaire + rule engine → obligation map | Part 12 — **assisted**, not legal determination |
| Certification readiness | Weighted score 0–100 + structured gaps | Part 13 — **not** legal certification |
| Regulatory monitor | Polled change feed + impact hints | Part 14 — not live proprietary legal scrapers |
| Landing legal + demo | Privacy, terms, refunds, disclaimer, request-demo | Part 3 verified live |
| Public product year | **2026** | GitHub repo created 2026-06-05 |

---

## Feature claim checklist (true as of freeze)

| Feature | Claimable wording |
|---------|-------------------|
| Controls catalog | Control library + system-control status tracking |
| Hash-chained audit | SHA-256/HMAC hash-chained append-only audit ledger with chain verify |
| Evidence pack PDF | PDF + JSON evidence pack export with deterministic seal |
| CI release-gate | Machine-readable release-gate for CI pipelines |
| Compose / Terraform | Local Docker Compose + Terraform scaffolding |
| Assisted determination | Assisted obligation determination (disclaimers required) |
| Certification readiness | Certification **readiness** score + gap report |
| Reg monitor | Regulatory change monitoring feed (polled) |
| Sector packs | 3 sector packs (insurance, HR, finance) + integration SPI |
| Landing legal/demo | Live legal pages + demo request form |

---

## Public one-liner

> Devernance is a multi-tenant governance control plane that helps teams ship AI systems into the EU market with guided risk classification, cited-evidence RAG, eval and contract gates, assisted obligation maps, certification readiness scoring, and audit-ready evidence packs — without claiming legal certification or notified-body status.

---

## Landing metrics strip (subset)

Source of UI chips: `apps/dashboard/lib/metrics-canon.ts`
Keep strip qualitative + structural (release decisions, risk classes, stack, scale). **Never** put invented customer counts, ARR, or “X enterprises in production” on the strip.

Recommended scale line (measured):

- **Spring Boot 4.1.1 · Tika 4.0.0 · Flyway through V20 · Next.js 16 · pinned evgraph-cli 0.1.3**

---

## Correct phrases (use these)

| Topic | Correct phrase |
|-------|----------------|
| Embeddings | DJL + ONNX Runtime · all-MiniLM-L6-v2 (postgres); local-hash (dev) |
| Vector index | pgvector HNSW |
| Audit | Hash-chained append-only audit ledger |
| Eval integrity | HMAC-SHA-256 signed eval-result callbacks |
| Frontend | Next.js 16 |
| Migrations | Flyway V1–V20 (+ postgres V4) |
| Obligations | **Assisted** obligation determination / assisted obligation map |
| Certification | Certification **readiness** (score + gaps) |
| Sector | 3 sector packs (insurance, HR, finance) + SPI |
| Risk | Guided EU AI Act risk classification (Minimal / Limited / High / Prohibited) |
| Gates | PASS / REVIEW / BLOCKED release decisions |
| Auth (safe public) | JWT + API keys; Google/Microsoft OAuth **implemented, production smoke pending** |
| Live year | Live / public product **2026** |

---

## Do not claim

| Forbidden / overstated | Why |
|------------------------|-----|
| **FAISS** (for this product) | Not in this codebase; use DJL/ONNX |
| **HMAC-SHA-256 signed audit event stream** as the *only* audit story without hash-chain | Audit is a **hash-chained** ledger; HMAC also signs **eval callbacks** and OAuth state — say both precisely |
| **Next.js 14** | Product is Next.js **16** |
| **Flyway V1–V6 only** | Migrations through **V20** (+ postgres V4) |
| **“You are certified”** / legal certificate / notified-body attestation | Product does readiness only |
| **“Legal determination”** without **assisted** | Part 12 is assisted obligation mapping with disclaimers |
| **Automated risk classification** (ML sense) | API records submitted class + basis; guided workflow |
| Risk tiers **Unacceptable / High / Limited / Minimal** as enum labels | Code: **MINIMAL / LIMITED / HIGH / PROHIBITED** (map “unacceptable” → Prohibited carefully) |
| **Live since 2024** | Repo created **2026-06-05**; public product **2026** unless external proof exists |
| **Production OAuth/SSO verified** | Code + tests shipped; claim **prod smoke pending** until smoke doc executed in production |
| Customer counts, ARR, “X enterprises in production” | No substantiated commercial metrics |
| Live proprietary connectors (Workday, Guidewire, core banking, …) | Sector packs are SPI + stubs/overlays only |
| Official EU AI Act conformity assessment | Forever out of product claims |

---

## Paste-ready short stack line

```text
Spring Boot 4.1.1 · Java 17 · Tika 4.0.0 · Next.js 16 · pgvector HNSW · DJL/ONNX embeddings · Multi-tenant
JWT + API keys · Google/Microsoft OAuth (implemented; prod smoke pending)
Flyway V1–V20 · Hash-chained audit · HMAC eval callbacks · Evidence Pack JSON+PDF · pinned evgraph-cli 0.1.3
```

---

## Related

- Channel update packet: `docs/investigations/2026-07-19-channel-update-instructions.md`
- Investigation (historical baseline): `docs/investigations/2026-07-19-end-to-end-status-metrics-and-channel-alignment.md`
- Part 2 plan: `docs/superpowers/plans/2026-07-19-part-2-metrics-canon.md`
- OAuth smoke runbook: `docs/oauth-production-smoke-test.md`
- Sector pack claims: `docs/SECTOR_PACKS.md`

---

*Freeze generated 2026-07-20 from `origin/main` @ `c0d5cd4`. Re-measure before Part 16 binary updates.*
