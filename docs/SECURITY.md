# Security and Compliance Model

## Tenant Isolation

MVP / current ship:

- Tenant discriminator column on every tenant-owned table.
- API middleware injects tenant context from **verified credentials** only (`Authorization: Bearer` JWT or `X-Api-Key`). Client-supplied `X-Tenant-Id` / `X-Actor-Id` are **not** trusted.
- Queries always include tenant filters; cross-tenant ID access returns 404.
- CI regression: `TenantIsolationTest` (two tenants; never leak B data to A).

Production hardening options (not all required for current deploy):

- Row-level security in PostgreSQL.
- Enterprise option for schema-per-tenant or dedicated deployment.

See also: `docs/ROLE_MATRIX.md`, `docs/NFR.md`, Part 10.

## Authentication and Authorization

Roles (API enum `UserRole`):

- Admin (`ADMIN`)
- AI Engineering Lead (`AI_ENGINEERING_LEAD`)
- Compliance Officer (`COMPLIANCE_OFFICER`)
- Legal Counsel (`LEGAL_COUNSEL`)
- Auditor (`AUDITOR`)

(PRD/SECURITY “Data Platform Owner” maps to Admin or Engineering for contract mutations in the MVP matrix.)

Controls:

- **Password JWT + refresh tokens** and **API keys** for day-to-day API access.
- **OAuth/OIDC (Google + Microsoft)** — **implemented** (Part 4 / PR #27): authorization-code start + BFF callback exchange. **Production smoke pending** — do not claim production SSO verified until `docs/oauth-production-smoke-test.md` is executed and signed off.
- RBAC for system edits, approvals, exports, and audit access — full matrix in `docs/ROLE_MATRIX.md`.
- Service accounts for CI/CD and eval workers (`X-Api-Key`).
- Eval callback and manual execution endpoints require Admin or AI Engineering Lead actors.
- Eval operations visibility is limited to Admin, AI Engineering Lead, and Compliance Officer actors.
- External eval callbacks require timestamped HMAC-SHA-256 signatures over the raw request body (`EVAL_CALLBACK_SECRET`).

### Unauthenticated path allowlist

`TenantContextFilter` allows **only**:

| Path | Purpose |
|---|---|
| `/.well-known/jwks.json` | JWT and evidence-pack public keys (also served by the dashboard at the same path) |
| `/auth/login`, `/auth/refresh`, `/auth/logout` | Token lifecycle |
| `/auth/signup`, `/auth/verify-email/**`, `/auth/password/**` | Self-serve sign-up and password reset (Turnstile at the BFF) |
| `/auth/accept-invite`, `/auth/invites/**` | Invite preview and acceptance |
| `/auth/demo` | Read-only demo session (only when `ASSURANCE_DEMO_ENABLED=true`) |
| `/auth/oauth/**` | OAuth start + token exchange (BFF) |
| `/api/public/**` | Free no-signup tools (read-only, rate limited per IP) |
| `/api/v1/billing/webhooks/dodo` | Dodo webhooks (Standard Webhooks signature required) |
| `/actuator/health` (+ `/liveness`, `/readiness` prefix) | Probes |

All other routes require a valid Bearer token or API key. Spring Security is intentionally `permitAll` so this filter remains the single authentication gate — do not bypass it.

### Dashboard session hard-gate

Next.js `proxy.ts` (formerly `middleware.ts`) redirects unauthenticated browsers away from app shell routes (`/command`, `/systems`, `/approvals`, `/evidence`, `/evals`, `/contracts`, `/audit`, `/readiness`, `/reg-monitor`, …) to `/login`. API access goes through the BFF proxy with httpOnly cookies; the browser never sends tenant headers.

### Session cookies

Dashboard BFF sets httpOnly, `Secure` (production), `SameSite` cookies for access/refresh tokens after password or OAuth login. Prefer short-lived access JWTs and rotating refresh tokens (server-side hashed).

## Sensitive Data

- Do not send raw personal data to external LLM providers by default.
- Redact or tokenize PII before embedding.
- Encrypt evidence documents at rest (provider/object-store default encryption).
- Store API keys in a secret manager; only SHA-256 hashes in DB.
- Track data region and retention settings per tenant.

## Encryption and secrets (NFR)

### In transit

- TLS terminates at the edge for the dashboard (Vercel HTTPS or reverse proxy).
- Production API and BFF→API traffic should use HTTPS (`ASSURANCE_API_BASE_URL`).
- Managed Postgres and object storage connections use provider TLS.

### At rest

- **Postgres disk encryption** is a **provider responsibility** (RDS/Cloud SQL/etc.); the app does not field-encrypt every column.
- Object storage should use bucket default encryption (SSE-S3/SSE-KMS or equivalent).
- API keys stored as SHA-256 hashes only; passwords BCrypt-hashed.

### Secrets

| Secret | Purpose |
|---|---|
| `EVAL_CALLBACK_SECRET` | HMAC for eval result callbacks |
| `AUDIT_CHAIN_SECRET` | HMAC material for audit hash-chain (`prev`/`event` hashes) |
| `DATABASE_PASSWORD` | DB credentials |
| OAuth client secrets | Google / Microsoft apps (env / secret manager) |
| `ASSURANCE_STORAGE_*` | Optional S3/MinIO credentials |

- **Never committed** — gitleaks (Part 1) and code review. Root `.env.example` is template only.
- Full ops detail: `docs/NFR.md`, `docs/DEPLOYMENT.md`.

## Audit Requirements

Audit events are **append-only** and **hash-chained** (HMAC-SHA-256 over event content + previous hash). Columns: `prev_event_hash`, `event_hash`, `retain_until` (default ≥ 7 years). Verify endpoints:

- `GET /api/v1/audit/verify`
- `GET /api/v1/audit-events/verify-chain`

High-value events include:

- Risk classification changed; high-risk system created.
- Evidence query answered; evidence pack exported (JSON/PDF) with `contentSha256`.
- Eval gate completed; data contract breach detected.
- Approval / override submitted.
- Determination run completed; certification readiness assessed/exported; reg item reviewed.

Production storage:

- **Shipped:** PostgreSQL/H2 append-only table with restricted write path + hash chain.
- Optional later: Kafka audit topic or WORM object storage for multi-region enterprise — **not required** for current architecture.

## Threat Model

Primary risks:

- Cross-tenant data leakage.
- Prompt injection in evidence documents.
- Incomplete or fabricated citations.
- Unauthorized release approval.
- Forged or replayed eval callbacks.
- Missing audit trail for high-risk decision.
- PII leakage to model providers.
- Data-contract drift causing unvalidated model behavior.
- OAuth state CSRF / token confusion if misconfigured.
- Overclaiming certification or legal determination via product UI copy.

Mitigations:

- Tenant-scoped retrieval and authorization from verified credentials only.
- Evidence chunk source validation; upload limits; URI schemes; checksums; metadata shape; chunk hashes; embedding-provider provenance.
- Citation-required answer generation.
- Reviewer approval workflows with oversight evidence.
- Hash-chained immutable audit events + verify API.
- Short-window callback signatures with no default production secret.
- Pessimistic eval run claims with Postgres `FOR UPDATE SKIP LOCKED`.
- Product disclaimers: assisted determination / readiness only — never notified-body language.
- OAuth state signed; BFF-mediated start and callback (the API stays on the private network); production smoke runbook before claiming SSO live.
- The dashboard BFF forwards only single, re-encoded path segments under `/api/v1/`: dot segments and decoded `/`, `\`, `?` or `#` are refused, so a session cannot reach actuator or other API routes through it. Request bodies are capped (26 MB for uploads, 16 KB for auth forms) before anything is buffered.
- Platform metrics (`/actuator/metrics`, `/prometheus`, `/info`) answer only an admin of the operator tenant; everyone else gets `404`.
- Authenticated API budget: 600 requests a minute per caller, 30 heavy requests (exports, packs, uploads, mapping) a minute per workspace; the public demo is budgeted per visitor address. Evidence scans (evgraph) run at most 4 at a time.
- Outbound fetches (evidence URLs, regulatory feeds) connect only to globally routable addresses: private, loopback, link-local (cloud metadata), carrier-grade NAT, benchmark, documentation and reserved ranges are refused, including IPv6 forms that embed them. Fetched documents are capped at 25 MB and parsed under a 30 s timeout.
- Auth emails are sent off the request thread, so response time does not reveal whether an address has an account.
- Post-login and OAuth return paths must resolve to the same origin (`/\host` and control-character tricks are refused).

## Remaining residual risks (honest)

- OAuth **production** configuration and smoke not yet signed off.
- p95 latency and 99.9% uptime are **targets**, not measured production SLOs (see `docs/NFR.md`).
- Sector packs are SPI + stubs — no live proprietary connectors.
- Reg monitor remote sources disabled by default; curated bootstrap is assistive, not legal authority.
- PDF extraction from arbitrary binary object-store docs is still limited vs text upload path.
- The dashboard CSP allows `'unsafe-inline'` scripts: Next.js inlines its RSC payload, and a nonce-based policy would make every page dynamic. XSS defence rests on React escaping and on never rendering user HTML.
- Rate limits, the demo question cap and the JWKS and chain-verification caches are in memory, so they hold per API instance. Production runs one instance; a second one would need a shared store (or Cloudflare edge rules) for the limits.
- A document that hangs the PDF or Office parser past its 30 s timeout keeps one of two parser threads busy until it finishes; parsing is not isolated in a separate process.

## EU Context

The product supports EU-focused assurance workflows. It does **not** make final legal determinations, issue certificates, or replace notified-body / qualified counsel review. It helps teams gather evidence, enforce controls, and prepare documentation for legal/compliance review.
