"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { useEvalOperations } from "@/hooks/use-eval-runs";
import { useEvalDatasets } from "@/hooks/use-eval-datasets";
import { api, ApiError } from "@/lib/api";
import { toApiThreshold } from "@/lib/eval-threshold";
import type { EvalRun } from "@/lib/types";
import { cn } from "@/lib/utils";
import { useDashboard } from "@/context/dashboard-context";
import { Modal } from "@/components/ui/modal";
import { Plus } from "lucide-react";

export default function EvalsPage() {
  const { allSystems: systems } = useDashboard();
  const { data: operations } = useEvalOperations();
  const { data: datasetRows = [] } = useEvalDatasets();
  const evalDatasets = datasetRows.map((d) => d.name);
  const qc = useQueryClient();

  const [selectedSystemId, setSelectedSystemId] = useState(systems[0]?.id ?? "");
  const [dataset, setDataset] = useState("");
  const activeDataset = dataset || evalDatasets[0] || "";
  const [threshold, setThreshold] = useState(85);
  const [consoleLines, setConsoleLines] = useState<string[]>([]);
  const [running, setRunning] = useState(false);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [newDsName, setNewDsName] = useState("");
  const [newDsDesc, setNewDsDesc] = useState("");
  const [newDsVersion, setNewDsVersion] = useState("v1");
  const [newDsSamples, setNewDsSamples] = useState(100);
  const [datasetError, setDatasetError] = useState<string | null>(null);

  async function handleRun(e: React.SyntheticEvent) {
    e.preventDefault();
    setRunning(true);
    const system = systems.find((s) => s.id === selectedSystemId) ?? systems[0];
    if (!system) {
      setConsoleLines(["> Create an eval dataset first"]);
      setRunning(false);
      return;
    }
    setConsoleLines([`> queued eval run for ${system.name}`, "> submitting to API…"]);
    try {
      const { runId } = await api.evals.create({
        systemId: selectedSystemId, dataset: activeDataset,
        modelVersion: `${system.name.toLowerCase().replace(/\s/g, "-")}-2026-06`,
        promptVersion: "v1", threshold: toApiThreshold(threshold),
      });
      setConsoleLines((p) => [...p, `> run ${runId} created, executing…`]);
      const result: EvalRun = await api.evals.execute(runId);
      const m = result.metrics;
      setConsoleLines([
        `> run ${runId} — ${system.name}`,
        `> faithfulness:       ${m.faithfulness !== undefined ? Math.round(m.faithfulness * 100) : "—"}%`,
        `> relevance:          ${m.relevance !== undefined ? Math.round(m.relevance * 100) : "—"}%`,
        `> safety refusal:     ${m.safetyRefusal !== undefined ? Math.round(m.safetyRefusal * 100) : "—"}%`,
        `> bias slice pass:    ${m.biasSlicePassRate !== undefined ? Math.round(m.biasSlicePassRate * 100) : "—"}%`,
        `> latency p95:        ${m.latencyP95Ms ?? "—"}ms`,
        `> cost:               $${m.costUsd ?? "—"}`,
        `> decision:           ${result.releaseDecision}`,
      ]);
    } catch (err) {
      setConsoleLines([
        `> eval run for ${system.name} failed`,
        `> ${err instanceof ApiError ? err.message : "The eval service did not respond."}`,
      ]);
    } finally {
      setRunning(false);
    }
  }

  async function handleCreateDataset(e: React.SyntheticEvent) {
    e.preventDefault();
    setDatasetError(null);
    const name = newDsName.trim().toLowerCase().replace(/\s+/g, "-");
    if (!name) return;
    try {
      await api.evals.createDataset({ name, version: newDsVersion.trim() || "v1", sampleCount: newDsSamples, golden: false });
      await qc.invalidateQueries({ queryKey: ["eval-datasets"] });
      setDataset(name);
      setIsModalOpen(false);
      setNewDsName("");
      setNewDsDesc("");
    } catch (err) {
      setDatasetError(err instanceof ApiError ? err.message : "Could not create the dataset.");
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">
        Article 72 stand-in until 2 Sep 2027: eval drift. The Commission template is not included.
      </p>
      <div className="grid grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle>Eval Gate Runner</CardTitle>
            <CardDescription>LLM-as-judge, RAG faithfulness, safety refusal, latency, and cost guardrails.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleRun} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">System</label>
                <Select value={selectedSystemId} onValueChange={(v) => v && setSelectedSystemId(v)}>
                  <SelectTrigger aria-label="System"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {systems.map((s) => <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>

              {/* Dataset selection with inline creator */}
              <div className="space-y-1.5">
                <div className="flex justify-between items-center">
                  <label className="text-xs font-medium text-muted-foreground">Dataset</label>
                  <button
                    type="button"
                    onClick={() => setIsModalOpen(true)}
                    className="text-[10px] font-semibold text-primary underline flex items-center gap-1 cursor-pointer"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    Register Dataset
                  </button>
                </div>
                <Select value={activeDataset} onValueChange={(v) => v && setDataset(v)}>
                  <SelectTrigger aria-label="Dataset"><SelectValue placeholder="Create an eval dataset first" /></SelectTrigger>
                  <SelectContent>
                    {evalDatasets.map((d) => <SelectItem key={d} value={d}>{d}</SelectItem>)}
                  </SelectContent>
                </Select>
                {evalDatasets.length === 0 && (
                  <p className="text-[10px] text-muted-foreground">Create an eval dataset first</p>
                )}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="eval-threshold" className="text-xs font-medium text-muted-foreground">Pass threshold (%)</label>
                <input
                  id="eval-threshold"
                  type="number" min={70} max={98} value={threshold}
                  onChange={(e) => setThreshold(Number(e.target.value))}
                  className="w-full border border-border rounded-lg px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring/50"
                />
              </div>
              <Button type="submit" disabled={running}>
                {running ? "Running…" : "Run eval gate"}
              </Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Gate Console</CardTitle>
            <CardDescription>Worker output with release decision.</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="min-h-60 max-h-72 overflow-auto rounded-lg bg-zinc-950 text-emerald-400 font-mono text-xs p-4 leading-loose">
              {consoleLines.length === 0
                ? <span className="text-zinc-400">Awaiting eval run…</span>
                : consoleLines.map((line, i) => <div key={i}>{line}</div>)
              }
            </div>
          </CardContent>
        </Card>
      </div>

      {operations && (
        <Card>
          <CardHeader>
            <CardTitle>Worker Operations</CardTitle>
            <CardDescription>Queued, running, retryable, and failed eval runs.</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-4 gap-3">
              {[
                { label: "Queued",      count: operations.queued ?? 0,                    color: "text-foreground" },
                { label: "Running",     count: operations.running ?? 0,                   color: "text-primary" },
                { label: "Retryable",   count: (operations.retryQueue ?? []).length,      color: "text-amber-700 dark:text-amber-400" },
                { label: "Dead Letter", count: (operations.deadLetter ?? []).length,      color: "text-red-700 dark:text-red-400" },
              ].map((op) => (
                <div key={op.label} className="bg-muted/40 rounded-xl px-4 py-3.5">
                  <p className={cn("text-2xl font-bold", op.color)}>{op.count}</p>
                  <p className="text-xs text-muted-foreground mt-1">{op.label}</p>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Dataset Registration Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title="Register Eval Dataset"
        description="Register a new golden set for automatic model regression checks."
      >
        <form onSubmit={handleCreateDataset} className="space-y-4">
          <div className="space-y-1.5">
            <label htmlFor="dataset-name" className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Dataset Name</label>
            <input
              id="dataset-name"
              className="w-full border border-border rounded-lg px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring/50"
              value={newDsName}
              onChange={(e) => setNewDsName(e.target.value)}
              placeholder="e.g. claims-denial-v5"
            />
          </div>
          <div className="space-y-1.5">
            <label htmlFor="dataset-description" className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Description</label>
            <input
              id="dataset-description"
              className="w-full border border-border rounded-lg px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring/50"
              value={newDsDesc}
              onChange={(e) => setNewDsDesc(e.target.value)}
              placeholder="e.g. 500 gold standard claims classification checks"
            />
          </div>
          <div className="space-y-1.5">
            <label htmlFor="dataset-version" className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Version</label>
            <input
              id="dataset-version"
              className="w-full border border-border rounded-lg px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring/50"
              value={newDsVersion}
              onChange={(e) => setNewDsVersion(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <label htmlFor="dataset-samples" className="text-xs font-semibold text-muted-foreground uppercase tracking-wide">Sample count</label>
            <input
              id="dataset-samples"
              type="number"
              min={1}
              className="w-full border border-border rounded-lg px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring/50"
              value={newDsSamples}
              onChange={(e) => setNewDsSamples(Number(e.target.value))}
            />
          </div>
          {datasetError && <p role="alert" className="text-xs text-destructive">{datasetError}</p>}
          <div className="flex justify-end gap-2.5">
            <Button type="button" variant="outline" size="sm" onClick={() => setIsModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" size="sm" disabled={!newDsName}>
              Register Dataset
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
