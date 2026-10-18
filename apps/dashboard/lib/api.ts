import type {
  AiSystem,
  ApiKeyCreated,
  ApiKeyView,
  BillingSummary,
  ApprovalWorkflow,
  AuditEvent,
  CertificationReadiness,
  Control,
  Me,
  ControlStatus,
  DataContract,
  DeterminationQuestionnaire,
  DeterminationRun,
  DriftEvent,
  EvalRun,
  EvalDataset,
  EvalRunOperationsView,
  EvidenceDocument,
  EvidencePack,
  FrameworkView,
  AuAdmPack,
  Assessment,
  CorpusView,
  EvidenceQueryResponse,
  ProposalList,
  MappingProposal,
  RegItem,
  RegMonitorFeed,
  ReleaseGateResponse,
  OpsReadiness,
  SectorPack,
  SectorPacksResponse,
  SystemControl,
  WorkflowNotification,
  WorkspaceInvite,
  WorkspaceUser,
} from "./types";
import { dispatchPlanLimit, reportPlanLimit } from "./plan-limit";
import type {
  PublicClaimsArtifacts,
  PublicClaimsIndex,
  PublicClaimsTeaser,
} from "./public-claims";

import { loginRedirectHref } from "./auth-redirect";
import { SYSTEM_EXPORTS, downloadFilename, exportPath, type SystemExportKind } from "./export-view";

const BASE = "/api/proxy";

/** Parallel 401s must not keep assigning window.location (reload flicker). */
let loginRedirectStarted = false;

function redirectToLoginOnUnauthorized() {
  if (typeof window === "undefined" || loginRedirectStarted) return;
  const href = loginRedirectHref(window.location.pathname, window.location.search);
  if (!href) return;
  loginRedirectStarted = true;
  window.location.assign(href);
}

export { PLAN_LIMIT_EVENT } from "./plan-limit";

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

interface RequestOptions {
  /** The caller shows its own inline upgrade note, so a 402 does not open the shell's upgrade prompt. */
  quietPlanLimit?: boolean;
}

async function request<T>(path: string, init?: RequestInit, options?: RequestOptions): Promise<T> {
  const headers = new Headers(init?.headers);
  const isForm = typeof FormData !== "undefined" && init?.body instanceof FormData;
  if (init?.body !== undefined && !isForm && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const res = await fetch(`${BASE}${path}`, { ...init, headers });
  if (res.status === 401) {
    redirectToLoginOnUnauthorized();
  }
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`;
    try {
      const body = await res.json();
      message = body.message ?? body.error ?? body.detail ?? message;
    } catch {
      // keep status text
    }
    if (res.status === 402 && !options?.quietPlanLimit) {
      // Plan limit: the shell shows an upgrade prompt for any screen that hits one.
      dispatchPlanLimit(message);
    }
    throw new ApiError(res.status, message);
  }
  return res.status === 204 ? (null as T) : res.json();
}

function triggerBrowserDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export interface CreateSystemPayload {
  name: string;
  owner: string;
  purpose: string;
  riskClass: "MINIMAL" | "LIMITED" | "HIGH" | "PROHIBITED";
  riskBasis: string;
  deploymentRegion: string;
  sector?: string;
  decisionImpact?: string;
}

export interface ClassifyPayload {
  riskClass: "MINIMAL" | "LIMITED" | "HIGH" | "PROHIBITED";
  basis: string;
  humanOversightRequired: boolean;
  sector?: string;
  decisionImpact?: string;
  affectedUsers?: string[];
}

export type DpaAcceptance = { version: string | null; acceptedAt: string | null };

export const api = {
  packs: {
    /** AU automated-decision disclosure pack (Business and above). Inline upgrade note on 402. */
    auAdm: () => request<AuAdmPack>("/packs/au-adm", undefined, { quietPlanLimit: true }),
  },
  systems: {
    list: () => request<AiSystem[]>("/systems"),
    get: (id: string) => request<AiSystem>(`/systems/${id}`),
    create: (payload: CreateSystemPayload) =>
      request<{ id: string; releaseDecision: string; createdAt: string }>("/systems", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
    classify: (id: string, payload: ClassifyPayload) =>
      request<unknown>(`/systems/${id}/risk-classification`, {
        method: "POST",
        body: JSON.stringify(payload),
      }),
    releaseGate: (id: string) => request<ReleaseGateResponse>(`/systems/${id}/release-gate`),
    controls: (id: string) => request<SystemControl[]>(`/systems/${id}/controls`),
    updateControl: (systemId: string, controlId: string, status: ControlStatus, notes?: string) =>
      request<SystemControl>(`/systems/${systemId}/controls/${controlId}`, {
        method: "PUT",
        body: JSON.stringify({ status, notes }),
      }),
    /** Primary sealed JSON evidence pack (authenticated proxy). */
    evidencePack: (id: string) => request<EvidencePack>(`/systems/${id}/evidence-pack`),
    assessment: (id: string) => request<Assessment>(`/systems/${id}/assessment`),
    /** Framework crosswalk coverage (relevance, not conformity). 402 when the plan lacks it. */
    framework: (id: string, framework: string) =>
      request<FrameworkView>(`/systems/${id}/frameworks/${framework}`, undefined, { quietPlanLimit: true }),
    evgraphArtifacts: (id: string) =>
      request<Record<string, unknown>>(`/systems/${id}/evgraph-artifacts`),
    conformity: (id: string) => request<Record<string, unknown>>(`/systems/${id}/conformity`),
    /**
     * Phase 6 PDF export of the sealed pack. Returns contentSha256 from response header.
     */
    evidencePackPdf: async (id: string): Promise<{ contentSha256: string; filename: string }> => {
      const res = await fetch(`${BASE}/systems/${id}/evidence-pack.pdf`);
      if (res.status === 401) {
        redirectToLoginOnUnauthorized();
      }
      const pdfLimit = await reportPlanLimit(res);
      if (pdfLimit) throw new ApiError(402, pdfLimit);
      if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
      const contentSha256 = res.headers.get("X-Content-Sha256") ?? "";
      const disposition = res.headers.get("Content-Disposition") ?? "";
      const match = /filename="?([^";]+)"?/i.exec(disposition);
      const filename =
        match?.[1] ?? `evidence-pack-${id}-${new Date().toISOString().slice(0, 10)}.pdf`;
      const blob = await res.blob();
      triggerBrowserDownload(blob, filename);
      return { contentSha256, filename };
    },
    /** Questionnaire CSV or GRC ZIP for one system. 402 opens the upgrade prompt and throws ApiError. */
    downloadExport: async (id: string, kind: SystemExportKind): Promise<{ filename: string }> => {
      const descriptor = SYSTEM_EXPORTS.find((e) => e.kind === kind)!;
      const res = await fetch(`${BASE}${exportPath(id, kind)}`);
      if (res.status === 401) {
        redirectToLoginOnUnauthorized();
      }
      const limit = await reportPlanLimit(res);
      if (limit) throw new ApiError(402, limit);
      if (!res.ok) throw new ApiError(res.status, `${res.status} ${res.statusText}`);
      const filename = downloadFilename(res.headers.get("Content-Disposition"), descriptor.fallbackFilename(id));
      triggerBrowserDownload(await res.blob(), filename);
      return { filename };
    },
    /** Certification readiness score + gaps (not legal certification). */
    certificationReadiness: (id: string) =>
      request<CertificationReadiness>(`/systems/${id}/certification-readiness`),
    certificationReadinessExport: async (
      id: string,
      format: "json" | "pdf" = "json"
    ): Promise<{ readinessStatus: string; filename: string }> => {
      const res = await fetch(`${BASE}/systems/${id}/certification-readiness/export`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ format }),
      });
      if (res.status === 401) {
        redirectToLoginOnUnauthorized();
      }
      const exportLimit = await reportPlanLimit(res);
      if (exportLimit) throw new ApiError(402, exportLimit);
      if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
      const readinessStatus = res.headers.get("X-Readiness-Status") ?? "";
      const disposition = res.headers.get("Content-Disposition") ?? "";
      const match = /filename="?([^";]+)"?/i.exec(disposition);
      const filename =
        match?.[1] ??
        `certification-readiness-${id}.${format === "pdf" ? "pdf" : "json"}`;
      const blob = await res.blob();
      triggerBrowserDownload(blob, filename);
      return { readinessStatus, filename };
    },
  },
  controls: {
    catalog: () => request<Control[]>("/controls"),
  },
  workflows: {
    open: () => request<ApprovalWorkflow[]>("/workflows/open"),
    mine: () => request<ApprovalWorkflow[]>("/workflows/mine"),
    list: (systemId: string) =>
      request<ApprovalWorkflow[]>(`/systems/${systemId}/workflows`),
    active: (systemId: string) =>
      request<ApprovalWorkflow>(`/systems/${systemId}/workflows/active`),
    approve: (
      systemId: string,
      workflowId: string,
      stageId: string,
      rationale?: string,
      oversightEvidence?: string
    ) =>
      request<ApprovalWorkflow>(
        `/systems/${systemId}/workflows/${workflowId}/stages/${stageId}/approve`,
        { method: "POST", body: JSON.stringify({ rationale, oversightEvidence }) }
      ),
    reject: (systemId: string, workflowId: string, stageId: string, rationale: string) =>
      request<ApprovalWorkflow>(
        `/systems/${systemId}/workflows/${workflowId}/stages/${stageId}/reject`,
        { method: "POST", body: JSON.stringify({ rationale }) }
      ),
    override: (systemId: string, workflowId: string, stageId: string, rationale: string) =>
      request<ApprovalWorkflow>(
        `/systems/${systemId}/workflows/${workflowId}/stages/${stageId}/override`,
        { method: "POST", body: JSON.stringify({ rationale }) }
      ),
  },
  notifications: {
    mine: () => request<WorkflowNotification[]>("/workflow-notifications/mine"),
  },
  evidence: {
    documents: (systemId: string) =>
      request<EvidenceDocument[]>(`/evidence/systems/${systemId}/documents`),
    query: (systemId: string, question: string) =>
      request<EvidenceQueryResponse>("/evidence/query", {
        method: "POST",
        body: JSON.stringify({ systemId, question }),
      }),
    index: (payload: {
      systemId: string;
      type: string;
      title: string;
      sourceUri: string;
      content?: string;
      metadata?: Record<string, string>;
    }) =>
      request<EvidenceDocument>("/evidence/documents", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
    upload: ({ systemId, type, title, file }: { systemId: string; type: string; title: string; file: File }) => {
      const form = new FormData();
      form.set("systemId", systemId);
      form.set("type", type);
      form.set("title", title);
      form.set("file", file);
      return request<EvidenceDocument>("/evidence/documents/upload", { method: "POST", body: form });
    },
  },
  evals: {
    operations: () => request<EvalRunOperationsView>("/eval-runs/operations"),
    datasets: () => request<EvalDataset[]>("/eval-datasets"),
    createDataset: (payload: { name: string; version: string; sampleCount: number; golden: boolean }) =>
      request<EvalDataset>("/eval-datasets", { method: "POST", body: JSON.stringify(payload) }),
    get: (id: string) => request<EvalRun>(`/eval-runs/${id}`),
    create: (payload: {
      systemId: string;
      dataset: string;
      modelVersion: string;
      promptVersion: string;
      threshold: number;
    }) =>
      request<{ runId: string; status: string }>("/eval-runs", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
    execute: (id: string) =>
      request<EvalRun>(`/eval-runs/${id}/execute`, { method: "POST" }),
  },
  contracts: {
    list: (systemId?: string) =>
      request<DataContract[]>(
        systemId ? `/data-contracts?systemId=${systemId}` : "/data-contracts"
      ),
    get: (id: string) => request<DataContract>(`/data-contracts/${id}`),
    driftEvents: (id: string) =>
      request<DriftEvent[]>(`/data-contracts/${id}/drift-events`),
    updateDriftEvent: (contractId: string, eventId: string, status: "ACKNOWLEDGED" | "RESOLVED") =>
      request<DriftEvent>(`/data-contracts/${contractId}/drift-events/${eventId}`, {
        method: "PATCH",
        body: JSON.stringify({ status }),
      }),
  },
  audit: {
    list: (systemId?: string) =>
      request<AuditEvent[]>(
        systemId ? `/audit-events?systemId=${systemId}` : "/audit-events"
      ),
  },
  determination: {
    questionnaire: () => request<DeterminationQuestionnaire>("/determination/questionnaire"),
    createRun: (systemId: string, answers: Record<string, unknown>) =>
      request<DeterminationRun>(`/systems/${systemId}/determination/runs`, {
        method: "POST",
        body: JSON.stringify({ answers }),
      }),
    getRun: (systemId: string, runId: string) =>
      request<DeterminationRun>(`/systems/${systemId}/determination/runs/${runId}`),
    listRuns: (systemId: string) =>
      request<DeterminationRun[]>(`/systems/${systemId}/determination/runs`),
  },
  regMonitor: {
    items: (params?: { since?: string; reviewed?: boolean }) => {
      const q = new URLSearchParams();
      if (params?.since) q.set("since", params.since);
      if (params?.reviewed !== undefined) q.set("reviewed", String(params.reviewed));
      const suffix = q.toString() ? `?${q.toString()}` : "";
      return request<RegMonitorFeed>(`/reg-monitor/items${suffix}`);
    },
    relevant: (systemId: string) =>
      request<RegMonitorFeed>(`/systems/${systemId}/reg-monitor/relevant`),
    markReviewed: (itemId: string, notes?: string) =>
      request<RegItem>(`/reg-monitor/items/${itemId}/review`, {
        method: "POST",
        body: JSON.stringify({ notes }),
      }),
  },
  billing: {
    get: () => request<BillingSummary>("/billing"),
    checkout: (plan: "TEAM" | "BUSINESS", interval: "MONTHLY" | "YEARLY") =>
      request<{ checkoutUrl: string }>("/billing/checkout", {
        method: "POST",
        body: JSON.stringify({ plan, interval }),
      }),
    portal: () => request<{ url: string }>("/billing/portal", { method: "POST" }),
  },
  admin: {
    users: () => request<WorkspaceUser[]>("/admin/users"),
    invites: () => request<WorkspaceInvite[]>("/admin/users/invites"),
    inviteUser: (payload: { email: string; role: string }) =>
      request<WorkspaceInvite>("/admin/users/invites", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
    createTenant: (payload: {
      name: string;
      plan?: string;
      dataRegion?: string;
      adminEmail: string;
      adminPassword: string;
    }) =>
      request<{ tenant: { id: string; name: string }; admin: WorkspaceUser }>("/admin/tenants", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
  },
  me: () => request<Me>("/me"),
  account: {
    /** Downloads every workspace table as a zip. Admin only. */
    exportAll: async () => {
      const res = await fetch(`${BASE}/account/export`);
      if (res.status === 401) redirectToLoginOnUnauthorized();
      if (!res.ok) throw new ApiError(res.status, "Export failed");
      triggerBrowserDownload(await res.blob(), "devernance-export.zip");
    },
    /** Schedules deletion in 30 days; the typed name must match exactly. */
    deleteWorkspace: (confirmOrganisationName: string) =>
      request<{ status: string }>("/account", {
        method: "DELETE",
        body: JSON.stringify({ confirmOrganisationName }),
      }),
    /** The DPA version this workspace accepted, if any. */
    dpaAcceptance: () => request<DpaAcceptance>("/account/dpa-acceptance"),
    /** Admin accepts the DPA for the organisation. */
    acceptDpa: (version: string) =>
      request<DpaAcceptance>("/account/dpa-acceptance", { method: "POST", body: JSON.stringify({ version }) }),
  },
  apiKeys: {
    list: () => request<ApiKeyView[]>("/api-keys"),
    create: (name: string) =>
      request<ApiKeyCreated>("/api-keys", { method: "POST", body: JSON.stringify({ name }) }),
    revoke: (id: string) => request<null>(`/api-keys/${id}`, { method: "DELETE" }),
  },
  ops: {
    readiness: () => request<OpsReadiness>("/ops/readiness"),
  },
  publicClaims: {
    list: () => request<PublicClaimsIndex>("/public-claims"),
    get: (slug: string) => request<PublicClaimsTeaser>(`/public-claims/${slug}`),
    evgraph: (slug: string) =>
      request<PublicClaimsArtifacts>(`/public-claims/${slug}/evgraph`),
  },
  corpus: {
    current: () => request<CorpusView>("/corpus"),
  },
  proposals: {
    list: (systemId: string) => request<ProposalList>(`/systems/${systemId}/proposals`),
    create: (
      systemId: string,
      payload: {
        relation: string;
        provisionKey?: string;
        excerpt?: string;
        corpusVersion?: string;
      }
    ) =>
      request<MappingProposal>(`/systems/${systemId}/proposals`, {
        method: "POST",
        body: JSON.stringify(payload),
      }),
    accept: (systemId: string, proposalId: string) =>
      request<MappingProposal>(`/systems/${systemId}/proposals/${proposalId}/accept`, {
        method: "POST",
      }),
    reject: (systemId: string, proposalId: string) =>
      request<MappingProposal>(`/systems/${systemId}/proposals/${proposalId}/reject`, {
        method: "POST",
      }),
    mapDocuments: (systemId: string, documents: { title: string; text: string }[]) =>
      request<MappingProposal[]>(`/systems/${systemId}/proposals/map`, {
        method: "POST",
        body: JSON.stringify({ documents }),
      }),
  },
  sectorPacks: {
    list: () => request<SectorPacksResponse>("/sector-packs"),
    get: (packId: string) => request<SectorPack>(`/sector-packs/${packId}`),
    resolve: (sector: string) =>
      request<SectorPack>(`/sector-packs/resolve?sector=${encodeURIComponent(sector)}`),
  },
};
