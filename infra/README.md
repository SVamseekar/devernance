# Infrastructure (Part 9)

Local Docker stack and Terraform skeleton for Devernance.

## One-command local stack

From the **repository root**:

```bash
docker compose -f infra/docker-compose.yml --env-file .env.example up --build
```

This starts:

| Service | Port | Notes |
|---|---|---|
| `postgres` | 5432 | `pgvector/pgvector:pg16` |
| `api` | 8080 | Multi-stage image from `infra/Dockerfile`, profile `postgres` |

Health:

```bash
curl -fsS http://localhost:8080/actuator/health
```

Local compose users must copy `.env.example` → `.env` and fill values (specifically required random secrets of at least 32 characters for `EVAL_CALLBACK_SECRET`, `AUDIT_CHAIN_SECRET`, `JWT_KEY_ENCRYPTION_SECRET`, and `OAUTH_STATE_SECRET`).

### Optional profiles

```bash
# Self-host dashboard on :3000 (production UI is still Vercel)
docker compose -f infra/docker-compose.yml --env-file .env.example --profile dashboard up --build

# MinIO S3-compatible store on :9000 (console :9001)
docker compose -f infra/docker-compose.yml --env-file .env.example --profile minio up --build

# Both extras
docker compose -f infra/docker-compose.yml --env-file .env.example --profile full up --build
```

MinIO + storage-enabled API helper:

```bash
./scripts/compose-with-minio.sh
```

Evidence upload → query smoke (API must be healthy):

```bash
./scripts/compose-evidence-smoke.sh
```

### Images

| File | Purpose |
|---|---|
| `Dockerfile` | API multi-stage Maven → non-root JRE 17, healthcheck |
| `Dockerfile.dashboard` | Optional Next.js standalone self-host |

### Resource guidance

- **API + DJL:** allow **1–2 GB** RAM; first boot may download ONNX model weights.
- **Postgres:** ≥ 512 MB.
- JVM uses `-XX:MaxRAMPercentage=75.0` inside the API container.

Full runbook: [`docs/DEPLOYMENT.md`](../docs/DEPLOYMENT.md).

## Production and staging stack

`docker-compose.prod.yml` runs one environment (Oracle A1 production or the GCP staging VM) from `/opt/aos`:

```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

| Service | Image | Memory | Notes |
|---|---|---|---|
| `postgres` | `pgvector/pgvector:0.8.6-pg16` | 1.5 GB | volume `aos_pgdata` |
| `api` | `ghcr.io/svamseekar/aos-api:${AOS_TAG}` | 3 GB | `postgres` profile; reachable only from the dashboard |
| `dashboard` | `ghcr.io/svamseekar/aos-dashboard:${AOS_TAG}` | 768 MB | BFF proxies to `http://api:8080` |
| `cloudflared` | `cloudflare/cloudflared:2026.9.3` | 128 MB | outbound tunnel; public hostnames point at `dashboard:3000` only |
| `backup` | `ghcr.io/svamseekar/aos-backup:${AOS_TAG}` | 256 MB | nightly/weekly encrypted dumps to R2 (`docs/OPS.md`) |

- No service publishes a host port. Open only TCP 22 on the VM.
- Each service gets only the variables it uses. Fill `/opt/aos/.env` from `.env.prod.example` (`chmod 600`).
- All services restart `unless-stopped`; Docker logs rotate at 10 MB × 5 per service.
- Contract test (no daemon needed): `infra/test/compose_contract_test.sh`.

### Images and deploys

- `.github/workflows/images.yml` builds `aos-api`, `aos-dashboard` and `aos-backup` for `linux/arm64` after CI passes on
  `main`, and pushes `:<sha>` and `:main` to GHCR. Public build values (`NEXT_PUBLIC_*`) come from repository variables.
- `deploy/aos-update.sh` runs every 5 minutes (`deploy/aos-update.timer`). For a new API image it takes a `pre-deploy`
  backup, starts the new API, and keeps it only when the container turns healthy within 5 minutes. Otherwise it restores
  the previous image, writes the rejected id to `/opt/aos/.rejected-api-image` (so it is not retried), and posts to the
  Discord webhook in `DEPLOY_ALERT_URL` (`/opt/aos/deploy.env`). Dashboard and backup images roll forward without a backup.
- Pin a build: set `AOS_TAG=<sha>` in `/opt/aos/.env`; the updater then only follows that tag.

### Tests

```bash
infra/test/run.sh                      # shellcheck, compose contract, backup and updater behaviour (no Docker daemon)
RUN_DOCKER_TESTS=1 infra/test/run.sh   # adds the real Postgres → S3 → restore round trip (CI runs this)
```

## Terraform skeleton

```bash
cd infra/terraform
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
```

Or without a host install:

```bash
docker run --rm -v "$PWD":/work -w /work/infra/terraform hashicorp/terraform:1.9 \
  sh -c 'terraform fmt -check -recursive && terraform init -backend=false && terraform validate'
```

**Not applied by default** — no real cloud credentials in CI. See [`terraform/README.md`](./terraform/README.md).

## Layout

```text
infra/
  docker-compose.yml
  docker-compose.override.yml
  docker-compose.prod.yml   # production / staging stack
  .env.prod.example
  Dockerfile
  Dockerfile.dashboard
  backup/             # backup sidecar image (pg_dump → age → R2)
  test/               # infra tests
  terraform/          # network, database, secrets, compute stubs
  README.md
```
