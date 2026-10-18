# Part 16 — Cross-channel checklist (proposed alignment)

**Date:** 2026-07-20
**Canon:** `docs/METRICS_CANONICAL.md`
**Use when:** applying CV / pitch / portfolio updates from companion MD files.

Legend: `[ ]` = not applied yet · `[x]` = verified after you apply

---

## Consistency (all four channels: CV, pitch, portfolio, landing)

| # | Check | CV | Pitch | Portfolio | Landing (code) |
|---|--------|----|-------|-----------|----------------|
| 1 | Same product one-liner (control plane / EU AI Act release governance) | [ ] | [ ] | [ ] | [x] Part 3 |
| 2 | Risk classes: Minimal / Limited / High / Prohibited | [ ] | [ ] | [ ] | [x] |
| 3 | Release decisions: PASS / REVIEW / BLOCKED | [ ] | [ ] | [ ] | [x] |
| 4 | Embeddings: DJL/ONNX · **not FAISS** for this product | [ ] | [ ] | [ ] | [x] |
| 5 | Audit: hash-chained ledger **+** HMAC eval callbacks | [ ] | [ ] | [ ] | [x] |
| 6 | Auth: JWT + API keys; OAuth **prod smoke pending** | [ ] | [ ] | [ ] | [x] wording |
| 7 | Flyway through **V16** | [ ] | [ ] | [ ] | [x] |
| 8 | Next.js major **16** | [ ] | [ ] | [ ] | [x] |
| 9 | Live URL `https://euassuranceai.souravamseekar.com` | [ ] | [ ] | [ ] | [x] |
| 10 | GitHub `https://github.com/SVamseekar/devernance` | [ ] | [ ] | [ ] | [x] |
| 11 | Assisted obligations / readiness — never “certified” | [ ] | [ ] | [ ] | [x] |
| 12 | 3 sector packs only (no fake live Workday/Guidewire) | [ ] | [ ] | [ ] | [x] |
| 13 | Public product year **2026** (not live-since-2024) | [ ] | [ ] | [ ] | [x] |
| 14 | Scale: 64 endpoints · 190 tests (if cited) | [ ] | [ ] | [ ] | strip qualitative OK |

---

## Per-asset apply status (session 2026-07-20)

| Asset | Original file modified? | MD proposal filed? |
|-------|-------------------------|--------------------|
| CV DOCX/PDF | **No** (restored / left original) | Yes — `…-cv-proposed-changes.md` |
| Pitch PDF | **No** | Yes — `…-pitch-proposed-changes.md` |
| Portfolio `projects.ts` | **No** (any trial edit reverted) | Yes — `…-portfolio-proposed-changes.md` |
| Markdown CVs under Projects/ | **No** | Optional paste in CV MD |
| Product application code | **No** | N/A |

---

## Residual (program)

1. **OAuth production smoke** not signed off — public wording must stay “implemented; prod smoke pending.”
2. **NFR p95 / 99.9%** are targets, not production-certified SLOs.
3. **Sector connectors** are stubs/SPI only.
4. **Reg monitor** assistive / polled; not live proprietary legal scrapers.
5. Channel binaries (CV / pitch / portfolio) remain **owner-applied** after reviewing these MD files.

---

## Recommended apply order

1. Portfolio `projects.ts` (code, easy review / PR)
2. Pitch dated PDF (keep old file)
3. CV via **Save As** dated copy first, then optional replace of live DOCX
4. Re-check this checklist and file a short signoff when live surfaces match

---

*Checklist only. No original channel binaries modified by documentation packet.*
