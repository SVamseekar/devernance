# Part 16 — CV proposed changes (do not auto-apply)

**Target (live — leave alone until you approve):**
`/Users/souravamseekarmarti/Documents/Marti_Soura_Vamseekar_CV.docx`
`/Users/souravamseekarmarti/Documents/Marti_Soura_Vamseekar_CV.pdf`

**Optional later copies (if you want a parallel file, not overwrite):**
`Marti_Soura_Vamseekar_CV_Part16_metrics.docx`
`Marti_Soura_Vamseekar_CV_Part16_metrics.pdf`

**Canon:** `docs/METRICS_CANONICAL.md`
**Scope:** Only the **Devernance** project block (and any summary lines that over-claim this product’s stack). Do **not** invent numbers. Leave WorkforceGuard / Aequitas / Masova / Bharat Alpha claims alone unless their own canons exist.

---

## A. Current text (as of original CV — before any Part 16 edit)

```text
Devernance — Governance Control Plane for EU AI Act  ·  GitHub

Spring Boot · Java · Next.js 14 · FAISS + pgvector HNSW · Multi-tenant ·
Regulated AI teams

• Governance Engine: AI system registry with automated EU AI Act risk
  classification across all four risk tiers (Unacceptable, High, Limited,
  Minimal); cited-evidence RAG (FAISS + pgvector HNSW) grounding every
  classification decision in the relevant Article and Annex of the Act.

• Eval Gate & Audit: Eval gate simulation delivering PASS / REVIEW / BLOCKED
  release decisions with HMAC-SHA-256 signed audit event stream; Evidence Pack
  JSON generates audit-ready EU AI Act technical documentation at every model
  release cycle.

• Architecture: Spring Boot backend with Flyway schema migrations (V1–V6),
  select-for-update-skip-locked eval worker, multi-tenant isolation via scoped
  JPA + TenantContextFilter; Next.js dashboard with interactive DAG lineage
  graph, data-contract drift monitoring, full data lineage traceability, and
  RBAC + SSO roadmap.
```

---

## B. Problems to fix (map)

| Current claim | Why wrong | Correct |
|---------------|-----------|---------|
| Next.js 14 | Product is Next.js 16 | Next.js **16** |
| FAISS + pgvector | FAISS not in this product | DJL + ONNX · pgvector HNSW |
| automated … risk classification | API records guided class + basis | **guided** risk classification |
| Unacceptable, High, Limited, Minimal | Code enums differ | Minimal / Limited / High / **Prohibited** |
| HMAC-SHA-256 signed audit event stream (only) | Audit is hash-chained ledger; HMAC also signs eval callbacks | Split both claims |
| Evidence Pack JSON only | PDF + JSON with seal shipped | Evidence Pack **JSON + PDF** |
| Flyway V1–V6 | Migrations through V16 | **V1–V16** (+ postgres V4) |
| RBAC + SSO roadmap | OAuth implemented; prod smoke pending | JWT + API keys; OAuth **prod smoke pending** |

---

## C. Paste-ready replacement (English) — recommended

Replace the entire Devernance project block with:

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

### Shorter 3-bullet variant (if space is tight on the DOCX)

```text
Devernance — Governance Control Plane for EU AI Act  ·  GitHub
Spring Boot 3.3 · Java 17 · Next.js 16 · pgvector HNSW · DJL/ONNX embeddings · Multi-tenant

• Governance: guided EU AI Act risk classification (Minimal / Limited / High / Prohibited),
  open-gap tracking, controls catalog, and PASS / REVIEW / BLOCKED release decisions from
  evidence coverage, eval thresholds, and data-contract status.

• Evidence & evals: Cited-evidence RAG (DJL + ONNX Runtime all-MiniLM-L6-v2; pgvector HNSW
  on Postgres); durable eval worker; HMAC-SHA-256 signed eval-result callbacks; Evidence Pack
  JSON + PDF with deterministic seal.

• Architecture: Flyway V1–V16; hash-chained audit ledger; JWT/API-key auth; Google/Microsoft
  OAuth (implemented; prod smoke pending); assisted obligation determination; certification
  readiness score + gaps; reg monitor; 3 sector packs (insurance, HR, finance) + SPI; Next.js 16
  dashboard; 64 REST endpoints · 190 automated tests · ~31k app LOC (measured 2026-07-20).
```

---

## D. Summary / skills lines (only if they over-index *this* product)

**Leave alone if they correctly describe other products** (e.g. FAISS for Aequitas / Bharat Alpha is fine).

Do **not** state that Devernance uses FAISS or Next.js 14 anywhere in summary or skills when naming this product.

Governance skills line already using “SHA-256 / HMAC-SHA-256 audit chains” is acceptable if framed generally; prefer precision when naming this product: **hash-chained audit ledger** + **HMAC eval-result callbacks**.

---

## E. Optional markdown CV variants

Paths (if still used / published):

- `/Users/souravamseekarmarti/Projects/SouraVamseekarMarti_CV_2026.md`
- `/Users/souravamseekarmarti/Projects/SouraVamseekarMarti_CV_EU_DE_AT.md`
- `/Users/souravamseekarmarti/Projects/SouraVamseekarMarti_CV_EU_NL_IE_UK.md`

As of 2026-07-20 these files **do not contain** a Devernance project block (older project set). If you still publish them, **add** section C above under PROJECTS — do not invent other product metrics.

---

## F. Apply instructions (manual only)

1. Open original DOCX (or **Save As** a Part16-dated copy first).
2. Replace only the Devernance block with section C.
3. Export PDF from that DOCX so DOCX + PDF stay in sync.
4. Optionally copy PDF into portfolio `public/` if the site hosts CV.
5. Do not claim production OAuth verified until `docs/oauth-production-smoke-test.md` is signed off.

---

*Proposed text only. Original `Marti_Soura_Vamseekar_CV.docx` / `.pdf` intentionally not modified by this packet.*
