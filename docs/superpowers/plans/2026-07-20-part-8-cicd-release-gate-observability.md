# Part 8 — CI/CD Release Gate Integration + Observability

> **For agentic workers:** Part 1 creates basic CI; this part adds **productized** release-gate-for-CI and observability.

**Goal:**  
1) Every PR runs full quality gates.  
2) External CI/CD can call Devernance for a machine-readable release decision (EvalForge / PRD §10).  
3) Operators have metrics + a minimal observability surface (Phase 6).

**Depends on:** Part 1 (CI skeleton), Part 5 (stable gate + controls).

---

### Task 8.1: Harden GitHub CI (extends Part 1)

- [x] **Step 1:** Jobs: `api-test`, `dashboard-build`, `secret-scan`, optional `acceptance` (`ClaimsTriageAcceptanceTest`).
- [x] **Step 2:** Cache Maven + npm.
- [x] **Step 3:** Fail on test failure; upload reports as artifacts.
- [x] **Step 4:** Branch protection requires these checks on `main` (exact check names):
  `API tests`, `Dashboard checks`, `Secret scan`, `Acceptance (Claims Triage)`

### Task 8.2: Release gate CI API (product feature)

Enable pipelines to block deploys on BLOCKED systems.

- [x] **Step 1:** `GET /api/v1/systems/{id}/release-gate` already exists — add:
  - `GET /api/v1/ci/release-gate?systemId=`
  - Response machine-friendly: `{ decision, blockers[], evalScore, evidenceCoverage, content?, exitCode, … }`
  - Exit-code oriented docs: PASS=0, REVIEW=2, BLOCKED=1 for CLI
- [x] **Step 2:** CLI script `scripts/ci-release-gate.sh`
- [x] **Step 3:** Example GitHub Action workflow `release-gate-example.yml`
- [x] **Step 4:** Auth: API key recommended for CI bots (`docs/OPS.md`)
- [x] **Step 5:** Tests for response contract stability (`CiReleaseGateControllerTest`)

### Task 8.3: Observability — backend

- [x] **Step 1:** Actuator health/metrics + `micrometer-registry-prometheus`
- [x] **Step 2:** Metrics:
  - `assurance.release_gate.decision` counter tags decision
  - evidence query latency via `assurance.api.evidence.query` (Part 10 name; documented)
  - `assurance.audit.append`
  - `assurance.auth.login.failures`
- [x] **Step 3:** Correlation ID filter + MDC `requestId` in logs
- [x] **Step 4:** Document scrape endpoint in `docs/OPS.md`

### Task 8.4: Observability — dashboard surface

- [x] **Step 1:** `/command` widgets: gate decision counts, open workflows, open breaches
- [x] **Step 2:** API status pill on command (no secret leak)
- [x] **Step 3:** Optional public status: out of scope (health remains public probe only)

### Task 8.5: Latency NFR hooks (PRD §7)

- [x] **Step 1:** Timed metrics on registry list/get and evidence query (Part 10)
- [x] **Step 2:** Document p95 reading in `docs/NFR.md` / `docs/OPS.md` — no fake compliance
- [x] **Step 3:** Unit tests assert timers exist only (`AssuranceMetricsTest`, `CiReleaseGateControllerTest`)

### Done when

- [x] CI jobs hardened (acceptance + artifacts)  
- [x] `scripts/ci-release-gate.sh` blocks on BLOCKED  
- [x] Prometheus/Actuator metrics for gate + RAG + auth  
- [x] Operator-visible summary in dashboard  
- [x] Merged to main (PR #24); branch protection configured for required CI checks
