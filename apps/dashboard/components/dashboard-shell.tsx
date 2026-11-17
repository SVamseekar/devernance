"use client";

import { useState } from "react";
import { usePathname } from "next/navigation";
import { DemoBanner } from "@/components/demo-banner";
import { PlanLimitPrompt } from "@/components/plan-limit-prompt";
import { Sidebar } from "@/components/sidebar";
import { Header } from "@/components/header";
import { normaliseDecision } from "@/lib/utils";
import { useDashboard } from "@/context/dashboard-context";
import { SystemDetailsSheet, ContractDetailsSheet } from "@/components/details-sheets";
import { api } from "@/lib/api";
import { useDriftEvents } from "@/hooks/use-drift-events";
import { isLiveEntityId } from "@/lib/ids";
import { Modal } from "@/components/ui/modal";
import { Button } from "@/components/ui/button";

const PAGE_META: Record<string, { title: string; subtitle: string }> = {
  "/command": {
    title: "Command Centre",
    subtitle: "Live compliance posture, release gate status, and risk overview across all AI systems.",
  },
  "/systems": {
    title: "AI System Registry",
    subtitle: "Inventory of providers, deployers, risk classifications, and EU Act obligations.",
  },
  "/governance": {
    title: "Governance Runtime",
    subtitle: "Policies applied as code, runtime decisions for agents, human approvals of high-impact actions, and drift.",
  },
  "/approvals": {
    title: "Approval Workflows",
    subtitle: "Review and sign off AI system releases. Staged approvals for engineering, compliance, and legal.",
  },
  "/evidence": {
    title: "Compliance Evidence",
    subtitle: "Upload and query policies, DPIAs, model cards, and vendor documentation with citations.",
  },
  "/evals": {
    title: "Eval Gates",
    subtitle: "Article 72 stand-in until 2 Sep 2027: eval drift. The Commission template is not included.",
  },
  "/contracts": {
    title: "Data Contracts",
    subtitle: "Article 72 stand-in until 2 Sep 2027: contract drift. The Commission template is not included.",
  },
  "/audit": {
    title: "Audit Ledger",
    subtitle: "Append-only record of evidence checks, approvals, overrides, and release decisions.",
  },
  "/reg-monitor": {
    title: "Regulatory Change Monitor",
    subtitle: "Article 72 stand-in until 2 Sep 2027: reg-monitor diff. The Commission template is not included.",
  },
  "/corpus": {
    title: "Law corpus",
    subtitle: "English Formex provisions with force dates. Guidance is stored as interpretation, not as statute.",
  },
  "/proposals": {
    title: "Proposal queue",
    subtitle: "Tenant-scoped links a compliance officer or admin can accept or reject.",
  },
  "/readiness": {
    title: "Readiness",
    subtitle: "Evidence count for this system and this corpus version.",
  },
  "/public-claims": {
    title: "Public-claims teasers",
    subtitle:
      "Four EU firms reconstructed from public pages. Not customers. Evgraph fails closed when approval or dataset license is unpublished.",
  },
  "/onboarding": {
    title: "Get started",
    subtitle: "Register your first AI system, see what applies, and gate your pipeline.",
  },
  "/settings": {
    title: "Workspace",
    subtitle: "Users, invites, and workspace settings.",
  },
};

function downloadJson(filename: string, payload: unknown) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

/** Client app chrome for authenticated dashboard routes. */
export function DashboardShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const {
    allSystems,
    allAudits,
    driftEvents,
    selectedSystem,
    setSelectedSystem,
    selectedContract,
    setSelectedContract,
  } = useDashboard();

  const [exportBusy, setExportBusy] = useState(false);
  const [sealModal, setSealModal] = useState<{
    format: "JSON" | "PDF";
    contentSha256: string;
    systemName: string;
    filename?: string;
    corpusVersion?: string;
  } | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);

  const { data: liveContractDrift = [] } = useDriftEvents(selectedContract?.id ?? "");
  const sheetDriftEvents =
    selectedContract && isLiveEntityId(selectedContract.id) ? liveContractDrift : driftEvents;

  const blockedCount = allSystems.filter(
    (s) => normaliseDecision(s.releaseDecision) === "Blocked"
  ).length;

  const meta = PAGE_META[pathname] ?? PAGE_META["/command"];

  function resolveExportTarget() {
    if (selectedSystem) return selectedSystem;
    if (allSystems.length > 0) return allSystems[0];
    return null;
  }

  async function handleExportJson() {
    const target = resolveExportTarget();
    if (!target) {
      setExportError("Register an AI system before exporting an evidence pack.");
      return;
    }
    setExportBusy(true);
    setExportError(null);
    try {
      const pack = await api.systems.evidencePack(target.id);
      const date = pack.generatedAt?.slice(0, 10) ?? new Date().toISOString().slice(0, 10);
      downloadJson(`evidence-pack-${target.id}-${date}.json`, pack);
      setSealModal({
        format: "JSON",
        contentSha256: pack.contentSha256,
        systemName: target.name,
        corpusVersion: pack.corpusVersion,
      });
    } catch (err) {
      setExportError(err instanceof Error ? err.message : "JSON export failed");
    } finally {
      setExportBusy(false);
    }
  }

  async function handleExportPdf() {
    const target = resolveExportTarget();
    if (!target) {
      setExportError("Register an AI system before exporting an evidence pack.");
      return;
    }
    setExportBusy(true);
    setExportError(null);
    try {
      const result = await api.systems.evidencePackPdf(target.id);
      setSealModal({
        format: "PDF",
        contentSha256: result.contentSha256,
        systemName: target.name,
        filename: result.filename,
      });
    } catch (err) {
      setExportError(err instanceof Error ? err.message : "PDF export failed");
    } finally {
      setExportBusy(false);
    }
  }

  function handleRunControls() {
    window.location.reload();
  }

  return (
    <div className="min-h-screen bg-background">
      <Sidebar blockedCount={blockedCount} />
      <main className="ml-56 px-8 py-7">
        <DemoBanner />
        <PlanLimitPrompt />
        <Header
          title={meta.title}
          subtitle={meta.subtitle}
          onExportJson={handleExportJson}
          onExportPdf={handleExportPdf}
          onRunControls={handleRunControls}
          exportBusy={exportBusy}
        />
        {children}
      </main>

      <SystemDetailsSheet
        system={selectedSystem}
        isOpen={selectedSystem !== null}
        onClose={() => setSelectedSystem(null)}
        auditEvents={allAudits}
      />

      <ContractDetailsSheet
        contract={selectedContract}
        isOpen={selectedContract !== null}
        onClose={() => setSelectedContract(null)}
        driftEvents={sheetDriftEvents}
        systems={allSystems}
      />

      <Modal
        isOpen={sealModal !== null}
        onClose={() => setSealModal(null)}
        title={`Evidence pack exported (${sealModal?.format ?? ""})`}
        description={
          sealModal
            ? `Sealed pack for ${sealModal.systemName}. JSON is the primary machine-readable format; PDF is a Phase 6 human-readable export.`
            : undefined
        }
      >
        <div className="space-y-3">
          <div>
            <p className="text-[10px] uppercase font-semibold text-muted-foreground tracking-wider mb-1">
              contentSha256
            </p>
            <code className="block text-[11px] break-all bg-muted/40 border border-border rounded-lg p-3 font-mono leading-relaxed">
              {sealModal?.contentSha256 || "(not returned)"}
            </code>
          </div>
          {sealModal?.corpusVersion && (
            <p className="text-xs text-muted-foreground">
              corpus_version:{" "}
              <span className="font-medium text-foreground break-all">{sealModal.corpusVersion}</span>
            </p>
          )}
          {sealModal?.filename && (
            <p className="text-xs text-muted-foreground">
              File: <span className="font-medium text-foreground">{sealModal.filename}</span>
            </p>
          )}
          <div className="flex justify-end pt-1">
            <Button size="sm" onClick={() => setSealModal(null)}>
              Close
            </Button>
          </div>
        </div>
      </Modal>

      <Modal
        isOpen={exportError !== null}
        onClose={() => setExportError(null)}
        title="Export failed"
        description="Could not download the evidence pack via the authenticated API proxy."
      >
        <div className="space-y-3">
          <p className="text-xs text-muted-foreground leading-relaxed">{exportError}</p>
          <div className="flex justify-end">
            <Button size="sm" variant="outline" onClick={() => setExportError(null)}>
              Dismiss
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
