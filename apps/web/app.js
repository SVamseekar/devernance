const defaultState = {
  theme: localStorage.getItem("euai-theme") || "light",
  systems: [
    {
      name: "Claims Triage AI",
      owner: "Insurance Ops",
      risk: "high",
      basis: "Eligibility and access to essential private services",
      evidence: 72,
      eval: 78,
      contract: "Breach",
      decision: "Blocked",
      gaps: ["Human oversight SOP missing", "Bias eval below threshold"]
    },
    {
      name: "HR Candidate Screener",
      owner: "People Systems",
      risk: "high",
      basis: "Employment access and ranking",
      evidence: 84,
      eval: 82,
      contract: "Warning",
      decision: "Review",
      gaps: ["Data lineage stale", "Reviewer calibration required"]
    },
    {
      name: "Bank KYC Assistant",
      owner: "Financial Crime",
      risk: "high",
      basis: "Credit, onboarding, and fraud control support",
      evidence: 91,
      eval: 88,
      contract: "Healthy",
      decision: "Pass",
      gaps: ["Quarterly red-team due in 9 days"]
    },
    {
      name: "Support RAG Copilot",
      owner: "Customer Success",
      risk: "limited",
      basis: "Customer-facing assistant with transparency duty",
      evidence: 88,
      eval: 86,
      contract: "Healthy",
      decision: "Pass",
      gaps: ["Update chatbot disclosure copy"]
    },
    {
      name: "Sales Email Generator",
      owner: "Revenue",
      risk: "limited",
      basis: "Generative AI content tool",
      evidence: 76,
      eval: 81,
      contract: "Healthy",
      decision: "Review",
      gaps: ["Copyright prompt guardrail incomplete"]
    },
    {
      name: "Invoice OCR Classifier",
      owner: "Finance",
      risk: "minimal",
      basis: "Back-office document extraction",
      evidence: 94,
      eval: 91,
      contract: "Healthy",
      decision: "Pass",
      gaps: []
    },
    {
      name: "Learning Tutor",
      owner: "EdTech Unit",
      risk: "limited",
      basis: "Student assistant with explainability duty",
      evidence: 79,
      eval: 77,
      contract: "Warning",
      decision: "Review",
      gaps: ["Minor safety refusal regression"]
    },
    {
      name: "Clinical Intake Summarizer",
      owner: "Health Product",
      risk: "high",
      basis: "Healthcare workflow support with PHI controls",
      evidence: 81,
      eval: 73,
      contract: "Breach",
      decision: "Blocked",
      gaps: ["PHI masking failed", "Audit justification missing"]
    }
  ],
  controls: [
    {
      title: "Claims Triage release approval",
      body: "SAGA paused after eval regression. Legal approval and owner attestation are pending.",
      chips: ["High-risk", "Human oversight", "Blocked"]
    },
    {
      title: "Clinical Intake data drift",
      body: "FHIR mapping added patient_language without a signed contract update.",
      chips: ["PHI", "Data contract", "Breach"]
    },
    {
      title: "HR Screener calibration",
      body: "Judge-human agreement dropped below 0.85 on adverse-impact samples.",
      chips: ["Employment", "Bias eval", "Review"]
    }
  ],
  contracts: [
    {
      name: "claims_events.v4",
      owner: "Insurance Data",
      status: "Breach",
      detail: "New field denial_reason_category is not mapped to fairness monitoring.",
      coverage: 68
    },
    {
      name: "candidate_profiles.v2",
      owner: "People Analytics",
      status: "Warning",
      detail: "Education history optionality changed from required to nullable.",
      coverage: 81
    },
    {
      name: "kyc_decisions.v7",
      owner: "Financial Crime",
      status: "Healthy",
      detail: "No schema or semantic drift detected in the last 24 hours.",
      coverage: 94
    },
    {
      name: "clinical_notes.v3",
      owner: "Health Product",
      status: "Breach",
      detail: "PHI redaction contract missing for locale-specific identifiers.",
      coverage: 63
    }
  ],
  audit: [
    ["09:12", "Release gate blocked Claims Triage AI after faithfulness fell to 78 percent."],
    ["09:07", "Compliance RAG cited DPIA-CLM-014 and EU-AIA-HR-003 for missing oversight evidence."],
    ["08:48", "Data contract monitor detected clinical_notes.v3 PHI redaction breach."],
    ["08:32", "Reviewer Marta approved KYC Assistant model card version 2026.06.04."],
    ["08:10", "EvalForge worker completed support-rag-v8 with pass decision."]
  ]
};

function loadState() {
  const saved = localStorage.getItem("euai-state");
  if (!saved) {
    return structuredClone(defaultState);
  }

  try {
    const parsed = JSON.parse(saved);
    return {
      ...structuredClone(defaultState),
      ...parsed,
      theme: localStorage.getItem("euai-theme") || parsed.theme || defaultState.theme
    };
  } catch {
    return structuredClone(defaultState);
  }
}

const state = loadState();

const api = {
  baseUrl: localStorage.getItem("euai-api-base") || "http://localhost:8080/api/v1",
  online: false
};

const riskColors = {
  high: "#b42318",
  limited: "#b54708",
  minimal: "#057a55"
};

function qs(selector) {
  return document.querySelector(selector);
}

function qsa(selector) {
  return Array.from(document.querySelectorAll(selector));
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function sentenceCase(value) {
  return String(value ?? "")
    .toLowerCase()
    .replace(/(^|_)([a-z])/g, (_, boundary, letter) => `${boundary === "_" ? " " : ""}${letter.toUpperCase()}`);
}

function normalizeSystem(system) {
  return {
    id: system.id,
    name: system.name,
    owner: system.owner,
    risk: String(system.riskClass || system.risk || "limited").toLowerCase(),
    basis: system.riskBasis || system.basis || system.purpose,
    evidence: system.evidenceCoverage ?? system.evidence ?? 0,
    eval: system.evalScore ?? system.eval ?? 0,
    contract: sentenceCase(system.dataContractStatus || system.contract || "warning"),
    decision: sentenceCase(system.releaseDecision || system.decision || "review"),
    gaps: system.openGaps || system.gaps || []
  };
}

async function apiRequest(path, options = {}) {
  const response = await fetch(`${api.baseUrl}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`);
  }
  return response.status === 204 ? null : response.json();
}

function setApiStatus(online, detail = "") {
  api.online = online;
  const status = qs("#apiStatus");
  if (!status) return;
  status.textContent = online ? "API connected" : "Demo mode";
  status.title = detail || (online ? api.baseUrl : "Spring API not reachable");
  status.classList.toggle("online", online);
}

function applyTheme() {
  document.documentElement.dataset.theme = state.theme === "dark" ? "dark" : "";
  localStorage.setItem("euai-theme", state.theme);
}

function saveState() {
  localStorage.setItem("euai-state", JSON.stringify(state));
}

function showToast(message) {
  const toast = qs("#toast");
  toast.textContent = message;
  toast.classList.add("show");
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => toast.classList.remove("show"), 2200);
}

function addAudit(message) {
  const now = new Date();
  const time = now.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  state.audit.unshift([time, message]);
  saveState();
  renderAudit();
}

function renderMetrics() {
  const blocked = state.systems.filter((system) => system.decision === "Blocked").length;
  const highRisk = state.systems.filter((system) => system.risk === "high").length;
  const gaps = state.systems.reduce((sum, system) => sum + system.gaps.length, 0);
  const avgEval = Math.round(state.systems.reduce((sum, system) => sum + system.eval, 0) / state.systems.length);
  const avgEvidence = Math.round(state.systems.reduce((sum, system) => sum + system.evidence, 0) / state.systems.length);

  qs("#systemsCount").textContent = state.systems.length;
  qs("#highRiskCount").textContent = `${highRisk} high-risk`;
  qs("#gapCount").textContent = gaps;
  qs("#passRate").textContent = `${avgEval}%`;
  qs("#auditRate").textContent = `${Math.min(98, avgEvidence + 5)}%`;
  qs("#releaseGate").textContent = `${blocked} blocked`;
}

function classForRisk(risk) {
  return risk === "high" ? "high" : risk === "limited" ? "limited" : "minimal";
}

function renderGateRows() {
  const rows = state.systems.map((system) => {
    const riskClass = classForRisk(system.risk);
    const decisionClass = system.decision === "Pass" ? "good" : system.decision === "Review" ? "warn" : "bad";
    return `
      <tr>
        <td><strong>${escapeHtml(system.name)}</strong><br><em>${escapeHtml(system.owner)}</em></td>
        <td><span class="chip ${riskClass}">${escapeHtml(system.risk)}</span><br><em>${escapeHtml(system.basis)}</em></td>
        <td>${system.evidence}%</td>
        <td>${system.eval}%</td>
        <td>${escapeHtml(system.contract)}</td>
        <td class="decision ${decisionClass}">${system.decision}</td>
      </tr>
    `;
  }).join("");
  qs("#gateRows").innerHTML = rows;
}

function renderInbox() {
  qs("#controlInbox").innerHTML = state.controls.map((control) => `
    <article class="task">
      <strong>${escapeHtml(control.title)}</strong>
      <p>${escapeHtml(control.body)}</p>
      <div class="chips">${control.chips.map((chip) => `<span class="chip">${escapeHtml(chip)}</span>`).join("")}</div>
    </article>
  `).join("");
}

function renderSystems() {
  qs("#systemCards").innerHTML = state.systems.map((system) => {
    const riskClass = classForRisk(system.risk);
    return `
      <article class="card">
        <div class="chips"><span class="chip ${riskClass}">${escapeHtml(system.risk)}</span><span class="chip">${escapeHtml(system.owner)}</span></div>
        <h2>${escapeHtml(system.name)}</h2>
        <p>${escapeHtml(system.basis)}</p>
        <span class="metric-label">Assurance coverage</span>
        <div class="progress"><span style="width:${system.evidence}%"></span></div>
        <p>${system.gaps.length ? escapeHtml(system.gaps.join("; ")) : "No open gaps."}</p>
      </article>
    `;
  }).join("");

  qs("#evalSystem").innerHTML = state.systems.map((system) => `<option>${escapeHtml(system.name)}</option>`).join("");
  qs("#evidenceSystem").innerHTML = state.systems.map((system) => `
    <option value="${escapeHtml(system.id || system.name)}">${escapeHtml(system.name)}</option>
  `).join("");
}

function renderContracts() {
  qs("#contractCards").innerHTML = state.contracts.map((contract) => {
    const statusClass = contract.status === "Healthy" ? "good" : contract.status === "Warning" ? "warn" : "bad";
    return `
      <article class="contract">
        <span class="metric-label">${contract.owner}</span>
        <strong>${contract.name}</strong>
        <p>${contract.detail}</p>
        <div class="chips"><span class="chip ${statusClass === "bad" ? "high" : statusClass === "warn" ? "limited" : "minimal"}">${contract.status}</span><span class="chip">${contract.coverage}% coverage</span></div>
      </article>
    `;
  }).join("");
}

function renderAudit() {
  qs("#auditList").innerHTML = state.audit.map(([time, text]) => `
    <li><time>${time}</time><strong>${text}</strong></li>
  `).join("");
}

function drawRiskCanvas() {
  const canvas = qs("#riskCanvas");
  const ctx = canvas.getContext("2d");
  const filter = qs("#riskFilter").value;
  const systems = state.systems.filter((system) => filter === "all" || system.risk === filter);
  const dark = document.documentElement.dataset.theme === "dark";

  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = dark ? "#202631" : "#f9fafb";
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  ctx.strokeStyle = dark ? "#313846" : "#d7dce3";
  ctx.lineWidth = 1;
  for (let x = 100; x < canvas.width; x += 150) {
    ctx.beginPath();
    ctx.moveTo(x, 30);
    ctx.lineTo(x, canvas.height - 40);
    ctx.stroke();
  }
  for (let y = 75; y < canvas.height; y += 75) {
    ctx.beginPath();
    ctx.moveTo(40, y);
    ctx.lineTo(canvas.width - 30, y);
    ctx.stroke();
  }

  ctx.fillStyle = dark ? "#a7b0bf" : "#667085";
  ctx.font = "13px system-ui";
  ctx.fillText("Lower release readiness", 44, 28);
  ctx.fillText("Higher release readiness", canvas.width - 188, 28);
  ctx.save();
  ctx.translate(20, canvas.height - 70);
  ctx.rotate(-Math.PI / 2);
  ctx.fillText("Risk and data criticality", 0, 0);
  ctx.restore();

  systems.forEach((system, index) => {
    const x = 130 + (system.evidence / 100) * 650 + ((index % 2) * 18);
    const y = 295 - (system.eval / 100) * 230 + (system.risk === "high" ? -18 : system.risk === "limited" ? 10 : 24);
    const radius = system.decision === "Blocked" ? 20 : 15;
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, Math.PI * 2);
    ctx.fillStyle = riskColors[system.risk];
    ctx.globalAlpha = .9;
    ctx.fill();
    ctx.globalAlpha = 1;
    ctx.strokeStyle = dark ? "#f2f4f7" : "#ffffff";
    ctx.lineWidth = 3;
    ctx.stroke();
    ctx.fillStyle = dark ? "#f2f4f7" : "#111827";
    ctx.font = "12px system-ui";
    const label = system.name.length > 22 ? `${system.name.slice(0, 21)}...` : system.name;
    ctx.fillText(label, x + radius + 7, y + 4);
  });
}

function selectedEvidenceSystem() {
  const selected = qs("#evidenceSystem").value;
  return state.systems.find((system) => String(system.id || system.name) === selected) || state.systems[0];
}

function renderEvidenceDocuments(documents = []) {
  const target = qs("#evidenceDocuments");
  if (!documents.length) {
    target.classList.add("empty");
    target.innerHTML = "No indexed documents loaded.";
    return;
  }
  target.classList.remove("empty");
  target.innerHTML = documents.map((document) => `
    <article class="document-row">
      <div>
        <strong>${escapeHtml(document.title)}</strong>
        <span>${escapeHtml(document.type)} · ${document.chunkCount} chunk${document.chunkCount === 1 ? "" : "s"}</span>
      </div>
      <em>${escapeHtml(document.ingestionStatus)}</em>
    </article>
  `).join("");
}

async function loadBackendSystems() {
  try {
    const systems = await apiRequest("/systems");
    state.systems = systems.map(normalizeSystem);
    setApiStatus(true);
    saveState();
  } catch (error) {
    setApiStatus(false, error.message);
  }
}

async function loadEvidenceDocuments() {
  const system = selectedEvidenceSystem();
  if (!api.online || !system?.id) {
    renderEvidenceDocuments([]);
    return;
  }
  try {
    renderEvidenceDocuments(await apiRequest(`/evidence/systems/${system.id}/documents`));
  } catch (error) {
    setApiStatus(false, error.message);
    renderEvidenceDocuments([]);
  }
}

function renderRagResponse(question, response) {
  qs("#ragAnswer").classList.remove("empty");
  qs("#ragAnswer").innerHTML = `
    <span class="metric-label">Question</span>
    <p>${escapeHtml(question)}</p>
    <h2>Cited evidence answer</h2>
    <p>${escapeHtml(response.answer)}</p>
    ${(response.citations || []).map((citation) => `
      <div class="citation">
        <strong>${escapeHtml(citation.title)} · ${escapeHtml(citation.section)}</strong><br>
        ${escapeHtml(citation.snippet)}
      </div>
    `).join("")}
    <div class="citation"><strong>Confidence: ${Math.round((response.confidence || 0) * 100)}%</strong><br>Reviewer should inspect the cited source material before release approval.</div>
  `;
}

function renderDemoRagResponse(question) {
  const claims = state.systems.find((system) => system.name === "Claims Triage AI") || state.systems[0];
  qs("#ragAnswer").classList.remove("empty");
  qs("#ragAnswer").innerHTML = `
    <span class="metric-label">Question</span>
    <p>${escapeHtml(question)}</p>
    <h2>${escapeHtml(claims.name)} is ${escapeHtml(claims.decision.toLowerCase())} for release.</h2>
    <p>The system is classified as ${escapeHtml(claims.risk)} risk. The release gate depends on human oversight evidence, eval threshold performance, and data-contract status.</p>
    <div class="citation"><strong>Source: DPIA-CLM-014</strong><br>Reviewer override must include purpose, affected cohort, appeal route, and owner sign-off before production.</div>
    <div class="citation"><strong>Source: EU-AIA-HR-003 control map</strong><br>High-risk systems require documented risk management, data governance, logging, transparency, human oversight, accuracy, and cybersecurity controls.</div>
    <div class="citation"><strong>Confidence: ${claims.evidence}%</strong><br>Recommended action: complete oversight SOP, rerun bias eval, approve the schema contract update, then re-open release review.</div>
  `;
}

async function answerEvidence(event) {
  event.preventDefault();
  const question = qs("#evidenceQuestion").value.trim();
  const system = selectedEvidenceSystem();
  if (api.online && system?.id) {
    try {
      const response = await apiRequest("/evidence/query", {
        method: "POST",
        body: JSON.stringify({ systemId: system.id, question })
      });
      renderRagResponse(question, response);
      addAudit(`Compliance RAG answered a cited evidence query for ${system.name}.`);
      saveState();
      return;
    } catch (error) {
      setApiStatus(false, error.message);
      showToast("API unavailable; using demo answer");
    }
  }
  renderDemoRagResponse(question);
  addAudit(`Compliance RAG answered a demo evidence query for ${system?.name || "Claims Triage AI"}.`);
  saveState();
}

async function uploadEvidence(event) {
  event.preventDefault();
  const system = selectedEvidenceSystem();
  if (!api.online || !system?.id) {
    showToast("Start the API to index evidence");
    return;
  }
  try {
    await apiRequest("/evidence/documents", {
      method: "POST",
      body: JSON.stringify({
        systemId: system.id,
        type: qs("#evidenceType").value,
        title: qs("#evidenceTitle").value.trim(),
        sourceUri: qs("#evidenceSource").value.trim(),
        content: qs("#evidenceContent").value.trim(),
        metadata: { source: "web-prototype" }
      })
    });
    addAudit(`Evidence document indexed for ${system.name}.`);
    await loadEvidenceDocuments();
    showToast("Evidence indexed");
  } catch (error) {
    showToast(`Index failed: ${error.message}`);
  }
}

function runEval(event) {
  event.preventDefault();
  const name = qs("#evalSystem").value;
  const threshold = Number(qs("#evalThreshold").value);
  const system = state.systems.find((item) => item.name === name);
  const jitter = Math.round(Math.random() * 10 - 4);
  system.eval = Math.max(55, Math.min(96, system.eval + jitter));
  const pass = system.eval >= threshold && system.contract !== "Breach";
  system.decision = pass ? "Pass" : system.eval < threshold - 7 || system.contract === "Breach" ? "Blocked" : "Review";

  qs("#evalConsole").innerHTML = [
    `> queued eval run for ${name}`,
    "> loaded dataset and judge rubric",
    `> faithfulness: ${system.eval}%`,
    `> safety refusal: ${Math.max(70, system.eval - 3)}%`,
    `> latency guard: ${system.risk === "high" ? "pass" : "pass"}`,
    `> data contract: ${system.contract}`,
    `> release decision: ${system.decision.toUpperCase()}`
  ].join("<br>");

  addAudit(`Eval gate completed for ${name} with ${system.decision.toLowerCase()} decision.`);
  saveState();
  renderAll();
  showToast(`Eval gate: ${system.decision}`);
}

function simulateDrift() {
  const healthy = state.contracts.find((contract) => contract.status === "Healthy") || state.contracts[0];
  healthy.status = "Warning";
  healthy.coverage = Math.max(70, healthy.coverage - 9);
  healthy.detail = "Semantic drift detected: source column meaning changed without downstream approval.";
  const linked = state.systems.find((system) => system.name === "Bank KYC Assistant");
  if (linked) {
    linked.contract = "Warning";
    linked.decision = "Review";
    linked.gaps.push("KYC decision contract drift needs approval");
  }
  addAudit("Data contract monitor simulated semantic drift and opened release review.");
  saveState();
  renderAll();
  showToast("Drift detected and review opened");
}

function addSystem() {
  const next = {
    name: "Credit Appeal Advisor",
    owner: "Lending",
    risk: "high",
    basis: "Credit appeal support with human final decision",
    evidence: 69,
    eval: 75,
    contract: "Warning",
    decision: "Blocked",
    gaps: ["Adverse-impact test missing", "Appeal disclosure incomplete"]
  };
  state.systems.unshift(next);
  addAudit("Credit Appeal Advisor registered as high-risk AI system.");
  saveState();
  renderAll();
  showToast("Sample high-risk system added");
}

function runControls() {
  state.systems.forEach((system) => {
    if (system.contract === "Breach" || system.eval < 78 || system.evidence < 75) {
      system.decision = "Blocked";
    } else if (system.contract === "Warning" || system.eval < 85 || system.evidence < 82) {
      system.decision = "Review";
    } else {
      system.decision = "Pass";
    }
  });
  addAudit("Full control suite executed across evidence, eval, contract, and audit checks.");
  saveState();
  renderAll();
  showToast("Controls refreshed");
}

function buildEvidencePack() {
  const generatedAt = new Date().toISOString();
  const blockedSystems = state.systems.filter((system) => system.decision === "Blocked");
  const reviewSystems = state.systems.filter((system) => system.decision === "Review");

  return {
    product: "Devernance",
    generatedAt,
    scope: "EU AI Act, GDPR, data governance, evaluation gates, and audit-readiness controls",
    summary: {
      systems: state.systems.length,
      highRiskSystems: state.systems.filter((system) => system.risk === "high").length,
      blockedSystems: blockedSystems.length,
      reviewSystems: reviewSystems.length,
      averageEvidenceCoverage: Math.round(state.systems.reduce((sum, system) => sum + system.evidence, 0) / state.systems.length),
      averageEvalScore: Math.round(state.systems.reduce((sum, system) => sum + system.eval, 0) / state.systems.length)
    },
    releaseDecision: blockedSystems.length ? "Do not release until blockers are remediated" : reviewSystems.length ? "Release requires owner review" : "Release gate passed",
    systems: state.systems,
    dataContracts: state.contracts,
    controlInbox: state.controls,
    auditTrail: state.audit.map(([time, event]) => ({ time, event })),
    sourceConcepts: [
      "ComplianceGuard RAG: cited compliance answers and audit logs",
      "EvalForge: eval gates, model comparison, regression checks, and CI decisioning",
      "Data Contracts AI: schema drift, lineage, and CI/CD data-quality controls",
      "Spring Boot regulated-system PRDs: multi-tenancy, auditability, SAGA-style workflows, and security controls"
    ]
  };
}

function downloadJson(filename, payload) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function exportPack() {
  const pack = buildEvidencePack();
  const blocked = state.systems.filter((system) => system.decision === "Blocked").map((system) => system.name).join(", ");
  addAudit("Evidence pack export prepared for legal and compliance reviewers.");
  downloadJson(`devernance-evidence-${new Date().toISOString().slice(0, 10)}.json`, pack);
  saveState();
  showToast(`Evidence pack ready: blocked systems - ${blocked || "none"}`);
}

function initNav() {
  qsa(".nav").forEach((button) => {
    button.addEventListener("click", () => {
      qsa(".nav").forEach((item) => item.classList.remove("active"));
      qsa(".view").forEach((view) => view.classList.remove("active"));
      button.classList.add("active");
      qs(`#${button.dataset.view}`).classList.add("active");
      if (button.dataset.view === "command") drawRiskCanvas();
    });
  });
}

function renderAll() {
  renderMetrics();
  renderGateRows();
  renderInbox();
  renderSystems();
  renderContracts();
  renderAudit();
  drawRiskCanvas();
}

async function init() {
  applyTheme();
  initNav();
  await loadBackendSystems();
  renderAll();
  await loadEvidenceDocuments();
  qs("#themeToggle").addEventListener("click", () => {
    state.theme = state.theme === "dark" ? "light" : "dark";
    applyTheme();
    saveState();
    drawRiskCanvas();
  });
  qs("#riskFilter").addEventListener("change", drawRiskCanvas);
  qs("#evidenceForm").addEventListener("submit", answerEvidence);
  qs("#evidenceUploadForm").addEventListener("submit", uploadEvidence);
  qs("#evidenceSystem").addEventListener("change", loadEvidenceDocuments);
  qs("#evalForm").addEventListener("submit", runEval);
  qs("#simulateDrift").addEventListener("click", simulateDrift);
  qs("#addSystem").addEventListener("click", addSystem);
  qs("#runControls").addEventListener("click", runControls);
  qs("#exportPack").addEventListener("click", exportPack);
}

window.devernance = {
  buildEvidencePack,
  getState: () => structuredClone(state)
};

init();
