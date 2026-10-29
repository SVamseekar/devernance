# Changelog

Newest first. One entry per release of the hosted product.

## 2026-10-28 — Sign-off is part of the release gate

- Approvals now count. A rejected approval cycle blocks the release until a later cycle is approved or an admin overrides the rejected stage, and a high-risk system waits in REVIEW while any cycle is open.
- Separation of duties: the engineering lead can raise a control to REVIEW or BLOCKED, but only compliance or an admin can mark it PASS or lift a block. Only compliance or an admin can resolve a data contract breach, and every drift closure needs a note.
- Eval runs hold the release to their own threshold and to per-metric floors for safety refusal (0.90) and bias-slice pass rate (0.80), so a strong average can no longer hide a failing slice.
- Devernance never makes up eval results: runs wait for your harness to report measured metrics through the signed callback, and the Evals page shows the call to make.
- The gate explains a REVIEW: missing Annex IV sections, an unrecorded FRIA, pending approvals, low coverage. Compliance and legal can complete the Annex IV dossier and FRIA record from the new Sign-off tab.
- Vendor documents that describe "system prompts" now count as evidence; only lines that try to instruct the AI are left out of answers.
- Evidence packs (version 1.2) seal the list of indexed documents with their checksums and the recent eval runs with their versions and metrics.

## 2026-10-18 — EU AI Assurance OS is now Devernance

- New name, same product: development and governance, hand in hand. Workspaces, data and release gates are unchanged.
- New API keys start with dvn_. Keys that start with aos_ keep working, so there is nothing to rotate.
- Evidence packs and PDFs carry the new name. Packs you exported earlier still verify against the same public JWKS.

## 2026-10-04 — Self-serve plans and billing

- Free, Team and Business plans with public prices, trials and checkout through Dodo Payments (Merchant of Record).
- Self-serve signup with email verification, API keys for CI, and a read-only live demo workspace.
- Account controls: export your data and close your workspace from Settings.
- The open-source CLI is pinned to evgraph-cli 0.1.3.

## 2026-10-02 — Evidence-driven release gate

- The release gate now decides from cited evidence, evaluation thresholds, data contract checks and approvals.
- The audit chain is written in order, so every approval and decision has one place in the hash chain.
- Evidence packs are signed with RS256 and can be verified against the public JWKS.

## 2026-09-30 — Hardening

- Dashboard actions call the API for real and keep working through short API outages.
- Safer seeding, OAuth account linking, rate limits on sign-in and secret handling.
