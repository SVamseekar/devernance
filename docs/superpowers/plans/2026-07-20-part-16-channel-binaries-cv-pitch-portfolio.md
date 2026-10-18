# Part 16 — Channel Binaries & Portfolio (CV, Pitch, Portfolio Site)

> **For agentic workers:** This part **is in program**. Prefer implementing Portfolio in code; for DOCX/PDF/PPTX use dedicated doc skills or export pipelines. Still require **metrics freeze (Part 2 final)** before public claims.

**Goal:** Align **all** go-to-market surfaces with `docs/METRICS_CANONICAL.md`—including binary assets formerly deferred to “other sessions only.”

**Depends on:** Part 2 final freeze (after Parts 5–15 numbers stabilize enough for public claims). Interim draft updates allowed if labeled DRAFT.

---

## Assets in scope

| Asset | Path |
|---|---|
| CV DOCX | `/Users/souravamseekarmarti/Documents/Marti_Soura_Vamseekar_CV.docx` |
| CV PDF | `/Users/souravamseekarmarti/Documents/Marti_Soura_Vamseekar_CV.pdf` (export from DOCX) |
| Pitch deck | `/Users/souravamseekarmarti/Downloads/MSV_AI_Labs_Pitch_Deck.pdf` (source PPTX if found; else edit PDF carefully or recreate slide) |
| Portfolio site | `/Users/souravamseekarmarti/Projects/Portfolio/martisouravamseekar-portfolio` (`src/data/projects.ts`, SEO, etc.) |
| Landing | This repo Part 3 (already) |

Also update EU/DE markdown CV variants under `Projects/SouraVamseekarMarti_CV_*.md` if still published.

---

### Task 16.1: Metrics freeze gate

- [ ] **Step 1:** Confirm `docs/METRICS_CANONICAL.md` exists with date + “do not claim” list.
- [ ] **Step 2:** Diff against investigation claim matrix; zero FAISS / false HMAC-audit / Next 14 / V1–V6-only / live-since-2024 without proof.
- [ ] **Step 3:** Include new product lines only if shipped: assisted determination, readiness, reg monitor, sector packs, OAuth, hash-chain audit.

### Task 16.2: Portfolio website (code)

- [ ] **Step 1:** Update `projects.ts` Devernance entry from freeze.
- [ ] **Step 2:** SEO keywords/description if they mention wrong stack.
- [ ] **Step 3:** Deploy portfolio (Vercel) and verify live.
- [ ] **Step 4:** Commit in Portfolio repo with conventional message.

### Task 16.3: CV DOCX (+ PDF)

- [ ] **Step 1:** Open DOCX; replace Devernance bullets with freeze text (see investigation channel instructions; refresh from Part 2).
- [ ] **Step 2:** Align WorkforceGuard/Aequitas/Masova only if those canons exist—do not invent.
- [ ] **Step 3:** Export PDF; keep DOCX + PDF in sync.
- [ ] **Step 4:** Optionally copy PDF into Portfolio public assets if site hosts CV.

**Tooling:** `docx` skill / Word; avoid broken zip edits by hand.

### Task 16.4: Pitch deck

- [ ] **Step 1:** Locate editable source (PPTX). If only PDF: recreate Devernance slide in PPTX then export, or use `pptx` skill.
- [ ] **Step 2:** Fix Devernance metrics slide (HMAC claim, live-since, stack).
- [ ] **Step 3:** Keep NVIDIA Inception framing if still submitting; metrics must stay honest.
- [ ] **Step 4:** Save dated copy: `MSV_AI_Labs_Pitch_Deck_YYYY-MM-DD.pdf`.

### Task 16.5: Cross-channel checklist

- [ ] Same product one-liner
- [ ] Same risk class names
- [ ] Same release decisions
- [ ] Same embedding stack
- [ ] Same auth story
- [ ] Same Flyway range / phase status
- [ ] Same live URL + GitHub URL
- [ ] New features: assisted determination / readiness / reg feed / sector packs only if live

### Task 16.6: Signoff

- [ ] **Step 1:** Attach screenshots/PDF hashes in `docs/investigations/YYYY-MM-DD-channel-assets-signoff.md`.
- [ ] **Step 2:** Note which assets updated and by whom/session.

### Done when

- [ ] Portfolio live matches canon
- [ ] CV DOCX+PDF match canon
- [ ] Pitch deck match canon
- [ ] Signoff doc filed
