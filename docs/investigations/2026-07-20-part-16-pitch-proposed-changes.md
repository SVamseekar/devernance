# Part 16 — Pitch deck proposed changes (do not auto-apply)

**Current asset:** `/Users/souravamseekarmarti/Downloads/MSV_AI_Labs_Pitch_Deck.pdf`
**Also seen:** `Projects/MaSoVa-restaurant-management-system/docs/MSV_AI_Labs_Pitch_Deck.pdf`
**Editable source:** PPTX not found in common paths (PDF produced via PptxGenJS / LibreOffice).
**Suggested dated output (when you apply):**
`/Users/souravamseekarmarti/Downloads/MSV_AI_Labs_Pitch_Deck_2026-07-20.pdf`

**Canon:** `docs/METRICS_CANONICAL.md`
**Scope:** Fix **Devernance** slide (and any deck-wide line that falsely attributes FAISS / Next 14 / live-since-2024 to *this* product). Keep NVIDIA Inception framing; keep other products’ own metrics honest.

---

## A. Current Devernance slide (extracted 2026-07-20)

**Title / badge**

- Product: Devernance
- Subtitle: The compliance control plane for the EU AI Act
- Badge: **Live since 2024** ← wrong for public product year freeze

**Body**

> Governance control plane for EU AI Act compliance: AI system registry, **automated risk classification**, cited-evidence RAG for regulatory Q&A, evaluation gates, data-contract drift monitoring, and audit-ready evidence packs. Built for enterprises and regulated industries facing EU AI Act enforcement beginning August 2026.

**Key metrics (right rail)**

| Label | Claim |
|-------|--------|
| Aug 2026 | EU AI Act enforcement date — timing is the market (keep) |
| HMAC-256 | **Tamper-evident audit event streams** ← incomplete / wrong sole audit framing |
| RAG | Cited-evidence compliance Q&A (OK with stack fix) |

**Tech stack line**

> Spring Boot 3.3 backend · Next.js 16 dashboard · pgvector HNSW · ONNX Runtime + DJL embeddings · **HMAC-SHA-256 signed audit streams** · GCP Cloud Run

---

## B. Before → after (Devernance only)

| Current / wrong | Corrected |
|-----------------|-----------|
| Live since 2024 | **Live 2026** / **Public product 2026** |
| automated risk classification | **Guided** risk classification + release gates |
| HMAC-256 tamper-evident audit event streams (only) | **Hash-chained audit ledger** + **HMAC-SHA-256 eval-result callbacks** |
| HMAC-SHA-256 signed audit streams (stack line) | Hash-chained audit · HMAC eval callbacks · Flyway V1–V16 |
| Thin metrics only | Add **64** endpoints · **190** tests · 3 sector packs · Evidence Pack PDF+JSON |
| Legal certification tone | Certification **readiness**; **assisted** obligations |
| FAISS for this product | **Do not** list FAISS on this product (DJL/ONNX OK) |

**Shared infra / “Why NVIDIA” slides:** FAISS may remain as multi-product portfolio claim (Aequitas / Bharat Alpha use FAISS). Do not imply Devernance uses FAISS.

---

## C. Proposed Devernance slide copy

### Title block

```text
Devernance
The governance control plane for EU AI Act release governance
Public product 2026
Live: euassuranceai.souravamseekar.com
```

### Body

```text
Multi-tenant governance control plane for EU AI Act release governance:
AI system registry with guided risk classification (Minimal / Limited / High /
Prohibited), cited-evidence RAG, PASS / REVIEW / BLOCKED release gates, data-contract
drift monitoring, assisted obligation maps, certification readiness scoring, and
audit-ready Evidence Pack JSON + PDF. Built for teams facing EU AI Act enforcement
from August 2026 — not a notified body and not legal certification.
```

### Key metrics rail

```text
Aug 2026
EU AI Act enforcement timing — market context

64 · 190
REST endpoints · automated tests (code-measured 2026-07-20)

Hash-chain + HMAC
Hash-chained audit ledger · HMAC-SHA-256 eval-result callbacks

Readiness
Certification readiness score + gaps · assisted obligations · 3 sector packs
```

### Tech stack line

```text
Spring Boot 3.3 · Java 17 · Next.js 16 · pgvector HNSW · DJL/ONNX (all-MiniLM-L6-v2)
Flyway V1–V16 · JWT + API keys · Google/Microsoft OAuth (prod smoke pending)
Hash-chained audit · HMAC eval callbacks · Evidence Pack JSON+PDF
```

### Optional one-liner bullets for denser layout

```text
• 64 REST endpoints · 190 automated tests · multi-tenant Spring Boot 3.3 + Next.js 16
• PASS / REVIEW / BLOCKED gates · hash-chained audit · HMAC eval callbacks
• Assisted obligation maps · certification readiness score · 3 sector packs
• Live: euassuranceai.souravamseekar.com
```

---

## D. Do not invent on any slide

- Revenue, ARR, customer counts, “X enterprises in production”
- Notified-body / “you are certified”
- Production OAuth/SSO verified (until smoke signoff)
- Official EU AI Act conformity assessment

---

## E. Manual apply steps

1. Prefer editing source PPTX if you locate it; else rebuild Devernance slide in PptxGenJS / PowerPoint and re-export full deck.
2. Save dated copy: `MSV_AI_Labs_Pitch_Deck_2026-07-20.pdf` (keep old file).
3. Optionally refresh MaSoVa docs copy only after review.
4. Spot-check all 9 pages for leftover “Live since 2024” **on the Devernance slide only**.

---

*Proposed pitch text only. Original pitch PDF intentionally not modified by this packet.*
