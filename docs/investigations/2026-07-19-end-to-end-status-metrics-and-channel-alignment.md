# Investigation Report — Devernance

**Date:** 2026-07-19 (UTC)  
**Investigator:** Grok Code session (repo `eu-ai-assurance-os`)  
**Scope:** GitHub hygiene, local WIP, codebase + live site deep review, metrics consistency across CV / Portfolio / Pitch Deck / Landing, sister-product patterns (demo form, legal footer, OAuth), SEO, and delivery plans.

**Non-goals of this session:** Editing CV / DOCX / PDF / pitch deck / Portfolio content (instructions only). Full product implementation is deferred to the phased plans below — **WIP must be finished first** (see §1).

---

## 0. Executive stop-gate (user point 7)

### What is in progress (WIP) right now

Local working tree is **not clean**. Phase 5 “workflow completion” is implemented on disk but **not committed and not on `origin/main`**.

| Area | Status on `origin/main` | Status locally (2026-07-19) |
|---|---|---|
| Phase 0–4 product core | Shipped | Shipped |
| Phase 5 base (workflows V8, controller, service, tests) | Shipped | Shipped |
| Phase 5 completion (reviewer assignment, oversight evidence field, persisted notifications, approvals UI wiring, V10) | **Missing** | **Present as uncommitted + untracked files** |
| Phase 6 auth (JWT login, refresh, API keys, JWKS, dashboard session cookies, login page) | Shipped (partial) | Shipped (partial) |
| Phase 6 OAuth / OIDC / SSO | Not started | Not started |
| Landing legal pages + demo form | Not started | Not started |
| Industry-grade GitHub (CI, LICENSE, branch protection, CONTRIBUTING) | Not started | Not started |

### Uncommitted / untracked inventory (Phase 5 completion)

**Modified (21 files):** approvals dashboard, workflow service/controller/entities, mock data, types, API client, BootstrapData, API.md, ROADMAP.md (marks Phase 5 complete), README, `.gitignore`, favicon.

**Untracked:**
- `V10__phase5_workflow_completion.sql` — `assigned_reviewer_id`, `oversight_evidence`, `notification_sent_at`, `workflow_notifications` table
- `WorkflowNotification*.java` (domain + JPA + repository)
- `apps/dashboard/app/icon.svg`
- `docs/superpowers/plans/2026-06-15-marketing-landing-page.md`

### Stop rule (confirmed)

1. **Finish Phase 5 WIP end-to-end first** (tests green, migration applied, approvals UI verified against API, commit on a feature branch, PR, deploy).
2. **Only then** freeze a metrics canon and propagate to CV / Portfolio / Landing / Pitch.
3. Parallel safe work after WIP lands: GitHub hygiene, legal/footer/demo form, SEO hardening, OAuth plan.

Do **not** publish metrics that claim “Phase 5 complete / notifications / V10” until that commit is on the default branch and deployable.

---

## 1. GitHub / Git setup — current vs industry-grade

**Repo:** https://github.com/SVamseekar/devernance  
**Default branch:** `main`  
**Visibility:** public  
**Homepage URL:** empty (should be `https://euassuranceai.souravamseekar.com`)  
**Topics:** good (ai-safety, eu-ai-act, spring-boot, nextjs, …)  
**License:** **none**  
**Stars / forks / open issues:** 1 / 0 / 0  
**Created:** 2026-06-05 · **Last push on remote:** 2026-06-26  
**Commits on main:** 92, all dated 2026-06  
**Branch protection:** **not configured**  
**CI:** **no `.github/` directory at all**  
**PR / issue templates:** none  
**CODEOWNERS / Dependabot / security policy:** none  

### Compared to sister product (WorkforceGuard AI)

WorkforceGuard already has industry baseline:

- `.github/workflows/ci.yml` + deploy
- `CONTRIBUTING.md`, `CODEOWNERS`, PR template, Dependabot
- Branch protection expectations documented
- Pre-commit / secret scan culture

### Gaps to close for industry-grade Git (this repo)

| Control | Status | Target |
|---|---|---|
| Branch protection on `main` | Missing | Require PR, 1 review optional (solo), status checks, no force-push |
| CI (Java tests + dashboard typecheck/build) | Missing | GitHub Actions matrix |
| LICENSE | Missing | MIT or Apache-2.0 (decide) |
| CONTRIBUTING.md | Missing | Branch naming, PR rules, local test commands |
| SECURITY.md | Present (threat model) | Add `SECURITY.md` GitHub security policy path or link |
| Code of Conduct | Missing | Optional but common for public OSS |
| Dependabot | Missing | npm + Maven |
| README accuracy | **Stale** | Still describes static prototype as primary; understates Next.js dashboard + auth |
| Secrets hygiene | `.env` gitignored | Add gitleaks in CI |
| Dirty local main | Dirty | Never commit WIP straight to main; use `feature/*` PR |
| Homepage + About | Empty homepage | Set homepage URL + short About |
| Releases / tags | None | Tag after Phase 5 land (`v0.5.0` or similar) |

### “Git mess” diagnosis (plain language)

The product code quality is ahead of the **repository operations**. History is linear and recent, but there is no CI gate, no protection, no license, outdated README, and a large uncommitted Phase 5 completion sitting on a dirty `main`. That is the opposite of how sister products are run.

---

## 2. Product deep-dive (code + live site)

### 2.1 Architecture (verified)

| Layer | Stack (verified) |
|---|---|
| Dashboard | Next.js **16.2.9**, React 19, Tailwind v4, shadcn/ui (Base UI), TanStack Query, `@xyflow/react` |
| Landing | Same Next app at `/` — marketing sections + SEO metadata + GA4 |
| API | Spring Boot **3.3.7**, Java 17, Flyway, Spring Security present but **permit-all**; real auth is `TenantContextFilter` |
| Auth today | Email/password → JWT access + refresh; httpOnly cookies via Next route handlers; API keys (`X-Api-Key`); JWKS |
| Evidence | Chunk + embed; providers: `local-hash` (default H2) and `djl-sentence` (all-MiniLM-L6-v2 ONNX via DJL) |
| Storage | Optional S3 via `FileStorageService` + Apache Tika extraction deps |
| Eval | Durable queue, skip-locked claim, HMAC-signed **callback** endpoint |
| Workflows | Approval stages + override; V10 completion WIP for notifications |
| Deploy | Dashboard on Vercel (`eu-ai-assurance-os` project); live site responds 200 |

### 2.2 Verified surface area (as of 2026-07-19 local tree including WIP)

| Metric | Count / value | Notes |
|---|---|---|
| Java production source files | 137 | under `services/api/src/main` |
| Java test source files | 15 | |
| Java production LOC | ~6,679 | |
| Java test LOC | ~2,712 | |
| Dashboard TS/TSX LOC | ~6,232 (excl. node_modules) | ~74 TS/TSX files |
| Legacy static prototype LOC | ~1,700 | `apps/web` |
| Approx. combined app LOC (Java main+test + dashboard TS) | **~15.6k** | Do not invent larger numbers |
| REST `@*Mapping` endpoints | **40** | Controllers listed below |
| `@Test` methods | **102** | |
| Flyway on remote main | V1–V3, V5–V9 (+ postgres V4) | **No V10 on remote** |
| Flyway local WIP | V10 | Uncommitted |
| Dashboard routes | Landing `/`, login, command, systems, approvals, evidence, evals, contracts, audit | |
| Live domain | `https://euassuranceai.souravamseekar.com` | SEO robots/sitemap OK for landing |

**Controllers / domains:** `AiSystem`, `Evidence`, `EvalDataset`, `EvalRun`, `DataContract`, `Audit`, `ApprovalWorkflow`, `Auth`, `Jwks`.

### 2.3 Release gate (truth)

`ReleaseGateService` computes **PASS / REVIEW / BLOCKED** from:

1. Risk class **PROHIBITED** → BLOCKED  
2. HIGH risk + oversight gap → BLOCKED  
3. Eval score hard block &lt; 78 → BLOCKED  
4. Data contract **BREACH** → BLOCKED  
5. Else REVIEW if evidence coverage &lt; 82, eval &lt; 85, WARNING contract, or open gaps  
6. Else PASS  

**RiskClass enum (code):** `MINIMAL`, `LIMITED`, `HIGH`, `PROHIBITED` — **not** “Unacceptable”.

Risk classification API **accepts** a class + basis from the caller; it is **not** an automated classifier that infers tier from free text alone. Gaps (e.g. human oversight) can be added when flagged.

### 2.4 Auth (truth) — password JWT yes; OAuth no

| Capability | Implemented? | Evidence |
|---|---|---|
| Email/password login | Yes | `AuthController`, BCrypt, timing-parity dummy hash |
| Access JWT + refresh rotation | Yes | `JwtService`, `RefreshTokenService` |
| JWKS | Yes | `/.well-known/jwks.json` |
| Dashboard httpOnly session cookies | Yes | `lib/session.ts`, `/api/auth/login|logout` |
| Server proxy attaching Bearer | Yes | `/api/proxy/[...path]` |
| API key auth | Yes | `X-Api-Key`, hashed at rest |
| Unauthenticated header impersonation | **Removed** (post security audit) | Filter requires Bearer or API key |
| Spring Security method security / OAuth2 client | **No** | `SecurityConfig` permitAll; filter is the gate |
| Google / Microsoft / generic OIDC | **No** | No OAuth routes; Phase 6 roadmap item |
| Dashboard Next middleware hard-gate | **No** | Pages return 200; client redirects on API 401 |
| Live API without auth | 401 on proxy | Good |

**OAuth finding for user point 15:** OAuth is **not** end-to-end. Sister product WorkforceGuard has Google + Microsoft OAuth with production smoke tests and `OAUTH_REDIRECT_BASE_URL`. Devernance only has first-party credentials. SSO/OIDC remains Phase 6 roadmap.

### 2.5 Live marketing surface (2026-07-19)

| URL | HTTP |
|---|---|
| `/` landing | 200 |
| `/login` | 200 |
| `/command` | 200 (HTML shell; data needs auth / mock) |
| `/privacy` | **404** |
| `/terms` | **404** |
| `/refunds` | **404** |
| `/request-demo` | **404** |
| `/robots.txt` | 200, disallows dashboard routes, correct host |
| `/sitemap.xml` | 200, **only `/`** |

Landing sections present: Header, Hero, Problem, Capabilities (6), How it works (4), Personas (6), Trust badges, FAQ (6), CTA, Footer.

Landing **missing vs sister products / industry:**

- Quantitative metrics strip  
- Request demo CTA + form  
- Legal pages (Privacy, Terms, Refunds)  
- Contact / support email in footer  
- Accessibility statement  
- Methodology / security overview public page (optional)  
- Disclaimer (“not legal certification”) beyond FAQ  
- Portfolio / GitHub / company links in footer  
- Cookie / analytics notice  
- Multi-column footer (Product / Resources / Legal / Contact)

### 2.6 Feature playthrough notes (local + live)

| Feature | Works as designed? | Caveats |
|---|---|---|
| Landing SEO metadata | Yes | Strong baseline (title, OG, Twitter, JSON-LD FAQ/Org/SoftwareApp) |
| GA4 | Wired when `NEXT_PUBLIC_GA_MEASUREMENT_ID` set | Live HTML preloads `G-VPNF36L7PS` |
| Systems registry | Yes via API + mock fallback | Mock used when API unreachable |
| Risk classification form | Yes | Manual tier selection, not ML automation |
| Evidence upload/query | Yes | Default embeddings local-hash; DJL on postgres profile |
| Eval runs | Yes | Deterministic MVP worker metrics |
| Contracts + lineage DAG | Yes | `@xyflow/react` |
| Approvals | Base yes; notifications WIP | V10 not on remote |
| Evidence pack JSON export | Yes | Endpoint + tests |
| Audit log | Append-only table | **No hash chain / HMAC over audit rows** |
| Login | Yes | No OAuth buttons |

---

## 3. Channel metrics inventory & gap analysis (points 8–11)

### 3.1 Sources inspected

| Channel | Path / URL | Format |
|---|---|---|
| CV (primary) | `/Users/souravamseekarmarti/Documents/Marti_Soura_Vamseekar_CV.docx` (+ `.pdf`) | Note: user path was a non-existent *folder*; actual files are `Marti_Soura_Vamseekar_CV.docx/pdf` |
| CV markdown variants | `Projects/SouraVamseekarMarti_CV_2026.md` etc. | May drift from DOCX |
| Portfolio | `/Users/souravamseekarmarti/Projects/Portfolio/martisouravamseekar-portfolio` → `src/data/projects.ts` | Code |
| Pitch deck | `/Users/souravamseekarmarti/Downloads/MSV_AI_Labs_Pitch_Deck.pdf` | NVIDIA Inception deck; reused multi-product |
| Landing | Live + `apps/dashboard/lib/landing-content.ts` | Code |
| Codebase | This repo | Source of truth for claims |

### 3.2 Claim matrix for **Devernance only**

Legend: **OK** accurate · **STALE** once true / partial · **WRONG** false or overstated · **MISSING** should exist · **N/A**

| Claim | CV | Portfolio | Pitch | Landing | Code truth (2026-07-19) |
|---|---|---|---|---|---|
| Governance control plane for EU AI Act | OK | OK | OK | OK | OK |
| AI system registry | OK | OK | OK | OK | OK |
| Risk tiers “Unacceptable / High / Limited / Minimal” | WRONG label | — | — | FAQ says minimal/limited/high | Enum: **MINIMAL, LIMITED, HIGH, PROHIBITED** |
| “Automated” risk classification | WRONG tone | “automated” | “automated” | Softer | API records submitted class; not auto-inferred |
| Cited-evidence RAG | OK | OK | OK | OK | OK |
| **FAISS** embeddings | WRONG | Not claimed | Not for this product | Not claimed | **No FAISS** in this repo |
| **DJL + ONNX** all-MiniLM-L6-v2 | Missing on CV (says FAISS) | OK | OK | Not quantified | Code present; postgres default `djl-sentence` |
| **pgvector HNSW** | OK (with FAISS) | OK | OK | Not quantified | Migration path exists; needs postgres + extension |
| PASS / REVIEW / BLOCKED gates | OK | OK | OK | OK | OK |
| **HMAC-SHA-256 signed audit event stream** | WRONG | OK? (claims signed audit stream) | “HMAC-256 tamper-evident audit streams” | “Append-only audit ledger” (safer) | HMAC is for **eval callbacks**, not audit ledger hash-chain. Audit is append-only rows without per-event HMAC chain. |
| Evidence Pack JSON | OK | OK | OK | OK | OK |
| Flyway **V1–V6** | STALE | STALE | — | — | Remote: through **V9** (+ V4 postgres); local WIP **V10** |
| Next.js **14** | WRONG | **16** OK | **16** OK | — | **16.2.9** |
| Spring Boot 3.3 | Missing explicit | 3.3 OK | 3.3 OK | — | **3.3.7** |
| select-for-update-skip-locked eval worker | OK | — | — | — | OK |
| Multi-tenant JPA + TenantContextFilter | OK | OK | — | Trust badge | OK (JWT/API key derived) |
| Interactive DAG lineage | OK | OK | — | Capability | OK |
| Data-contract drift | OK | OK | OK | OK | OK |
| Approval workflows | Partial | — | — | OK | Base on remote; notifications **WIP** |
| RBAC + SSO roadmap | OK as roadmap | — | — | — | Password auth shipped; **SSO not** |
| AWS S3 / Apache Tika | — | In stack | — | — | Code deps + FileStorageService exist |
| Live since **2024** | — | period “2024 – Present” | “Live since 2024” | — | GitHub repo **created 2026-06-05** — “since 2024” is **questionable** |
| Quantitative product metrics strip | N/A | Partial qualitative | HMAC / RAG labels only | **MISSING** | Can support ~15.6k LOC, 40 endpoints, 102 tests, 7 dashboard routes, 6 capability modules |
| Demo request path | N/A | N/A | Contact only | **MISSING** | Sister products have forms |
| Legal / refunds | N/A | N/A | N/A | **MISSING** | Sister products have pages |

### 3.3 Cross-channel inconsistencies that must be fixed (other sessions for non-code)

1. **Drop FAISS** for this product everywhere (CV especially).  
2. **Replace** “HMAC-SHA-256 signed audit event stream” with precise wording:  
   - “Append-only audit ledger” and/or  
   - “HMAC-SHA-256 signed eval-result callbacks”  
   - Optional future: hash-chained audit (not built).  
3. **Risk tier names** align to code: Minimal / Limited / High / Prohibited (map EU “unacceptable” → Prohibited carefully).  
4. **Next.js 16**, not 14.  
5. **Flyway V1–V9** (and V10 only after merge).  
6. Soften **“automated risk classification”** → “guided / recorded EU AI Act risk classification with oversight gap tracking”.  
7. **“Live since 2024”** → prefer “Live 2026” or “Public beta 2026” unless there is external proof of 2024 production.  
8. Landing needs the **same numbers** as Portfolio once canon freezes — currently landing has almost **zero** numeric metrics.

### 3.4 Proposed metrics canon (freeze only after Phase 5 WIP merges)

Use a single file (planned): `docs/METRICS_CANONICAL.md` — same pattern as WorkforceGuard’s `docs/METRICS_CANONICAL.md`.

**Suggested freeze fields (re-measure on freeze day):**

```text
Product: Devernance
Domain: https://euassuranceai.souravamseekar.com
Repo: https://github.com/SVamseekar/devernance
Backend: Java 17 · Spring Boot 3.3.7
Frontend: Next.js 16.x · React 19 · TypeScript
Embeddings: DJL + ONNX Runtime · sentence-transformers/all-MiniLM-L6-v2 (postgres profile);
            local-hash deterministic provider (H2/dev)
Vector store: pgvector HNSW (postgres path)
Auth: JWT access+refresh, API keys, JWKS  |  OAuth/OIDC: not yet
Release decisions: PASS | REVIEW | BLOCKED
Risk classes: MINIMAL | LIMITED | HIGH | PROHIBITED
Flyway: V1–V9 (+ V4 postgres) [+ V10 when merged]
API endpoints: 40
Automated tests (@Test): 102
Java LOC (main): ~6.7k
Java LOC (test): ~2.7k
Dashboard TS/TSX LOC: ~6.2k
Dashboard product routes: 7 (+ login + landing)
Capability modules: 6 (registry, risk, evidence RAG, eval gates, contracts, approvals/audit)
Eval callback integrity: HMAC-SHA-256 (v1=hex)
Audit: append-only event log (not hash-chained)
Evidence export: Evidence Pack JSON
Lineage UI: @xyflow/react DAG
Phases complete (after WIP): 0–5; Phase 6 partial (password auth only)
```

**Do not claim until true:** OAuth, hash-chained audit, FAISS, legal certification, “automated” risk ML, V10, enterprise SSO.

---

## 4. Sister products — demo form, footer, refunds (points 12–14)

### 4.1 WorkforceGuard AI (`workforceguardai.souravamseekar.com`)

**Local:** `/Users/souravamseekarmarti/Projects/WorkforceGuard-AI`

| Piece | Implementation |
|---|---|
| Demo API | `dashboard/frontend/api/request-demo.js` (Vercel serverless) |
| Client | `src/lib/demo-request.ts` + landing form options |
| Delivery | Discord webhook `DISCORD_WEBHOOK_URL` |
| Anti-abuse | Honeypot `website`, min fill time 3s, IP rate limit 5 / 15 min |
| Required fields | first/last name, work email, job title, company, size, industry, country, reporting obligation, ESG team size, timeline, referral, primary interests[], privacy consent |
| Legal pages | `privacy.html`, `terms.html`, `refunds.html` (live 200) |
| Refunds tone | **Enterprise / order-form led**: sold after demo & scoping; cancellations/refunds per order form or SOW; contact email for billing |
| OAuth | Google + Microsoft; production smoke doc; auto-provision flag |

### 4.2 Aequitas (`aequitas.souravamseekar.com`)

**Local:** `/Users/souravamseekarmarti/Projects/aequitas`

| Piece | Implementation |
|---|---|
| Contact API | `frontend/api/contact.js` |
| Delivery | `DISCORD_CONTACT_WEBHOOK_URL` |
| Anti-abuse | Honeypot + 2s min time + rate limit |
| Fields | name, email, message, optional organisation |
| Legal | privacy, terms, refunds, disclaimer, accessibility, methodology, about |
| Refunds tone | **Subscription**: free tier + paid; cancel anytime; no pro-rata refunds except where law requires |
| Footer plan | Product / Resources / Legal / Contact (see `docs/superpowers/plans/2026-07-19-part-d-landing-seo-footer.md`) |

### 4.3 Industry pattern for **this** product (B2B governance / EU AI Act)

Comparable positioning: Vanta / Drata / OneTrust / Credo AI style — **enterprise demo sales**, not consumer monthly SaaS.

**Recommended refunds stance for Devernance** (align to WorkforceGuard, not Aequitas):

> Sold to organisations after a tailored demo and scoping conversation. Cancellation and refund terms are set out in the organisation’s order form or statement of work. Contact `euassuranceai@souravamseekar.com` (or chosen alias) for billing questions. Not a consumer app; no self-serve instant refunds page promising 30-day money-back unless you productise self-serve billing later.

Also include a **regulatory disclaimer**: the product organises evidence and controls; it does **not** provide legal certification or a final determination of EU AI Act obligations (already in FAQ — promote to footer + legal).

### 4.4 Demo form recommendation for this product

Mirror WorkforceGuard structure with **EU AI Act–specific** fields, e.g.:

- Identity: first, last, work email, job title  
- Org: company, website, size, industry, country, HQ city  
- Context: number of AI systems in production / planned, risk exposure (high-risk yes/no), current tooling (spreadsheets / GRC / none), timeline, primary interests (registry, evidence RAG, eval gates, contracts, approvals), referral  
- Consent: privacy required; marketing optional  
- Honeypot + timing + rate limit  
- Notify via Discord webhook (same ops pattern)

CTA hierarchy:

1. Primary: **Request a demo**  
2. Secondary: **Open dashboard** / Sign in (for evaluators with accounts)

---

## 5. Landing footer & SEO (points 13, 16)

### 5.1 Footer target columns

| Product | Resources | Legal | Contact |
|---|---|---|---|
| Capabilities | FAQ | Privacy | Email |
| How it works | Security overview (short) | Terms | Request demo |
| Who it’s for | GitHub | Refunds | Portfolio (souravamseekar.com) |
| Open dashboard / Sign in | Docs (when public) | Disclaimer | LinkedIn (optional) |

Copyright: `© {year} Devernance · Marti Soura Vamseekar` (or MSV AI Labs if brand requires).

### 5.2 SEO current score

**Strong already:**
- `metadataBase`, title template, keywords  
- Canonical, robots index/follow, googleBot previews  
- OG + Twitter large image via `opengraph-image`  
- JSON-LD: Organization, SoftwareApplication, FAQPage  
- `robots.ts` disallows app routes  
- `lang="en-GB"`  
- GA4 production hook  

**Gaps:**
- Sitemap only indexes `/` — add legal + demo pages once built; keep dashboard disallowed  
- No `WebSite` schema with `SearchAction` (optional)  
- No public security / methodology page for long-tail  
- No hreflang (OK if single locale)  
- Dashboard routes still HTTP 200 without auth (robots disallow ≠ auth)  
- Metrics strip / social proof absent (hurts conversion + rich content SEO)  
- Footer internal links weak (legal 404s waste crawl equity when added without content)  
- README / GitHub homepage not reinforcing canonical URL  

---

## 6. OAuth findings (point 15) — summary

| Layer | Status |
|---|---|
| First-party password auth E2E | **Yes** (API + dashboard cookies + proxy) |
| API key machine auth | **Yes** |
| OAuth 2.0 / OIDC (Google, Microsoft, Okta, …) | **No** |
| Spring OAuth2 Client / Resource Server full stack | **No** (custom JWT + filter) |
| Production SSO for enterprise | Roadmap Phase 6 |

**Reference implementation:** WorkforceGuard `dashboard/backend/auth/oauth.py` + frontend auth components + `deploy/oauth-production-smoke-test.md`.

**Implication:** Do not claim “SSO” or “OAuth” on CV/Portfolio/Pitch until implemented. “JWT session auth + API keys; SSO roadmap” is accurate.

---

## 7. Security audit follow-up status

`docs/SECURITY_AUDIT_2026-06-22.md` critical tenant impersonation and several highs were addressed in subsequent commits (JWT required, SSRF pin via HttpClient5, postcss/next bumps, prompt-injection hardening). Residual:

- Prompt-injection guard still defense-in-depth only  
- No OAuth/OIDC  
- Audit not immutable/hash-chained  
- Phase 6 enterprise items open  

---

## 8. Instructions for other sessions (CV / Portfolio / Pitch — no code edits here)

### 8.1 CV (`Marti_Soura_Vamseekar_CV.docx` / `.pdf`)

Replace Devernance bullets with (after Phase 5 merge, re-count numbers):

```text
Devernance — Governance Control Plane for EU AI Act  ·  GitHub
Spring Boot 3.3 · Java 17 · Next.js 16 · pgvector HNSW · DJL/ONNX embeddings · Multi-tenant

• Governance: AI system registry with guided EU AI Act risk classification
  (Minimal / Limited / High / Prohibited), open-gap tracking, and release decisions
  PASS / REVIEW / BLOCKED driven by evidence coverage, eval thresholds, and data-contract status.
• Evidence & evals: Cited-evidence RAG (DJL + ONNX Runtime all-MiniLM-L6-v2; pgvector HNSW on Postgres);
  durable eval worker (skip-locked claims); HMAC-SHA-256 signed eval-result callbacks;
  Evidence Pack JSON export for audit-ready technical documentation.
• Architecture: Spring Boot API with Flyway migrations (V1–V9+), tenant-scoped JPA + JWT/API-key auth,
  Next.js dashboard (lineage DAG, contracts, approvals, audit log). OAuth/SSO on roadmap.
```

**Remove:** FAISS for this product; “HMAC signed audit event stream” as written; Next.js 14; V1–V6 only; “automated” without qualification; Unacceptable tier name unless mapped to Prohibited.

### 8.2 Portfolio (`src/data/projects.ts`)

Update metrics array to match canon; drop any overclaim; ensure stack list matches deps actually used; set period carefully (“2026 – Present” recommended unless 2024 proof).

### 8.3 Pitch deck (`MSV_AI_Labs_Pitch_Deck.pdf`)

Devernance slide:

- Key metrics: prefer **PASS/REVIEW/BLOCKED**, **Flyway V1–V9**, **40 API endpoints**, **JWT + API keys**, **DJL/ONNX RAG**, **HMAC eval callbacks** — not “HMAC audit streams”.  
- “Live since 2024” → verify or change to 2026.  
- Keep Aug 2026 enforcement timing narrative (market).  

### 8.4 Landing (code — this repo, later plan)

Add metrics strip from canon; Request demo; legal footer; keep trust language dry.

---

## 9. Ordered delivery plans

**Authoritative full program (2026-07-20 — includes PRD + Phase 6 + GTM):**

1. `docs/superpowers/specs/2026-07-20-full-roadmap-completion.md`  
2. `docs/superpowers/plans/2026-07-20-INDEX.md` — Parts **0–11**

**Original channel pack (Parts 0–4) still valid; extended by Parts 5–11:**

- Parts 0–4: Phase 5 WIP, GitHub, metrics, landing, OAuth  
- Parts 5–11: PRD MVP (controls/E2E), immutable audit, PDF pack, CI release-gate + observability, Docker/Terraform, isolation/NFR, docs signoff  

---

## 10. Appendix — asset path corrections

| User said | Actual on disk |
|---|---|
| CV folder `Documents/Marti_Soura_Vamseekar_CV` | Does not exist; use `Documents/Marti_Soura_Vamseekar_CV.docx` and `.pdf` |
| Portfolio `/Projects/Portfolio` | Contains `martisouravamseekar-portfolio/` Next app |
| Pitch `Downloads/MSV_AI_Labs_Pitch_Deck.pdf` | Found (174941 bytes, dated 2026-07-04) |

---

*End of investigation report — 2026-07-19.*
