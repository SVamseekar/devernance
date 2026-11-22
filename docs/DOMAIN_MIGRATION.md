# Domain migration: devernance.space on Cloudflare

Devernance moves from `euassuranceai.souravamseekar.com` (Vercel) to **`devernance.space`**, with DNS and hosting on
Cloudflare. Vercel is retired. The domain was registered at Porkbun on 16 November 2026. This page lists every step in
order, who does it, and how to check it. Steps that need an account login belong to the owner.

## Target layout

| Name | Points to | Purpose |
|---|---|---|
| `devernance.space` | Cloudflare Worker `devernance-site` (custom domain) | Marketing site, login and dashboard ([DEPLOYMENT.md](./DEPLOYMENT.md#c-site-on-cloudflare-workers)) |
| `www.devernance.space` | Same worker (custom domain), 308 to the apex | Convenience redirect |
| `app.devernance.space` | Cloudflare Tunnel to the Oracle VM | Dashboard with the live API, later ([DEPLOYMENT.md](./DEPLOYMENT.md#e-api-cutover-to-oracle-and-retiring-the-dell-lab-owner)) |
| `*@devernance.space` | Zoho Mail, see [EMAIL.md](./EMAIL.md) | Contact, support, security, privacy and the other role addresses |
| `euassuranceai.souravamseekar.com` | Porkbun URL forwarding, 301 to `https://devernance.space` | Old address, kept as a permanent redirect |

`souravamseekar.com` keeps its DNS at Porkbun. Its apex serves another site; change only the `euassuranceai` record.

## Order of work

Merge the code pull request (step 6) only after steps 1 to 4 pass their checks. Point the old address at the new one
(step 5) only after `https://devernance.space` serves the site, or visitors land on an empty domain.

### 1. Cloudflare zone and nameservers (owner) — done 22 November 2026

1. Cloudflare, **Add a domain**, `devernance.space`, **Free** plan. Delete every imported Porkbun parking record.
2. Porkbun, **Domain Management**, `devernance.space`, **Details**: delete any DNSSEC DS record, then replace the four
   Porkbun nameservers under **Authoritative Nameservers** with the two Cloudflare names.
3. Wait for Cloudflare to mark the zone **Active**.

Check: `dig +short NS devernance.space` shows only `*.ns.cloudflare.com`.

After the zone is active:

1. **SSL/TLS**: mode **Full (strict)**, **Always Use HTTPS** on, **Minimum TLS** 1.2.
2. **DNS, Settings, DNSSEC**: enable, then add the DS values (key tag, algorithm 13, digest type 2, digest) in Porkbun,
   **Details, DNSSEC**.
3. Leave **Bot Fight Mode** off. It blocks webhook calls.

### 2. Email (owner) — receiving done 22 November 2026

Receiving runs on Zoho Mail; the steps, records and lessons are in [EMAIL.md](./EMAIL.md). Cloudflare Email Routing
stays disabled, because it and Zoho both need the apex `MX` and `SPF` records.

Check: mail sent from another account to `hello@`, `security@` and `sales@` arrives, and a reply shows SPF, DKIM and
DMARC `pass` in the headers.

### 3. Site on Cloudflare Workers — first deployed from the CLI 23 November 2026

Follow [DEPLOYMENT.md, section C](./DEPLOYMENT.md#c-site-on-cloudflare-workers). The Discord secret is set; merges to
`main` deploy through `.github/workflows/deploy-site.yml` once its Cloudflare token is in place.

Check:

```bash
curl -sI https://devernance.space | head -1                     # HTTP/2 200
curl -sI https://www.devernance.space | grep -i '^location'     # https://devernance.space/
```

### 4. Sign-in providers (owner, when the API goes live)

Not needed for the marketing site.

1. Google Cloud console and Microsoft Entra: add `https://devernance.space/api/auth/oauth/google/callback` and
   `https://devernance.space/api/auth/oauth/microsoft/callback`. Keep the old URIs for one week, then remove them.
2. Set `OAUTH_REDIRECT_BASE_URL` and `ASSURANCE_APP_BASE_URL` to the site origin on the API host.

Check: sign in with Google and with Microsoft on `https://devernance.space/login`.

### 5. Redirect the old address (owner)

1. Porkbun, `souravamseekar.com`, **DNS**: delete the `A` record `euassuranceai` (`76.76.21.21`, Vercel).
2. Same domain, **URL Forwarding**, add: hostname `euassuranceai`, forward to `https://devernance.space`, type
   **Permanent (301)**, **Include URI path** on, wildcard off.
3. Wait for Porkbun to issue the certificate (up to 30 minutes).

Check:

```bash
curl -sI "https://euassuranceai.souravamseekar.com/pricing?x=1" | grep -iE '^HTTP|^location'
# 301, location: https://devernance.space/pricing?x=1
```

### 6. Merge the code pull request (agent or owner)

The pull request changes the defaults (`siteUrl` and `contactEmails` in `apps/dashboard/lib/site-config.ts`), adds the
`/contact` page and the Cloudflare Workers config, and updates the docs. Merged 23 November 2026 as #129.

Check after the deploy: `https://devernance.space/.well-known/security.txt` lists `security@devernance.space`, and
`https://devernance.space/sitemap.xml` lists `devernance.space` URLs.

### 7. After the move (owner)

1. Google Search Console: add `devernance.space` as a **Domain** property (DNS TXT record in Cloudflare), submit the
   sitemap, and file a **Change of address** from the old site.
2. After seven days of clean redirects, delete the Vercel projects `eu-ai-assurance` and `eu-ai-assurance-os` and
   uninstall the Vercel GitHub app from the repository.
3. When the Oracle VM runs the API: create the tunnel and route `app.devernance.space` to it
   ([DEPLOYMENT.md](./DEPLOYMENT.md)).

## Rollback

- Site broken after step 3: Cloudflare, `devernance-site`, **Deployments**, roll back. The old Vercel site keeps
  working until step 5.
- Mail not arriving: `dig +short MX devernance.space` must show the three `zoho.in` names; then the Zoho console,
  **Reports**.
- Redirect wrong after step 5: delete the Porkbun URL forward and restore the `A` record `euassuranceai` to
  `76.76.21.21`. The old Vercel site then serves again (until the projects are deleted in step 7).

## What does not change

Internal identifiers keep the old name on purpose ([BRAND.md](./BRAND.md#identifiers-that-keep-the-old-name)). The
JWT issuer, the `aos_` API key prefix and the database names do not depend on the domain.
