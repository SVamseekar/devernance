# Marketing Landing Page + Site-Wide SEO

## Goal

Replace the current root route (`apps/dashboard/app/page.tsx`, which redirects to `/command`) with a real marketing landing page for Devernance, and make the dashboard app SEO-compliant site-wide.

## Background research

- Reviewed positioning of comparable B2B governance/compliance platforms (Vanta, Drata, OneTrust, Modulos) — common pattern: trust-first hero, audit/evidence language, framework-mapping (ISO 42001, EU AI Act, NIST AI RMF), persona-based messaging, FAQ sections for long-tail SEO.
- Target audience is broad per PRD §2: AI Engineering Lead, Compliance Officer, Legal Counsel, Data Platform Lead, Product Owner, Auditor — so the page must speak to technical and non-technical roles without over-indexing on either.
- Style direction: trust-focused enterprise SaaS, but avoid generic AI-template aesthetics — use real product artifacts (release decision card, control checklist) instead of stock illustrations/gradient blobs, and a direct/dry regulatory voice rather than hype copy.

## Visual system

Reuse existing dashboard theme tokens (`app/globals.css`): indigo primary (`oklch(0.47 0.22 264)`), warm light-gray background, Inter for body/headings, JetBrains Mono for code/control-ID accents. The landing page does NOT use the dashboard sidebar shell — it's a standalone full-width marketing layout with its own header/footer.

Section backgrounds alternate between `bg-background` and `bg-muted`/`bg-secondary` for visual rhythm. Use existing `Button`, `Badge`, `Card` components from `components/ui/` for consistency.

## Page structure (`apps/dashboard/app/page.tsx`)

1. **Header/nav** (sticky, transparent→solid on scroll)
   - Wordmark "Devernance"
   - Anchor links: Product, How it works, Who it's for, FAQ
   - "Open Dashboard" button → `/command`

2. **Hero**
   - `<h1>`: value proposition centered on releasing AI systems in the EU market with evidence and auditability (not "AI-powered" hype)
   - Subhead: one sentence connecting risk classification, eval gates, evidence, and approvals to a release decision
   - Primary CTA "Open Dashboard" → `/command`; secondary CTA "See how it works" → `#how-it-works` (smooth scroll anchor)
   - Visual: a stylized static mock of a release-gate decision card (PASS/REVIEW/BLOCKED badge + 3-4 control checklist rows with status icons), built with existing `Card`/`Badge` components — represents real product output, not abstract art

3. **Problem/stakes section**
   - Short `<h2>` + 3 short statements naming pain points: manual evidence chasing before audits, unclear obligations per risk tier, no single release decision combining evals/contracts/approvals
   - Establishes credibility for both technical and compliance audiences

4. **Core capabilities grid** (`<h2>` + 6 cards, 3x2 or 2x3 grid)
   - AI System Registry, Risk Classification, Evidence RAG (cited answers), Eval Gates, Data Contract Monitor, Approval Workflow & Audit Ledger
   - Each card: icon (lucide, matching sidebar icon set), title, 1-2 sentence description pulled from PRD §5 feature descriptions

5. **How it works** (`id="how-it-works"`, `<h2>` + horizontal 4-step flow, stacks vertically on mobile)
   - Register → Classify & attach evidence → Run eval gates & check contracts → Get PASS/REVIEW/BLOCKED decision + export evidence pack
   - Each step: number badge, short title, 1-sentence description

6. **Built for your team** (personas, `<h2>` + responsive grid of 6 cards)
   - One card per PRD persona (AI Eng Lead, Compliance Officer, Legal Counsel, Data Platform Lead, Product Owner, Auditor), each with role name + one sentence on what they get from the platform

7. **Trust/compliance strip**
   - Compact horizontal row of short badges/labels: EU AI Act-aligned controls, Tenant data isolation, Encryption in transit & at rest, Append-only audit ledger, Deterministic evidence export
   - No fake certification logos — text/icon badges only, framed as product capabilities (avoids misleading "certified" claims)

8. **FAQ** (`<h2>` + accordion using native `<details>`/`<summary>`, 5-6 Q&As)
   - Targets long-tail SEO queries, e.g.: "What is an EU AI Act risk classification?", "What is an evidence pack?", "How does an eval gate determine release readiness?", "What counts as a data contract drift event?", "Who needs to approve a high-risk AI system release?"
   - Answers are concise (2-3 sentences), grounded in PRD definitions
   - Feeds `FAQPage` JSON-LD (see SEO section)

9. **Final CTA band**
   - Repeat of primary CTA ("Open Dashboard" → `/command`) with one supporting sentence

10. **Footer**
    - Wordmark + one-line description
    - Link columns: Product (anchors to sections), App (links to `/command`, `/systems`, `/approvals`, `/evidence`, `/evals`, `/contracts`, `/audit` — internal linking for SEO/crawlability), Resources (placeholder for docs if/when public)
    - Copyright line

## SEO implementation (site-wide)

- **`app/layout.tsx`**: expand `metadata` export — `title` with template (`%s | Devernance`), richer `description`, `metadataBase` from `NEXT_PUBLIC_SITE_URL`, Open Graph (`og:title`, `og:description`, `og:type`, `og:url`, `og:image`), Twitter card (`summary_large_image`), `robots: { index: true, follow: true }`
- **`app/page.tsx`**: page-specific `metadata` override (title/description tuned for landing page), plus JSON-LD via inline `<script type="application/ld+json">`:
  - `Organization` schema (name, url, description)
  - `SoftwareApplication` schema (name, applicationCategory, description)
  - `FAQPage` schema generated from the FAQ section's Q&A data (single source of truth — same array renders the accordion and the JSON-LD)
- **`app/sitemap.ts`**: Next.js sitemap convention — entries for `/`, `/command`, `/systems`, `/approvals`, `/evidence`, `/evals`, `/contracts`, `/audit`, each with `lastModified`/`changeFrequency`/`priority`
- **`app/robots.ts`**: Next.js robots convention — allow all, point to sitemap URL
- **Env var**: add `NEXT_PUBLIC_SITE_URL` (placeholder default `https://euaiassurance.example`) used by metadataBase, sitemap, robots, JSON-LD URLs — documented in a `.env.example` entry if one exists, otherwise noted in code comment
- **Semantic HTML**: single `<h1>` in hero only; `<h2>` per major section; `<header>`, `<nav>`, `<main>`, `<section aria-label="...">`, `<footer>` landmarks throughout the page
- **OG image**: use Next.js `opengraph-image` convention with a simple generated text/brand image (via `next/og` ImageResponse) rather than a static asset, to avoid needing design assets

## Out of scope

- No new design assets/illustrations — built entirely from existing component primitives + lucide icons + CSS
- No real signup/auth flow — CTAs route to `/command` (existing dashboard entry)
- No changes to `/command` or other dashboard routes beyond sitemap inclusion
- No public docs site — "Resources" footer column links are internal anchors only for now
