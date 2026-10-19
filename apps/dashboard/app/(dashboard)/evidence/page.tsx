"use client";

import { useState } from "react";
import Link from "next/link";
import { useQueryClient } from "@tanstack/react-query";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { ApiStatusPill } from "@/components/api-status-pill";
import { useSystems } from "@/hooks/use-systems";
import { useEvidenceDocuments } from "@/hooks/use-evidence-documents";
import { api, ApiError } from "@/lib/api";
import { allowMockFallback } from "@/lib/live-mode";
import { MOCK_SYSTEMS } from "@/lib/mock-data";
import type { EvidenceQueryResponse, EvidenceDocument } from "@/lib/types";
import { formatDate, cn } from "@/lib/utils";
import { useDashboard } from "@/context/dashboard-context";
import { isLiveEntityId } from "@/lib/ids";
import { UploadCloud } from "lucide-react";

const EVIDENCE_TYPES = ["DPIA", "POLICY", "MODEL_CARD", "VENDOR_DOC", "CONTROL_MAP"];

const INPUT = "w-full border border-border rounded-lg px-3 py-2 text-sm bg-background focus:outline-none focus:ring-2 focus:ring-ring/50 transition-shadow";

const d = (daysAgo: number) =>
  new Date(Date.now() - daysAgo * 86_400_000).toISOString();

const MOCK_DOCUMENTS: Record<string, EvidenceDocument[]> = {
  "mock-sys-001": [
    { id: "mc-1", systemId: "mock-sys-001", type: "MODEL_CARD", title: "Claims Triage XGBoost v4 Card", sourceUri: "file:///models/claims-triage-v4-card.pdf", checksum: "sha256-a1b2c3d4", chunkCount: 18, ingestionStatus: "indexed", createdAt: d(40) },
    { id: "dpia-1", systemId: "mock-sys-001", type: "DPIA", title: "Claims Triage DPIA v2.1", sourceUri: "file:///policies/claims-dpia-v2.pdf", checksum: "sha256-f8e9d0c2", chunkCount: 24, ingestionStatus: "indexed", createdAt: d(30) }
  ],
  "mock-sys-002": [
    { id: "mc-2", systemId: "mock-sys-002", type: "MODEL_CARD", title: "HR Resume Classifier Card", sourceUri: "file:///models/resume-rank-v2-card.pdf", checksum: "sha256-4d5e6f7a", chunkCount: 14, ingestionStatus: "indexed", createdAt: d(25) }
  ],
  "mock-sys-003": [
    { id: "mc-3", systemId: "mock-sys-003", type: "MODEL_CARD", title: "KYC Analyst Assistant Card", sourceUri: "file:///models/kyc-fraud-v7-card.pdf", checksum: "sha256-9a8b7c6d", chunkCount: 22, ingestionStatus: "indexed", createdAt: d(80) },
    { id: "dpia-3", systemId: "mock-sys-003", type: "DPIA", title: "KYC Privacy Impact Assessment", sourceUri: "file:///policies/kyc-dpia-v5.pdf", checksum: "sha256-3f4e5d6c", chunkCount: 31, ingestionStatus: "indexed", createdAt: d(75) },
    { id: "pm-3", systemId: "mock-sys-003", type: "CONTROL_MAP", title: "KYC Control Checkpoint Matrix", sourceUri: "file:///policies/kyc-controls-v1.pdf", checksum: "sha256-1a2b3c4d", chunkCount: 9, ingestionStatus: "indexed", createdAt: d(70) }
  ]
};

export default function EvidencePage() {
  const { allSystems: systems } = useDashboard();
  const { isError: systemsError } = useSystems();
  const qc = useQueryClient();
  const liveMode = !allowMockFallback();
  const apiOnline = liveMode || (!systemsError && systems.length > 0 && isLiveEntityId(systems[0].id));

  const liveSystems = systems.filter((system) => isLiveEntityId(system.id));
  const systemOptions = liveSystems.length > 0 ? liveSystems : systems;
  const [pickedId, setPickedId] = useState("");
  const selectedSystemId = systemOptions.some((system) => system.id === pickedId)
    ? pickedId
    : (systemOptions.find((system) => system.name === "Claims Triage AI")?.id ?? systemOptions[0]?.id ?? "");
  const [question, setQuestion] = useState("");
  const [ragResponse, setRagResponse] = useState<EvidenceQueryResponse | null>(null);
  const [queryError, setQueryError] = useState<string | null>(null);
  const [querying, setQuerying] = useState(false);

  const [docType, setDocType] = useState("DPIA");
  const [docTitle, setDocTitle] = useState("");
  const [docSource, setDocSource] = useState("");
  const [docContent, setDocContent] = useState("");
  const [indexing, setIndexing] = useState(false);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [isDragActive, setIsDragActive] = useState(false);

  const { data: documents = [] } = useEvidenceDocuments(apiOnline ? selectedSystemId : undefined);

  const displayedDocuments = apiOnline
    ? documents
    : (MOCK_DOCUMENTS[selectedSystemId] ?? []);

  const selectedSystem = systemOptions.find((s) => s.id === selectedSystemId) ?? systemOptions[0];

  function handleDrag(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setIsDragActive(true);
    } else if (e.type === "dragleave") {
      setIsDragActive(false);
    }
  }

  function handleDrop(e: React.DragEvent) {
    e.preventDefault();
    e.stopPropagation();
    setIsDragActive(false);

    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      acceptFile(e.dataTransfer.files[0]);
    }
  }

  function acceptFile(file: File) {
    setUploadError(null);
    if (file.size > 25 * 1024 * 1024) {
      setUploadError("Files up to 25 MB are supported.");
      return;
    }
    setPendingFile(file);
    if (!docTitle) setDocTitle(file.name.replace(/\.[^/.]+$/, ""));
  }

  async function handleQuery(e: React.SyntheticEvent) {
    e.preventDefault();
    setQuerying(true);
    try {
      const res = await api.evidence.query(selectedSystemId, question);
      setRagResponse(res);
      setQueryError(null);
    } catch (err) {
      setRagResponse(null);
      setQueryError(err instanceof ApiError ? err.message : "The evidence search failed. Try again.");
    } finally {
      setQuerying(false);
    }
  }

  async function handleIndex(e: React.SyntheticEvent) {
    e.preventDefault();
    if (!apiOnline) return;
    setIndexing(true);
    setUploadError(null);
    try {
      if (pendingFile) {
        await api.evidence.upload({ systemId: selectedSystemId, type: docType, title: docTitle, file: pendingFile });
      } else {
        await api.evidence.index({
          systemId: selectedSystemId,
          type: docType,
          title: docTitle,
          sourceUri: docSource || `memory://${encodeURIComponent(docTitle)}`,
          content: docContent,
          metadata: { source: "web-dashboard" },
        });
      }
      setPendingFile(null);
      setDocContent("");
      await qc.invalidateQueries({ queryKey: ["evidence-documents", selectedSystemId] });
      await qc.invalidateQueries({ queryKey: ["systems"] });
    } catch (err) {
      setUploadError(err instanceof ApiError ? err.message : "Upload failed. Try again.");
    } finally {
      setIndexing(false);
    }
  }

  if (apiOnline && systemOptions.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Evidence</CardTitle>
          <CardDescription>Register your first AI system to attach evidence.</CardDescription>
        </CardHeader>
        <CardContent>
          <Link href="/systems" className="text-sm text-primary hover:underline">
            Register your first system
          </Link>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle>Compliance Evidence RAG</CardTitle>
            <CardDescription>Ask against policies, DPIAs, model cards, vendor docs, and EU control mappings.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleQuery} className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">System</label>
                <Select value={selectedSystemId} onValueChange={(v) => v && setPickedId(v)}>
                  <SelectTrigger><SelectValue>{selectedSystem?.name ?? "Select system"}</SelectValue></SelectTrigger>
                  <SelectContent>
                    {systemOptions.map((s) => <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">Question</label>
                <Textarea rows={5} value={question} onChange={(e) => setQuestion(e.target.value)} />
              </div>
              <Button type="submit" disabled={querying || !apiOnline} size="sm">
                {querying ? "Querying…" : "Ask with citations"}
              </Button>
              {queryError && <p role="alert" className="text-xs text-destructive">{queryError}</p>}
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Grounded Answer</CardTitle>
            <CardDescription>Every answer includes confidence, source clauses, and reviewer action.</CardDescription>
          </CardHeader>
          <CardContent>
            {ragResponse ? (
              <div className="space-y-3">
                <p className="text-xs text-muted-foreground">{question}</p>
                <p className="text-sm font-medium">{ragResponse.answer}</p>
                {ragResponse.citations.map((c, i) => (
                  <div key={i} className="border-l-2 border-primary/40 pl-3 text-xs text-muted-foreground">
                    <span className="font-medium text-foreground">{c.title} · {c.section}</span>
                    <p className="mt-0.5">{c.snippet}</p>
                  </div>
                ))}
                {ragResponse.citations.length > 0 ? (
                  <div className="border-l-2 border-primary/40 pl-3 text-xs text-muted-foreground">
                    <span className="font-medium">Confidence: {Math.round(ragResponse.confidence * 100)}%</span>
                    <p className="mt-0.5">Reviewer should inspect cited source material before release approval.</p>
                  </div>
                ) : (
                  <p className="text-xs text-muted-foreground">No sources matched, so nothing is cited.</p>
                )}
              </div>
            ) : queryError ? (
              <p role="alert" className="text-sm text-destructive">{queryError}</p>
            ) : (
              <p className="text-sm text-muted-foreground">Run a cited compliance query to see results here.</p>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <Card>
          <CardHeader>
            <CardTitle>Index Evidence</CardTitle>
            <CardDescription>Drag and drop PDF/Word artifacts or upload extracted text.</CardDescription>
          </CardHeader>
          <CardContent>
            {/* Drag and Drop Zone */}
            <div
              onDragEnter={handleDrag}
              onDragOver={handleDrag}
              onDragLeave={handleDrag}
              onDrop={handleDrop}
              className={cn(
                "border border-dashed rounded-xl p-6 text-center transition-all flex flex-col items-center justify-center cursor-pointer min-h-32 mb-4 bg-muted/10 relative overflow-hidden group",
                isDragActive ? "border-primary bg-primary/5 scale-[0.99]" : "border-border hover:border-primary/40 hover:bg-muted/20"
              )}
            >
              {pendingFile ? (
                <p className="text-xs font-semibold text-foreground">
                  {pendingFile.name} · {(pendingFile.size / 1024).toFixed(1)} KB
                </p>
              ) : (
                <>
                  <UploadCloud className="w-6 h-6 text-muted-foreground/60 mb-2 group-hover:text-primary transition-colors shrink-0" />
                  <p className="text-xs font-semibold text-foreground leading-none">Drag & Drop Compliance File</p>
                  <p className="text-[10px] text-muted-foreground mt-1.5 leading-normal max-w-64">
                    Drop PDF, DOCX, TXT, or JSON. The server extracts the text. Files up to 25 MB.
                  </p>
                </>
              )}
              <input
                type="file"
                className="absolute inset-0 opacity-0 cursor-pointer"
                onChange={(e) => {
                  if (e.target.files && e.target.files[0]) {
                    acceptFile(e.target.files[0]);
                  }
                }}
              />
            </div>

            <form onSubmit={handleIndex} className="space-y-4">
              <fieldset disabled={!apiOnline} className="space-y-4 disabled:opacity-60">
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">Type</label>
                <Select value={docType} onValueChange={(v) => v && setDocType(v)}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {EVIDENCE_TYPES.map((t) => <SelectItem key={t} value={t}>{t}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">Title</label>
                <input className={INPUT} value={docTitle} onChange={(e) => setDocTitle(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">Source URI</label>
                <input className={INPUT} value={docSource} onChange={(e) => setDocSource(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">Extracted text</label>
                <Textarea rows={4} value={docContent} onChange={(e) => setDocContent(e.target.value)} />
              </div>
              {uploadError && <p role="alert" className="text-xs text-destructive">{uploadError}</p>}
              <div className="flex items-center gap-3">
                <Button
                  type="submit"
                  disabled={indexing || !apiOnline || !docTitle || (!pendingFile && !docContent.trim())}
                  size="sm"
                >
                  {indexing ? "Indexing…" : "Index document"}
                </Button>
                {!apiOnline && <p className="text-[10px] text-muted-foreground">Connect the API to index evidence.</p>}
              </div>
              </fieldset>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex-row items-start justify-between">
            <div>
              <CardTitle>Indexed Documents</CardTitle>
              <CardDescription>{selectedSystem?.name ?? "Selected system"}</CardDescription>
            </div>
            <ApiStatusPill online={apiOnline} />
          </CardHeader>
          <CardContent>
            {displayedDocuments.length === 0 ? (
              <div className="border border-dashed border-border rounded-xl min-h-32 flex items-center justify-center text-sm text-muted-foreground">
                No indexed documents.
              </div>
            ) : (
              <div className="space-y-2">
                {(displayedDocuments as EvidenceDocument[]).map((doc) => (
                  <div key={doc.id} className="flex items-center justify-between rounded-lg bg-muted/30 px-3 py-2.5">
                    <div>
                      <p className="text-sm font-medium">{doc.title}</p>
                      <p className="text-xs text-muted-foreground mt-0.5" suppressHydrationWarning>
                        {doc.type} · {doc.chunkCount} chunk{doc.chunkCount !== 1 ? "s" : ""} · {formatDate(doc.createdAt)}
                      </p>
                    </div>
                    <span className="text-xs text-muted-foreground uppercase font-semibold text-[10px] tracking-wider">{doc.ingestionStatus}</span>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
