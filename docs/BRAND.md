# Devernance: name and naming rules

On 18 October 2026 the product was renamed from **EU AI Assurance OS** (short form "Assurance OS") to **Devernance**.
The old name tied the product to the EU and to AI, and the product is expanding to other compliance regimes.

## Using the name

- Write it as one word with a capital D: **Devernance**. A common misspelling is "Devernence".
- The name blends development and governance. Tagline: *Development and governance, hand in hand.*
- Use it as the product's name: "the Devernance release gate", "sign in to Devernance".
- Do not use it as the name of a practice ("teams that do devernance"). Use a generic term such as
  **governance-as-code** for that. A brand that becomes the generic word for its category loses trademark protection.
- The former name appears on purpose in two places only. `siteConfig.formerName` puts it in the homepage JSON-LD as
  `alternateName`, so search engines link the two names. The README intro says "formerly EU AI Assurance OS".

## Identifiers that keep the old name

These were left unchanged on purpose. Each one is either invisible to users or holds deployed state, so renaming it
would break something.

| Identifier | Where | Why it stays |
|---|---|---|
| Java package `os.assurance.eu.api` | `services/api` | Internal only. Renaming it touches every Java file and changes nothing users see. |
| Database, user and schema `eu_ai_assurance` | properties, compose, CI, scripts | Existing Postgres databases and their backups use these names. |
| Flyway migrations V1–V27 | `db/migration` | Editing an applied migration breaks Flyway's checksum check. `V28__devernance_rename.sql` rewrites the old text in existing rows instead. |
| Config keys `assurance.*` and env vars `ASSURANCE_*` | API, dashboard, hosts | Deployed hosts set them, and "assurance" is a plain word here, not the old brand. |
| Production names: compose project `aos`, `/opt/aos`, `aos-update.*` units, `AOS_*` variables, volume `aos_pgdata`, images `ghcr.io/svamseekar/aos-*`, backup file prefixes | `infra/` | The running host, its timers and its backups use them. |
| Compose projects `eu-ai-assurance` and `eu-ai-assurance-dell` | `infra/docker-compose*.yml` | Volume names start with the project name, so a new name orphans existing data. |
| Evidence buckets `eu-ai-assurance-evidence` (local default) and `aos-evidence` (production R2) | API default, compose, `.env.prod.example` | Stored `sourceUri` values point at these buckets. |
| JWT issuer `eu-ai-assurance-os` and evidence JWS type `aos-evidence+jws` | `JwtService` | A new issuer signs out every live session. The JWS type is part of the signed evidence-pack format, so changing it is a format change. |
| API keys that start with `aos_` | `TenantContextFilter` | Keys issued before the rename keep working. New keys start with `dvn_`. |
| Calendar UIDs `…@assurance-os` | `apps/dashboard/lib/ics.ts` | Calendar apps match re-imported events by UID, so a new UID duplicates every event. |
| Browser storage key `eu-ai-theme` | `apps/dashboard/app/layout.tsx` | A new key resets every visitor's theme choice. |
| The Dell checkout `D:/Projects/eu-ai-assurance-os`, and checkout paths in the plans under `docs/superpowers/` | Dell scripts, plans | The Dell's services run from that folder. The plans record where commands ran at the time. The Mac checkout is `~/Projects/devernance`, and the Evgraph files in `public-claims/` point there. |

## GitHub repo

On 18 October 2026 the repo was renamed from `SVamseekar/eu-ai-assurance-os` to `SVamseekar/devernance`.
GitHub redirects the old web, API and git URLs.

- Never create a new repo named `eu-ai-assurance-os` under this account. GitHub drops the redirect once the old name
  is reused.
- Checkouts made before the rename still use the old URL through the redirect. Update each one with
  `git remote set-url origin https://github.com/SVamseekar/devernance.git`. `scripts/deploy-dell.sh` does this for
  the Dell on every git sync.
- The release gate action lives in its own repo, `SVamseekar/devernance-release-gate`, and users reference it as
  `uses: SVamseekar/devernance-release-gate@v1`. Never rename that repo: GitHub does not redirect `uses:` references
  to a renamed repo, so every workflow that uses the old name would fail.

## Domain

`devernance.space` is the product domain. It was registered at Porkbun on 16 November 2026 and its DNS and hosting
moved to Cloudflare; Vercel is retired. `devernance.com` was the first choice and was not bought. The full ordered runbook, with the checks for each
step, is [DOMAIN_MIGRATION.md](./DOMAIN_MIGRATION.md). In short:

1. Add the zone in Cloudflare and switch the Porkbun nameservers to Cloudflare.
2. Deploy the site to Cloudflare Workers (`devernance-site`, custom domain `devernance.space`) and redirect `www`.
3. Set up the Zoho mailboxes and aliases ([EMAIL.md](./EMAIL.md)) and, with the API, the sending domain in Brevo.
4. Redirect `euassuranceai.souravamseekar.com` to `devernance.space` with a Porkbun URL forward (301).
5. When the API goes live, add the new OAuth redirect URIs in Google and Microsoft.
6. Merge the code change: `siteUrl` and `contactEmails` in `apps/dashboard/lib/site-config.ts`, the
   `NEXT_PUBLIC_SITE_URL` build value in `.github/workflows/ci.yml`, the fallback disclaimer URL in
   `PublicClaimsService`, and the links in the README and docs.
7. In Google Search Console, add the new domain, submit the sitemap and file a change of address. Then delete the
   Vercel projects.
8. Trademark: get a clearance search from a trademark lawyer, then file with the EUIPO and the USPTO in classes 9 and 42.
