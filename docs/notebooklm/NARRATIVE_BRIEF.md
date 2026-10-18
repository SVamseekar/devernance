# NARRATIVE BRIEF

I built Devernance to solve a specific problem I kept encountering in production engineering: how do you convert the regulatory requirement of "can we legally ship this AI system into the EU" into an executable, machine-computable decision rather than a manual spreadsheet process and a folder of static PDFs?

The EU AI Act establishes a risk-tiered compliance model. The higher an AI system's risk tier—from Minimal and Limited up to High and Prohibited—the more statutory evidence, model testing, data governance, and human sign-off an organization must produce prior to deployment. Conceptually, this framework is straightforward. In engineering reality, compliance evidence is fragmented across isolated teams using incompatible tools:
- ML engineers measure benchmark evaluation scores and prompt versions in MLOps platforms.
- Compliance officers maintain DPIAs and control checklists in enterprise GRC tools.
- Data platform engineers track schema changes and upstream drift in data catalogs.
- Legal teams require explicit statutory risk rationales and formal sign-off records.

None of the standard MLOps platforms treat legal compliance and release gate validation as first-class software concerns, and standard GRC software lacks awareness of model evaluation thresholds or real-time data contract drift. 

To bridge this gap, I designed and built a multi-tenant governance control plane that bridges engineering and compliance: an AI system registry with guided risk classification, an EU AI Act control catalog, cited-evidence RAG scoped per tenant, an asynchronous evaluation queue worker, data contract drift monitoring, multi-stage human approval workflows, and an append-only audit ledger. Crucially, all of these signals feed into a single computed release decision (`PASS`, `REVIEW`, or `BLOCKED`) exposed both to an interactive web dashboard and to CI/CD deployment pipelines via a machine-readable endpoint returning process exit codes.

---

## Key Engineering & Domain Decisions

Four specific technical decisions shaped the system's architecture during development:

### 1. Unified Release Decision Engine over Isolated Compliance Signals
It would have been simpler to build independent checks—one for eval scores, one for data drift, one for control status—and leave it to human reviewers to inspect each screen. However, isolated checks permit false confidence. `ReleaseGateService` fuses evidence coverage, benchmark evaluation scores (against soft pass and hard block thresholds), data contract drift status, system control states, and mandatory legal oversight logs into a single deterministic decision function. The exact same calculation backs both the human dashboard and the machine-facing `/ci/release-gate` endpoint. Writing end-to-end integration tests for high-risk insurance claims models proved the necessity of this fusion: a system achieving a 95% evaluation score must still evaluate to `BLOCKED` if an upstream data contract is in breach or mandatory human oversight evidence is unverified.

### 2. Cryptographic Hash-Chained Audit Ledger for Tamper-Evidence
Standard database audit tables with application-level `INSERT`-only rules do not satisfy external auditors. An auditor's primary question is whether historical records could have been modified outside the application—by database administrators, direct SQL execution, or compromised credentials. To solve this, `AuditChainHasher` computes an HMAC-SHA-256 signature for every event based on its canonical fields and the preceding event's hash (`prev_event_hash`). Modifying or deleting a historical database row invalidates the cryptographic hash chain from that record forward, making administrative tampering immediately detectable via `/audit/verify` endpoints.

### 3. Architecture Conversion to Next.js BFF for Strict Tenant Isolation
Tenant identity resolution initially contained a security flaw. An early pass resolved tenant context from `X-Tenant-Id` and `X-Actor-Id` HTTP headers when API keys were omitted, while the client stored default tenant IDs in browser `localStorage`. An internal security audit highlighted that any browser user could modify `localStorage` and impersonate other tenant accounts. The fix required an architectural shift rather than a minor code patch: I refactored `apps/dashboard` into a Backend-for-Frontend (BFF) architecture. Browser sessions hold secure `httpOnly` cookies, all API requests pass through a server-side proxy (`/api/proxy`) that injects verified JWT Bearer tokens, and `middleware.ts` validates sessions across all product routes. Client-supplied tenant headers are strictly ignored by the API (`TenantContextFilter`), ensuring complete multi-tenant data isolation.

### 4. Two-Pass Engineering Fix for Outbound SSRF Defense
The evidence RAG pipeline allows extracting text from remote document URLs (`sourceUri`). Because outbound HTTP requests originate from inside the server trust boundary, this exposes Server-Side Request Forgery (SSRF) and DNS-rebinding vectors—where a domain resolves to a public IP during validation but re-resolves to a private IP (e.g. `127.0.0.1`) when the HTTP socket opens. My initial fix validated the resolved IP and attempted to connect to the IP directly with a custom `Host` header. However, Java's JDK `HttpClient` blocks `Host` header overrides as a security restriction, causing outbound evidence fetches to silently fail and fall back to metadata stubs. The resolution required replacing JDK `HttpClient` with Apache HttpClient5 and implementing a custom single-lookup `DnsResolver` on the connection manager. The resolver resolves hostnames exactly once, validates the IP against private subnet blacklists, and passes that identical IP socket to the connection manager, eliminating the TOCTOU DNS race condition.

---

## Current Status & Disclaimers

The core governance platform—including system registry, cited evidence RAG, evaluation queue worker, data contracts, staged workflows, hash-chained audit, evidence pack PDF generation, and CI release gates—is complete, fully covered by automated unit/integration tests, and verified against an end-to-end Claims Triage insurance scenario.

On top of the core control plane, I built four assistive governance layers: obligation determination, certification readiness scoring, regulatory change monitoring, and sector packs (Insurance, HR, Finance). I was careful to enforce structural disclaimers within the codebase to prevent overclaiming legal authority:
- The obligation determination engine returns suggested EU AI Act articles with `autoApplied: false` and `requiresHumanConfirm: true`.
- The certification readiness score calculates a weighted 0–100 index across 9 governance dimensions, but its schema intentionally omits any `certified` boolean field.
- The regulatory monitor defaults item impact hints to `UNCERTAIN` and logs `autoMutatesRiskOrControls: false` on every ingested item.
- Google and Microsoft OAuth 2.0 authentication flows are fully implemented, wired in the BFF proxy, and testable locally via `./scripts/run-local-dev.sh`, but production IdP verification remains pending formal sign-off (`docs/oauth-production-smoke-test.md`).

---

## Open Technical & Domain Question

The primary open technical question I am evaluating concerns release gate threshold configuration.

Currently, evaluation pass/block thresholds (`85` soft pass, `78` hard block) and evidence coverage thresholds (`82%`) are hardcoded class constants shared across all tenants. While this simplification was appropriate for establishing a deterministic baseline, it does not account for domain variations at scale. An automated medical diagnostic tool and a customer service routing bot operate under vastly different risk tolerances.

I am evaluating three potential approaches to configurable thresholds:
1. **Per-Tenant Configuration**: Allow tenants to define custom numerical thresholds. (Risk: shifts legal judgment back onto tenants who may lack qualification to establish safe thresholds).
2. **Per-Sector Defaults**: Tie threshold baselines to sector packs (e.g., higher thresholds for Insurance and HR than for general enterprise search).
3. **Policy-Engine Rules**: Integrate an explicit policy language (e.g., OPA / Rego) allowing compliance officers to write declarative release policies.

Each approach presents distinct trade-offs between architectural complexity, legal defensibility, and user operational burden.
