# Phase 4 Completion + Next.js Frontend Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the vanilla JS prototype with a production-grade Next.js 14 + TypeScript dashboard, wire the contracts view to the real Spring Boot API, and complete Phase 4 with a React Flow lineage DAG showing data source → contract → AI system relationships with health status overlaid.

**Architecture:** Next.js 14 App Router with a single protected layout shell containing the sidebar and header. All API calls go through a typed `lib/api.ts` client that mirrors Spring Boot response shapes. Each domain (systems, evidence, evals, contracts, audit) is a route segment under `app/(dashboard)/`. Demo/fallback mode is preserved — when the API is unreachable, pages fall back to seeded mock data so the app is always runnable. The lineage view uses React Flow with custom nodes for data sources, contracts, and AI systems.

**Tech Stack:** Next.js 14 (App Router), TypeScript, Tailwind CSS, shadcn/ui, React Flow (lineage graph), TanStack Query (data fetching + cache), next-themes (dark mode)

---

## File Structure

```
apps/
  dashboard/                          ← new Next.js app (replaces apps/web/)
    package.json
    tsconfig.json
    tailwind.config.ts
    next.config.ts
    components.json                   ← shadcn/ui config
    app/
      layout.tsx                      ← root layout (html, body, providers)
      (dashboard)/
        layout.tsx                    ← protected shell: sidebar + header
        page.tsx                      ← redirect → /systems
        command/
          page.tsx                    ← command center: metrics, risk topology, release gate table, inbox
        systems/
          page.tsx                    ← AI system registry cards
        evidence/
          page.tsx                    ← evidence RAG query + document index
        evals/
          page.tsx                    ← eval gate runner + console
        contracts/
          page.tsx                    ← contract monitor + drift events + lineage DAG
        audit/
          page.tsx                    ← audit ledger timeline
    components/
      providers.tsx                   ← QueryClientProvider + ThemeProvider
      sidebar.tsx                     ← fixed left nav with numbered items + release gate card
      header.tsx                      ← page title, export pack button, run controls button
      risk-topology.tsx               ← canvas scatter plot (ported from vanilla JS)
      lineage-graph.tsx               ← React Flow DAG: sources → contracts → systems
      lineage-nodes.tsx               ← custom node types for each entity kind
      release-gate-table.tsx          ← table of all systems with decision badge
      system-card.tsx                 ← single AI system card with progress bar
      contract-card.tsx               ← single contract card with status badge + drift list
      eval-console.tsx                ← monospace console output panel
      evidence-form.tsx               ← RAG query form
      evidence-upload-form.tsx        ← document index form
      api-status-pill.tsx             ← online/demo mode indicator
      decision-badge.tsx              ← PASS/REVIEW/BLOCKED coloured badge
      risk-badge.tsx                  ← high/limited/minimal coloured chip
    lib/
      api.ts                          ← typed fetch client wrapping all Spring Boot endpoints
      types.ts                        ← TypeScript types mirroring all backend response shapes
      mock-data.ts                    ← seeded demo data (migrated from defaultState in app.js)
      utils.ts                        ← cn(), sentenceCase(), formatDate()
    hooks/
      use-systems.ts                  ← TanStack Query hook for /systems
      use-contracts.ts                ← TanStack Query hook for /data-contracts
      use-drift-events.ts             ← TanStack Query hook for /data-contracts/{id}/drift-events
      use-evidence-documents.ts       ← TanStack Query hook for /evidence/systems/{id}/documents
      use-audit-events.ts             ← TanStack Query hook for /audit-events
      use-eval-runs.ts                ← TanStack Query hook for /eval-runs/operations
```

---

## Task 1: Scaffold Next.js App

**Files:**
- Create: `apps/dashboard/package.json`
- Create: `apps/dashboard/tsconfig.json`
- Create: `apps/dashboard/next.config.ts`
- Create: `apps/dashboard/tailwind.config.ts`
- Create: `apps/dashboard/components.json`
- Create: `apps/dashboard/app/layout.tsx`

- [ ] **Step 1: Create the Next.js app**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os/apps
npx create-next-app@latest dashboard \
  --typescript \
  --tailwind \
  --app \
  --no-src-dir \
  --import-alias "@/*" \
  --no-eslint
```

Expected: `apps/dashboard/` created with `app/`, `public/`, `package.json`, `tsconfig.json`, `tailwind.config.ts`, `next.config.ts`.

- [ ] **Step 2: Install dependencies**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os/apps/dashboard
npm install @tanstack/react-query @tanstack/react-query-devtools
npm install reactflow
npm install next-themes
npm install class-variance-authority clsx tailwind-merge
npm install lucide-react
```

Expected: all packages installed, no peer dep errors.

- [ ] **Step 3: Install shadcn/ui**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os/apps/dashboard
npx shadcn@latest init --defaults
```

When prompted: style = Default, base color = Slate, CSS variables = yes.

Expected: `components.json` created, `components/ui/` directory created, `globals.css` updated with CSS variables.

- [ ] **Step 4: Add shadcn components we need**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os/apps/dashboard
npx shadcn@latest add badge button card select table textarea toast
```

Expected: component files appear in `components/ui/`.

- [ ] **Step 5: Update next.config.ts to allow API proxy**

Replace contents of `apps/dashboard/next.config.ts`:

```typescript
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: "http://localhost:8080/api/v1/:path*",
      },
    ];
  },
};

export default nextConfig;
```

- [ ] **Step 6: Commit scaffold**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os
git add apps/dashboard
git commit -m "feat(dashboard): scaffold Next.js 14 app with shadcn/ui and dependencies"
```

---

## Task 2: Types and API Client

**Files:**
- Create: `apps/dashboard/lib/types.ts`
- Create: `apps/dashboard/lib/api.ts`
- Create: `apps/dashboard/lib/utils.ts`
- Create: `apps/dashboard/lib/mock-data.ts`

- [ ] **Step 1: Write `lib/types.ts` — all backend response shapes**

Create `apps/dashboard/lib/types.ts`:

```typescript
export type RiskClass = "minimal" | "limited" | "high" | "prohibited";
export type ReleaseDecision = "pass" | "review" | "blocked" | "PASS" | "REVIEW" | "BLOCKED";
export type DataContractStatus = "HEALTHY" | "WARNING" | "BREACH";
export type DriftSeverity = "INFO" | "WARNING" | "BREACH";
export type DriftStatus = "OPEN" | "ACKNOWLEDGED" | "RESOLVED";
export type EvalRunStatus = "queued" | "running" | "completed" | "failed";
export type IngestionStatus = "indexed" | "pending" | "failed";

export interface AiSystem {
  id: string;
  name: string;
  owner: string;
  purpose: string;
  riskClass: RiskClass;
  riskBasis: string;
  deploymentRegion: string;
  evidenceCoverage: number;
  evalScore: number;
  dataContractStatus: DataContractStatus;
  releaseDecision: ReleaseDecision;
  openGaps: string[];
  createdAt: string;
  updatedAt: string;
}

export interface EvidenceDocument {
  id: string;
  systemId: string;
  type: string;
  title: string;
  sourceUri: string;
  checksum: string;
  chunkCount: number;
  ingestionStatus: IngestionStatus;
  createdAt: string;
}

export interface Citation {
  documentId: string;
  title: string;
  section: string;
  snippet: string;
}

export interface EvidenceQueryResponse {
  answer: string;
  confidence: number;
  citations: Citation[];
}

export interface EvalRunMetrics {
  faithfulness?: number;
  relevance?: number;
  safetyRefusal?: number;
  biasSlicePassRate?: number;
  latencyP95Ms?: number;
  costUsd?: number;
}

export interface EvalRun {
  runId: string;
  systemId: string;
  datasetId: string | null;
  status: EvalRunStatus;
  dataset: string;
  modelVersion: string;
  promptVersion: string;
  threshold: number;
  metrics: EvalRunMetrics;
  releaseDecision: ReleaseDecision;
  createdAt: string;
  queuedAt: string;
  startedAt: string | null;
  completedAt: string | null;
  failedAt: string | null;
  workerAttempts: number;
  maxAttempts: number;
  failureReason: string | null;
}

export interface EvalRunOperationsView {
  queued: EvalRun[];
  running: EvalRun[];
  retryable: EvalRun[];
  deadLetter: EvalRun[];
}

export interface DataContract {
  id: string;
  systemId: string;
  name: string;
  owner: string;
  version: string;
  status: DataContractStatus;
  coverage: number;
  createdAt: string;
  updatedAt: string;
}

export interface DriftEvent {
  id: string;
  contractId: string;
  severity: DriftSeverity;
  field: string | null;
  description: string;
  status: DriftStatus;
  createdAt: string;
  updatedAt: string;
}

export interface AuditEvent {
  id: string;
  systemId: string | null;
  actorId: string | null;
  eventType: string;
  resourceType: string;
  resourceId: string | null;
  payload: Record<string, unknown>;
  createdAt: string;
}

export interface ReleaseGateResponse {
  systemId: string;
  decision: ReleaseDecision;
  blockers: string[];
}

// Normalised view model used in UI components
export interface SystemViewModel {
  id: string;
  name: string;
  owner: string;
  purpose: string;
  riskClass: RiskClass;
  riskBasis: string;
  evidenceCoverage: number;
  evalScore: number;
  dataContractStatus: DataContractStatus;
  releaseDecision: "Pass" | "Review" | "Blocked";
  openGaps: string[];
}
```

- [ ] **Step 2: Write `lib/api.ts` — typed fetch client**

Create `apps/dashboard/lib/api.ts`:

```typescript
import type {
  AiSystem,
  AuditEvent,
  DataContract,
  DriftEvent,
  EvalRun,
  EvalRunOperationsView,
  EvidenceDocument,
  EvidenceQueryResponse,
  ReleaseGateResponse,
} from "./types";

const BASE = "/api/v1";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json", ...init?.headers },
    ...init,
  });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  return res.status === 204 ? (null as T) : res.json();
}

export const api = {
  systems: {
    list: () => request<AiSystem[]>("/systems"),
    get: (id: string) => request<AiSystem>(`/systems/${id}`),
    releaseGate: (id: string) => request<ReleaseGateResponse>(`/systems/${id}/release-gate`),
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
  },
  evals: {
    operations: () => request<EvalRunOperationsView>("/eval-runs/operations"),
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
      request<DataContract[]>(systemId ? `/data-contracts?systemId=${systemId}` : "/data-contracts"),
    get: (id: string) => request<DataContract>(`/data-contracts/${id}`),
    driftEvents: (id: string) =>
      request<DriftEvent[]>(`/data-contracts/${id}/drift-events`),
  },
  audit: {
    list: (systemId?: string) =>
      request<AuditEvent[]>(systemId ? `/audit-events?systemId=${systemId}` : "/audit-events"),
  },
};
```

- [ ] **Step 3: Write `lib/utils.ts`**

Create `apps/dashboard/lib/utils.ts`:

```typescript
import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
import type { ReleaseDecision, RiskClass, DataContractStatus } from "./types";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function normaliseDecision(raw: string): "Pass" | "Review" | "Blocked" {
  const lower = raw.toLowerCase();
  if (lower === "pass") return "Pass";
  if (lower === "review") return "Review";
  return "Blocked";
}

export function normaliseRisk(raw: string): RiskClass {
  const lower = raw.toLowerCase() as RiskClass;
  return lower;
}

export function normaliseContractStatus(raw: string): DataContractStatus {
  return raw.toUpperCase() as DataContractStatus;
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function decisionColor(decision: "Pass" | "Review" | "Blocked"): string {
  return decision === "Pass"
    ? "text-emerald-600 dark:text-emerald-400"
    : decision === "Review"
    ? "text-amber-600 dark:text-amber-400"
    : "text-red-600 dark:text-red-400";
}

export function riskColor(risk: RiskClass): string {
  return risk === "high"
    ? "text-red-600 border-red-300 dark:border-red-800"
    : risk === "limited"
    ? "text-amber-600 border-amber-300 dark:border-amber-800"
    : "text-emerald-600 border-emerald-300 dark:border-emerald-800";
}

export function contractStatusColor(status: DataContractStatus): string {
  return status === "HEALTHY"
    ? "text-emerald-600 dark:text-emerald-400"
    : status === "WARNING"
    ? "text-amber-600 dark:text-amber-400"
    : "text-red-600 dark:text-red-400";
}
```

- [ ] **Step 4: Write `lib/mock-data.ts` — migrated from defaultState in app.js**

Create `apps/dashboard/lib/mock-data.ts`:

```typescript
import type { AiSystem, DataContract, DriftEvent, AuditEvent } from "./types";

export const MOCK_SYSTEMS: AiSystem[] = [
  {
    id: "mock-sys-001",
    name: "Claims Triage AI",
    owner: "Insurance Ops",
    purpose: "Prioritise and route insurance claims",
    riskClass: "high",
    riskBasis: "Eligibility and access to essential private services",
    deploymentRegion: "EU",
    evidenceCoverage: 72,
    evalScore: 78,
    dataContractStatus: "BREACH",
    releaseDecision: "blocked",
    openGaps: ["Human oversight SOP missing", "Bias eval below threshold"],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: "mock-sys-002",
    name: "HR Candidate Screener",
    owner: "People Systems",
    purpose: "Rank and shortlist job applicants",
    riskClass: "high",
    riskBasis: "Employment access and ranking",
    deploymentRegion: "EU",
    evidenceCoverage: 84,
    evalScore: 82,
    dataContractStatus: "WARNING",
    releaseDecision: "review",
    openGaps: ["Data lineage stale", "Reviewer calibration required"],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: "mock-sys-003",
    name: "Bank KYC Assistant",
    owner: "Financial Crime",
    purpose: "Credit, onboarding, and fraud control support",
    riskClass: "high",
    riskBasis: "Credit, onboarding, and fraud control support",
    deploymentRegion: "EU",
    evidenceCoverage: 91,
    evalScore: 88,
    dataContractStatus: "HEALTHY",
    releaseDecision: "pass",
    openGaps: ["Quarterly red-team due in 9 days"],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: "mock-sys-004",
    name: "Support RAG Copilot",
    owner: "Customer Success",
    purpose: "Customer-facing assistant with transparency duty",
    riskClass: "limited",
    riskBasis: "Customer-facing assistant with transparency duty",
    deploymentRegion: "EU",
    evidenceCoverage: 88,
    evalScore: 86,
    dataContractStatus: "HEALTHY",
    releaseDecision: "pass",
    openGaps: ["Update chatbot disclosure copy"],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: "mock-sys-005",
    name: "Clinical Intake Summarizer",
    owner: "Health Product",
    purpose: "Healthcare workflow support with PHI controls",
    riskClass: "high",
    riskBasis: "Healthcare workflow support with PHI controls",
    deploymentRegion: "EU",
    evidenceCoverage: 81,
    evalScore: 73,
    dataContractStatus: "BREACH",
    releaseDecision: "blocked",
    openGaps: ["PHI masking failed", "Audit justification missing"],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
];

export const MOCK_CONTRACTS: DataContract[] = [
  {
    id: "mock-contract-001",
    systemId: "mock-sys-001",
    name: "claims_events.v4",
    owner: "Insurance Data",
    version: "v4",
    status: "BREACH",
    coverage: 68,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: "mock-contract-002",
    systemId: "mock-sys-002",
    name: "candidate_profiles.v2",
    owner: "People Analytics",
    version: "v2",
    status: "WARNING",
    coverage: 81,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: "mock-contract-003",
    systemId: "mock-sys-003",
    name: "kyc_decisions.v7",
    owner: "Financial Crime",
    version: "v7",
    status: "HEALTHY",
    coverage: 94,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: "mock-contract-004",
    systemId: "mock-sys-005",
    name: "clinical_notes.v3",
    owner: "Health Product",
    version: "v3",
    status: "BREACH",
    coverage: 63,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
];

export const MOCK_DRIFT_EVENTS: DriftEvent[] = [
  {
    id: "mock-drift-001",
    contractId: "mock-contract-001",
    severity: "BREACH",
    field: "denial_reason_category",
    description: "New field denial_reason_category is not mapped to fairness monitoring.",
    status: "OPEN",
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: "mock-drift-002",
    contractId: "mock-contract-002",
    severity: "WARNING",
    field: "education_history",
    description: "Education history optionality changed from required to nullable.",
    status: "OPEN",
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: "mock-drift-003",
    contractId: "mock-contract-004",
    severity: "BREACH",
    field: "patient_language",
    description: "PHI redaction contract missing for locale-specific identifiers.",
    status: "OPEN",
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
];

export const MOCK_AUDIT_EVENTS: AuditEvent[] = [
  {
    id: "mock-audit-001",
    systemId: "mock-sys-001",
    actorId: null,
    eventType: "release_gate.calculated",
    resourceType: "ai_system",
    resourceId: "mock-sys-001",
    payload: { decision: "BLOCKED", reason: "faithfulness below threshold" },
    createdAt: new Date(Date.now() - 300_000).toISOString(),
  },
  {
    id: "mock-audit-002",
    systemId: "mock-sys-001",
    actorId: null,
    eventType: "evidence.query_answered",
    resourceType: "evidence_query",
    resourceId: null,
    payload: { question: "Which controls block the Claims Triage AI?" },
    createdAt: new Date(Date.now() - 600_000).toISOString(),
  },
  {
    id: "mock-audit-003",
    systemId: "mock-sys-005",
    actorId: null,
    eventType: "data_contract.drift_detected",
    resourceType: "data_contract",
    resourceId: "mock-contract-004",
    payload: { severity: "BREACH", field: "patient_language" },
    createdAt: new Date(Date.now() - 1_440_000).toISOString(),
  },
];
```

- [ ] **Step 5: Commit types and API client**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os
git add apps/dashboard/lib
git commit -m "feat(dashboard): add typed API client, types, utils, and mock data"
```

---

## Task 3: Providers and Root Layout

**Files:**
- Create: `apps/dashboard/components/providers.tsx`
- Modify: `apps/dashboard/app/layout.tsx`
- Create: `apps/dashboard/app/globals.css`

- [ ] **Step 1: Write `components/providers.tsx`**

Create `apps/dashboard/components/providers.tsx`:

```typescript
"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "next-themes";
import { useState } from "react";

export function Providers({ children }: { children: React.ReactNode }) {
  const [client] = useState(() => new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: 1,
      },
    },
  }));

  return (
    <QueryClientProvider client={client}>
      <ThemeProvider attribute="class" defaultTheme="light" enableSystem={false}>
        {children}
      </ThemeProvider>
    </QueryClientProvider>
  );
}
```

- [ ] **Step 2: Write root `app/layout.tsx`**

Replace `apps/dashboard/app/layout.tsx`:

```typescript
import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { Providers } from "@/components/providers";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Devernance",
  description: "Governance control plane for AI systems in the European market",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={inter.className}>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
```

- [ ] **Step 3: Verify the app runs**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os/apps/dashboard
npm run dev
```

Expected: server starts on http://localhost:3000, no TypeScript errors, blank page loads without console errors.

- [ ] **Step 4: Commit**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os
git add apps/dashboard/app apps/dashboard/components/providers.tsx
git commit -m "feat(dashboard): add root layout with QueryClient and ThemeProvider"
```

---

## Task 4: Sidebar and Dashboard Shell

**Files:**
- Create: `apps/dashboard/components/sidebar.tsx`
- Create: `apps/dashboard/components/header.tsx`
- Create: `apps/dashboard/app/(dashboard)/layout.tsx`
- Create: `apps/dashboard/app/(dashboard)/page.tsx`

- [ ] **Step 1: Write `components/sidebar.tsx`**

Create `apps/dashboard/components/sidebar.tsx`:

```typescript
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

const NAV_ITEMS = [
  { href: "/command", label: "Command", num: "01" },
  { href: "/systems", label: "Systems", num: "02" },
  { href: "/evidence", label: "Evidence", num: "03" },
  { href: "/evals", label: "Evals", num: "04" },
  { href: "/contracts", label: "Contracts", num: "05" },
  { href: "/audit", label: "Audit", num: "06" },
];

interface SidebarProps {
  blockedCount: number;
}

export function Sidebar({ blockedCount }: SidebarProps) {
  const pathname = usePathname();

  return (
    <aside className="fixed inset-y-0 left-0 w-64 border-r border-border bg-card flex flex-col gap-6 p-5 z-10">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-lg bg-cyan-900 dark:bg-cyan-400 grid place-items-center text-white dark:text-cyan-950 font-black text-sm">
          EA
        </div>
        <div>
          <p className="font-bold text-sm leading-tight">Devernance</p>
          <p className="text-xs text-muted-foreground mt-0.5">Governance control plane</p>
        </div>
      </div>

      <nav className="flex flex-col gap-1">
        {NAV_ITEMS.map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-semibold transition-colors",
                active
                  ? "bg-accent text-accent-foreground shadow-[inset_3px_0_0_hsl(var(--primary))]"
                  : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
              )}
            >
              <span className="w-6 text-xs text-muted-foreground">{item.num}</span>
              {item.label}
            </Link>
          );
        })}
      </nav>

      <div className="mt-auto border border-border rounded-lg p-3.5 bg-muted/40">
        <span className="inline-block w-2.5 h-2.5 rounded-full bg-red-500 shadow-[0_0_0_5px_color-mix(in_srgb,_theme(colors.red.500),_transparent_80%)]" />
        <p className="text-xs text-muted-foreground mt-2 mb-1">Release gate active</p>
        <p className="text-xl font-bold">{blockedCount} blocked</p>
      </div>
    </aside>
  );
}
```

- [ ] **Step 2: Write `components/header.tsx`**

Create `apps/dashboard/components/header.tsx`:

```typescript
"use client";

import { useTheme } from "next-themes";
import { Button } from "@/components/ui/button";
import { Moon, Sun, Download, Play } from "lucide-react";

interface HeaderProps {
  onExportPack: () => void;
  onRunControls: () => void;
}

export function Header({ onExportPack, onRunControls }: HeaderProps) {
  const { theme, setTheme } = useTheme();

  return (
    <header className="flex justify-between items-start gap-6 mb-6">
      <div>
        <p className="text-xs font-black uppercase tracking-wider text-cyan-700 dark:text-cyan-400 mb-1.5">
          EU AI Act + GDPR + operational controls
        </p>
        <h1 className="text-3xl font-bold leading-tight">
          Assure high-risk AI systems before they reach production.
        </h1>
      </div>
      <div className="flex items-center gap-2 flex-shrink-0 pt-1">
        <Button
          variant="outline"
          size="icon"
          onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
          aria-label="Toggle theme"
        >
          {theme === "dark" ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
        </Button>
        <Button variant="outline" onClick={onExportPack}>
          <Download className="h-4 w-4 mr-2" />
          Export evidence pack
        </Button>
        <Button onClick={onRunControls}>
          <Play className="h-4 w-4 mr-2" />
          Run controls
        </Button>
      </div>
    </header>
  );
}
```

- [ ] **Step 3: Write dashboard layout `app/(dashboard)/layout.tsx`**

Create `apps/dashboard/app/(dashboard)/layout.tsx`:

```typescript
"use client";

import { Sidebar } from "@/components/sidebar";
import { Header } from "@/components/header";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { MOCK_SYSTEMS } from "@/lib/mock-data";
import { normaliseDecision } from "@/lib/utils";

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

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const { data: systems } = useQuery({
    queryKey: ["systems"],
    queryFn: api.systems.list,
    placeholderData: MOCK_SYSTEMS,
  });

  const blockedCount = (systems ?? MOCK_SYSTEMS).filter(
    (s) => normaliseDecision(s.releaseDecision) === "Blocked"
  ).length;

  function handleExportPack() {
    const pack = {
      product: "Devernance",
      generatedAt: new Date().toISOString(),
      systems: systems ?? MOCK_SYSTEMS,
    };
    downloadJson(`eu-ai-assurance-evidence-${new Date().toISOString().slice(0, 10)}.json`, pack);
  }

  function handleRunControls() {
    window.location.reload();
  }

  return (
    <div className="min-h-screen bg-background">
      <Sidebar blockedCount={blockedCount} />
      <main className="ml-64 p-7">
        <Header onExportPack={handleExportPack} onRunControls={handleRunControls} />
        {children}
      </main>
    </div>
  );
}
```

- [ ] **Step 4: Write redirect page `app/(dashboard)/page.tsx`**

Create `apps/dashboard/app/(dashboard)/page.tsx`:

```typescript
import { redirect } from "next/navigation";

export default function DashboardRoot() {
  redirect("/command");
}
```

- [ ] **Step 5: Verify shell renders**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os/apps/dashboard
npm run dev
```

Open http://localhost:3000. Expected: sidebar with 6 nav items, header with theme toggle and export buttons. No console errors.

- [ ] **Step 6: Commit**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os
git add apps/dashboard
git commit -m "feat(dashboard): add sidebar, header, and dashboard shell layout"
```

---

## Task 5: TanStack Query Hooks

**Files:**
- Create: `apps/dashboard/hooks/use-systems.ts`
- Create: `apps/dashboard/hooks/use-contracts.ts`
- Create: `apps/dashboard/hooks/use-drift-events.ts`
- Create: `apps/dashboard/hooks/use-evidence-documents.ts`
- Create: `apps/dashboard/hooks/use-audit-events.ts`
- Create: `apps/dashboard/hooks/use-eval-runs.ts`

- [ ] **Step 1: Write `hooks/use-systems.ts`**

Create `apps/dashboard/hooks/use-systems.ts`:

```typescript
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { MOCK_SYSTEMS } from "@/lib/mock-data";

export function useSystems() {
  return useQuery({
    queryKey: ["systems"],
    queryFn: api.systems.list,
    placeholderData: MOCK_SYSTEMS,
  });
}
```

- [ ] **Step 2: Write `hooks/use-contracts.ts`**

Create `apps/dashboard/hooks/use-contracts.ts`:

```typescript
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { MOCK_CONTRACTS } from "@/lib/mock-data";

export function useContracts(systemId?: string) {
  return useQuery({
    queryKey: ["contracts", systemId],
    queryFn: () => api.contracts.list(systemId),
    placeholderData: MOCK_CONTRACTS,
  });
}
```

- [ ] **Step 3: Write `hooks/use-drift-events.ts`**

Create `apps/dashboard/hooks/use-drift-events.ts`:

```typescript
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { MOCK_DRIFT_EVENTS } from "@/lib/mock-data";

export function useDriftEvents(contractId: string) {
  return useQuery({
    queryKey: ["drift-events", contractId],
    queryFn: () => api.contracts.driftEvents(contractId),
    placeholderData: MOCK_DRIFT_EVENTS.filter((e) => e.contractId === contractId),
    enabled: !!contractId,
  });
}
```

- [ ] **Step 4: Write `hooks/use-evidence-documents.ts`**

Create `apps/dashboard/hooks/use-evidence-documents.ts`:

```typescript
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";

export function useEvidenceDocuments(systemId: string | undefined) {
  return useQuery({
    queryKey: ["evidence-documents", systemId],
    queryFn: () => api.evidence.documents(systemId!),
    enabled: !!systemId,
    placeholderData: [],
  });
}
```

- [ ] **Step 5: Write `hooks/use-audit-events.ts`**

Create `apps/dashboard/hooks/use-audit-events.ts`:

```typescript
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";
import { MOCK_AUDIT_EVENTS } from "@/lib/mock-data";

export function useAuditEvents(systemId?: string) {
  return useQuery({
    queryKey: ["audit-events", systemId],
    queryFn: () => api.audit.list(systemId),
    placeholderData: MOCK_AUDIT_EVENTS,
    refetchInterval: 15_000,
  });
}
```

- [ ] **Step 6: Write `hooks/use-eval-runs.ts`**

Create `apps/dashboard/hooks/use-eval-runs.ts`:

```typescript
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api";

export function useEvalOperations() {
  return useQuery({
    queryKey: ["eval-operations"],
    queryFn: api.evals.operations,
    refetchInterval: 10_000,
  });
}
```

- [ ] **Step 7: Commit**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os
git add apps/dashboard/hooks
git commit -m "feat(dashboard): add TanStack Query hooks for all API domains"
```

---

## Task 6: Shared UI Components

**Files:**
- Create: `apps/dashboard/components/decision-badge.tsx`
- Create: `apps/dashboard/components/risk-badge.tsx`
- Create: `apps/dashboard/components/api-status-pill.tsx`
- Create: `apps/dashboard/components/system-card.tsx`
- Create: `apps/dashboard/components/contract-card.tsx`
- Create: `apps/dashboard/components/release-gate-table.tsx`

- [ ] **Step 1: Write `components/decision-badge.tsx`**

Create `apps/dashboard/components/decision-badge.tsx`:

```typescript
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

interface DecisionBadgeProps {
  decision: "Pass" | "Review" | "Blocked";
  className?: string;
}

export function DecisionBadge({ decision, className }: DecisionBadgeProps) {
  return (
    <Badge
      variant="outline"
      className={cn(
        "font-bold",
        decision === "Pass" && "border-emerald-300 text-emerald-700 dark:border-emerald-700 dark:text-emerald-400",
        decision === "Review" && "border-amber-300 text-amber-700 dark:border-amber-700 dark:text-amber-400",
        decision === "Blocked" && "border-red-300 text-red-700 dark:border-red-700 dark:text-red-400",
        className
      )}
    >
      {decision}
    </Badge>
  );
}
```

- [ ] **Step 2: Write `components/risk-badge.tsx`**

Create `apps/dashboard/components/risk-badge.tsx`:

```typescript
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import type { RiskClass } from "@/lib/types";

interface RiskBadgeProps {
  risk: RiskClass;
  className?: string;
}

export function RiskBadge({ risk, className }: RiskBadgeProps) {
  return (
    <Badge
      variant="outline"
      className={cn(
        "font-bold capitalize",
        risk === "high" && "border-red-300 text-red-700 dark:border-red-700 dark:text-red-400",
        risk === "limited" && "border-amber-300 text-amber-700 dark:border-amber-700 dark:text-amber-400",
        risk === "minimal" && "border-emerald-300 text-emerald-700 dark:border-emerald-700 dark:text-emerald-400",
        risk === "prohibited" && "border-red-500 bg-red-50 text-red-800 dark:bg-red-950 dark:text-red-300",
        className
      )}
    >
      {risk}
    </Badge>
  );
}
```

- [ ] **Step 3: Write `components/api-status-pill.tsx`**

Create `apps/dashboard/components/api-status-pill.tsx`:

```typescript
import { cn } from "@/lib/utils";

interface ApiStatusPillProps {
  online: boolean;
}

export function ApiStatusPill({ online }: ApiStatusPillProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-bold",
        online
          ? "border-emerald-300 text-emerald-700 dark:border-emerald-700 dark:text-emerald-400"
          : "border-border text-muted-foreground"
      )}
    >
      <span
        className={cn(
          "inline-block w-1.5 h-1.5 rounded-full",
          online ? "bg-emerald-500" : "bg-muted-foreground"
        )}
      />
      {online ? "API connected" : "Demo mode"}
    </span>
  );
}
```

- [ ] **Step 4: Write `components/system-card.tsx`**

Create `apps/dashboard/components/system-card.tsx`:

```typescript
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { RiskBadge } from "./risk-badge";
import { DecisionBadge } from "./decision-badge";
import { Badge } from "@/components/ui/badge";
import { normaliseDecision } from "@/lib/utils";
import type { AiSystem } from "@/lib/types";

interface SystemCardProps {
  system: AiSystem;
}

export function SystemCard({ system }: SystemCardProps) {
  const decision = normaliseDecision(system.releaseDecision);

  return (
    <Card>
      <CardHeader className="pb-2">
        <div className="flex items-center gap-2 flex-wrap mb-1">
          <RiskBadge risk={system.riskClass} />
          <Badge variant="outline" className="text-muted-foreground">
            {system.owner}
          </Badge>
        </div>
        <h3 className="font-bold text-base">{system.name}</h3>
        <p className="text-sm text-muted-foreground">{system.riskBasis}</p>
      </CardHeader>
      <CardContent>
        <p className="text-xs font-bold uppercase text-muted-foreground mb-1.5">
          Assurance coverage
        </p>
        <div className="h-2 rounded-full bg-muted overflow-hidden mb-3">
          <div
            className="h-full rounded-full bg-gradient-to-r from-cyan-700 to-teal-600"
            style={{ width: `${system.evidenceCoverage}%` }}
          />
        </div>
        <div className="flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            {system.openGaps.length
              ? system.openGaps.join("; ")
              : "No open gaps."}
          </p>
          <DecisionBadge decision={decision} />
        </div>
      </CardContent>
    </Card>
  );
}
```

- [ ] **Step 5: Write `components/contract-card.tsx`**

Create `apps/dashboard/components/contract-card.tsx`:

```typescript
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { cn, contractStatusColor } from "@/lib/utils";
import type { DataContract, DriftEvent } from "@/lib/types";

interface ContractCardProps {
  contract: DataContract;
  driftEvents: DriftEvent[];
}

export function ContractCard({ contract, driftEvents }: ContractCardProps) {
  const openEvents = driftEvents.filter((e) => e.status === "OPEN");

  return (
    <Card className={cn(
      contract.status === "BREACH" && "border-red-300 dark:border-red-800",
      contract.status === "WARNING" && "border-amber-300 dark:border-amber-800"
    )}>
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <span className="text-xs text-muted-foreground font-bold">{contract.owner}</span>
          <Badge
            variant="outline"
            className={cn("font-bold text-xs", contractStatusColor(contract.status))}
          >
            {contract.status}
          </Badge>
        </div>
        <h3 className="font-bold text-sm font-mono">{contract.name}</h3>
        <p className="text-xs text-muted-foreground">v{contract.version} · {contract.coverage}% coverage</p>
      </CardHeader>
      {openEvents.length > 0 && (
        <CardContent>
          <div className="space-y-2">
            {openEvents.map((event) => (
              <div
                key={event.id}
                className="border-l-2 border-red-400 pl-3 text-xs text-muted-foreground"
              >
                <span className="font-bold text-foreground">{event.field ?? "schema"}</span>
                {" — "}
                {event.description}
              </div>
            ))}
          </div>
        </CardContent>
      )}
    </Card>
  );
}
```

- [ ] **Step 6: Write `components/release-gate-table.tsx`**

Create `apps/dashboard/components/release-gate-table.tsx`:

```typescript
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { RiskBadge } from "./risk-badge";
import { DecisionBadge } from "./decision-badge";
import { normaliseDecision } from "@/lib/utils";
import type { AiSystem } from "@/lib/types";

interface ReleaseGateTableProps {
  systems: AiSystem[];
}

export function ReleaseGateTable({ systems }: ReleaseGateTableProps) {
  return (
    <div className="overflow-x-auto border border-border rounded-lg">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>System</TableHead>
            <TableHead>Risk class</TableHead>
            <TableHead>Evidence</TableHead>
            <TableHead>Eval score</TableHead>
            <TableHead>Data contract</TableHead>
            <TableHead>Decision</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {systems.map((system) => {
            const decision = normaliseDecision(system.releaseDecision);
            return (
              <TableRow key={system.id}>
                <TableCell>
                  <p className="font-semibold">{system.name}</p>
                  <p className="text-xs text-muted-foreground">{system.owner}</p>
                </TableCell>
                <TableCell>
                  <RiskBadge risk={system.riskClass} />
                  <p className="text-xs text-muted-foreground mt-1">{system.riskBasis}</p>
                </TableCell>
                <TableCell>{system.evidenceCoverage}%</TableCell>
                <TableCell>{system.evalScore}%</TableCell>
                <TableCell>
                  <span className={
                    system.dataContractStatus === "BREACH"
                      ? "text-red-600 dark:text-red-400 font-semibold"
                      : system.dataContractStatus === "WARNING"
                      ? "text-amber-600 dark:text-amber-400 font-semibold"
                      : "text-emerald-600 dark:text-emerald-400"
                  }>
                    {system.dataContractStatus}
                  </span>
                </TableCell>
                <TableCell>
                  <DecisionBadge decision={decision} />
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
```

- [ ] **Step 7: Commit**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os
git add apps/dashboard/components
git commit -m "feat(dashboard): add shared UI components (badges, cards, table)"
```

---

## Task 7: Risk Topology Component

**Files:**
- Create: `apps/dashboard/components/risk-topology.tsx`

- [ ] **Step 1: Write `components/risk-topology.tsx` — ported canvas scatter plot**

Create `apps/dashboard/components/risk-topology.tsx`:

```typescript
"use client";

import { useEffect, useRef } from "react";
import { useTheme } from "next-themes";
import type { AiSystem } from "@/lib/types";
import { normaliseDecision } from "@/lib/utils";

const RISK_COLORS: Record<string, string> = {
  high: "#b42318",
  limited: "#b54708",
  minimal: "#057a55",
  prohibited: "#7f1d1d",
};

interface RiskTopologyProps {
  systems: AiSystem[];
  filter: "all" | "high" | "limited" | "minimal";
}

export function RiskTopology({ systems, filter }: RiskTopologyProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const { resolvedTheme } = useTheme();

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const filtered = systems.filter((s) => filter === "all" || s.riskClass === filter);
    const dark = resolvedTheme === "dark";

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = dark ? "#111318" : "#f9fafb";
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
    ctx.fillText("Lower release readiness", 44, 24);
    ctx.fillText("Higher release readiness", canvas.width - 190, 24);
    ctx.save();
    ctx.translate(18, canvas.height - 70);
    ctx.rotate(-Math.PI / 2);
    ctx.fillText("Risk and data criticality", 0, 0);
    ctx.restore();

    filtered.forEach((system, index) => {
      const x = 130 + (system.evidenceCoverage / 100) * 650 + (index % 2) * 18;
      const y =
        295 -
        (system.evalScore / 100) * 230 +
        (system.riskClass === "high" ? -18 : system.riskClass === "limited" ? 10 : 24);
      const decision = normaliseDecision(system.releaseDecision);
      const radius = decision === "Blocked" ? 20 : 15;

      ctx.beginPath();
      ctx.arc(x, y, radius, 0, Math.PI * 2);
      ctx.fillStyle = RISK_COLORS[system.riskClass] ?? "#667085";
      ctx.globalAlpha = 0.9;
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.strokeStyle = dark ? "#f2f4f7" : "#ffffff";
      ctx.lineWidth = 3;
      ctx.stroke();

      ctx.fillStyle = dark ? "#f2f4f7" : "#111827";
      ctx.font = "12px system-ui";
      const label = system.name.length > 22 ? `${system.name.slice(0, 21)}…` : system.name;
      ctx.fillText(label, x + radius + 7, y + 4);
    });
  }, [systems, filter, resolvedTheme]);

  return (
    <canvas
      ref={canvasRef}
      width={900}
      height={360}
      className="block w-full h-auto rounded-lg border border-border bg-muted/20"
      aria-label="AI system risk topology"
    />
  );
}
```

- [ ] **Step 2: Commit**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os
git add apps/dashboard/components/risk-topology.tsx
git commit -m "feat(dashboard): add risk topology canvas component"
```

---

## Task 8: Lineage Graph Component (Phase 4 Core Feature)

**Files:**
- Create: `apps/dashboard/components/lineage-nodes.tsx`
- Create: `apps/dashboard/components/lineage-graph.tsx`

- [ ] **Step 1: Install React Flow**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os/apps/dashboard
npm install @xyflow/react
```

Expected: `@xyflow/react` in package.json.

- [ ] **Step 2: Write `components/lineage-nodes.tsx` — custom node types**

Create `apps/dashboard/components/lineage-nodes.tsx`:

```typescript
import { Handle, Position, type NodeProps } from "@xyflow/react";
import { cn, contractStatusColor } from "@/lib/utils";
import type { DataContractStatus, ReleaseDecision } from "@/lib/types";

export interface SourceNodeData {
  label: string;
  owner: string;
}

export interface ContractNodeData {
  label: string;
  version: string;
  coverage: number;
  status: DataContractStatus;
}

export interface SystemNodeData {
  label: string;
  owner: string;
  decision: "Pass" | "Review" | "Blocked";
}

export function SourceNode({ data }: NodeProps) {
  const d = data as SourceNodeData;
  return (
    <div className="bg-card border border-border rounded-lg px-3 py-2 shadow-sm min-w-[140px]">
      <p className="text-xs font-bold uppercase text-muted-foreground mb-0.5">Data Source</p>
      <p className="text-sm font-semibold">{d.label}</p>
      <p className="text-xs text-muted-foreground">{d.owner}</p>
      <Handle type="source" position={Position.Right} className="!bg-border" />
    </div>
  );
}

export function ContractNode({ data }: NodeProps) {
  const d = data as ContractNodeData;
  return (
    <div className={cn(
      "bg-card border rounded-lg px-3 py-2 shadow-sm min-w-[160px]",
      d.status === "BREACH" ? "border-red-400 dark:border-red-700" :
      d.status === "WARNING" ? "border-amber-400 dark:border-amber-700" :
      "border-emerald-400 dark:border-emerald-700"
    )}>
      <Handle type="target" position={Position.Left} className="!bg-border" />
      <p className="text-xs font-bold uppercase text-muted-foreground mb-0.5">Contract</p>
      <p className={cn("text-xs font-bold mb-0.5", contractStatusColor(d.status))}>{d.status}</p>
      <p className="text-sm font-mono font-semibold">{d.label}</p>
      <p className="text-xs text-muted-foreground">v{d.version} · {d.coverage}% coverage</p>
      <Handle type="source" position={Position.Right} className="!bg-border" />
    </div>
  );
}

export function SystemNode({ data }: NodeProps) {
  const d = data as SystemNodeData;
  return (
    <div className={cn(
      "bg-card border rounded-lg px-3 py-2 shadow-sm min-w-[160px]",
      d.decision === "Blocked" ? "border-red-400 dark:border-red-700" :
      d.decision === "Review" ? "border-amber-400 dark:border-amber-700" :
      "border-emerald-400 dark:border-emerald-700"
    )}>
      <Handle type="target" position={Position.Left} className="!bg-border" />
      <p className="text-xs font-bold uppercase text-muted-foreground mb-0.5">AI System</p>
      <p className={cn(
        "text-xs font-bold mb-0.5",
        d.decision === "Blocked" ? "text-red-600 dark:text-red-400" :
        d.decision === "Review" ? "text-amber-600 dark:text-amber-400" :
        "text-emerald-600 dark:text-emerald-400"
      )}>{d.decision}</p>
      <p className="text-sm font-semibold">{d.label}</p>
      <p className="text-xs text-muted-foreground">{d.owner}</p>
    </div>
  );
}
```

- [ ] **Step 3: Write `components/lineage-graph.tsx` — React Flow DAG**

Create `apps/dashboard/components/lineage-graph.tsx`:

```typescript
"use client";

import "@xyflow/react/dist/style.css";
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  type Node,
  type Edge,
} from "@xyflow/react";
import { useMemo } from "react";
import { SourceNode, ContractNode, SystemNode } from "./lineage-nodes";
import type { AiSystem, DataContract } from "@/lib/types";
import { normaliseDecision } from "@/lib/utils";

const NODE_TYPES = {
  source: SourceNode,
  contract: ContractNode,
  system: SystemNode,
};

interface LineageGraphProps {
  systems: AiSystem[];
  contracts: DataContract[];
}

export function LineageGraph({ systems, contracts }: LineageGraphProps) {
  const { nodes, edges } = useMemo(() => {
    const nodes: Node[] = [];
    const edges: Edge[] = [];

    // Derive unique data sources from contract owners
    const sources = Array.from(new Set(contracts.map((c) => c.owner)));

    // Source nodes — column 0
    sources.forEach((owner, i) => {
      nodes.push({
        id: `source-${owner}`,
        type: "source",
        position: { x: 0, y: i * 120 },
        data: { label: owner, owner: "Data Platform" },
      });
    });

    // Contract nodes — column 1
    contracts.forEach((contract, i) => {
      nodes.push({
        id: `contract-${contract.id}`,
        type: "contract",
        position: { x: 260, y: i * 130 },
        data: {
          label: contract.name,
          version: contract.version,
          coverage: contract.coverage,
          status: contract.status,
        },
      });

      // Source → Contract edge
      edges.push({
        id: `edge-source-${contract.id}`,
        source: `source-${contract.owner}`,
        target: `contract-${contract.id}`,
        animated: contract.status !== "HEALTHY",
        style: {
          stroke: contract.status === "BREACH" ? "#b42318" :
                  contract.status === "WARNING" ? "#b54708" : "#667085",
        },
      });
    });

    // System nodes — column 2
    systems.forEach((system, i) => {
      const decision = normaliseDecision(system.releaseDecision);
      nodes.push({
        id: `system-${system.id}`,
        type: "system",
        position: { x: 540, y: i * 110 },
        data: { label: system.name, owner: system.owner, decision },
      });

      // Contract → System edges (match by systemId)
      const linked = contracts.filter((c) => c.systemId === system.id);
      linked.forEach((contract) => {
        edges.push({
          id: `edge-contract-${contract.id}-system-${system.id}`,
          source: `contract-${contract.id}`,
          target: `system-${system.id}`,
          animated: contract.status !== "HEALTHY",
          style: {
            stroke: contract.status === "BREACH" ? "#b42318" :
                    contract.status === "WARNING" ? "#b54708" : "#667085",
          },
        });
      });
    });

    return { nodes, edges };
  }, [systems, contracts]);

  return (
    <div className="h-[520px] rounded-lg border border-border overflow-hidden bg-muted/10">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        nodeTypes={NODE_TYPES}
        fitView
        fitViewOptions={{ padding: 0.2 }}
        nodesDraggable
        nodesConnectable={false}
        elementsSelectable
        proOptions={{ hideAttribution: true }}
      >
        <Background gap={16} size={1} />
        <Controls showInteractive={false} />
        <MiniMap nodeStrokeWidth={3} zoomable pannable />
      </ReactFlow>
    </div>
  );
}
```

- [ ] **Step 4: Commit**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os
git add apps/dashboard/components/lineage-nodes.tsx apps/dashboard/components/lineage-graph.tsx
git commit -m "feat(dashboard): add React Flow lineage DAG with source/contract/system nodes"
```

---

## Task 9: Command Center Page

**Files:**
- Create: `apps/dashboard/app/(dashboard)/command/page.tsx`

- [ ] **Step 1: Write the command center page**

Create `apps/dashboard/app/(dashboard)/command/page.tsx`:

```typescript
"use client";

import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { RiskTopology } from "@/components/risk-topology";
import { ReleaseGateTable } from "@/components/release-gate-table";
import { useSystems } from "@/hooks/use-systems";
import { MOCK_SYSTEMS } from "@/lib/mock-data";
import { normaliseDecision } from "@/lib/utils";

export default function CommandPage() {
  const { data: systems = MOCK_SYSTEMS } = useSystems();
  const [riskFilter, setRiskFilter] = useState<"all" | "high" | "limited" | "minimal">("all");

  const blocked = systems.filter((s) => normaliseDecision(s.releaseDecision) === "Blocked").length;
  const highRisk = systems.filter((s) => s.riskClass === "high").length;
  const totalGaps = systems.reduce((sum, s) => sum + s.openGaps.length, 0);
  const avgEval = Math.round(systems.reduce((sum, s) => sum + s.evalScore, 0) / systems.length);
  const avgEvidence = Math.round(systems.reduce((sum, s) => sum + s.evidenceCoverage, 0) / systems.length);

  return (
    <div className="space-y-4">
      {/* Metrics */}
      <div className="grid grid-cols-4 gap-4">
        <Card>
          <CardContent className="pt-5">
            <p className="text-xs font-black uppercase text-muted-foreground">AI systems</p>
            <p className="text-4xl font-bold my-2">{systems.length}</p>
            <p className="text-sm text-muted-foreground">{highRisk} high-risk</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-5">
            <p className="text-xs font-black uppercase text-muted-foreground">Open control gaps</p>
            <p className="text-4xl font-bold my-2">{totalGaps}</p>
            <p className="text-sm text-red-600 dark:text-red-400">{blocked} release blockers</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-5">
            <p className="text-xs font-black uppercase text-muted-foreground">Eval gate pass rate</p>
            <p className="text-4xl font-bold my-2">{avgEval}%</p>
            <p className="text-sm text-amber-600 dark:text-amber-400">target 85%</p>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-5">
            <p className="text-xs font-black uppercase text-muted-foreground">Audit completeness</p>
            <p className="text-4xl font-bold my-2">{Math.min(98, avgEvidence + 5)}%</p>
            <p className="text-sm text-emerald-600 dark:text-emerald-400">ready for review</p>
          </CardContent>
        </Card>
      </div>

      {/* Risk Topology */}
      <Card>
        <CardHeader className="flex-row items-center justify-between pb-3">
          <div>
            <CardTitle>Risk Topology</CardTitle>
            <p className="text-sm text-muted-foreground mt-1">
              Controls grouped by system risk, data criticality, and release readiness.
            </p>
          </div>
          <Select value={riskFilter} onValueChange={(v) => setRiskFilter(v as typeof riskFilter)}>
            <SelectTrigger className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All systems</SelectItem>
              <SelectItem value="high">High-risk</SelectItem>
              <SelectItem value="limited">Limited-risk</SelectItem>
              <SelectItem value="minimal">Minimal-risk</SelectItem>
            </SelectContent>
          </Select>
        </CardHeader>
        <CardContent>
          <RiskTopology systems={systems} filter={riskFilter} />
        </CardContent>
      </Card>

      {/* Release Gate Table */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle>Release Gate</CardTitle>
          <p className="text-sm text-muted-foreground">
            Combined view of compliance evidence, eval regression, data drift, and human oversight.
          </p>
        </CardHeader>
        <CardContent>
          <ReleaseGateTable systems={systems} />
        </CardContent>
      </Card>
    </div>
  );
}
```

- [ ] **Step 2: Verify page renders**

Navigate to http://localhost:3000/command. Expected: 4 metric cards, canvas scatter plot, release gate table all populated. No console errors.

- [ ] **Step 3: Commit**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os
git add apps/dashboard/app/'(dashboard)'/command
git commit -m "feat(dashboard): add command center page with metrics, topology, and release gate table"
```

---

## Task 10: Systems, Evidence, Evals, and Audit Pages

**Files:**
- Create: `apps/dashboard/app/(dashboard)/systems/page.tsx`
- Create: `apps/dashboard/app/(dashboard)/evidence/page.tsx`
- Create: `apps/dashboard/app/(dashboard)/evals/page.tsx`
- Create: `apps/dashboard/app/(dashboard)/audit/page.tsx`

- [ ] **Step 1: Write systems page**

Create `apps/dashboard/app/(dashboard)/systems/page.tsx`:

```typescript
"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { SystemCard } from "@/components/system-card";
import { useSystems } from "@/hooks/use-systems";
import { MOCK_SYSTEMS } from "@/lib/mock-data";

export default function SystemsPage() {
  const { data: systems = MOCK_SYSTEMS } = useSystems();

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle>AI System Registry</CardTitle>
        <p className="text-sm text-muted-foreground">
          Inventory for providers, deployers, owners, use purpose, and risk basis.
        </p>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-3 gap-4">
          {systems.map((system) => (
            <SystemCard key={system.id} system={system} />
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
```

- [ ] **Step 2: Write evidence page**

Create `apps/dashboard/app/(dashboard)/evidence/page.tsx`:

```typescript
"use client";

import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ApiStatusPill } from "@/components/api-status-pill";
import { useSystems } from "@/hooks/use-systems";
import { useEvidenceDocuments } from "@/hooks/use-evidence-documents";
import { api } from "@/lib/api";
import { MOCK_SYSTEMS } from "@/lib/mock-data";
import type { EvidenceQueryResponse, EvidenceDocument } from "@/lib/types";
import { formatDate } from "@/lib/utils";

const EVIDENCE_TYPES = ["DPIA", "POLICY", "MODEL_CARD", "VENDOR_DOC", "CONTROL_MAP"];

export default function EvidencePage() {
  const { data: systems = MOCK_SYSTEMS, isError: systemsError } = useSystems();
  const qc = useQueryClient();
  const apiOnline = !systemsError && systems !== MOCK_SYSTEMS;

  const [selectedSystemId, setSelectedSystemId] = useState<string>(systems[0]?.id ?? "");
  const [question, setQuestion] = useState("Which controls block the Claims Triage AI release, and what evidence is missing?");
  const [ragResponse, setRagResponse] = useState<EvidenceQueryResponse | null>(null);
  const [demoAnswer, setDemoAnswer] = useState<string | null>(null);
  const [querying, setQuerying] = useState(false);

  const [docType, setDocType] = useState("DPIA");
  const [docTitle, setDocTitle] = useState("Claims Triage Oversight SOP");
  const [docSource, setDocSource] = useState("memory://claims-oversight-sop");
  const [docContent, setDocContent] = useState("Human oversight SOP requires reviewer override, claimant appeal route, owner sign-off, and monthly bias monitoring evidence before release.");
  const [indexing, setIndexing] = useState(false);

  const { data: documents = [] } = useEvidenceDocuments(apiOnline ? selectedSystemId : undefined);

  async function handleQuery(e: React.FormEvent) {
    e.preventDefault();
    setQuerying(true);
    try {
      const res = await api.evidence.query(selectedSystemId, question);
      setRagResponse(res);
      setDemoAnswer(null);
    } catch {
      setRagResponse(null);
      setDemoAnswer("API unavailable. The system is classified as high-risk. The release gate depends on human oversight evidence, eval threshold performance, and data-contract status.");
    } finally {
      setQuerying(false);
    }
  }

  async function handleIndex(e: React.FormEvent) {
    e.preventDefault();
    setIndexing(true);
    try {
      await api.evidence.index({
        systemId: selectedSystemId,
        type: docType,
        title: docTitle,
        sourceUri: docSource,
        content: docContent,
        metadata: { source: "web-dashboard" },
      });
      qc.invalidateQueries({ queryKey: ["evidence-documents", selectedSystemId] });
    } catch (err) {
      console.error("Index failed", err);
    } finally {
      setIndexing(false);
    }
  }

  const selectedSystem = systems.find((s) => s.id === selectedSystemId) ?? systems[0];

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4">
        {/* Query */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle>Compliance Evidence RAG</CardTitle>
            <p className="text-sm text-muted-foreground">
              Ask against policies, DPIAs, model cards, vendor docs, and EU control mappings.
            </p>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleQuery} className="space-y-3">
              <div className="space-y-1.5">
                <label className="text-xs font-bold uppercase text-muted-foreground">System</label>
                <Select value={selectedSystemId} onValueChange={setSelectedSystemId}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {systems.map((s) => (
                      <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-bold uppercase text-muted-foreground">Question</label>
                <Textarea rows={5} value={question} onChange={(e) => setQuestion(e.target.value)} />
              </div>
              <Button type="submit" disabled={querying}>
                {querying ? "Querying…" : "Ask with citations"}
              </Button>
            </form>
          </CardContent>
        </Card>

        {/* Answer */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle>Grounded Answer</CardTitle>
            <p className="text-sm text-muted-foreground">
              Every answer includes confidence, source clauses, and reviewer action.
            </p>
          </CardHeader>
          <CardContent>
            {ragResponse ? (
              <div className="space-y-3">
                <p className="text-xs font-bold uppercase text-muted-foreground">Question</p>
                <p className="text-sm">{question}</p>
                <p className="font-semibold">{ragResponse.answer}</p>
                {ragResponse.citations.map((c, i) => (
                  <div key={i} className="border-l-2 border-teal-500 pl-3 text-sm text-muted-foreground">
                    <span className="font-bold text-foreground">{c.title} · {c.section}</span>
                    <br />{c.snippet}
                  </div>
                ))}
                <div className="border-l-2 border-teal-500 pl-3 text-sm text-muted-foreground">
                  <span className="font-bold">Confidence: {Math.round(ragResponse.confidence * 100)}%</span>
                  <br />Reviewer should inspect cited source material before release approval.
                </div>
              </div>
            ) : demoAnswer ? (
              <div className="space-y-3">
                <p className="text-xs font-bold uppercase text-muted-foreground">Demo mode answer</p>
                <p className="text-sm">{demoAnswer}</p>
                <div className="border-l-2 border-teal-500 pl-3 text-sm text-muted-foreground">
                  <span className="font-bold">Source: DPIA-CLM-014</span>
                  <br />Reviewer override must include purpose, affected cohort, appeal route, and owner sign-off.
                </div>
              </div>
            ) : (
              <p className="text-muted-foreground text-sm">Run a cited compliance query.</p>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-2 gap-4">
        {/* Index */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle>Index Evidence</CardTitle>
            <p className="text-sm text-muted-foreground">
              Upload extracted text from a policy, DPIA, model card, or vendor document.
            </p>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleIndex} className="space-y-3">
              <div className="space-y-1.5">
                <label className="text-xs font-bold uppercase text-muted-foreground">Type</label>
                <Select value={docType} onValueChange={setDocType}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {EVIDENCE_TYPES.map((t) => <SelectItem key={t} value={t}>{t}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-bold uppercase text-muted-foreground">Title</label>
                <input
                  className="w-full border border-border rounded-md px-3 py-2 text-sm bg-background"
                  value={docTitle}
                  onChange={(e) => setDocTitle(e.target.value)}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-bold uppercase text-muted-foreground">Source URI</label>
                <input
                  className="w-full border border-border rounded-md px-3 py-2 text-sm bg-background"
                  value={docSource}
                  onChange={(e) => setDocSource(e.target.value)}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-bold uppercase text-muted-foreground">Extracted text</label>
                <Textarea rows={5} value={docContent} onChange={(e) => setDocContent(e.target.value)} />
              </div>
              <Button type="submit" disabled={indexing || !apiOnline}>
                {indexing ? "Indexing…" : "Index document"}
              </Button>
              {!apiOnline && <p className="text-xs text-muted-foreground">Start the API to index evidence.</p>}
            </form>
          </CardContent>
        </Card>

        {/* Documents */}
        <Card>
          <CardHeader className="pb-3 flex-row items-center justify-between">
            <div>
              <CardTitle>Indexed Documents</CardTitle>
              <p className="text-sm text-muted-foreground mt-1">
                Documents available for cited retrieval on {selectedSystem?.name ?? "selected system"}.
              </p>
            </div>
            <ApiStatusPill online={apiOnline} />
          </CardHeader>
          <CardContent>
            {documents.length === 0 ? (
              <div className="border border-dashed border-border rounded-lg min-h-32 grid place-items-center text-sm text-muted-foreground">
                No indexed documents loaded.
              </div>
            ) : (
              <div className="space-y-2">
                {documents.map((doc: EvidenceDocument) => (
                  <div key={doc.id} className="flex items-center justify-between border border-border rounded-lg bg-muted/30 px-3 py-2.5">
                    <div>
                      <p className="text-sm font-semibold">{doc.title}</p>
                      <p className="text-xs text-muted-foreground mt-0.5">{doc.type} · {doc.chunkCount} chunk{doc.chunkCount !== 1 ? "s" : ""} · {formatDate(doc.createdAt)}</p>
                    </div>
                    <span className="text-xs text-muted-foreground">{doc.ingestionStatus}</span>
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
```

- [ ] **Step 3: Write evals page**

Create `apps/dashboard/app/(dashboard)/evals/page.tsx`:

```typescript
"use client";

import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useSystems } from "@/hooks/use-systems";
import { useEvalOperations } from "@/hooks/use-eval-runs";
import { api } from "@/lib/api";
import { MOCK_SYSTEMS } from "@/lib/mock-data";
import type { EvalRun } from "@/lib/types";

const DATASETS = ["golden-eu-claims-v4", "hr-candidate-screening-v2", "customer-support-rag-v8"];

export default function EvalsPage() {
  const { data: systems = MOCK_SYSTEMS } = useSystems();
  const { data: operations } = useEvalOperations();

  const [selectedSystemId, setSelectedSystemId] = useState(systems[0]?.id ?? "");
  const [dataset, setDataset] = useState(DATASETS[0]);
  const [threshold, setThreshold] = useState(85);
  const [consoleLines, setConsoleLines] = useState<string[]>([]);
  const [running, setRunning] = useState(false);

  async function handleRun(e: React.FormEvent) {
    e.preventDefault();
    setRunning(true);
    const system = systems.find((s) => s.id === selectedSystemId) ?? systems[0];
    setConsoleLines([`> queued eval run for ${system.name}`, "> submitting to API…"]);

    try {
      const { runId } = await api.evals.create({
        systemId: selectedSystemId,
        dataset,
        modelVersion: `${system.name.toLowerCase().replace(/\s/g, "-")}-2026-06`,
        promptVersion: "v1",
        threshold,
      });
      setConsoleLines((prev) => [...prev, `> run ${runId} created, executing…`]);
      const result: EvalRun = await api.evals.execute(runId);
      const m = result.metrics;
      setConsoleLines([
        `> run ${runId} for ${system.name}`,
        `> faithfulness: ${m.faithfulness !== undefined ? Math.round(m.faithfulness * 100) : "—"}%`,
        `> relevance: ${m.relevance !== undefined ? Math.round(m.relevance * 100) : "—"}%`,
        `> safety refusal: ${m.safetyRefusal !== undefined ? Math.round(m.safetyRefusal * 100) : "—"}%`,
        `> bias slice pass rate: ${m.biasSlicePassRate !== undefined ? Math.round(m.biasSlicePassRate * 100) : "—"}%`,
        `> latency p95: ${m.latencyP95Ms ?? "—"}ms`,
        `> cost: $${m.costUsd ?? "—"}`,
        `> release decision: ${result.releaseDecision}`,
      ]);
    } catch {
      const system = systems.find((s) => s.id === selectedSystemId) ?? systems[0];
      const jitter = Math.round(Math.random() * 10 - 4);
      const score = Math.max(55, Math.min(96, system.evalScore + jitter));
      const decision = score >= threshold ? "PASS" : score < threshold - 7 ? "BLOCKED" : "REVIEW";
      setConsoleLines([
        `> queued eval run for ${system.name} (demo)`,
        "> loaded dataset and judge rubric",
        `> faithfulness: ${score}%`,
        `> safety refusal: ${Math.max(70, score - 3)}%`,
        `> latency guard: pass`,
        `> data contract: ${system.dataContractStatus}`,
        `> release decision: ${decision}`,
      ]);
    } finally {
      setRunning(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-4">
        <Card>
          <CardHeader className="pb-3">
            <CardTitle>Eval Gate Runner</CardTitle>
            <p className="text-sm text-muted-foreground">
              LLM-as-judge, RAG faithfulness, safety refusal, latency, and cost guardrails.
            </p>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleRun} className="space-y-3">
              <div className="space-y-1.5">
                <label className="text-xs font-bold uppercase text-muted-foreground">System</label>
                <Select value={selectedSystemId} onValueChange={setSelectedSystemId}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {systems.map((s) => <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-bold uppercase text-muted-foreground">Dataset</label>
                <Select value={dataset} onValueChange={setDataset}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {DATASETS.map((d) => <SelectItem key={d} value={d}>{d}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-bold uppercase text-muted-foreground">Threshold</label>
                <input
                  type="number"
                  min={70}
                  max={98}
                  value={threshold}
                  onChange={(e) => setThreshold(Number(e.target.value))}
                  className="w-full border border-border rounded-md px-3 py-2 text-sm bg-background"
                />
              </div>
              <Button type="submit" disabled={running}>
                {running ? "Running…" : "Run eval gate"}
              </Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3">
            <CardTitle>Gate Console</CardTitle>
            <p className="text-sm text-muted-foreground">Worker output with release decision.</p>
          </CardHeader>
          <CardContent>
            <div className="min-h-72 max-h-96 overflow-auto rounded-lg bg-gray-950 text-emerald-300 font-mono text-sm p-4 leading-relaxed">
              {consoleLines.length === 0 ? (
                <span className="text-gray-600">Awaiting eval run…</span>
              ) : (
                consoleLines.map((line, i) => <div key={i}>{line}</div>)
              )}
            </div>
          </CardContent>
        </Card>
      </div>

      {operations && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle>Operations</CardTitle>
            <p className="text-sm text-muted-foreground">
              Queued, running, retryable, and failed eval runs.
            </p>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-4 gap-4 text-center">
              <div>
                <p className="text-2xl font-bold">{operations.queued.length}</p>
                <p className="text-xs text-muted-foreground uppercase font-bold">Queued</p>
              </div>
              <div>
                <p className="text-2xl font-bold">{operations.running.length}</p>
                <p className="text-xs text-muted-foreground uppercase font-bold">Running</p>
              </div>
              <div>
                <p className="text-2xl font-bold text-amber-600">{operations.retryable.length}</p>
                <p className="text-xs text-muted-foreground uppercase font-bold">Retryable</p>
              </div>
              <div>
                <p className="text-2xl font-bold text-red-600">{operations.deadLetter.length}</p>
                <p className="text-xs text-muted-foreground uppercase font-bold">Dead letter</p>
              </div>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Write audit page**

Create `apps/dashboard/app/(dashboard)/audit/page.tsx`:

```typescript
"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuditEvents } from "@/hooks/use-audit-events";
import { MOCK_AUDIT_EVENTS } from "@/lib/mock-data";
import { formatDate } from "@/lib/utils";

export default function AuditPage() {
  const { data: events = MOCK_AUDIT_EVENTS } = useAuditEvents();

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle>Immutable Audit Ledger</CardTitle>
        <p className="text-sm text-muted-foreground">
          Append-only entries for evidence checks, approvals, overrides, and release decisions.
        </p>
      </CardHeader>
      <CardContent>
        <ol className="space-y-2">
          {events.map((event) => (
            <li
              key={event.id}
              className="border border-border rounded-lg px-4 py-3 bg-muted/20"
            >
              <time className="block text-xs text-muted-foreground mb-1">
                {formatDate(event.createdAt)}
              </time>
              <p className="text-sm font-semibold">{event.eventType}</p>
              {event.resourceId && (
                <p className="text-xs text-muted-foreground mt-0.5">
                  {event.resourceType} · {event.resourceId}
                </p>
              )}
            </li>
          ))}
        </ol>
      </CardContent>
    </Card>
  );
}
```

- [ ] **Step 5: Commit**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os
git add apps/dashboard/app/'(dashboard)'
git commit -m "feat(dashboard): add systems, evidence, evals, and audit pages"
```

---

## Task 11: Contracts Page with Lineage DAG (Phase 4 Completion)

**Files:**
- Create: `apps/dashboard/app/(dashboard)/contracts/page.tsx`

- [ ] **Step 1: Write the contracts page**

Create `apps/dashboard/app/(dashboard)/contracts/page.tsx`:

```typescript
"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ContractCard } from "@/components/contract-card";
import { LineageGraph } from "@/components/lineage-graph";
import { useContracts } from "@/hooks/use-contracts";
import { useSystems } from "@/hooks/use-systems";
import { MOCK_CONTRACTS, MOCK_DRIFT_EVENTS, MOCK_SYSTEMS } from "@/lib/mock-data";
import { useQueries } from "@tanstack/react-query";
import { api } from "@/lib/api";
import type { DriftEvent } from "@/lib/types";

export default function ContractsPage() {
  const { data: systems = MOCK_SYSTEMS } = useSystems();
  const { data: contracts = MOCK_CONTRACTS } = useContracts();

  // Fetch drift events for all contracts in parallel
  const driftQueries = useQueries({
    queries: contracts.map((contract) => ({
      queryKey: ["drift-events", contract.id],
      queryFn: () => api.contracts.driftEvents(contract.id),
      placeholderData: MOCK_DRIFT_EVENTS.filter((e) => e.contractId === contract.id),
    })),
  });

  const allDriftEvents: DriftEvent[] = driftQueries.flatMap((q) => q.data ?? []);

  return (
    <div className="space-y-6">
      {/* Contract Cards */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle>Data Contract Monitor</CardTitle>
          <p className="text-sm text-muted-foreground">
            Schema drift and lineage breaches are treated as AI control failures.
          </p>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-2 gap-4">
            {contracts.map((contract) => {
              const events = allDriftEvents.filter((e) => e.contractId === contract.id);
              return <ContractCard key={contract.id} contract={contract} driftEvents={events} />;
            })}
          </div>
        </CardContent>
      </Card>

      {/* Lineage DAG */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle>Data Lineage</CardTitle>
          <p className="text-sm text-muted-foreground">
            Directed graph showing data sources → contracts → AI systems. Animated edges indicate active drift.
            Drag nodes to rearrange. Node colour reflects health status.
          </p>
        </CardHeader>
        <CardContent>
          <LineageGraph systems={systems} contracts={contracts} />
        </CardContent>
      </Card>
    </div>
  );
}
```

- [ ] **Step 2: Verify the contracts page renders**

Navigate to http://localhost:3000/contracts. Expected: contract cards showing status badges and open drift events, React Flow lineage DAG below with source/contract/system nodes, animated edges for breach/warning contracts. No console errors.

- [ ] **Step 3: Test with API running**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os/services/api
mvn spring-boot:run
```

Navigate to http://localhost:3000/contracts. Expected: real contracts load from `/api/v1/data-contracts`, drift events load per contract, lineage DAG reflects real system-contract relationships.

- [ ] **Step 4: Commit**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os
git add apps/dashboard/app/'(dashboard)'/contracts
git commit -m "feat(dashboard): add contracts page with real API wiring and React Flow lineage DAG — completes Phase 4"
```

---

## Task 12: TypeScript Build Verification and Cleanup

**Files:**
- Modify: `apps/dashboard/tsconfig.json` (verify strict mode)

- [ ] **Step 1: Run TypeScript check**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os/apps/dashboard
npx tsc --noEmit
```

Expected: no type errors. Fix any that appear — common issues are missing `"use client"` directives on components using hooks, or mismatched API response types.

- [ ] **Step 2: Run production build**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os/apps/dashboard
npm run build
```

Expected: build completes successfully. No errors. Warnings about missing `key` props or `useEffect` deps should be fixed.

- [ ] **Step 3: Update CLAUDE.md with dashboard commands**

Add to the existing `CLAUDE.md` at the repo root, under a new section after the existing API Service section:

```markdown
## Dashboard (`apps/dashboard/`)

### Build and Run

```bash
# Install dependencies
cd apps/dashboard && npm install

# Development server (proxies /api/v1/* to localhost:8080)
cd apps/dashboard && npm run dev
# → http://localhost:3000

# Production build
cd apps/dashboard && npm run build

# TypeScript check
cd apps/dashboard && npx tsc --noEmit
```

The dashboard falls back to seeded mock data when the Spring Boot API is unreachable. Start the API for full functionality.
```

- [ ] **Step 4: Update ROADMAP.md — mark Phase 4 complete**

In `docs/ROADMAP.md`, update Phase 4 to:

```markdown
## Phase 4: Data Contracts

Status: complete.

- Data contract CRUD: implemented for create/list/read/update with tenant scope.
- Drift event ingestion: implemented with open, acknowledged, and resolved states.
- Contract-to-system mapping: implemented through persisted `system_id`.
- Lineage display: implemented as interactive React Flow DAG (data source → contract → AI system) in Next.js dashboard.
- Release gate integration: implemented for warning and breach rollups.
- Frontend rewrite: vanilla JS prototype replaced with Next.js 14 + TypeScript + shadcn/ui + TanStack Query production dashboard.
```

- [ ] **Step 5: Final commit**

```bash
cd /Users/souravamseekarmarti/Projects/eu-ai-assurance-os
git add CLAUDE.md docs/ROADMAP.md apps/dashboard
git commit -m "feat: complete Phase 4 — Next.js dashboard rewrite with lineage DAG, contracts API wiring, and full frontend production upgrade"
```

---

## Self-Review

**Spec coverage check:**

| Requirement | Task |
|---|---|
| Replace vanilla JS with Next.js 14 + TypeScript | Tasks 1–4 |
| shadcn/ui components | Task 1 (scaffold), Tasks 6 (usage) |
| TanStack Query hooks for all API domains | Task 5 |
| Risk topology ported | Task 7 |
| React Flow lineage DAG — source → contract → system nodes | Task 8 |
| Node color reflects health status | Task 8 (lineage-nodes.tsx) |
| Animated edges for breach/warning | Task 8 (lineage-graph.tsx) |
| Contracts wired to real API with demo fallback | Tasks 5, 11 |
| Drift events per contract displayed | Tasks 6, 11 |
| All 6 views implemented | Tasks 9, 10, 11 |
| Dark mode | Tasks 3, 4 (ThemeProvider + useTheme) |
| API proxied to Spring Boot | Task 1 (next.config.ts rewrites) |
| TypeScript strict build passes | Task 12 |
| ROADMAP.md updated | Task 12 |
| CLAUDE.md updated | Task 12 |

**Placeholder scan:** No TBDs, TODOs, or incomplete steps found.

**Type consistency:** `AiSystem`, `DataContract`, `DriftEvent`, `EvalRun`, `AuditEvent` defined in Task 2 and used consistently across hooks (Task 5), components (Task 6), and pages (Tasks 9–11). `normaliseDecision` used wherever `releaseDecision` string is rendered as a badge.
