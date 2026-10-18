# Part 16 — Portfolio proposed changes (do not auto-apply)

**Repo:** `/Users/souravamseekarmarti/Projects/Portfolio/martisouravamseekar-portfolio`  
**Primary file:** `src/data/projects.ts` (Devernance entry only)  
**Related (check only):** `src/lib/seo.ts`, `src/lib/highlight.tsx`, `src/components/Hero.tsx`  
**Canon:** `docs/METRICS_CANONICAL.md`  
**Live product:** https://euassuranceai.souravamseekar.com  
**GitHub:** https://github.com/SVamseekar/devernance

Working-tree edits to `projects.ts` were **reverted**; apply only when you approve.

---

## A. Current entry (before)

```ts
{
  name: "Devernance",
  tagline:
    "Governance control plane for EU AI Act compliance — risk classification, cited-evidence RAG, eval gates, and audit-ready evidence packs.",
  regulation: "EU AI Act · technical documentation & conformity assessment",
  metrics: [
    "AI system registry with automated EU AI Act risk classification",
    "Cited-evidence RAG: DJL + ONNX Runtime embeddings, pgvector HNSW, PASS / REVIEW / BLOCKED eval gates",
    "HMAC-SHA-256 signed audit event stream and Evidence Pack JSON export",
    "Spring Boot 3.3 backend: Flyway V1–V6, multi-tenant JPA isolation, eval worker queue",
    "Next.js 16 dashboard with interactive DAG lineage graph (@xyflow/react)",
  ],
  stack: [
    "Java 17",
    "Spring Boot 3.3",
    "PostgreSQL",
    "pgvector",
    "Next.js 16",
    "DJL",
    "ONNX Runtime",
    "HuggingFace tokenizers",
    "AWS S3",
    "Apache Tika",
    "@xyflow/react",
  ],
  period: "2024 – Present",
  liveUrl: "https://euassuranceai.souravamseekar.com",
  githubUrl: "https://github.com/SVamseekar/devernance",
  priority: "primary",
},
```

### Problems

| Field | Issue |
|-------|--------|
| `tagline` | “compliance” OK-ish; missing assisted/readiness framing |
| `regulation` | “conformity assessment” over-reads like official assessment |
| metrics | “automated” risk class; HMAC as audit stream only; V1–V6; no PDF / OAuth / assisted / readiness / sector packs / scale |
| `period` | “2024 – Present” without product-year proof → prefer **2026 – Present** |
| stack | OK (no FAISS) — keep |

**Do not remove FAISS from other projects** (Aequitas, Bharat Alpha) — those are different products.

---

## B. Proposed entry (after)

```ts
{
  name: "Devernance",
  tagline:
    "Multi-tenant governance control plane for EU AI Act release governance — guided risk classification, cited-evidence RAG, eval and contract gates, assisted obligation maps, certification readiness, and audit-ready evidence packs.",
  regulation: "EU AI Act · release governance (not legal certification / notified-body)",
  metrics: [
    "AI system registry with guided EU AI Act risk classification (Minimal / Limited / High / Prohibited)",
    "Cited-evidence RAG: DJL + ONNX Runtime (all-MiniLM-L6-v2), pgvector HNSW; PASS / REVIEW / BLOCKED release gates",
    "HMAC-SHA-256 signed eval-result callbacks; hash-chained append-only audit ledger; Evidence Pack JSON + PDF",
    "Assisted obligation determination; certification readiness score + gaps; reg monitor; 3 sector packs (insurance, HR, finance)",
    "Spring Boot 3.3 backend: Flyway V1–V16, multi-tenant JPA, JWT + API keys, Google/Microsoft OAuth (prod smoke pending), 64 endpoints · 190 tests",
    "Next.js 16 dashboard with interactive DAG lineage (@xyflow/react), contracts, approvals, readiness, reg monitor",
  ],
  stack: [
    "Java 17",
    "Spring Boot 3.3",
    "PostgreSQL",
    "pgvector",
    "Next.js 16",
    "DJL",
    "ONNX Runtime",
    "HuggingFace tokenizers",
    "AWS S3",
    "Apache Tika",
    "@xyflow/react",
  ],
  period: "2026 – Present",
  liveUrl: "https://euassuranceai.souravamseekar.com",
  githubUrl: "https://github.com/SVamseekar/devernance",
  priority: "primary",
},
```

---

## C. SEO / copy hygiene (optional)

| File | Current | Action |
|------|---------|--------|
| `src/lib/seo.ts` | Mentions Devernance generically; no FAISS / Next 14 for this product | OK as-is; optional keyword “EU AI Act release governance” |
| `src/lib/highlight.tsx` | Includes FAISS + Next.js 16 | FAISS highlight is fine for other projects; no change required |
| `src/components/Hero.tsx` | “EU AI Act governance tooling” | OK; no false stack claims |

---

## D. Manual apply steps

```bash
cd /Users/souravamseekarmarti/Projects/Portfolio/martisouravamseekar-portfolio
# Edit src/data/projects.ts Devernance entry using section B
npm run build   # or project’s usual check
git add src/data/projects.ts
git commit -m "docs(portfolio): align Devernance entry with metrics freeze"
# Deploy on Vercel as usual; verify live card text
```

Do **not** commit unrelated untracked research markdown or screenshot junk in that repo.

---

*Proposed portfolio text only. No product-app code changes. Portfolio working tree left clean of this edit.*
