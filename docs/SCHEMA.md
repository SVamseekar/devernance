# Data Model

Schema as implemented by Flyway under `services/api/src/main/resources/db/migration/` (H2 + Postgres shared) plus postgres-only `db/postgresql/V4` (pgvector HNSW).

## Migration inventory

| Version | Topic |
|---|---|
| V1 | tenants, users, ai_systems, eval_runs (legacy slice), audit_events |
| V2 | evidence_documents, evidence_chunks, evidence_queries |
| V3 | evidence production hardening (content SHA-256, provider, metadata) |
| V4 | **postgres only** — pgvector HNSW index (skipped gracefully if extension missing) |
| V5 | eval_datasets, eval_runs worker durability columns |
| V6 | data_contracts, drift_events |
| V7 | api_keys |
| V8 | approval_workflows, approval_stages |
| V9 | password_hash, refresh_tokens, signing_keys |
| V10 | workflow reviewer assignment, oversight_evidence, workflow_notifications |
| V11 | controls, system_controls |
| V12 | registry metadata (vendor, model, sector, data sources, …) |
| V13 | audit hash-chain columns + retain_until |
| V14 | oauth_provider / oauth_subject on users |
| V15 | obligation_rules, determination_runs, determination_obligations |
| V16 | reg_sources, reg_items, reg_impact_hints, reg_item_reviews |
| V17 | user invites, conformity dossiers (assisted Act-as-code; not certification) |
| V18 | pinned legal corpus: instruments, provisions, mapping proposals |
| V19 | proposal control mode, system change scope |
| V20 | evidence applicability and exceptions |
| V21 | audit chain heads (one serialised chain per tenant) |
| V22 | proposal integrity (validation, locking, indexes) |
| V23 | self-serve accounts (email verification, password reset tokens); postgres V23.1 lower(email) unique |
| V24 | unique user email |
| V25 | API key management (prefixed keys, names, revocation, last use) |
| V26 | billing: subscriptions, webhook events, gate-run counters |
| V27 | DPA acceptance |
| V28 | Devernance rename (stored identifiers) |
| V29 | query indexes: `ai_systems(tenant_id, created_at)`, `audit_events(tenant_id, system_id, created_at)`, `refresh_tokens(user_id)` |

Canonical counts: `docs/METRICS_CANONICAL.md` (Flyway **V1–V20** + postgres V4); V21–V29 were added after that freeze.

---

## Core Tables

```sql
create table tenants (
  id uuid primary key,
  name varchar(255) not null,
  plan varchar(64) not null,
  data_region varchar(64) not null,
  created_at timestamptz not null
);

create table users (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  email varchar(320) not null,
  role varchar(64) not null,
  password_hash varchar(255),           -- V9; nullable for OAuth-only users
  oauth_provider varchar(32),           -- V14
  oauth_subject varchar(255),           -- V14
  created_at timestamptz not null,
  unique (tenant_id, email)
);
-- unique (oauth_provider, oauth_subject) where both non-null (V14)

create table ai_systems (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  name varchar(255) not null,
  owner varchar(255) not null,
  purpose varchar(2048) not null,
  risk_class varchar(32) not null check (risk_class in ('MINIMAL', 'LIMITED', 'HIGH', 'PROHIBITED')),
  risk_basis varchar(2048) not null,
  deployment_region varchar(64) not null,
  evidence_coverage integer not null,
  eval_score integer not null,
  data_contract_status varchar(32) not null check (data_contract_status in ('HEALTHY', 'WARNING', 'BREACH')),
  release_decision varchar(32) not null check (release_decision in ('PASS', 'REVIEW', 'BLOCKED')),
  open_gaps varchar(4096) not null,
  vendor_name varchar(255),             -- V12
  model_name varchar(255),
  model_version varchar(128),
  data_sources_json text not null default '[]',
  sector varchar(128),
  decision_impact varchar(512),
  affected_users_json text not null default '[]',
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create table controls (
  id uuid primary key,
  code varchar(64) not null unique,
  name varchar(255) not null,
  description varchar(2048) not null,
  applies_to_risk_class varchar(128) not null,
  category varchar(64) not null
);

create table system_controls (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  system_id uuid not null references ai_systems(id),
  control_id uuid not null references controls(id),
  status varchar(32) not null check (status in ('PASS', 'REVIEW', 'BLOCKED')),
  evidence_required boolean not null default true,
  reviewer_id uuid references users(id),
  notes varchar(2048),
  updated_at timestamptz not null,
  unique (tenant_id, system_id, control_id)
);
```

## Auth and API keys

```sql
create table api_keys (
  id uuid primary key,
  key_hash varchar(64) not null unique,  -- SHA-256 of raw key; never store raw
  tenant_id uuid not null references tenants(id),
  user_id uuid not null references users(id),
  created_at timestamptz not null
);

create table refresh_tokens (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  user_id uuid not null references users(id),
  token_hash varchar(64) not null unique,
  expires_at timestamptz not null,
  created_at timestamptz not null,
  revoked_at timestamptz,
  replaced_by_token_hash varchar(64)
);

create table signing_keys (
  kid uuid primary key,
  algorithm varchar(16) not null,
  public_key_pem text not null,
  private_key_pem text not null,
  created_at timestamptz not null,
  active boolean not null default false
);
```

## Evidence Tables

```sql
create table evidence_documents (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  system_id uuid not null references ai_systems(id),
  type text not null,
  title text not null,
  source_uri text not null,
  checksum text not null,
  chunk_count int not null,
  ingestion_status text not null,
  created_at timestamptz not null default now()
);

create table evidence_chunks (
  id uuid primary key,
  document_id uuid not null references evidence_documents(id),
  ordinal int not null,
  section_ref text,
  content text not null,
  content_sha256 text,
  embedding text not null,
  embedding_provider text not null default 'local-hash',
  metadata_json text not null default '{}'
);

create table evidence_queries (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  system_id uuid not null references ai_systems(id),
  question text not null,
  answer text not null,
  confidence double precision not null,
  citations_json text not null,
  created_by uuid not null references users(id),
  created_at timestamptz not null default now()
);
```

The portable schema stores deterministic local embeddings as text so the same core migrations validate against H2 and PostgreSQL. The PostgreSQL profile adds a `pgvector` migration path with HNSW cosine index for production. A non-local embedding provider should ship a matching vector dimension when dimensions change.

## Eval Tables

```sql
create table eval_datasets (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  name text not null,
  version text not null,
  sample_count int not null,
  golden boolean not null default false,
  created_at timestamptz not null default now(),
  unique (tenant_id, name, version)
);

create table eval_runs (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  system_id uuid not null references ai_systems(id),
  dataset_id uuid references eval_datasets(id),
  dataset text not null,
  model_version text not null,
  prompt_version text not null,
  threshold numeric not null,
  metrics_json text not null default '{}',
  status text not null check (status in ('queued', 'running', 'completed', 'failed')),
  release_decision text not null check (release_decision in ('PASS', 'REVIEW', 'BLOCKED')),
  created_at timestamptz not null default now(),
  queued_at timestamptz not null default now(),
  started_at timestamptz,
  completed_at timestamptz,
  failed_at timestamptz,
  worker_attempts int not null default 0,
  max_attempts int not null default 3,
  failure_reason text
);
```

Legacy `dataset` text remains for API compatibility; new runs bind `dataset_id`. Worker columns make background dispatch durable across restarts.

## Data Contract Tables

```sql
create table data_contracts (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  system_id uuid not null references ai_systems(id),
  name text not null,
  owner text not null,
  version text not null,
  status text not null check (status in ('HEALTHY', 'WARNING', 'BREACH')),
  coverage int not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (tenant_id, system_id, name, version)
);

create table drift_events (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  contract_id uuid not null references data_contracts(id),
  severity text not null check (severity in ('INFO', 'WARNING', 'BREACH')),
  field text,
  description text not null,
  status text not null check (status in ('OPEN', 'ACKNOWLEDGED', 'RESOLVED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

## Workflow Tables

```sql
create table approval_workflows (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  system_id uuid not null references ai_systems(id),
  trigger varchar(64) not null,  -- SYSTEM_CREATED | EVAL_REGRESSION | CONTRACT_BREACH | …
  status varchar(32) not null,   -- OPEN | APPROVED | REJECTED | SUPERSEDED
  opened_at timestamptz not null,
  closed_at timestamptz,
  created_at timestamptz not null
);

create table approval_stages (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  workflow_id uuid not null references approval_workflows(id),
  stage_order integer not null,
  stage_type varchar(64) not null,  -- ENG_LEAD_REVIEW | COMPLIANCE_REVIEW | LEGAL_SIGNOFF
  required_role varchar(64) not null,
  status varchar(32) not null,
  actor_id uuid references users(id),
  assigned_reviewer_id uuid references users(id),
  rationale varchar(2048),
  oversight_evidence varchar(2048),
  notification_sent_at timestamptz,
  acted_at timestamptz,
  created_at timestamptz not null
);

create table workflow_notifications (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  workflow_id uuid not null references approval_workflows(id),
  stage_id uuid references approval_stages(id),
  recipient_id uuid references users(id),
  event_type varchar(64) not null,
  message varchar(512) not null,
  read_at timestamptz,
  created_at timestamptz not null
);
```

## Audit (hash-chained)

```sql
create table audit_events (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  system_id uuid references ai_systems(id),
  actor_id uuid not null references users(id),
  event_type varchar(128) not null,
  resource_type varchar(128) not null,
  resource_id varchar(255),
  payload_json text not null,
  created_at timestamptz not null,
  prev_event_hash varchar(64),   -- V13
  event_hash varchar(64),        -- V13 HMAC-SHA-256 chain
  retain_until timestamptz       -- V13 ≥ 7y default
);
```

There is **no** public API to update or delete audit rows. Verify chain integrity via `GET /api/v1/audit/verify` and `GET /api/v1/audit-events/verify-chain`.

## Assisted obligation determination (Part 12 / V15)

Not legal advice; ruleset-driven suggestions only.

```sql
create table obligation_rules (
  id uuid primary key,
  code varchar(64) not null,
  title varchar(255) not null,
  description varchar(2048) not null,
  legal_refs varchar(512) not null,
  applies_when text not null,
  severity varchar(32) not null check (severity in ('LOW', 'MEDIUM', 'HIGH')),
  control_codes text not null,
  ruleset_version varchar(32) not null,
  active boolean not null default true,
  unique (code, ruleset_version)
);

create table determination_runs (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  system_id uuid not null references ai_systems(id),
  questionnaire_json text not null,
  result_json text not null,
  status varchar(32) not null check (status in ('COMPLETED', 'FAILED')),
  ruleset_version varchar(32) not null,
  created_by uuid references users(id),
  created_at timestamptz not null
);

create table determination_obligations (
  id uuid primary key,
  run_id uuid not null references determination_runs(id),
  rule_code varchar(64) not null,
  applicability varchar(32) not null check (applicability in ('APPLICABLE', 'NOT_APPLICABLE', 'UNCERTAIN')),
  rationale varchar(2048) not null,
  control_codes text not null,
  legal_refs varchar(512) not null,
  title varchar(255) not null,
  severity varchar(32) not null
);
```

## Regulatory change monitoring (Part 14 / V16)

Assistive polled feed — not an official legal bulletin. Items are global; reviews are tenant-scoped. Impact hints prefer `UNCERTAIN` and never auto-mutate risk class or control status.

```sql
create table reg_sources (
  id uuid primary key,
  code varchar(64) not null unique,
  name varchar(255) not null,
  url varchar(2048) not null,
  feed_type varchar(32) not null check (feed_type in ('RSS', 'STATIC_FIXTURE', 'HTML_LIST')),
  poll_interval_seconds int not null default 900,
  enabled boolean not null default true,
  last_polled_at timestamptz,
  notes varchar(1024),
  created_at timestamptz not null
);

create table reg_items (
  id uuid primary key,
  source_id uuid not null references reg_sources(id),
  external_id varchar(512) not null,
  title varchar(1024) not null,
  summary varchar(4096) not null,
  published_at timestamptz,
  url varchar(2048) not null,
  content_hash varchar(64) not null unique,
  fetched_at timestamptz not null,
  unique (source_id, external_id)
);

create table reg_impact_hints (
  id uuid primary key,
  reg_item_id uuid not null references reg_items(id),
  control_code varchar(64),
  obligation_code varchar(64),
  impact_level varchar(32) not null check (impact_level in ('UNCERTAIN', 'POSSIBLE', 'LIKELY')),
  impact_note varchar(2048) not null,
  created_at timestamptz not null
);

create table reg_item_reviews (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  reg_item_id uuid not null references reg_items(id),
  reviewed_by uuid references users(id),
  reviewed_at timestamptz not null,
  notes varchar(2048),
  unique (tenant_id, reg_item_id)
);
```

Seeded sources include `CURATED_BOOTSTRAP` (classpath JSON fixture, enabled) plus disabled EUR-Lex / OJ poll targets (respect ToS and rate limits when enabling).

## Notes not stored as first-class tables

| Concern | Storage / notes |
|---|---|
| Certification readiness | Computed from existing system/control/eval/audit state (Part 13); export is ephemeral report |
| Sector packs | Code/config SPI + seeded overlay control codes (Part 15); see `docs/SECTOR_PACKS.md` |
| Evidence pack seal | Computed at export time (`contentSha256`); not a durable pack table |
