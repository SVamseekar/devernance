# Operations — CI release gate & observability

Part 8 surface for deploy pipelines and operators. This is **not** a full Grafana stack; it is the product metrics + CI contract shipped with the API.

## CI/CD release gate

### Endpoint

```http
GET /api/v1/ci/release-gate?systemId={uuid}
Authorization: (prefer X-Api-Key for bots)
```

Stable JSON contract:

| Field | Type | Notes |
|---|---|---|
| `systemId` | UUID | Requested system |
| `systemName` | string | Display name |
| `decision` | `PASS` \| `REVIEW` \| `BLOCKED` | Same engine as UI gate |
| `blockers` | string[] | Empty when PASS |
| `evalScore` | int | Snapshot from registry |
| `evidenceCoverage` | int | Snapshot from registry |
| `dataContractStatus` | enum | HEALTHY / WARNING / BREACH |
| `riskClass` | enum | |
| `exitCode` | 0 \| 1 \| 2 | CLI convenience |
| `content` | string | One-line summary for logs |

**CLI exit mapping** (`scripts/ci-release-gate.sh`):

| Decision | Exit |
|---|---|
| PASS | **0** |
| BLOCKED | **1** |
| REVIEW | **2** |
| Missing env | 3 |
| HTTP / parse / transport | 4 |

Optional `FAIL_ON_REVIEW=1` maps REVIEW → exit 1 for strict deploys.

### API key for CI bots (recommended)

1. Create a service-account user in the tenant (role with release-gate read — any role per `ROLE_MATRIX.md`).
2. Insert an `api_keys` row with `key_hash = SHA-256(raw_key)` bound to that user/tenant (admin tooling / SQL; production should use a secret manager for the raw key).
3. In CI secrets store:
   - `ASSURANCE_API_BASE` — API origin, no trailing slash required
   - `ASSURANCE_API_KEY` / `API_KEY` — raw key
   - `SYSTEM_ID` — system under gate
4. Never use interactive user passwords or long-lived JWTs in CI when an API key is available.
5. Example workflow: `.github/workflows/release-gate-example.yml` (`workflow_call` / `workflow_dispatch`).

```bash
ASSURANCE_API_BASE=https://api.example.com \
API_KEY="$ASSURANCE_API_KEY" \
SYSTEM_ID="$SYSTEM_ID" \
./scripts/ci-release-gate.sh
```

Local H2 bootstrap seeds a **dev-only** key (`00000000-0000-0000-0000-000000000a01`) for the default compliance user — not for production.

Equivalent product UI path: `GET /api/v1/systems/{id}/release-gate` (same decision engine; CI endpoint adds machine fields).

## Actuator & Prometheus

| Endpoint | Auth | Purpose |
|---|---|---|
| `/actuator/health` (+ liveness/readiness) | Public | Probes |
| `/actuator/info` | Operator admin (API key / JWT) | Build info |
| `/actuator/metrics` | Operator admin (API key / JWT) | Micrometer snapshot |
| `/actuator/prometheus` | Operator admin (API key / JWT) | Prometheus text scrape |

"Operator admin" means a user with the `ADMIN` role in the platform operator tenant
(`ASSURANCE_OPERATOR_TENANT_ID`). Everyone else, including customer admins and the public demo, gets `404`:
these endpoints describe the whole platform, not one workspace (`OperatorActuatorFilter`). The dashboard BFF only
forwards `/api/v1/*` paths, so none of these are reachable through the public site.

`management.endpoints.web.exposure.include` defaults to `health,info,metrics,prometheus`.

**Do not** expose authenticated metrics publicly on the internet without network policy or mTLS. Prefer private scrape networks and short-lived scrape credentials.

### Product metrics (Part 8)

| Metric | Type | Tags | Meaning |
|---|---|---|---|
| `assurance.release_gate.decision` | counter | `decision` | Gate calculations served (UI + CI) |
| `assurance.api.evidence.query` | timer | `operation=answer` | RAG answer latency (Part 10; plan “evidence query latency”) |
| `assurance.api.registry.read` | timer | `operation=list\|get` | Registry read latency |
| `assurance.audit.append` | counter | — | Immutable audit appends |
| `assurance.auth.login.failures` | counter | `reason=invalid_credentials` | Failed password logins (no user enumeration tag) |
| `assurance.eval.run.*` | counter | various | Eval worker (existing) |

### Correlation IDs

`CorrelationFilter` sets MDC `requestId` from `X-Request-Id` (or generates one) and echoes the header. Non-default profiles use JSON logging with `requestId` via Logstash encoder (`logback-spring.xml`).

## Reading p95 (honest NFR)

See `docs/NFR.md`. Example Prometheus:

```promql
histogram_quantile(0.95,
  sum(rate(assurance_api_registry_read_seconds_bucket[5m])) by (le))
```

Unit tests only assert that **timers/counters exist after traffic** — they never claim p95 &lt; 300 ms.

## Local Compose stack (Part 9)

```bash
docker compose -f infra/docker-compose.yml --env-file .env.example up --build
```

- Health: `GET /actuator/health` (and liveness/readiness under `/actuator/health/*`)
- Secrets template: root `.env.example` (`EVAL_CALLBACK_SECRET`, `AUDIT_CHAIN_SECRET`, DB, storage)
- Deploy / rollback / Vercel matrix: `docs/DEPLOYMENT.md`
- Terraform skeleton validate (no cloud creds): `./scripts/terraform-validate.sh`

## Production backups (RPO 24 h, RTO 4 h target)

The `backup` service in `infra/docker-compose.prod.yml` runs `pg_dump` → gzip → [age](https://age-encryption.org)
→ rclone into the EU-jurisdiction R2 bucket `aos-backups`:

| Kind | When | R2 prefix | Retention (R2 lifecycle rule) | Heartbeat |
|---|---|---|---|---|
| `daily` | 02:15 UTC | `daily/` | 15 days | yes |
| `weekly` | Sundays 02:45 UTC | `weekly/` | 60 days | yes |
| `pre-deploy` | before the updater starts a new API image | `pre-deploy/` | 15 days | no |

- Backups are encrypted to the public key in `BACKUP_AGE_RECIPIENT`. The private key (`aos-backup.key`) lives in
  the password manager and offline. It is never on the VM.
- Every successful daily or weekly backup pings `BACKUP_HEARTBEAT_URL`. The UptimeRobot heartbeat monitor alerts when no
  ping arrives for 26 hours (expired R2 credentials, full disk, stopped container).
- A dump under 1 KB is refused and not uploaded.
- Run one by hand: `docker compose -f docker-compose.prod.yml --env-file .env exec backup backup.sh daily`.
- Tests: `infra/test/backup_test.sh` (behaviour, stubbed tools) and `infra/test/backup_integration_test.sh` (real
  Postgres → S3 → restore round trip).

### Restore from backup

Run from `/opt/aos`. `C` stands for `docker compose -f docker-compose.prod.yml --env-file .env`.

1. Stop writers: `C stop api dashboard`
2. Recreate the database from the maintenance database (you cannot drop the database you are connected to):
   `C exec postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "drop database \"$POSTGRES_DB\"" -c "create database \"$POSTGRES_DB\""'`
3. Pick the object: `C exec backup sh -c 'RCLONE_CONFIG_R2_TYPE=s3 RCLONE_CONFIG_R2_PROVIDER=Cloudflare RCLONE_CONFIG_R2_ENDPOINT=$BACKUP_R2_ENDPOINT RCLONE_CONFIG_R2_ACCESS_KEY_ID=$BACKUP_R2_ACCESS_KEY_ID RCLONE_CONFIG_R2_SECRET_ACCESS_KEY=$BACKUP_R2_SECRET_ACCESS_KEY rclone lsf -R r2:$BACKUP_R2_BUCKET'`
4. Copy the private key in, restore, and remove the key:
   `docker cp aos-backup.key "$(C ps -q backup)":/tmp/k`
   `C exec backup restore.sh daily/<file> /tmp/k`
   `C exec backup rm -f /tmp/k`
5. Start the app: `C up -d api dashboard`. Wait for `C ps` to show `api` healthy.
6. Verify: sign in on the site, open an evidence pack, and check the audit chain with
   `GET /api/v1/audit-events/verify-chain` while signed in → `"valid": true`.

If the restore follows a failed deploy, set `AOS_TAG` in `.env` to the last good image SHA before step 5, because the
newer image's Flyway migrations may not run against the older schema.

### Restore drill log

Drill on staging before launch, then monthly. Take a backup, destroy the volume (`C down && docker volume rm aos_pgdata`),
restore with the runbook above, and time it.

| Date | Environment | Backup used | Duration | Result |
|---|---|---|---|---|
| | | | | |

## Regulatory change monitoring (Part 14)

Near-real-time **poll** every `assurance.reg-monitor.poll-interval-ms` (default 60s
scheduler tick; each source has its own `poll_interval_seconds`). This is not
magical real-time law and not an official Official Journal subscription.

- Curated bootstrap fixtures load on startup when the feed is empty / network blocked
  (`assurance.reg-monitor.bootstrap-fixtures=true`).
- Remote EUR-Lex / OJ sources are seeded **disabled**; enable only after reviewing
  ToS and rate limits. Fetches are SSRF-safe (DNS pin, HTTPS only, no redirects).
- Impact hints prefer `UNCERTAIN` and **never** auto-change risk class or control status.
- Dashboard: `/reg-monitor`. APIs: `GET /api/v1/reg-monitor/items`,
  `POST /api/v1/reg-monitor/items/{id}/review`,
  `GET /api/v1/systems/{id}/reg-monitor/relevant`.

## Dashboard ops surface

`/command` summarizes:

- Release decision counts (blocked / review)
- Open contract breaches
- Open approval workflows (ops widget)
- Recent audit activity

API connectivity pill remains on Evidence (and similar) using existing health/proxy checks — **no secrets** in client health payloads.
