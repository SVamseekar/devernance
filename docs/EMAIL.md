# Email addresses for devernance.space

Receiving mail runs on **Zoho Mail** (India data centre, console `https://mailadmin.zoho.in`), live since
22 November 2026. Product mail (sign-up verification, password reset, invites) will be sent through **Brevo** SMTP from
`no-reply@devernance.space` once the API is in production. The layout copies MaSoVa's (`getmasova.rest`): a few real
mailboxes, with every role address as an alias on one of them.

The Zoho organisation lives in its own Zoho account, separate from MaSoVa's. Zoho Free allows one organisation and one
domain per account, so the two businesses cannot share one.

Cloudflare Email Routing is **not** used. It and Zoho both need the apex `MX` and `SPF` records, and a domain can use
only one receiver.

## Mailboxes and aliases

Zoho Free allows 5 users per organisation, one domain per organisation, and no IMAP or POP.

| Mailbox (a Zoho user) | Aliases on it |
|---|---|
| `admin@` (super admin) | `dev@`, `dmarc@`, `no-reply@`, `security@`, `abuse@`, `postmaster@` |
| `hello@` | `privacy@`, `legal@` |
| `billing@` | `invoices@`, `refunds@` |
| `souravamseekar@` (founder) | `demo@`, `partners@`, `press@`, `sales@` |
| `support@` | `help@` |

All addresses end in `@devernance.space`.

## Where each address appears on the site

All public addresses live in `apps/dashboard/lib/site-config.ts` (`contactEmails`). Never hardcode an address in a page.

| Address | Used in |
|---|---|
| `hello@` | `/contact` directory |
| `sales@` | `/contact`, `/order-form`, `/compare/[slug]` |
| `demo@` | `/request-demo` form, the request-demo API error message, `/contact` |
| `support@` | `siteConfig.supportEmail`: sign-in footer, page JSON-LD, Technical Support row in `/terms` and `/order-form`, `/contact` |
| `billing@` | `/contact` |
| `refunds@` | `/refunds`, `/contact` |
| `privacy@` | `/privacy`, `/subprocessors`, `/contact` |
| `legal@` | `/terms`, `/disclaimer`, `LEGAL.noticeEmail` (DPA provider contact, MSA, order form), `/contact` |
| `security@` | `/security`, `/.well-known/security.txt`, `/contact` |
| `partners@` | `/contact` |
| `press@` | `/contact` |
| `souravamseekar@` | `/contact` (founder, investors and strategic conversations) |

Not public, so not on the site: `admin@`, `dev@`, `dmarc@`, `no-reply@`, `abuse@`, `postmaster@`, `invoices@`, `help@`.
`dmarc@` receives DMARC reports (`rua=`). `no-reply@devernance.space` is the product sender (`ASSURANCE_EMAIL_FROM`)
and catches replies to product mail in the `admin@` mailbox.

## DNS (Cloudflare, all DNS only)

| Type | Name | Value |
|---|---|---|
| `TXT` | `@` | Zoho verification record (Zoho shows it) |
| `MX` | `@` | `mx.zoho.in` priority 10 |
| `MX` | `@` | `mx2.zoho.in` priority 20 |
| `MX` | `@` | `mx3.zoho.in` priority 50 |
| `TXT` | `@` | `v=spf1 include:zoho.in ~all` (one SPF record only) |
| `TXT` | `zmail._domainkey` | Zoho DKIM key (selector `zmail`, shown under **Domains, Email Configuration, DKIM**) |
| `TXT` | `_dmarc` | `v=DMARC1; p=none; rua=mailto:dmarc@devernance.space; adkim=r; aspf=r` |

Add these records by hand in Cloudflare. Zoho's **Configure automatically** (Cloudflare Domain Connect) reported success
on 22 November 2026 but wrote nothing; on a second run it offers to delete the hand-made records and add identical ones.
Decline it and use **Verify** instead.

After adding a record, Zoho's **Verify** can fail for up to 30 minutes: resolvers keep the earlier "no record" answer
for the zone's negative TTL (1800 seconds). Check the record directly, then wait; do not regenerate the DKIM key.

```bash
dig +short TXT zmail._domainkey.devernance.space @$(dig +short NS devernance.space | head -1)
```

Brevo, when set up, adds a `brevo-code` `TXT` on `@` and two DKIM `CNAME` records (`brevo1._domainkey`,
`brevo2._domainkey`, **DNS only**). They do not clash with Zoho. Do not add a second SPF or DMARC record; Brevo needs
neither changed. The SMTP settings go in `ASSURANCE_SMTP_PRIMARY_*` (`infra/.env.prod.example`).

Move `p=none` to `quarantine` after a few weeks of clean DMARC reports.

## Add or change an address

1. Zoho console, **Users**, the user, **Mailbox Settings**, **Email Alias**, **Add**.
2. Add the key to `contactEmails` and `contactDirectory` in `lib/site-config.ts`.
3. Use it in the page that needs it. `lib/site-config.test.ts` fails if an address is off the product domain or missing from
   the directory.
4. Send a test mail to the address before merging. A merge to `main` publishes it.
5. Update the tables above.

## Check

```bash
dig +short MX devernance.space      # the three zoho.in names
dig +short TXT devernance.space     # one SPF record, include:zoho.in
dig +short TXT _dmarc.devernance.space
```

Then send mail from another account to `hello@`, `security@` and `sales@`, send one back out, and confirm SPF, DKIM and
DMARC show `pass` in the message headers.
