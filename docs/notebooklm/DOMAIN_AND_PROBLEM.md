# DOMAIN AND PROBLEM

## The Regulatory Backdrop

The European Union Artificial Intelligence Act (EU AI Act) establishes a risk-tiered regulatory framework for organizations deploying or making available AI systems on the EU market. Systems are categorized into four distinct risk tiers:
- **`PROHIBITED`**: Unacceptable risk (e.g., social scoring, cognitive behavioral manipulation, untargeted facial recognition scraping). Must be blocked entirely.
- **`HIGH`**: High risk (e.g., critical infrastructure, employment candidate screening, credit scoring, biometrics, insurance claims triage). Subject to mandatory conformity assessments, extensive technical documentation, risk management systems, data governance standards, human oversight protocols, and robust cybersecurity.
- **`LIMITED`**: Specific transparency obligations (e.g., chatbots, emotion recognition systems, deepfakes/AI-generated content). Users must be informed they are interacting with AI.
- **`MINIMAL`**: Minimal or no risk (e.g., AI-enabled video games, spam filters). No extra statutory obligations, though voluntary codes of conduct apply.

From an engineering perspective, EU AI Act compliance is not a novel machine learning research problem. Instead, it closely resembles enterprise compliance control frameworks (such as SOX, SOC 2, ISO 27001, or GxP pharma validation). What makes it uniquely challenging in practice is that the statutory obligations cut across normally siloed organizational functions and disparate artifacts:
1. **ML Engineering**: Model cards, eval benchmark runs, prompt templates, latency/cost metrics.
2. **Data Engineering & Platform**: Data contracts, schema definitions, upstream drift detection, dataset lineage.
3. **Legal & Regulatory**: Fundamental Rights Impact Assessments (DPIAs), statutory risk classification rationales, regulatory mapping.
4. **Compliance & Risk**: Control catalog status (`PASS` / `REVIEW` / `BLOCKED`), gap analysis, oversight logs.
5. **Business & Operations**: Staged human sign-off chains (Engineering Lead → Compliance → Legal Sign-off).

Standard MLOps tooling (e.g., MLflow, Weights & Biases, Arize) tracks model performance and experiment artifacts but does not answer "is this release legally compliant to deploy in the EU?" Standard enterprise GRC tooling (e.g., ServiceNow GRC, OneTrust) manages manual policy checklists but lacks awareness of model eval thresholds, data drift breaches, or automated CI release gates.

---

## What Devernance Is

Devernance is an open-source, multi-tenant governance control plane and decision engine designed to bridge this gap. It acts as the system of record and execution gate that sits directly in front of an AI system's deployment pipeline.

Key capabilities implemented in the codebase:
- **AI System Registry**: Registers AI systems with metadata including system name, purpose, model/vendor details, sector, deployment region, decision impact level, and affected user demographics.
- **Guided Risk Classification**: Records risk tier (`MINIMAL`, `LIMITED`, `HIGH`, `PROHIBITED`) with an explicit human-supplied legal rationale. Risk classification is strictly guided and recorded — the system intentionally does **not** use AI/ML to auto-classify risk, avoiding false legal certainty.
- **Controls Catalog**: Implements an EU AI Act-aligned catalog of controls (covering risk management, data governance, technical documentation, record-keeping, transparency, human oversight, accuracy, and cybersecurity) and tracks per-system control evaluation states (`PASS`, `REVIEW`, `BLOCKED`).
- **Cited Evidence RAG Pipeline**: Ingests compliance artifacts (DPIAs, model cards, vendor assessments, policy documents, incident logs) with strict ingestion guards (URI validation, prompt-injection line stripping, content hashing). Performs tenant- and system-scoped retrieval-augmented generation (RAG) that returns answers only with explicit source citations.
- **Eval Gates & Queue Worker**: Maintains eval datasets (e.g., golden test sets), thresholds, and scored evaluation runs (measuring faithfulness, relevance, safety refusal, bias pass rate, latency, and cost). Operates a durable, DB-backed polling queue worker using `SELECT ... FOR UPDATE SKIP LOCKED` for concurrent execution.
- **Data Contracts & Drift Events**: Tracks schemas and data contracts for upstream data feeds. Open drift events (`BREACH` severity) automatically flip a data contract's status, which directly feeds into the release decision.
- **Staged Approval Workflows**: Routes system releases through sequential human approval chains (`ENG_LEAD_REVIEW` → `COMPLIANCE_REVIEW` → `LEGAL_SIGNOFF`). `LEGAL_SIGNOFF` mandatorily requires recorded human oversight evidence text.
- **Hash-Chained Audit Ledger**: Records every state change and approval into an append-only ledger. Each audit record contains an HMAC-SHA-256 hash computed over its canonical fields and the preceding event's hash, providing tamper-evident verification via REST endpoints (`/audit/verify`).
- **Unified Release Gate Engine**: Calculates a single release decision (`PASS`, `REVIEW`, `BLOCKED`) by fusing evidence coverage, eval scores (against soft and hard thresholds), data-contract status, control status, and oversight evidence. Exposes both human-facing dashboard views and machine-facing CI endpoints (`/ci/release-gate`) returning process exit codes (`PASS`=0, `BLOCKED`=1, `REVIEW`=2).
- **Assistive Phase 7 Layers**:
  - *Assisted Obligation Determination*: Guided questionnaire and ruleset suggesting applicable EU AI Act articles; never auto-applies risk classes (`autoApplied: false`).
  - *Certification Readiness Score*: Weighted 0–100 score across 9 governance dimensions; explicitly never returns a `certified: true` boolean.
  - *Regulatory Change Monitoring*: Polls regulatory sources for legislative updates and assigns heuristic impact hints biased toward `UNCERTAIN`; never auto-mutates system state.
  - *Sector Packs*: Provides domain-specific compliance controls and template packs for Insurance, HR, and Finance.

---

## Why Existing Tools & Approaches Are Insufficient

From the perspective of the system builder, existing approaches fail due to structural mismatches:

1. **Spreadsheets & Ticket Trackers Don't Compute Executable Decisions**  
   A compliance officer can track "DPIA completed: Yes/No" in Jira or Excel, but deployment pipelines cannot query a spreadsheet during a CI/CD run. Furthermore, spreadsheets cannot enforce rules such as "if eval score < 78, block deployment regardless of sign-offs." `ReleaseGateService` encodes this logic as executable code.

2. **MLOps Eval Tools Lack Legal & Governance Context**  
   Generic eval frameworks (e.g., Ragas, DeepEval) compute numeric scores for accuracy or toxicity. However, they are unaware of legal risk tiers, human oversight requirements, or data contract drift. A system can achieve a 95% eval score and still be illegal to ship under Article 14 if required human oversight mechanisms are unverified.

3. **Generic Document Chatbots Lack Scoping and Mandatory Citations**  
   Standard enterprise Q&A bots can hallucinate or mix context across tenants. In a regulatory audit, an uncited or plausible-sounding hallucinated answer is worse than no answer. Devernance enforces tenant-isolated, system-isolated retrieval with mandatory chunk citations and prompt-injection filtering.

4. **Standard Database Logs Lack Tamper-Evident Integrity**  
   Standard `audit_logs` SQL tables with application-level insert conventions do not satisfy external auditors because rows can be modified or deleted directly by DBAs or via compromised credentials. The hash-chained ledger ensures any historical row modification breaks the cryptographic hash chain, making tampering immediately detectable.

5. **Risk of Overclaiming Legal Certification**  
   Many compliance vendors market "automated compliance" or "AI-driven certification." Devernance explicitly rejects this framing. The codebase and documentation enforce strict disclaimers: the tool is **not** a Notified Body, cannot issue statutory CE certificates, and enforces human-in-the-loop validation at every boundary.

---

## Lineage & Prior Art Context

As documented in `docs/PRD.md` §10, the design of Devernance incorporates concepts from earlier internal/project specifications named "ComplianceGuard RAG," "EvalForge," and "Data Contracts AI." In this repository, these concepts have been unified into a single monolithic domain architecture.
