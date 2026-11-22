# Role authorization matrix

Source of truth for RBAC enforcement via `TenantAuthorizationService.requireAnyRole(...)` on API mutators and sensitive reads. Aligns with `docs/SECURITY.md` roles (enum: `UserRole`).

**Legend:** ✓ allowed · — denied · stage = workflow stage `requiredRole` (Admin may act on any stage)

## Matrix

| Capability | ADMIN | AI_ENGINEERING_LEAD | COMPLIANCE_OFFICER | LEGAL_COUNSEL | AUDITOR |
|---|:---:|:---:|:---:|:---:|:---:|
| List/get systems, release gate | ✓ | ✓ | ✓ | ✓ | ✓ |
| Create / update AI system | ✓ | ✓ | ✓ | — | — |
| Risk classification | ✓ | ✓ | ✓ | ✓ | — |
| List controls / system controls | ✓ | ✓ | ✓ | ✓ | ✓ |
| Update system control status | ✓ | REVIEW/BLOCKED only¹ | ✓ | — | — |
| Evidence list / query | ✓ | ✓ | ✓ | ✓ | ✓ |
| Evidence document ingest / upload | ✓ | ✓ | ✓ | — | — |
| Evidence pack export | ✓ | ✓ | ✓ | ✓ | ✓ |
| Data contract list / get | ✓ | ✓ | ✓ | ✓ | ✓ |
| Data contract create / update / report drift | ✓ | ✓ | ✓ | — | — |
| Acknowledge drift (note required) | ✓ | ✓ | ✓ | — | — |
| Resolve WARNING drift (note required) | ✓ | ✓ | ✓ | — | — |
| Resolve BREACH drift, or change a resolved breach (note required) | ✓ | — | ✓ | — | — |
| Eval dataset list | ✓ | ✓ | ✓ | ✓ | ✓ |
| Eval dataset create | ✓ | ✓ | — | — | — |
| Eval run create (queue) | ✓ | ✓ | ✓ | — | — |
| Eval run get | ✓ | ✓ | ✓ | ✓ | ✓ |
| Eval run retry / signed callback | ✓ | ✓ | — | — | — |
| Eval run execute (synthetic metrics; off unless `assurance.eval.synthetic-metrics.enabled`) | ✓ | ✓ | — | — | — |
| Eval operations view | ✓ | ✓ | ✓ | — | — |
| Workflow list / mine / notifications | ✓ | ✓ | ✓ | ✓ | ✓ |
| Workflow approve / reject | stage | stage | stage | stage | stage |
| Workflow override | ✓ | — | — | — | — |
| Audit list / chain verify | ✓ | ✓ | ✓ | ✓ | ✓ |
| Manual audit event append | ✓ | ✓ | ✓ | — | — |
| Governance: apply manifests | ✓ | ✓ | ✓ | — | — |
| Governance: validate, simulate, release check, status, graph, drift, resources | ✓ | ✓ | ✓ | ✓ | ✓ |
| Governance: authorize, report execution, report observations | ✓ | ✓ | — | — | — |
| Governance: read decisions and approvals, verify a token | ✓ | ✓ | ✓ | ✓ | ✓ |
| Governance: approve / reject an action² | policy | policy | policy | policy | — |
| Governance: evidence bundle, closure | ✓ | ✓ | ✓ | ✓ | ✓ |

¹ The engineering lead who ships a system may raise a control to REVIEW or BLOCKED, but only compliance or an admin can
mark it PASS or lift a BLOCKED control (`ControlService.updateSystemControl`). Nobody attests their own release.

² Only the roles the policy lists in `approverRoles` (default ADMIN and COMPLIANCE_OFFICER; AUDITOR can never be
listed), only from a signed-in session (never an API key), and never for a request the same person made, in a session or through an API key they created.

## Notes

- **Authentication** is orthogonal: every path except the unauthenticated allowlist requires a valid `Authorization: Bearer` JWT or `X-Api-Key`. Tenant/actor always come from the credential, never from client-supplied `X-Tenant-Id` / `X-Actor-Id`.
- **Tenant isolation** still applies after role checks: repositories filter by `tenantId` from `TenantContext`. Cross-tenant IDs return **404** (not 200 with foreign data).
- **AUDITOR** is intentionally read-mostly: no registry writes, control edits, evidence ingest, contract mutations, eval write ops, or manual audit append.
- **Workflow stages** encode required role at open time (`ApprovalWorkflowService`); Admin may approve/reject/override any stage.
- **Sign-off is part of the release decision**: a rejected approval cycle blocks every risk class until a later cycle is approved or an admin overrides the rejected stage; a high-risk system stays in REVIEW while any cycle is open (`ApprovalGateSignals`).
- **Eval callbacks** require both an authorized Admin/AI Engineering actor **and** a valid HMAC signature (`X-Eval-Timestamp` + `X-Eval-Signature`).

## Enforcement map

| Area | Enforcement |
|---|---|
| `AiSystemController` mutators + evidence pack | `TenantAuthorizationService` |
| `ControlController` PUT system control | `TenantAuthorizationService` |
| `EvidenceController` document create/upload | `TenantAuthorizationService` |
| `DataContractController` mutators | `TenantAuthorizationService` |
| `EvalDatasetController` POST | `TenantAuthorizationService` |
| `EvalRunController` create + privileged ops | `TenantAuthorizationService` |
| `AuditController` POST | `TenantAuthorizationService` |
| `ApprovalWorkflowService` stage actions | role + Admin override rules |
| `GovernanceApplyService`, `GovernanceAuthorizationService`, `GovernanceDriftService`, `GovernanceEvidenceService` | `TenantAuthorizationService`; approvals also `SessionOnly` and the policy's `approverRoles` |
| API key scopes (`ApiKeyScope`) | `RELEASE_GATE` and `RUNTIME` keys are limited to fixed routes in `TenantContextFilter` before any role check |

When adding a new mutator, extend this matrix and add a FORBIDDEN regression test (typically as `AUDITOR`).
