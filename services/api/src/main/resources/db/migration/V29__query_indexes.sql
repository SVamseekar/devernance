-- Indexes for queries that ran as full table scans.
-- ai_systems: every system list, lookup, and workspace purge filters by tenant first.
create index idx_ai_systems_tenant_created on ai_systems(tenant_id, created_at);

-- audit_events: the per-system trail (system page, evidence pack, readiness) filters by tenant and system.
create index idx_audit_events_tenant_system_created on audit_events(tenant_id, system_id, created_at);

-- refresh_tokens: password reset and sign-out-everywhere revoke a user's live tokens by user id.
create index idx_refresh_tokens_user on refresh_tokens(user_id);
