"use client";

import { useState } from "react";
import { Sheet } from "./ui/sheet";
import { ApprovalWorkflowPanel } from "./approval-workflow";
import { MOCK_WORKFLOWS } from "@/lib/mock-data";
import { RiskBadge } from "./risk-badge";
import { DecisionBadge } from "./decision-badge";
import { normaliseDecision, cn, formatDate } from "@/lib/utils";
import type { AiSystem, DataContract, DriftEvent, AuditEvent } from "@/lib/types";
import { useDashboard } from "@/context/dashboard-context";
import { isPublicClaimsSystem, publicClaimsSlug } from "@/lib/public-claims";
import { api } from "@/lib/api";
import Link from "next/link";
import { ObligationMapWizard } from "./obligation-map-wizard";
import { FrameworksPanel } from "./frameworks-panel";
import { SystemExportsPanel } from "./system-exports-panel";
import { Button } from "./ui/button";
import {
  ShieldAlert,
  AlertTriangle,
  CheckCircle2,
  FileText,
  Clock,
  Database,
  Calendar,
  Activity,
  Layers,
  FileSpreadsheet,
  Scale,
  Download,
} from "lucide-react";

interface SystemDetailsSheetProps {
  system: AiSystem | null;
  isOpen: boolean;
  onClose: () => void;
  auditEvents: AuditEvent[];
}

export function SystemDetailsSheet({ system, isOpen, onClose, auditEvents }: SystemDetailsSheetProps) {
  const [activeTab, setActiveTab] = useState<"details" | "frameworks" | "approval">("details");
  const [obligationWizardOpen, setObligationWizardOpen] = useState(false);
  const { activeRole } = useDashboard();

  if (!system) return null;

  const workflows = MOCK_WORKFLOWS[system.id] ?? [];
  const activeWorkflow = workflows.find((w) => w.status === "OPEN") ?? null;

  const decision = normaliseDecision(system.releaseDecision);
  const systemAudits = auditEvents
    .filter((e) => e.systemId === system.id)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

  // Dynamic EU Act Checklist Obligations
  const obligations = [
    { name: "Risk Management System (Art. 9)", status: "PASS", desc: "Systematic risk mapping for high-risk pipelines." },
    { name: "Data Governance & Quality (Art. 10)", status: system.dataContractStatus === "BREACH" ? "BLOCKED" : system.dataContractStatus === "WARNING" ? "REVIEW" : "PASS", desc: "Training/validation datasets validation checks." },
    { name: "Technical Documentation (Art. 11)", status: system.evidenceCoverage > 80 ? "PASS" : "REVIEW", desc: "Up-to-date model cards and system architecture sheets." },
    { name: "Automatic Logging System (Art. 12)", status: "PASS", desc: "Immutable audit events collection." },
    { name: "Transparency & Information (Art. 13)", status: system.riskClass === "limited" ? "PASS" : "PASS", desc: "Clear user disclosure warnings and disclosures." },
    { name: "Human Oversight Procedures (Art. 14)", status: system.openGaps.some(g => g.toLowerCase().includes("human") || g.toLowerCase().includes("oversight")) ? "BLOCKED" : "PASS", desc: "Designated oversight procedure and override SOP." },
    { name: "Accuracy, Robustness & Security (Art. 15)", status: system.evalScore >= 85 ? "PASS" : "BLOCKED", desc: "Benchmarked performance metrics against judge sets." }
  ];

  return (
    <Sheet
      isOpen={isOpen}
      onClose={onClose}
      title={system.name}
      description={`Owned by ${system.owner} · Registered in region: ${system.deploymentRegion}`}
    >
      <div className="flex gap-1 bg-muted/40 rounded-lg p-1 border border-border">
        <button
          onClick={() => setActiveTab("details")}
          className={cn(
            "flex-1 text-xs py-1.5 rounded-md font-medium transition-colors",
            activeTab === "details"
              ? "bg-card text-foreground shadow-sm"
              : "text-muted-foreground hover:text-foreground"
          )}
        >
          Details
        </button>
        <button
          onClick={() => setActiveTab("frameworks")}
          className={cn(
            "flex-1 text-xs py-1.5 rounded-md font-medium transition-colors",
            activeTab === "frameworks"
              ? "bg-card text-foreground shadow-sm"
              : "text-muted-foreground hover:text-foreground"
          )}
        >
          Frameworks
        </button>
        <button
          onClick={() => setActiveTab("approval")}
          className={cn(
            "flex-1 text-xs py-1.5 rounded-md font-medium transition-colors",
            activeTab === "approval"
              ? "bg-card text-foreground shadow-sm"
              : "text-muted-foreground hover:text-foreground"
          )}
        >
          Approval
          {activeWorkflow && (
            <span className="ml-1.5 inline-flex items-center justify-center w-3.5 h-3.5 rounded-full bg-amber-500 text-white text-[9px] font-bold">
              !
            </span>
          )}
        </button>
      </div>

      <ObligationMapWizard
        isOpen={obligationWizardOpen}
        onClose={() => setObligationWizardOpen(false)}
        systemId={system.id}
        systemName={system.name}
      />

      {activeTab === "details" && (
        <>
      {/* Risk and Decision badges */}
      <div className="flex items-center gap-3 bg-muted/40 p-4 rounded-xl border border-border">
        <div className="flex-1">
          <p className="text-[10px] uppercase font-semibold text-muted-foreground tracking-wider mb-1">Release Gate Status</p>
          <DecisionBadge decision={decision} />
        </div>
        <div className="flex-1 border-l border-border pl-4">
          <p className="text-[10px] uppercase font-semibold text-muted-foreground tracking-wider mb-1">EU Risk Tier</p>
          <RiskBadge risk={system.riskClass} />
        </div>
      </div>

      {/* Purpose */}
      <div>
        <h4 className="text-xs font-semibold text-foreground uppercase tracking-widest flex items-center gap-1.5 mb-2">
          <Database className="w-3.5 h-3.5 text-muted-foreground" />
          System Purpose
        </h4>
        <p className="text-xs text-muted-foreground leading-relaxed bg-muted/20 border border-border p-3 rounded-lg">
          {system.purpose}
        </p>
      </div>

      {isPublicClaimsSystem(system) && (
        <div className="rounded-xl border border-amber-200/80 dark:border-amber-900/40 bg-amber-50/40 dark:bg-amber-950/15 p-3.5 space-y-2">
          <p className="text-[11px] font-semibold text-foreground">Public-claims teaser</p>
          <p className="text-[10px] text-muted-foreground leading-relaxed">
            Named organisation is not a customer. Artifacts are reconstructed from public pages.
            Evgraph is a library: missing approval timestamps are INCONCLUSIVE unless you pass
            --strict; empty dataset licenses fail --gate.
          </p>
          <div className="flex flex-col gap-2">
            <Link href="/public-claims" className="text-[10px] text-primary font-medium hover:underline">
              Open public-claims catalog
            </Link>
            <Button
              size="sm"
              variant="outline"
              className="h-7 w-full text-[10px] whitespace-nowrap"
              onClick={async (event) => {
                event.stopPropagation();
                const slug = publicClaimsSlug(system);
                if (!slug) return;
                const files = await api.publicClaims.evgraph(slug);
                const blob = new Blob([JSON.stringify(files, null, 2)], { type: "application/json" });
                const url = URL.createObjectURL(blob);
                const a = document.createElement("a");
                a.href = url;
                a.download = `${slug}-evgraph-artifacts.json`;
                document.body.appendChild(a);
                a.click();
                a.remove();
                URL.revokeObjectURL(url);
              }}
            >
              <Download className="w-3 h-3 mr-1" />
              Evgraph artifacts
            </Button>
          </div>
        </div>
      )}

      {/* Legal Basis */}
      <div>
        <h4 className="text-xs font-semibold text-foreground uppercase tracking-widest flex items-center gap-1.5 mb-2">
          <Layers className="w-3.5 h-3.5 text-muted-foreground" />
          Regulatory Basis
        </h4>
        <p className="text-xs text-muted-foreground leading-relaxed italic bg-muted/20 border border-border p-3 rounded-lg">
          {system.riskBasis}
        </p>
      </div>

      {/* Assisted obligation map */}
      <div className="rounded-xl border border-border bg-muted/20 p-3.5 space-y-2">
        <h4 className="text-xs font-semibold text-foreground uppercase tracking-widest flex items-center gap-1.5">
          <Scale className="w-3.5 h-3.5 text-muted-foreground" />
          Obligation map
        </h4>
        <p className="text-[10px] text-muted-foreground leading-snug">
          Suggested applicability / obligation map only. Assisted determination — not legal advice.
          Requires human legal review. Never a compliance or certification claim.
        </p>
        <Button size="sm" variant="outline" className="w-full" onClick={() => setObligationWizardOpen(true)}>
          Open obligation map wizard
        </Button>
      </div>

      {/* Metrics */}
      <div className="grid grid-cols-2 gap-3">
        <div className="bg-muted/30 border border-border rounded-xl p-3.5">
          <p className="text-[10px] text-muted-foreground uppercase font-semibold tracking-wider">Evaluation Score</p>
          <p className={cn(
            "text-2xl font-bold mt-1",
            system.evalScore >= 85 ? "text-emerald-600 dark:text-emerald-400" : "text-amber-600 dark:text-amber-400"
          )}>{system.evalScore}%</p>
          <p className="text-[10px] text-muted-foreground mt-1">Pass threshold: 85%</p>
        </div>
        <div className="bg-muted/30 border border-border rounded-xl p-3.5">
          <p className="text-[10px] text-muted-foreground uppercase font-semibold tracking-wider">Evidence Coverage</p>
          <p className="text-2xl font-bold mt-1 text-primary">{system.evidenceCoverage}%</p>
          <p className="text-[10px] text-muted-foreground mt-1">
            Computed from indexed evidence: DPIA, model card, policy, control map, vendor docs (by risk class)
          </p>
        </div>
      </div>

      {/* Open Control Gaps */}
      {system.openGaps.length > 0 && (
        <div>
          <h4 className="text-xs font-semibold text-foreground uppercase tracking-widest flex items-center gap-1.5 mb-2.5">
            <ShieldAlert className="w-3.5 h-3.5 text-red-500" />
            Unresolved Control Gaps
          </h4>
          <div className="space-y-2">
            {system.openGaps.map((gap, i) => (
              <div key={i} className="flex items-start gap-2.5 bg-red-50/40 dark:bg-red-950/20 border border-red-100 dark:border-red-950 rounded-lg p-3 text-xs leading-normal">
                <AlertTriangle className="w-4 h-4 text-amber-500 shrink-0 mt-0.5" />
                <p className="text-muted-foreground text-[11px]">{gap}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* EU Act Compliance Checklist */}
      <div>
        <h4 className="text-xs font-semibold text-foreground uppercase tracking-widest flex items-center gap-1.5 mb-2.5">
          <CheckCircle2 className="w-3.5 h-3.5 text-muted-foreground" />
          EU AI Act Obligation Checklist
        </h4>
        <div className="divide-y divide-border border border-border rounded-xl overflow-hidden bg-card">
          {obligations.map((ob, i) => {
            const status = ob.status;
            return (
              <div key={i} className="p-3 flex items-start gap-3 hover:bg-muted/10 transition-colors">
                <div className="mt-0.5 shrink-0">
                  {status === "PASS" && <CheckCircle2 className="w-4 h-4 text-emerald-500" />}
                  {status === "REVIEW" && <AlertTriangle className="w-4 h-4 text-amber-500" />}
                  {status === "BLOCKED" && <ShieldAlert className="w-4 h-4 text-red-500" />}
                </div>
                <div className="flex-1">
                  <div className="flex items-center justify-between gap-2">
                    <p className="text-[11px] font-semibold text-foreground leading-snug">{ob.name}</p>
                    <span className={cn(
                      "text-[9px] uppercase font-bold px-1.5 py-0.2 rounded-full",
                      status === "PASS" && "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/30 dark:text-emerald-400",
                      status === "REVIEW" && "bg-amber-50 text-amber-700 dark:bg-amber-950/30 dark:text-amber-400",
                      status === "BLOCKED" && "bg-red-50 text-red-700 dark:bg-red-950/30 dark:text-red-400"
                    )}>{status}</span>
                  </div>
                  <p className="text-[10px] text-muted-foreground mt-0.5 leading-snug">{ob.desc}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Audit History */}
      <div>
        <h4 className="text-xs font-semibold text-foreground uppercase tracking-widest flex items-center gap-1.5 mb-2.5">
          <Clock className="w-3.5 h-3.5 text-muted-foreground" />
          Recent Audit History
        </h4>
        {systemAudits.length === 0 ? (
          <p className="text-xs text-muted-foreground italic">No audit records for this system.</p>
        ) : (
          <div className="space-y-3 pl-2 border-l border-border mt-1">
            {systemAudits.slice(0, 4).map((audit) => (
              <div key={audit.id} className="relative pl-4 flex flex-col gap-0.5">
                <div className="absolute -left-[13px] top-1 w-2.5 h-2.5 rounded-full border border-border bg-card" />
                <div className="flex items-center justify-between text-[10px] text-muted-foreground">
                  <span className="font-semibold text-foreground">{audit.eventType.replace(/_/g, " ")}</span>
                  <span className="tabular-nums" suppressHydrationWarning>{formatDate(audit.createdAt)}</span>
                </div>
                {!!audit.payload.reason && (
                  <p className="text-[10px] text-muted-foreground italic leading-normal">“{String(audit.payload.reason)}”</p>
                )}
                {!!audit.payload.decision && (
                  <p className="text-[10px] text-muted-foreground leading-normal">
                    Decision calculated: <span className={cn(
                      "font-semibold",
                      audit.payload.decision === "PASS" && "text-emerald-500",
                      audit.payload.decision === "BLOCKED" && "text-red-500",
                      audit.payload.decision === "REVIEW" && "text-amber-500"
                    )}>{String(audit.payload.decision)}</span>
                  </p>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
        </>
      )}

      {activeTab === "frameworks" && (
        <div className="space-y-6">
          <SystemExportsPanel systemId={system.id} />
          <FrameworksPanel systemId={system.id} />
          <FrameworksPanel
            systemId={system.id}
            framework="iso-42001"
            label="ISO/IEC 42001:2023 Annex A (identifiers; titles are our paraphrases)"
            upgradeText="Available on Business and above"
          />
          <FrameworksPanel
            systemId={system.id}
            framework="colorado-sb26-189"
            label="Colorado SB 26-189"
            upgradeText="Available on Business and above"
          />
        </div>
      )}

      {activeTab === "approval" && (
        <ApprovalWorkflowPanel
          workflows={workflows}
          activeWorkflow={activeWorkflow}
          activeRole={activeRole}
          onApprove={(wId, sId, r) => console.log("approve", wId, sId, r)}
          onReject={(wId, sId, r) => console.log("reject", wId, sId, r)}
          onOverride={(wId, sId, r) => console.log("override", wId, sId, r)}
        />
      )}
    </Sheet>
  );
}

interface ContractDetailsSheetProps {
  contract: DataContract | null;
  isOpen: boolean;
  onClose: () => void;
  driftEvents: DriftEvent[];
  systems: AiSystem[];
}

export function ContractDetailsSheet({ contract, isOpen, onClose, driftEvents, systems }: ContractDetailsSheetProps) {
  if (!contract) return null;

  const { acknowledgeDrift, resolveDrift } = useDashboard();
  const linkedSystem = systems.find((s) => s.id === contract.systemId);
  const openEvents = driftEvents.filter((e) => e.contractId === contract.id && e.status !== "RESOLVED");

  // Mock Schemas based on Name
  const getMockFields = (name: string) => {
    if (name.includes("claims")) {
      return [
        { field: "claim_id", type: "string (UUID)", description: "Primary Key event ID" },
        { field: "claimant_age", type: "integer", description: "Age of claimant. Privacy masked." },
        { field: "denial_reason_category", type: "enum (12 values)", description: "Fairness monitored classification category." },
        { field: "claim_amount_band", type: "enum (5 values)", description: "Claim range classification brackets." },
        { field: "postal_code", type: "string (first 3 chars)", description: "Locality code. Anonymised." }
      ];
    }
    if (name.includes("candidate")) {
      return [
        { field: "candidate_id", type: "string (UUID)", description: "Primary candidate key" },
        { field: "years_experience", type: "float", description: "Standardised candidate experience level" },
        { field: "education_history", type: "string (JSON)", description: "Parsed education history list" },
        { field: "score_calibration", type: "float", description: "Bias calibration scoring factor" }
      ];
    }
    return [
      { field: "id", type: "string", description: "Identifier token" },
      { field: "content_chunks", type: "array (string)", description: "Raw document chunks" },
      { field: "patient_language", type: "string", description: "Primary patient dialect. GDPR constrained." }
    ];
  };

  const fields = getMockFields(contract.name);

  return (
    <Sheet
      isOpen={isOpen}
      onClose={onClose}
      title={contract.name}
      description={`Owned by ${contract.owner} · Version ${contract.version}`}
    >
      {/* Status Indicators */}
      <div className="flex items-center gap-3 bg-muted/40 p-4 rounded-xl border border-border">
        <div className="flex-1">
          <p className="text-[10px] uppercase font-semibold text-muted-foreground tracking-wider mb-1">Contract Status</p>
          <span className={cn(
            "inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold",
            contract.status === "HEALTHY" && "bg-emerald-50 text-emerald-700 dark:bg-emerald-950/30 dark:text-emerald-400",
            contract.status === "WARNING" && "bg-amber-50 text-amber-700 dark:bg-amber-950/30 dark:text-amber-400",
            contract.status === "BREACH" && "bg-red-50 text-red-700 dark:bg-red-950/30 dark:text-red-400"
          )}>{contract.status}</span>
        </div>
        <div className="flex-1 border-l border-border pl-4">
          <p className="text-[10px] uppercase font-semibold text-muted-foreground tracking-wider mb-1">Linked AI System</p>
          <p className="text-xs font-semibold text-foreground truncate">{linkedSystem ? linkedSystem.name : "Unmapped"}</p>
        </div>
      </div>

      {/* Field coverage */}
      <div className="space-y-1.5">
        <div className="flex justify-between items-center text-xs">
          <span className="text-muted-foreground">Field Schema Coverage</span>
          <span className="font-semibold text-foreground">{contract.coverage}%</span>
        </div>
        <div className="h-2 rounded-full bg-muted overflow-hidden">
          <div
            className={cn(
              "h-full rounded-full transition-all",
              contract.status === "BREACH" && "bg-red-500",
              contract.status === "WARNING" && "bg-amber-500",
              contract.status === "HEALTHY" && "bg-emerald-500"
            )}
            style={{ width: `${contract.coverage}%` }}
          />
        </div>
      </div>

      {/* Active Schema Drift Log */}
      <div>
        <h4 className="text-xs font-semibold text-foreground uppercase tracking-widest flex items-center gap-1.5 mb-2.5">
          <Activity className="w-3.5 h-3.5 text-muted-foreground" />
          Active Schema Drift Log
        </h4>
        {openEvents.length === 0 ? (
          <div className="flex items-center gap-2 p-3.5 border border-dashed border-border rounded-xl text-xs text-emerald-600 dark:text-emerald-400 bg-emerald-50/20">
            <CheckCircle2 className="w-4 h-4" />
            No open schema drift events detected.
          </div>
        ) : (
          <div className="space-y-2.5">
            {openEvents.map((event) => (
              <div key={event.id} className="border border-border bg-card rounded-xl p-3.5 text-xs space-y-1.5 shadow-xs">
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono font-semibold text-foreground">{event.field ?? "global_schema"}</span>
                  <span className={cn(
                    "text-[9px] font-bold px-1.5 py-0.2 rounded-full uppercase",
                    event.severity === "BREACH" && "bg-red-50 text-red-700 dark:bg-red-950/30 dark:text-red-400",
                    event.severity === "WARNING" && "bg-amber-50 text-amber-700 dark:bg-amber-950/30 dark:text-amber-400"
                  )}>{event.severity}</span>
                </div>
                <p className="text-muted-foreground leading-normal text-[11px]">{event.description}</p>

                {/* Action buttons */}
                <div className="flex justify-end gap-2 pt-1">
                  {event.status === "OPEN" && (
                    <button
                      onClick={() => acknowledgeDrift(event.id)}
                      className="px-2 py-1 bg-muted hover:bg-muted-foreground/10 border border-border text-[9px] font-medium rounded-md cursor-pointer transition-colors text-foreground"
                    >
                      Acknowledge
                    </button>
                  )}
                  {event.status !== "RESOLVED" && (
                    <button
                      onClick={() => resolveDrift(event.id)}
                      className="px-2.5 py-1 bg-primary hover:bg-primary/95 text-[9px] font-medium text-primary-foreground rounded-md cursor-pointer transition-colors shadow-xs"
                    >
                      Resolve
                    </button>
                  )}
                </div>

                <div className="flex items-center justify-between text-[10px] text-muted-foreground pt-1 border-t border-border mt-1">
                  <span suppressHydrationWarning>Logged: {formatDate(event.createdAt)}</span>
                  <span className="font-semibold uppercase text-[9px] tracking-wide">Status: {event.status}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Monitored Schema Columns */}
      <div>
        <h4 className="text-xs font-semibold text-foreground uppercase tracking-widest flex items-center gap-1.5 mb-2.5">
          <FileSpreadsheet className="w-3.5 h-3.5 text-muted-foreground" />
          Monitored Schema Columns
        </h4>
        <div className="divide-y divide-border border border-border rounded-xl overflow-hidden bg-card">
          {fields.map((col, i) => (
            <div key={i} className="p-3 flex items-start justify-between gap-4 hover:bg-muted/10 transition-colors">
              <div>
                <p className="font-mono text-[11px] font-semibold text-foreground leading-none">{col.field}</p>
                <p className="text-[10px] text-muted-foreground mt-1 leading-snug">{col.description}</p>
              </div>
              <span className="font-mono text-[9px] font-semibold text-muted-foreground bg-muted border border-border px-1.5 py-0.5 rounded-md self-center">{col.type}</span>
            </div>
          ))}
        </div>
      </div>
    </Sheet>
  );
}
