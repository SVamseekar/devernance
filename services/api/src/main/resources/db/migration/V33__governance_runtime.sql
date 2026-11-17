-- Governance runtime (docs/GOVERNANCE_PLATFORM.md).
--
-- governance_resources    Declarative manifests applied with `devernance apply` (an AISystem or a
--                         GovernancePolicy). Every change is a new immutable version; one version per name is active.
-- governance_decisions    One row per runtime authorization request: what an agent asked to do and what the
--                         policy answered (ALLOW / DENY / REVIEW), with the exact policy and system version used.
-- governance_approvals    The human approval a REVIEW decision waits for.
-- governance_observations What a deployment reported it is really running, compared with the approved state
--                         to detect governance drift.

create table governance_resources (
  id              uuid primary key,
  tenant_id       uuid not null references tenants(id),
  system_id       uuid not null references ai_systems(id),
  kind            varchar(32) not null check (kind in ('AI_SYSTEM', 'GOVERNANCE_POLICY')),
  name            varchar(128) not null,
  version         integer not null,
  spec_json       text not null,
  content_sha256  varchar(64) not null,
  active          boolean not null,
  created_by      uuid not null,
  created_at      timestamp with time zone not null,
  constraint uq_governance_resources_version unique (tenant_id, kind, name, version)
);

create index idx_governance_resources_system
  on governance_resources(tenant_id, system_id, kind, active);

create table governance_decisions (
  id                       uuid primary key,
  tenant_id                uuid not null references tenants(id),
  system_id                uuid not null references ai_systems(id),
  system_resource_id       uuid not null references governance_resources(id),
  system_version           integer not null,
  policy_resource_id       uuid references governance_resources(id),
  policy_name              varchar(128),
  policy_version           integer,
  agent                    varchar(128) not null,
  api_key_id               uuid,
  actor_id                 uuid not null,
  action                   varchar(128) not null,
  resource_json            text not null,
  context_json             text not null,
  request_sha256           varchar(64) not null,
  model                    varchar(256),
  environment              varchar(64) not null,
  status                   varchar(16) not null check (status in ('ALLOW', 'DENY', 'REVIEW')),
  enforced                 boolean not null,
  rule                     varchar(160) not null,
  reason                   varchar(1024) not null,
  execution_status         varchar(16) check (execution_status in ('EXECUTED', 'FAILED')),
  execution_detail         varchar(1024),
  execution_reference      varchar(256),
  execution_recorded_at    timestamp with time zone,
  created_at               timestamp with time zone not null
);

create index idx_governance_decisions_system_time
  on governance_decisions(tenant_id, system_id, created_at);
create index idx_governance_decisions_tenant_time
  on governance_decisions(tenant_id, created_at);

create table governance_approvals (
  id               uuid primary key,
  tenant_id        uuid not null references tenants(id),
  decision_id      uuid not null references governance_decisions(id),
  system_id        uuid not null references ai_systems(id),
  status           varchar(16) not null check (status in ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
  approver_roles   varchar(512) not null,
  reason           varchar(1024) not null,
  decided_by       uuid,
  rationale        varchar(2048),
  requested_at     timestamp with time zone not null,
  expires_at       timestamp with time zone not null,
  decided_at       timestamp with time zone,
  constraint uq_governance_approvals_decision unique (decision_id)
);

create index idx_governance_approvals_tenant_status
  on governance_approvals(tenant_id, status, requested_at);

create table governance_observations (
  id             uuid primary key,
  tenant_id      uuid not null references tenants(id),
  system_id      uuid not null references ai_systems(id),
  environment    varchar(64) not null,
  observed_json  text not null,
  in_sync        boolean not null,
  source         varchar(128) not null,
  reported_by    uuid not null,
  created_at     timestamp with time zone not null
);

create index idx_governance_observations_system_time
  on governance_observations(tenant_id, system_id, created_at);
