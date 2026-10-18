# Changelog

Newest first. One entry per release of the hosted product.

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
