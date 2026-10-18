# Part 2 — Metrics Canon & Cross-Channel Alignment Packet

> **For agentic workers:** Measure from code after Part 0. **Do not edit** CV/PDF/pitch in this repo — produce instruction packet only.

**Goal:** One source of truth for numbers/wording used by CV, Portfolio, Pitch, Landing.

**Depends on:** Part 0 complete (V10 on main). **Final freeze** only after Parts 5–10 land (controls, audit chain, PDF, OAuth, etc. change claimable metrics). Early draft freeze OK for internal use; public channels wait for final freeze.

**Pattern:** WorkforceGuard `docs/METRICS_CANONICAL.md`.

---

### Task 2.1: Create `docs/METRICS_CANONICAL.md`

- [ ] **Step 1:** Re-run measurement commands:

```bash
# endpoints
grep -rn "@\(Get\|Post\|Put\|Patch\|Delete\)Mapping" services/api/src/main --include='*.java' | wc -l
# tests
grep -rn "@Test\|@ParameterizedTest" services/api/src/test --include='*.java' | wc -l
# LOC
find services/api/src/main -name '*.java' | xargs wc -l | tail -1
find services/api/src/test -name '*.java' | xargs wc -l | tail -1
find apps/dashboard \( -path '*/node_modules/*' -o -path '*/.next/*' \) -prune -o \( -name '*.ts' -o -name '*.tsx' \) -print | xargs wc -l | tail -1
# versions
grep spring-boot-starter-parent -A2 services/api/pom.xml
grep '"next"' apps/dashboard/package.json
ls services/api/src/main/resources/db/migration/
```

- [ ] **Step 2:** Write canon with date stamp and “do not claim” section (see investigation §3.4).
- [ ] **Step 3:** Add `apps/dashboard/lib/metrics-canon.ts` exporting public-facing strip strings (subset of canon for UI).

### Task 2.2: Forbidden / corrected phrases

| Forbidden | Replacement |
|---|---|
| FAISS (for this product) | DJL + ONNX Runtime / all-MiniLM-L6-v2 |
| HMAC-SHA-256 signed audit event stream | Append-only audit ledger; HMAC-SHA-256 signed eval callbacks |
| Next.js 14 | Next.js 16 |
| Flyway V1–V6 | Flyway V1–V9 (+ V10 if merged) |
| Automated risk classification (ML sense) | Guided / recorded risk classification |
| Unacceptable / High / Limited / Minimal | Minimal / Limited / High / Prohibited |
| Live since 2024 (unless proven) | Live 2026 / Public product 2026 |
| SSO implemented | JWT + API keys; SSO roadmap / OAuth after Part 4 |

### Task 2.3: Instruction packet for other sessions

Produce a short `docs/investigations/2026-07-19-channel-update-instructions.md` (or section in canon) with **paste-ready** bullets for:

1. **CV DOCX/PDF** — path `Documents/Marti_Soura_Vamseekar_CV.docx`
2. **Portfolio** — `Portfolio/martisouravamseekar-portfolio/src/data/projects.ts` EU AI entry
3. **Pitch deck** — Devernance slide only; keep NVIDIA framing

Include before/after for each bullet.

### Task 2.4: Landing consumption

- [ ] **Step 1:** Part 3 will import `metrics-canon.ts` into a metrics strip component.
- [ ] **Step 2:** Do not invent marketing numbers (users, ARR, “X enterprises”) unless true.

### Done when

- [x] Canon file committed with measured date (`docs/METRICS_CANONICAL.md`, 2026-07-20 @ c0d5cd4)
- [x] Instruction packet ready for Claude Chat / other agents (`docs/investigations/2026-07-19-channel-update-instructions.md`)
- [x] Landing strip wired to `apps/dashboard/lib/metrics-canon.ts`
- [x] Forbidden phrases documented (FAISS, HMAC audit-stream-only, Next 14, V1–V6-only, “certified”, unqualified legal determination)
- [x] PR merged; INDEX Part 2 → Complete (channel **binaries** remain Part 16)
