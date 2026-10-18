# Channel Update Instructions (Non-Code Assets)

**Date:** 2026-07-20 (final freeze after Parts 0–1, 3–15; metrics Part 2)
**Audience:** Claude Chat / separate sessions editing CV, Portfolio, Pitch (Part 16)
**Canon:** `docs/METRICS_CANONICAL.md` (measured tip `c0d5cd4`)
**Rule:** Apply numbers and wording **only** from the canon. Do **not** invent customers, ARR, or production SSO verification.

**This packet does not edit binaries** — Part 16 applies CV DOCX/PDF, portfolio `projects.ts`, and pitch PDF.

---

## Paths

| Asset | Path |
|---|---|
| CV DOCX | `/Users/souravamseekarmarti/Documents/Marti_Soura_Vamseekar_CV.docx` |
| CV PDF | `/Users/souravamseekarmarti/Documents/Marti_Soura_Vamseekar_CV.pdf` |
| Portfolio project entry | `/Users/souravamseekarmarti/Projects/Portfolio/martisouravamseekar-portfolio/src/data/projects.ts` |
| Pitch deck | `/Users/souravamseekarmarti/Downloads/MSV_AI_Labs_Pitch_Deck.pdf` |
| Landing (code) | `apps/dashboard` — metrics strip via `lib/metrics-canon.ts` (Part 2) |
| Live product | https://euassuranceai.souravamseekar.com |
| GitHub | https://github.com/SVamseekar/devernance |

---

## Frozen numbers (2026-07-20)

| Claim | Value |
|---|---|
| Spring Boot | 3.3.7 (say **3.3**) |
| Java | 17 |
| Next.js | **16** (16.2.9) |
| Flyway | **V1–V16** (+ postgres V4) |
| REST endpoints | **64** |
| Automated tests | **190** |
| Combined app LOC (approx.) | **~31k** (Java main+test + dashboard TS) |
| Sector packs | **3** (insurance, HR, finance) |
| Risk classes | Minimal / Limited / High / Prohibited |
| Release decisions | PASS / REVIEW / BLOCKED |
| Public product year | **2026** |
| OAuth | Implemented (Google + Microsoft); **prod smoke pending** |

---

## Forbidden vs correct (all channels)

| Forbidden | Correct |
|---|---|
| FAISS | DJL + ONNX Runtime / all-MiniLM-L6-v2 |
| HMAC as audit-stream-only framing | **Hash-chained** audit ledger **and** HMAC-SHA-256 **eval-result callbacks** |
| Next.js 14 | Next.js **16** |
| Flyway V1–V6 only | Flyway through **V16** |
| “You are certified” | Certification **readiness** score + gaps |
| “Legal determination” (unqualified) | **Assisted** obligation determination |
| Automated risk classification (ML) | **Guided / recorded** risk classification |
| Unacceptable / High / Limited / Minimal | Minimal / Limited / High / **Prohibited** |
| Live since 2024 | Live / public product **2026** |
| Production SSO verified | JWT + API keys; OAuth **implemented, prod smoke pending** |

---

## A. CV — replace Devernance block

### Remove / fix these claims

- FAISS
- Next.js 14
- Flyway V1–V6 only
- “HMAC-SHA-256 signed audit event stream” (without hash-chain + eval callback split)
- “automated … risk classification” (as ML automation)
- Risk tiers “Unacceptable, High, Limited, Minimal” without Prohibited mapping
- “You are certified” / legal determination without “assisted”

### Paste-ready replacement (English)

```text
Devernance — Governance Control Plane for EU AI Act  ·  GitHub
Spring Boot 3.3 · Java 17 · Next.js 16 · pgvector HNSW · DJL/ONNX embeddings · Multi-tenant

• Governance: AI system registry with guided EU AI Act risk classification
  (Minimal / Limited / High / Prohibited), open-gap tracking, controls catalog, and
  release decisions PASS / REVIEW / BLOCKED from evidence coverage, eval thresholds,
  and data-contract status.
• Evidence & evals: Cited-evidence RAG (DJL + ONNX Runtime all-MiniLM-L6-v2; pgvector HNSW
  on Postgres); durable eval worker (select-for-update-skip-locked); HMAC-SHA-256 signed
  eval-result callbacks; Evidence Pack JSON + PDF with deterministic seal.
• Assurance automation: assisted obligation determination; certification readiness score
  (0–100) + structured gap report — not legal certification or notified-body status;
  regulatory change feed; 3 sector packs (insurance, HR, finance) + integration SPI.
• Architecture: Spring Boot API with Flyway migrations (V1–V16), hash-chained audit ledger,
  tenant-scoped JPA + JWT/API-key auth, Google/Microsoft OAuth (implemented; prod smoke
  pending), Next.js 16 dashboard (lineage DAG, contracts, approvals, readiness, reg monitor).
• Scale (code-measured 2026-07-20): 64 REST endpoints · 190 automated tests · ~31k app LOC.
```

Also fix **summary line** if it over-indexes wrong tech (FAISS / Next 14) for this product.

---

## B. Portfolio — `projects.ts` Devernance entry

### Before (stale patterns to kill)

- “automated EU AI Act risk classification”
- Flyway **V1–V6**
- “HMAC-SHA-256 signed audit event stream” as sole audit claim
- FAISS
- Period “2024 – Present” without proof
- Next.js 14

### After — suggested `metrics` array

```ts
metrics: [
  "AI system registry with guided EU AI Act risk classification (Minimal / Limited / High / Prohibited)",
  "Cited-evidence RAG: DJL + ONNX Runtime (all-MiniLM-L6-v2), pgvector HNSW; PASS / REVIEW / BLOCKED release gates",
  "HMAC-SHA-256 signed eval-result callbacks; hash-chained append-only audit ledger; Evidence Pack JSON + PDF",
  "Assisted obligation determination; certification readiness score + gaps; reg monitor; 3 sector packs (insurance, HR, finance)",
  "Spring Boot 3.3 backend: Flyway V1–V16, multi-tenant JPA, JWT + API keys, Google/Microsoft OAuth (prod smoke pending), 64 endpoints · 190 tests",
  "Next.js 16 dashboard with interactive DAG lineage (@xyflow/react), contracts, approvals, readiness, reg monitor",
],
```

### Stack hygiene

**Keep:** Java 17, Spring Boot 3.3, PostgreSQL, pgvector, Next.js 16, DJL, ONNX Runtime, HuggingFace tokenizers, AWS S3, Apache Tika, @xyflow/react

**Avoid:** FAISS

### Period

Prefer `"2026 – Present"` unless you have independent proof of 2024 production traffic.

### Links

- Live: `https://euassuranceai.souravamseekar.com`
- GitHub: `https://github.com/SVamseekar/devernance`

---

## C. Pitch deck — Devernance slide

### Keep

- Control plane narrative
- Aug 2026 enforcement market timing
- Spring Boot 3.3 · Next.js 16 · pgvector HNSW · ONNX/DJL

### Change (before → after)

| Current / wrong | Corrected |
|---|---|
| “HMAC-256 Tamper-evident audit event streams” only | “Hash-chained audit ledger” + “HMAC-SHA-256 eval-result callbacks” |
| “automated risk classification” | “Guided risk classification + release gates” |
| “Live since 2024” | “Live 2026” / “Public product 2026” |
| FAISS | DJL/ONNX |
| Next 14 / Flyway V1–V6 | Next 16 · Flyway through V16 |
| Legal certification tone | Certification **readiness**; assisted obligations |
| Thin metrics only | **64** API endpoints · **190** automated tests · 3 sector packs · PDF evidence packs |

### Optional metric bullets for slide

```text
• 64 REST endpoints · 190 automated tests · multi-tenant Spring Boot 3.3 + Next.js 16
• PASS / REVIEW / BLOCKED gates · hash-chained audit · HMAC eval callbacks
• Assisted obligation maps · certification readiness score · 3 sector packs
• Live: euassuranceai.souravamseekar.com
```

### Do not invent

Revenue, customer counts, “X enterprises in production,” notified-body status, production OAuth verified without smoke sign-off.

---

## D. Landing (code) — already wired (Parts 2–3)

- Metrics strip: `apps/dashboard/lib/metrics-canon.ts` + `components/landing/metrics-strip.tsx`
- Legal: `/privacy`, `/terms`, `/refunds`, `/disclaimer`
- Demo: `/request-demo` (enterprise order-form / SOW refunds tone — not consumer SaaS)
- Primary CTA: Request demo
- Disclaimer: not legal certification

Re-measure strip if canon numbers change; do not invent marketing headcount metrics.

---

## E. Consistency checklist (all four channels)

- [ ] Same product one-liner (control plane / EU AI Act release governance)
- [ ] Same risk class names: Minimal / Limited / High / Prohibited
- [ ] Same release decision names: PASS / REVIEW / BLOCKED
- [ ] Same embedding stack (DJL/ONNX, **not** FAISS)
- [ ] Same audit story (hash-chained ledger + HMAC eval callbacks)
- [ ] Same auth story (JWT/API keys; OAuth implemented, **prod smoke pending** until signed off)
- [ ] Same Flyway range (through **V16**)
- [ ] Same Next.js major (**16**)
- [ ] Same live URL `https://euassuranceai.souravamseekar.com`
- [ ] Same GitHub URL
- [ ] Assisted obligations / certification readiness — never “certified” / unqualified “legal determination”
- [ ] 3 sector packs only (no fake live Workday/Guidewire connectors)

---

## F. Part 16 handoff

When editing binaries:

1. Open `docs/METRICS_CANONICAL.md`
2. Apply sections A–C above
3. Run checklist E
4. Do not re-measure inventively — if code changed after `c0d5cd4`, re-run measurement commands in the canon and update both canon + this packet

---

*Packet refreshed 2026-07-20 with Part 2 metrics freeze. Part 16 applies CV/pitch/portfolio binaries.*
