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
import { evalCallbackSnippet, evalRunLines } from "@/lib/eval-callback";
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
  const [modelVersion, setModelVersion] = useState("");
  const [promptVersion, setPromptVersion] = useState("");
  const [consoleLines, setConsoleLines] = useState<string[]>([]);
  const [running, setRunning] = useState(false);
  const [queuedRunId, setQueuedRunId] = useState<string | null>(null);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [newDsName, setNewDsName] = useState("");
  const [newDsDesc, setNewDsDesc] = useState("");
  const [newDsVersion, setNewDsVersion] = useState("v1");
  const [newDsSamples, setNewDsSamples] = useState(100);
  const [datasetError, setDatasetError] = useState<string | null>(null);

  async function handleRun(e: React.SyntheticEvent) {
    e.preventDefault();
    const system = systems.find((s) => s.id === selectedSystemId) ?? systems[0];
    if (!system || !activeDataset) {
      setConsoleLines(["> Register a system and an eval dataset first"]);
      return;
    }
    setRunning(true);
    setConsoleLines([`> queueing eval run for ${system.name}…`]);
    try {
      const { runId } = await api.evals.create({
        systemId: system.id, dataset: activeDataset,
        modelVersion: modelVersion.trim(), promptVersion: promptVersion.trim(),
        threshold: toApiThreshold(threshold),
      });
      setQueuedRunId(runId);
      setConsoleLines([
        `> run ${runId} queued for ${system.name}`,
        "> Devernance does not run models. Your eval harness reports what it measured with the signed callback.",
      ]);
    } catch (err) {
      setConsoleLines([
        `> could not queue the eval run for ${system.name}`,
        `> ${err instanceof ApiError ? err.message : "The eval service did not respond."}`,
      ]);
    } finally {
      setRunning(false);
    }
  }

  async function handleCheck() {
    if (!queuedRunId) return;
    try {
      const run = await api.evals.get(queuedRunId);
      setConsoleLines(evalRunLines(run));
      if (run.status === "completed") await qc.invalidateQueries();
    } catch (err) {
      setConsoleLines([`> ${err instanceof ApiError ? err.message : "The eval service did not respond."}`]);
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
            <CardTitle>Queue an eval run</CardTitle>
            <CardDescription>
              Record which model and prompt version is being evaluated. Your harness reports faithfulness, relevance,
              safety refusal and bias-slice results; safety and bias each have their own floor.
            </CardDescription>
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

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label htmlFor="eval-model-version" className="text-xs font-medium text-muted-foreground">Model version</label>
                  <input
                    id="eval-model-version" required value={modelVersion} placeholder="gpt-4o-mini-2024-07-18"
                    onChange={(e) => setModelVersion(e.target.value)}
                    className="w-full border border-border rounded-lg px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring/50"
                  />
                </div>
                <div className="space-y-1.5">
                  <label htmlFor="eval-prompt-version" className="text-xs font-medium text-muted-foreground">Prompt version</label>
                  <input
                    id="eval-prompt-version" required value={promptVersion} placeholder="claims-routing-v12"
                    onChange={(e) => setPromptVersion(e.target.value)}
                    className="w-full border border-border rounded-lg px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring/50"
                  />
                </div>
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
              <div className="flex gap-2">
                <Button type="submit" disabled={running}>
                  {running ? "Queueing…" : "Queue eval run"}
                </Button>
                <Button type="button" variant="outline" disabled={!queuedRunId} onClick={handleCheck}>
                  Check result
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Run status</CardTitle>
            <CardDescription>The queued run, then the measured result once your harness reports it.</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="min-h-60 max-h-72 overflow-auto rounded-lg bg-zinc-950 text-emerald-400 font-mono text-xs p-4 leading-loose">
              {consoleLines.length === 0
                ? <span className="text-zinc-400">No run queued yet.</span>
                : consoleLines.map((line, i) => <div key={i}>{line}</div>)
              }
            </div>
            {queuedRunId && (
              <div className="mt-3 space-y-1.5">
                <p className="text-xs font-medium text-muted-foreground">Report the result from your harness or CI</p>
                <pre className="overflow-x-auto rounded-lg bg-zinc-950 p-3 text-[11px] leading-relaxed text-zinc-100">
                  {evalCallbackSnippet(queuedRunId)}
                </pre>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {operations && (
        <Card>
          <CardHeader>
            <CardTitle>Eval run queue</CardTitle>
            <CardDescription>Runs waiting for a reported result, and runs that failed.</CardDescription>
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
