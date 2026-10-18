# Part 16 — Channel changes overview (documentation only)

**Date:** 2026-07-20  
**Canon:** `docs/METRICS_CANONICAL.md` (measured tip `c0d5cd4`, freeze 2026-07-20)  
**Product tip context:** `origin/main` @ `85ac589` (Part 11 complete)  
**Rule:** This packet **documents** proposed channel updates. It does **not** edit live CV binaries. Apply manually only when approved.

## Asset status

| Asset | Path | Apply status |
|-------|------|----------------|
| CV DOCX | `/Users/souravamseekarmarti/Documents/Marti_Soura_Vamseekar_CV.docx` | **Unchanged** — original preserved |
| CV PDF | `/Users/souravamseekarmarti/Documents/Marti_Soura_Vamseekar_CV.pdf` | **Unchanged** (restored to original content) |
| Pitch PDF | `/Users/souravamseekarmarti/Downloads/MSV_AI_Labs_Pitch_Deck.pdf` | **Not modified** — proposed text in MD only |
| Portfolio `projects.ts` | `…/martisouravamseekar-portfolio/src/data/projects.ts` | **Not applied** — proposed entry in MD only |
| Markdown CV variants | `Projects/SouraVamseekarMarti_CV_*.md` | No Devernance block today; optional paste in MD pack |

## Companion files (this folder)

| File | Contents |
|------|----------|
| `2026-07-20-part-16-cv-proposed-changes.md` | CV before → after, paste-ready block, forbidden claim list |
| `2026-07-20-part-16-portfolio-proposed-changes.md` | Full proposed Devernance entry + before/after metrics |
| `2026-07-20-part-16-pitch-proposed-changes.md` | Pitch slide before → after, optional metric bullets |
| `2026-07-20-part-16-cross-channel-checklist.md` | Checklist E + residual risks |

## Frozen numbers (do not invent)

| Claim | Value |
|-------|-------|
| Spring Boot | 3.3.7 (say **3.3**) |
| Java | 17 |
| Next.js | **16** (16.2.9) |
| Flyway | **V1–V16** (+ postgres V4) |
| REST endpoints | **64** |
| Automated tests | **190** |
| Combined app LOC | **~31k** |
| Sector packs | **3** (insurance, HR, finance) |
| Risk classes | Minimal / Limited / High / Prohibited |
| Release decisions | PASS / REVIEW / BLOCKED |
| Public product year | **2026** |
| OAuth | Implemented (Google + Microsoft); **prod smoke pending** |
| Live URL | https://euassuranceai.souravamseekar.com |
| GitHub | https://github.com/SVamseekar/devernance |

## Public one-liner (all channels)

> Devernance is a multi-tenant governance control plane that helps teams ship AI systems into the EU market with guided risk classification, cited-evidence RAG, eval and contract gates, assisted obligation maps, certification readiness scoring, and audit-ready evidence packs — without claiming legal certification or notified-body status.

## Forbidden claims (all channels)

| Forbidden | Correct |
|-----------|---------|
| FAISS (for this product) | DJL + ONNX Runtime / all-MiniLM-L6-v2 |
| HMAC as audit-stream-only | **Hash-chained** audit ledger **and** HMAC-SHA-256 **eval-result callbacks** |
| Next.js 14 | Next.js **16** |
| Flyway V1–V6 only | Flyway through **V16** |
| “You are certified” | Certification **readiness** score + gaps |
| Unqualified “legal determination” | **Assisted** obligation determination |
| Automated risk classification (ML) | **Guided / recorded** risk classification |
| Unacceptable / High / Limited / Minimal | Minimal / Limited / High / **Prohibited** |
| Live since 2024 | Live / public product **2026** |
| Production SSO verified | OAuth **implemented, prod smoke pending** |
| Customer counts / ARR | Do not invent |

## Sources

- `docs/METRICS_CANONICAL.md`
- `docs/investigations/2026-07-19-channel-update-instructions.md`
- `docs/superpowers/plans/2026-07-20-part-16-channel-binaries-cv-pitch-portfolio.md`
- `docs/investigations/2026-07-20-roadmap-completion-signoff.md`

*Documentation-only Part 16 packet. Original CV binaries intentionally not modified.*
