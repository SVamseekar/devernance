# Domain migration: devernance.space on Cloudflare

Devernance moves from `euassuranceai.souravamseekar.com` to **`devernance.space`**, with DNS on Cloudflare.
The domain was registered at Porkbun on 16 November 2026. This page lists every step in order, who does it, and how to
check it. The steps that need an account login belong to the owner. The code and doc changes are in the same pull
request as this page.

## Target layout

| Name | Points to | Cloudflare proxy | Purpose |
|---|---|---|---|
| `devernance.space` | Vercel project `eu-ai-assurance` | DNS only (grey cloud) | Marketing site and dashboard |
| `www.devernance.space` | Vercel, redirects to the apex | DNS only | Convenience redirect |
| `app.devernance.space` | Cloudflare Tunnel to the Oracle VM | Proxied (orange cloud) | Production API and dashboard, later ([DEPLOYMENT.md](./DEPLOYMENT.md#e-cutover-from-vercel-and-the-dell-lab-owner)) |
| `hello@devernance.space` | Cloudflare Email Routing to the owner's inbox | n/a | Support and security contact |
| `euassuranceai.souravamseekar.com` | Vercel redirect to `https://devernance.space` | n/a | Old address, kept as a permanent redirect |

Why DNS only for Vercel: Vercel issues its own certificate and routes at its edge. A Cloudflare proxy in front adds a
second hop, can break certificate renewal and hides the visitor's address from Vercel. The tunnel hostname is different:
it only works through the proxy.

## Order of work

Do the steps in this order. Merge the code pull request (step 7) only after steps 1 to 6 pass their checks, because a
merge to `main` deploys the site and the new contact address must already receive mail.

### 1. Add the zone to Cloudflare (owner)

1. Cloudflare dashboard, **Add a domain**, enter `devernance.space`, choose the **Free** plan.
2. Cloudflare imports the records it finds. Delete the Porkbun parking records (an `A` or `ALIAS` on the apex, and
   `CNAME *` to the parking host). Keep nothing for now.
3. Note the two Cloudflare nameservers it shows (for example `xxx.ns.cloudflare.com`).

### 2. Point Porkbun at Cloudflare (owner)

1. Porkbun, **Domain Management**, `devernance.space`, **Authoritative Nameservers**, replace the four Porkbun
   nameservers with the two from Cloudflare.
2. Wait until Cloudflare shows the zone as **Active** (minutes to a few hours).

Check:

```bash
dig +short NS devernance.space          # the two Cloudflare names, nothing from porkbun.com
```

### 3. Attach the domain in Vercel (owner)

1. Vercel, project `eu-ai-assurance`, **Settings, Domains**, add `devernance.space` and `www.devernance.space`.
   Set `www` to redirect to the apex.
2. Vercel lists the DNS records to create. Use exactly the values it shows (they can be project specific), usually an
   `A` record for the apex and a `CNAME` for `www`.
3. In Cloudflare, **DNS**, create those records with **Proxy status: DNS only**.
4. In Vercel, **Settings, Environment Variables**, set for Production: `NEXT_PUBLIC_SITE_URL=https://devernance.space`.
   Redeploy afterwards, because `NEXT_PUBLIC_*` values are fixed at build time.

Check:

```bash
curl -sI https://devernance.space | head -3     # HTTP/2 200, server: Vercel
curl -sI https://www.devernance.space | head -3 # a 308 to https://devernance.space/
```

### 4. Email (owner)

Receiving, for `hello@devernance.space`:

1. Cloudflare, **Email, Email Routing**, enable it. Cloudflare adds the MX and SPF records itself.
2. Add the custom address `hello` and a destination inbox. Confirm the verification email.

Sending, for the product (`ASSURANCE_EMAIL_FROM`): the API sends through Resend SMTP
(`infra/.env.prod.example`).

1. Resend, **Domains**, add `devernance.space`. Create the DKIM and SPF records it lists in Cloudflare, DNS only.
   A sending-only subdomain such as `send.devernance.space` keeps these records apart from Email Routing's SPF record,
   because a name can hold only one SPF record.
2. Add a DMARC record: name `_dmarc`, type `TXT`, value
   `v=DMARC1; p=none; rua=mailto:hello@devernance.space`. Tighten `p=none` to `quarantine` after a few weeks of clean
   reports.
3. Set on the API host: `ASSURANCE_EMAIL_FROM=Devernance <no-reply@devernance.space>` (use the subdomain if you chose
   one).

Check: send a mail to `hello@devernance.space` from another account and confirm it arrives. Send a test verification
mail from the product and confirm SPF, DKIM and DMARC show `pass` in the message headers.

### 5. Redirect the old address (owner)

1. Vercel, project `eu-ai-assurance`, **Settings, Domains**, open `euassuranceai.souravamseekar.com`, choose
   **Redirect to** `devernance.space`, status **308** (permanent). Vercel keeps path and query.
2. Leave the DNS for `souravamseekar.com` as it is.

Check:

```bash
curl -sI "https://euassuranceai.souravamseekar.com/pricing?x=1" | grep -i "^location"   # https://devernance.space/pricing?x=1
```

### 6. Sign-in providers (owner)

1. Google Cloud console and Microsoft Entra: add the redirect URIs
   `https://devernance.space/api/auth/oauth/google/callback` and `https://devernance.space/api/auth/oauth/microsoft/callback`.
   Keep the old `euassuranceai.souravamseekar.com` URIs for one week, then remove them.
2. On the API host set `OAUTH_REDIRECT_BASE_URL` and `ASSURANCE_APP_BASE_URL` to the new site origin
   (`https://devernance.space`, or `https://app.devernance.space` after the Oracle cutover).

Check: sign in with Google and with Microsoft on `https://devernance.space/login`.

### 7. Merge the code pull request (agent or owner)

The pull request changes the defaults only: `siteUrl` and the contact address in `apps/dashboard/lib/site-config.ts`,
the `NEXT_PUBLIC_SITE_URL` value in `.github/workflows/ci.yml`, the fallback disclaimer URL in `PublicClaimsService`,
and the links in the README and docs. Merging it deploys the site.

Check after the deploy: `https://devernance.space/.well-known/security.txt` shows `hello@devernance.space`, and
`https://devernance.space/sitemap.xml` lists `devernance.space` URLs.

### 8. After the move (owner)

1. Google Search Console: add `devernance.space` as a **Domain** property (DNS TXT record in Cloudflare), submit the
   sitemap, and use **Change of address** from the old site.
2. Cloudflare, **SSL/TLS**: set the mode to **Full (strict)**. It matters for the proxied `app` record.
3. After a week of clean redirects, remove the old OAuth redirect URIs.
4. Production API on the Oracle VM: create a Cloudflare Tunnel and route `app.devernance.space` to it
   (`cloudflared tunnel create devernance`, then `cloudflared tunnel route dns devernance app.devernance.space`).
   Steps for the VM are in [DEPLOYMENT.md](./DEPLOYMENT.md).

## Rollback

Nothing in the code is tied to the new domain. If a step fails:

- Site down after step 3: set the Cloudflare records back to what Vercel lists, or remove the domain from the Vercel
  project. `eu-ai-assurance.vercel.app` keeps working throughout.
- Mail not arriving after step 4: Email Routing lists the last failure under **Activity log**.
- Redirect wrong after step 5: remove the redirect in Vercel. The old domain then serves the site again.

## What does not change

Internal identifiers keep the old name on purpose ([BRAND.md](./BRAND.md#identifiers-that-keep-the-old-name)). The
JWT issuer, the `aos_` API key prefix and the database names do not depend on the domain.
