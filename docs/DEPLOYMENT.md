# Deployment runbook (Part 9)

How to run Devernance locally with Docker and how to deploy the production split: **Vercel dashboard** + **hosted Spring API** + **Postgres**.

Related: [NFR.md](./NFR.md) (secrets/encryption), [OPS.md](./OPS.md) (CI gate + metrics), [SECURITY.md](./SECURITY.md), root [`.env.example`](../.env.example), [`infra/README.md`](../infra/README.md).

---

## 1. Local full stack (Docker Compose)

### Prerequisites

- Docker Engine + Compose v2
- Optional: copy root `.env.example` → `.env` and change secrets

### One command (postgres + API)

From the **repository root**:

```bash
docker compose -f infra/docker-compose.yml --env-file .env.example up --build
```

Or from `infra/`:

```bash
cp ../.env.example ../.env   # once
docker compose --env-file ../.env up --build
```

| Service | URL |
|---|---|
| API | http://localhost:8080 |
| Health | http://localhost:8080/actuator/health |
| Postgres | `localhost:5432` (user/db/password from env) |

Default stack always starts **postgres + api** (API is no longer behind a Compose profile).

### Optional profiles

```bash
# Self-hosted Next.js dashboard (Vercel remains primary for production)
docker compose -f infra/docker-compose.yml --env-file .env.example --profile dashboard up --build

# MinIO for S3-compatible evidence storage
docker compose -f infra/docker-compose.yml --env-file .env.example --profile minio up --build

# Dashboard + MinIO together
docker compose -f infra/docker-compose.yml --env-file .env.example --profile full up --build
```

With MinIO, set storage env (see `.env.example`):

```bash
ASSURANCE_STORAGE_ENABLED=true
ASSURANCE_STORAGE_ENDPOINT=http://minio:9000
ASSURANCE_STORAGE_ACCESS_KEY_ID=minioadmin
ASSURANCE_STORAGE_SECRET_ACCESS_KEY=minioadmin
ASSURANCE_STORAGE_BUCKET=eu-ai-assurance-evidence
ASSURANCE_STORAGE_PATH_STYLE=true
```

Helper: `scripts/compose-with-minio.sh`.

### Flyway on a fresh volume

On first API boot against empty Postgres, Flyway applies `V1`–`V20` under `services/api/src/main/resources/db/migration` plus postgres-only `V4` (pgvector index is best-effort if the extension is missing).

Verify:

```bash
curl -fsS http://localhost:8080/actuator/health
# Local H2 runs seed demo users (`compliance@example.com`, `admin@example.com`, …) because
# `assurance.bootstrap.seed-demo-users=true` in the default profile. The `postgres` profile never seeds them and
# refuses to start if an old database still has them with the development password — run
# `scripts/disable-seeded-users.sql` once on such databases.
```

### Resource notes

| Component | Suggested memory |
|---|---|
| Postgres | ≥ 512 MB |
| API (local-hash embeddings) | ≥ 512 MB |
| API (`djl-sentence` / ONNX) | **1–2 GB** (the production image carries the model; other runs download it once) |
| MinIO | ≥ 256 MB |
| Dashboard container | ≥ 512 MB |

The API image sets `-XX:MaxRAMPercentage=60.0` so the JVM leaves room for ONNX Runtime, Tika, thread stacks and the
code cache outside the heap, and `-XX:+ExitOnOutOfMemoryError` so an out-of-memory JVM restarts instead of limping on.
The API drains in-flight requests for up to 30 s on shutdown (`server.shutdown=graceful`); Compose gives it 40 s.

### Evidence / DJL / object store path

| Mode | Config | Behavior |
|---|---|---|
| Dev H2 | `local-hash` | Portable text embeddings; no DJL |
| Compose default | `EVIDENCE_EMBEDDING_PROVIDER=local-hash` (in `.env.example`) | Fast local stack; no HF download |
| Postgres profile (app default when env unset) | `djl-sentence` | DJL + ONNX all-MiniLM-L6-v2, pinned to one Hugging Face revision and SHA-256-checked before loading. The production image bakes the files into `/opt/models` (`ASSURANCE_EVIDENCE_DJL_MODEL_DIR`), so it starts without reaching Hugging Face; elsewhere they are downloaded once into `assurance.evidence.djl.model-dir` (default: the temp directory) and reused |
| Object store off | `ASSURANCE_STORAGE_ENABLED=false` | Upload API returns synthetic `s3://` URIs; content still ingested from request body when provided |
| Object store on | MinIO or real S3 | `FileStorageService` PUT/GET with path-style when endpoint set |

To exercise DJL in Compose, set `EVIDENCE_EMBEDDING_PROVIDER=djl-sentence` and allow 1–2 GB RAM plus outbound HTTPS to Hugging Face.

Smoke (API up, password login):

```bash
./scripts/compose-evidence-smoke.sh
```

---

## 2. Production topology (Oracle A1 + Cloudflare)

Target topology from the launch plan. Until the cutover (section 4.E) the live site still runs on Vercel (section 4.C).

```text
[ Browser / CI bots ]
    │ HTTPS
    ▼
[ Cloudflare — DNS, WAF rate limit, Turnstile, Web Analytics ]
    │ Cloudflare Tunnel (outbound from the VM; no inbound ports)
    ▼
[ Oracle Cloud Always Free A1 VM, EU home region — Docker Compose, /opt/aos ]
    ├── cloudflared ──► dashboard:3000   (Next.js: marketing, app, BFF, CI and webhook passthrough)
    │                       │ http://api:8080 (internal network only)
    │                       ▼
    ├── api (Spring Boot, evgraph CLI baked in) ──► postgres (pgvector, volume aos_pgdata)
    │                                          └──► R2 bucket aos-evidence (EU)
    └── backup (pg_dump → age → R2 bucket aos-backups, EU)

[ GitHub Actions ] ── CI green on main ──► images.yml ──► GHCR aos-{api,dashboard,backup}:{sha,main}
[ VM aos-update.timer ] ── every 5 min ──► pull, pre-deploy backup, health-gated roll-forward
```

| Piece | File | Notes |
|---|---|---|
| Stack | `infra/docker-compose.prod.yml` | No host ports; each service gets only its own variables |
| Environment | `infra/.env.prod.example` → `/opt/aos/.env` (`chmod 600`) | Every variable the stack reads |
| Images | `.github/workflows/images.yml` | `linux/arm64`; `NEXT_PUBLIC_*` from repository variables |
| Deploys | `infra/deploy/aos-update.{sh,service,timer}` | Rejected images are remembered, not retried |
| Backups | `infra/backup/` | Runbook and drill log: [OPS.md](./OPS.md#production-backups-rpo-24-h-rto-4-h-target) |
| Uptime | `GET /api/health` on the dashboard | 200 when the API is UP, 503 otherwise; no details |
| Errors | Sentry (`SENTRY_DSN`) | Off when blank; personal data and traces always off |

Staging uses the same stack on a GCP `t2a-standard-2` Spot VM until 22 Nov 2026, then runs locally.

Terraform under `infra/terraform/` is a **skeleton** (validate/fmt only) and is not used by this topology.

### First install on a VM (owner)

1. Ubuntu 24.04 aarch64, SSH key only, `ufw` allowing only 22, Docker Engine + Compose v2, `/opt/aos` owned by `ubuntu`.
2. GHCR access: make the three `aos-*` packages public, or `docker login ghcr.io` with a read-only token.
3. Copy files:
   `scp infra/docker-compose.prod.yml infra/deploy/aos-update.sh ubuntu@<vm>:/opt/aos/` and
   `scp infra/deploy/aos-update.service infra/deploy/aos-update.timer ubuntu@<vm>:/tmp/`
4. Create `/opt/aos/.env` from `infra/.env.prod.example` (`chmod 600`). Optional `/opt/aos/deploy.env` with
   `DEPLOY_ALERT_URL=<Discord webhook>`.
5. `cd /opt/aos && docker compose -f docker-compose.prod.yml --env-file .env up -d`, then wait for `docker compose ps`
   to show `api` healthy (first boot downloads the embedding model; allow 3 minutes).
6. `sudo mv /tmp/aos-update.* /etc/systemd/system/ && chmod +x /opt/aos/aos-update.sh && sudo systemctl daemon-reload && sudo systemctl enable --now aos-update.timer`
7. Verify: `https://app.devernance.com/api/health` returns 200, sign-in works, `systemctl list-timers` lists `aos-update.timer`.

---

## 3. Environment matrix

| Variable | API | Dashboard (Vercel) | Notes |
|---|---|---|---|
| `EVAL_CALLBACK_SECRET` | **Required** | — | Fail closed if empty in shared envs |
| `AUDIT_CHAIN_SECRET` | **Required** (prod) | — | Hash-chain HMAC; rotate with care |
| `DATABASE_URL` / `USERNAME` / `PASSWORD` | **Required** | — | Postgres profile |
| `SPRING_PROFILES_ACTIVE` | `postgres` | — | |
| `EVIDENCE_EMBEDDING_PROVIDER` | `djl-sentence` recommended | — | Or `local-hash` for cheap envs |
| `ASSURANCE_STORAGE_*` | Optional | — | S3/MinIO; see §1 |
| `ASSURANCE_API_BASE_URL` | — | **Required** | Server-side only (BFF) |
| `NEXT_PUBLIC_SITE_URL` | — | Recommended | Canonical SEO URL |
| `NEXT_PUBLIC_GA_MEASUREMENT_ID` | — | Optional | GA4 |
| OAuth client ids/secrets | Dashboard / IdP | Dashboard | See Part 4 smoke doc |
| `DISCORD_WEBHOOK_URL` or `DISCORD_DEMO_WEBHOOK_URL` | — | Optional (required for live demo form delivery) | Server-only; never commit tokens |

Full template: [`.env.example`](../.env.example). Secrets handling: [NFR.md](./NFR.md#encryption-and-secrets-nfr).

---

## 4. Deploy order

### A. Database

1. Provision Postgres 16+ (prefer same region as API).
2. Create role/database; store password in a secret manager.
3. Optionally enable `CREATE EXTENSION vector;` for HNSW (migration skips gracefully if absent).

### B. API

1. Build image: `docker build -f infra/Dockerfile -t devernance-api:$TAG .`
2. Push to your registry.
3. Set env from secret manager (table above).
4. Expose port 8080; probes:
   - Liveness: `GET /actuator/health/liveness`
   - Readiness: `GET /actuator/health/readiness`
5. **Migrations:** Flyway runs automatically on application startup. Do not run ad-hoc SQL that races the API.
6. Confirm `GET /actuator/health` is UP before pointing the dashboard at the API.

### C. Dashboard (Vercel — current, until the cutover in E)

**Production project (canonical):** `eu-ai-assurance`

**Live domains:** `https://euassuranceai.souravamseekar.com`, `https://eu-ai-assurance.vercel.app`

**Git root directory:** `apps/dashboard` (monorepo `SVamseekar/devernance`, production branch `main`)

There is a second Vercel project `eu-ai-assurance-os` linked to the same GitHub repo for historical monorepo hooks. It is configured with **Ignore Build Step = `exit 1`** so it never builds (avoids burning Hobby build quota / rate limits on every PR). Do **not** use it for production.

#### One-time project setup

1. Project **eu-ai-assurance** → Git: connect `SVamseekar/devernance` → Root Directory **`apps/dashboard`** → Framework Next.js.
2. Project **eu-ai-assurance-os** (stub): set Ignore Build Step to `exit 1`, or disconnect Git entirely.
3. Env on **eu-ai-assurance** (Production + Preview as needed):
   - `ASSURANCE_API_BASE_URL=https://api.example.com` (server-side only)
   - `NEXT_PUBLIC_SITE_URL=https://euassuranceai.souravamseekar.com`
   - Optional `NEXT_PUBLIC_GA_MEASUREMENT_ID`
   - Optional `DISCORD_DEMO_WEBHOOK_URL` / `DISCORD_WEBHOOK_URL` for `/request-demo`
   - OAuth client ids/secrets + `OAUTH_REDIRECT_BASE_URL` (see `docs/oauth-production-smoke-test.md`)
4. Domains: attach `euassuranceai.souravamseekar.com` to **eu-ai-assurance** only.

#### Deploy

**Automatic:** push/merge to `main` triggers production on `eu-ai-assurance` (root `apps/dashboard`).

**Manual (when git hooks lag or quota recovered):**

```bash
cd apps/dashboard
vercel --prod --yes
```

#### Verify

1. Login UI: `https://euassuranceai.souravamseekar.com/login` (brand panel + Google/Microsoft + work email).
2. Dashboard BFF: sign-in + `/api/proxy` against the API.
3. Marketing: `/privacy`, `/terms`, `/refunds`, `/request-demo` return 200.

#### Rate limits / failed Vercel checks on GitHub PRs

Hobby accounts can hit **build rate limits** when many Dependabot/preview deploys fire. Required GitHub status checks for this repo are **CI jobs only** (API tests, Dashboard, Secret scan, Acceptance) — not Vercel. If Vercel shows “Deployment rate limited”, re-run `vercel --prod` later or wait for the window; do not treat that as a merge blocker.

### D. Post-deploy checks

```bash
curl -fsS https://api.example.com/actuator/health
# Optional CI gate (docs/OPS.md):
ASSURANCE_API_BASE=https://api.example.com \
API_KEY=... SYSTEM_ID=... ./scripts/ci-release-gate.sh
```

### E. Cutover from Vercel and the Dell lab (owner)

Product domain: `devernance.com`, chosen on 18 October 2026 and not registered yet. The full switch checklist is in
[BRAND.md](./BRAND.md#domain-day-checklist).

1. With production healthy on Oracle, add OAuth redirect URIs
   `https://app.devernance.com/api/auth/oauth/{google|microsoft}/callback` (keep the old ones until step 2).
2. Redirect `euassuranceai.souravamseekar.com/*` → `https://devernance.com/$1` (301, path and query kept).
3. Start production with a fresh database unless a real customer used the old system (the Dell database holds demo
   data). To migrate: `pg_dump` the Dell Postgres and restore with the [OPS.md](./OPS.md#restore-from-backup) runbook.
4. After 7 days of clean redirects, remove both Vercel projects and stop the Dell lab tunnel.
5. Update the README "Live product" link.

---

## 5. Migration order & rollback

### Forward

1. Ship API image that includes the new Flyway versions.
2. Deploy API → Flyway applies pending `V*` scripts in one transaction per version.
3. Deploy dashboard only if UI depends on new API fields.

### Rollback

| Layer | Strategy |
|---|---|
| Dashboard (VM) | Set `AOS_TAG=<previous sha>` in `/opt/aos/.env`, then `docker compose ... up -d dashboard` |
| API container | Automatic when the new image is unhealthy (`aos-update.sh`); by hand: `AOS_TAG=<previous sha>` |
| Database | **Prefer expand/contract.** Flyway undo is not wired. Destructive down-migrations are not provided — restore from snapshot/PITR if a migration corrupts data |
| Secrets | Rotate in secret manager; rolling restart API so all tasks see the new value |

Never force-push Flyway `schema_history` repairs in production without a backup.

---

## 6. Self-host dashboard (optional)

```bash
docker build -f infra/Dockerfile.dashboard \
  --build-arg NEXT_PUBLIC_SITE_URL=https://app.example.com \
  -t devernance-dashboard .

docker run -p 3000:3000 \
  -e ASSURANCE_API_BASE_URL=https://api.example.com \
  devernance-dashboard
```

Requires `DOCKER_BUILD=1` standalone output (set in the Dockerfile). In production this image runs behind Cloudflare Tunnel (section 2).

---

## 7. Terraform

```bash
cd infra/terraform
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
```

No cloud credentials required for validation. Do not `apply` the skeleton as-is.

---

## 8. Security checklist (deploy)

- [ ] TLS on all public endpoints
- [ ] Secrets only from env / secret manager (gitleaks clean)
- [ ] Metrics/prometheus not public without authz network policy ([OPS.md](./OPS.md))
- [ ] Eval callback secret non-empty
- [ ] Audit chain secret set and backed up for verification
- [ ] Bootstrap passwords (`dev-local-password-only`) **disabled or rotated** outside local Compose
