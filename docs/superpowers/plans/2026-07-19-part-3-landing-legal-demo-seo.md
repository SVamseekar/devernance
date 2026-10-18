# Part 3 — Landing: Legal, Demo Form, Footer, SEO

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans.

**Goal:** Bring `euassuranceai.souravamseekar.com` marketing surface to sister-product and B2B governance parity.

**References:**
- WorkforceGuard demo: `dashboard/frontend/api/request-demo.js`, `src/lib/demo-request.ts`
- WorkforceGuard refunds (enterprise tone): `dashboard/frontend/refunds.html`
- Aequitas footer plan: `aequitas/docs/superpowers/plans/2026-07-19-part-d-landing-seo-footer.md`
- Investigation §4–5

**Depends on:** Part 2 for metrics strip numbers (can scaffold pages first with placeholders).

**Tech:** Next.js App Router routes + Route Handlers (not separate Vite HTML stubs).

---

### Task 3.1: Legal pages (enterprise tone)

Create App Router pages:

- `apps/dashboard/app/privacy/page.tsx`
- `apps/dashboard/app/terms/page.tsx`
- `apps/dashboard/app/refunds/page.tsx`
- Optional: `apps/dashboard/app/disclaimer/page.tsx` (not a legal determination / not certification)

**Refunds copy (use this shape):**

```text
Devernance is sold to organisations on an enterprise basis following a
tailored demo and scoping conversation.

Cancellation and refund terms are set out in your organisation's order form or
statement of work. Contact {SUPPORT_EMAIL} for billing questions.

This product is a governance control plane for evidence, evals, and approvals.
It does not provide legal certification or a final determination of EU AI Act
obligations.
```

- [ ] **Step 1:** Shared minimal legal layout (nav home, article, last-updated date).  
- [ ] **Step 2:** Unique metadata + canonical per page.  
- [ ] **Step 3:** WebPage JSON-LD.  
- [ ] **Step 4:** Support email constant in `site-config.ts`.

### Task 3.2: Request demo form

- [ ] **Step 1:** Page `app/request-demo/page.tsx` with form fields adapted to AI Act (see investigation §4.4).  
- [ ] **Step 2:** Client submit helper `lib/demo-request.ts`.  
- [ ] **Step 3:** Route handler `app/api/request-demo/route.ts`:
  - honeypot `website`
  - min 3s form fill
  - IP rate limit 5 / 15 min
  - validate email + privacy consent
  - Discord webhook `DISCORD_WEBHOOK_URL` or `DISCORD_DEMO_WEBHOOK_URL`
  - 503 fallback with support email  
- [ ] **Step 4:** Wire env on Vercel; document in README.  
- [ ] **Step 5:** Primary CTA on hero + final CTA → `/request-demo`; secondary → `/login` or `/command`.

### Task 3.3: Footer completeness

Replace thin footer with 4 columns:

| Product | Resources | Legal | Contact |
|---|---|---|---|
| section anchors | FAQ | Privacy | Support email |
| Request demo | GitHub | Terms | Portfolio link |
| Sign in | Metrics (optional) | Refunds | Request demo |
| | | Disclaimer | |

- [ ] **Step 1:** Update `landing-footer.tsx`.  
- [ ] **Step 2:** Copyright + owner name.  
- [ ] **Step 3:** Analytics one-liner if GA configured.

### Task 3.4: Metrics strip

- [ ] **Step 1:** New section between hero and problem (or under hero) using `metrics-canon.ts`.  
- [ ] **Step 2:** 4–6 factual chips only (endpoints, phases, decision outcomes, embedding stack, etc.).

### Task 3.5: SEO hardening

- [ ] **Step 1:** Expand `sitemap.ts` to include `/`, `/request-demo`, `/privacy`, `/terms`, `/refunds`, `/disclaimer` (if any).  
- [ ] **Step 2:** Keep dashboard routes disallowed in `robots.ts`.  
- [ ] **Step 3:** Add `WebSite` node to JSON-LD graph on landing.  
- [ ] **Step 4:** Ensure each new page has title, description, canonical, OG.  
- [ ] **Step 5:** Verify live after deploy: all legal/demo URLs 200; sitemap lists them.  
- [ ] **Step 6:** Optional Search Console re-submit sitemap.

### Task 3.6: Header CTAs

- [ ] **Step 1:** Header: Request demo (primary outline) + Sign in / Open dashboard.  
- [ ] **Step 2:** Mobile menu includes Legal links or rely on footer.

### Done when

- [ ] Live 200: `/privacy` `/terms` `/refunds` `/request-demo`  
- [ ] Demo form delivers Discord message in staging  
- [ ] Footer matches column structure  
- [ ] Sitemap + robots correct  
- [ ] Metrics strip matches canon  
