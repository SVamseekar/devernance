/**
 * Static product illustrations for the public site (storyboard frames 01–09).
 * They use one sample system ("Claims Triage AI") with internally consistent numbers,
 * and every page that shows one labels it as an illustrative example.
 */
import {
  AlertTriangle,
  ArrowRight,
  Check,
  Bot,
  CheckCircle2,
  ClipboardCheck,
  Database,
  FileCheck2,
  FileSearch,
  FileText,
  FlaskConical,
  GitPullRequestArrow,
  LayoutDashboard,
  Layers,
  Lock,
  Network,
  Search,
  ShieldAlert,
  Upload,
  type LucideIcon,
} from "lucide-react";

import type { ReactNode } from "react";

import { DevernanceMark } from "@/components/marketing/brand-icons";
import { CheckDot } from "@/components/marketing/primitives";
import { cn } from "@/lib/utils";


/**
 * Display frame for product mock-ups: a near-square, thin-bezel monitor with a browser bar (route included)
 * and an aluminium stand. Reads as "a computer showing the app", not a box on the page.
 */
export function ProductFrame({
  label,
  path,
  children,
  className,
}: {
  label: string;
  path: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <figure aria-label={label} className={cn("relative mx-auto w-full max-w-[52rem]", className)}>
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -inset-x-16 -top-10 bottom-10 rounded-[4rem] bg-[radial-gradient(ellipse_at_50%_45%,rgb(79_99_255/0.4),transparent_70%)] blur-3xl"
      />
      {/* Display: metallic rim around a thin black bezel. */}
      <div className="relative rounded-[1.6rem] bg-gradient-to-b from-[#e8ebf2] via-[#c3c9d6] to-[#9aa2b4] p-[3px] shadow-[0_40px_90px_-30px_rgb(5_10_28/0.6)] sm:rounded-[2rem]">
        <div className="rounded-[1.45rem] bg-[#05070f] p-2 sm:rounded-[1.8rem] sm:p-3">
          {/* Every product screen uses the same 4:3 screen, so all frames on the site have one shape. */}
          <div className="relative flex flex-col overflow-hidden rounded-[1rem] bg-white sm:aspect-[4/3] sm:rounded-[1.2rem]">
            <div className="flex h-10 shrink-0 items-center gap-3 border-b border-line bg-mist/80 px-4 backdrop-blur">
              <span className="flex gap-1.5" aria-hidden="true">
                <span className="h-3 w-3 rounded-full bg-[#ff5f57]" />
                <span className="h-3 w-3 rounded-full bg-[#febc2e]" />
                <span className="h-3 w-3 rounded-full bg-[#28c840]" />
              </span>
              <span className="mx-auto hidden min-w-0 max-w-sm flex-1 items-center justify-center gap-1.5 rounded-full bg-white px-3 py-1 font-mono text-[11px] text-ink-muted shadow-sm ring-1 ring-line sm:flex">
                <Lock className="h-3 w-3 shrink-0" aria-hidden="true" />
                <span className="truncate">{path}</span>
              </span>
              <span className="ml-auto rounded-full bg-review-soft px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-review sm:ml-0">
                Sample data
              </span>
            </div>
            <div className="flex min-h-0 flex-1 flex-col [&>*]:flex-1">{children}</div>
            {/* Soft glass reflection across the screen. */}
            <span
              aria-hidden="true"
              className="pointer-events-none absolute inset-0 bg-[linear-gradient(115deg,rgb(255_255_255/0.18)_0%,transparent_35%)]"
            />
          </div>
        </div>
      </div>
      {/* Stand: tapered neck and a flat foot. */}
      <div aria-hidden="true" className="relative mx-auto flex flex-col items-center">
        <div className="h-10 w-[22%] bg-gradient-to-b from-[#8f97aa] via-[#c9cfdc] to-[#dfe3ec] [clip-path:polygon(8%_0,92%_0,100%_100%,0_100%)] sm:h-14" />
        <div className="h-2.5 w-[34%] rounded-full bg-gradient-to-b from-[#e9ecf3] to-[#a7aebf] shadow-[0_12px_24px_-8px_rgb(5_10_28/0.45)]" />
      </div>
    </figure>
  );
}

type Signal = {
  icon: LucideIcon;
  label: string;
  value: string;
  state: "ok" | "risk" | "info";
};

/** The five signals plus the system itself, shared by the hero panel and the how-it-works column. */
export const sampleSignals: Signal[] = [
  { icon: ShieldAlert, label: "Risk classification", value: "High risk", state: "risk" },
  { icon: FileCheck2, label: "Evidence coverage", value: "5/5 complete", state: "ok" },
  { icon: FlaskConical, label: "Evaluation results", value: "0.92 (pass)", state: "ok" },
  { icon: Database, label: "Data contracts", value: "Healthy", state: "ok" },
  { icon: ClipboardCheck, label: "Human approvals", value: "3/3 approved", state: "ok" },
];

const stateStyle = {
  ok: { dot: "bg-pass text-white", value: "text-[#4ade80]" },
  risk: { dot: "bg-blocked text-white", value: "text-[#f87171]" },
  info: { dot: "bg-brand text-white", value: "text-periwinkle" },
} as const;

function PassCard({ caption = "Ready for release" }: { caption?: string }) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-pass/50 bg-[#0b2a1c]/90 p-4 shadow-[0_0_40px_-10px_rgb(34_197_94/0.6)]">
      <span className="flex h-9 w-9 items-center justify-center rounded-full bg-pass">
        <CheckCircle2 className="h-5 w-5 text-white" aria-hidden="true" />
      </span>
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-white/80">{caption}</p>
        <p className="text-xl font-bold tracking-tight text-[#4ade80]">PASS</p>
      </div>
    </div>
  );
}

/** Storyboard frame 01: floating readiness panel beside the hero headline. */
export function ReadinessPanel({ className }: { className?: string }) {
  return (
    <figure aria-label="Example release readiness for a sample AI system" className={cn("w-full max-w-xs", className)}>
      <div className="overflow-hidden rounded-xl border border-white/10 bg-navy-900/80 shadow-2xl shadow-black/50 backdrop-blur-md">
        <div className="flex items-center gap-3 border-b border-white/10 px-4 py-3">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-navy-700 text-periwinkle">
            <Bot className="h-4 w-4" aria-hidden="true" />
          </span>
          <div>
            <p className="text-[11px] text-on-dark-muted">AI system</p>
            <p className="text-sm font-semibold text-white">Claims Triage AI</p>
          </div>
          <span className="ml-auto rounded-full bg-white/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-on-dark-muted">
            Sample data
          </span>
        </div>
        <ul className="divide-y divide-white/5">
          {sampleSignals.map((signal) => (
            <li key={signal.label} className="flex items-center gap-3 px-4 py-2.5">
              <span className={cn("flex h-7 w-7 items-center justify-center rounded-full", stateStyle[signal.state].dot)}>
                {signal.state === "ok" ? (
                  <Check className="h-4 w-4" strokeWidth={3} aria-hidden="true" />
                ) : (
                  <signal.icon className="h-3.5 w-3.5" aria-hidden="true" />
                )}
              </span>
              <div>
                <p className="text-[13px] text-white/90">{signal.label}</p>
                <p className={cn("text-[13px] font-semibold", stateStyle[signal.state].value)}>{signal.value}</p>
              </div>
            </li>
          ))}
        </ul>
      </div>
      <PassCard />
    </figure>
  );
}

const sidebarItems: { icon: LucideIcon; label: string; active?: boolean }[] = [
  { icon: LayoutDashboard, label: "Dashboard" },
  { icon: Layers, label: "AI Systems" },
  { icon: Network, label: "Risk & Compliance" },
  { icon: FileSearch, label: "Evidence" },
  { icon: FlaskConical, label: "Evaluations" },
  { icon: Database, label: "Data Contracts" },
  { icon: ClipboardCheck, label: "Approvals" },
  { icon: GitPullRequestArrow, label: "Release Gate", active: true },
  { icon: FileText, label: "Audit" },
];

const readinessRows = [
  ["Risk classification", "High risk"],
  ["Evidence coverage", "5/5"],
  ["Evaluation results", "0.92"],
  ["Data contracts", "Healthy"],
  ["Human approvals", "3/3"],
] as const;

/** Storyboard frame 02: the dashboard as a framed screenshot. */
export function DashboardMock() {
  return (
    <ProductFrame label="Release gate view for a sample AI system" path="/systems/claims-triage-ai">
      <div className="grid md:grid-cols-[12rem_minmax(0,1fr)]">
        <aside className="hidden border-r border-line bg-mist/60 p-3 md:block">
          <div className="mb-4 flex items-center gap-2 px-2 pt-1 text-xs font-bold text-ink">
            <DevernanceMark className="h-5 w-5" />
            Devernance
          </div>
          <ul className="space-y-0.5">
            {sidebarItems.map((item) => (
              <li
                key={item.label}
                className={cn(
                  "flex items-center gap-2 rounded-md px-2 py-1.5 text-xs",
                  item.active ? "bg-brand-soft font-semibold text-brand" : "text-ink-muted",
                )}
              >
                <item.icon className="h-3.5 w-3.5" aria-hidden="true" />
                {item.label}
              </li>
            ))}
          </ul>
        </aside>
        <div className="min-w-0 p-4 sm:p-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-lg font-bold text-ink">Claims Triage AI</p>
              <span className="mt-1 inline-flex rounded-full bg-blocked-soft px-2 py-0.5 text-[11px] font-semibold text-blocked">
                High risk
              </span>
            </div>
            <div className="flex h-8 items-center gap-2 rounded-lg border border-line px-3 text-xs text-ink-muted">
              <Search className="h-3.5 w-3.5" aria-hidden="true" />
              Search evidence
            </div>
          </div>
          <div className="mt-4 flex gap-1 overflow-hidden border-b border-line text-xs">
            {["Overview", "Evidence", "Evaluations", "Contracts", "Approvals", "Evidence pack"].map((tab, i) => (
              <span
                key={tab}
                className={cn(
                  "-mb-px shrink-0 border-b-2 px-3 py-2",
                  i === 0 ? "border-brand font-semibold text-brand" : "border-transparent text-ink-muted",
                )}
              >
                {tab}
              </span>
            ))}
          </div>
          <div className="mt-5 grid gap-4">
            <div className="rounded-xl border border-line p-4">
              <p className="text-sm font-semibold text-ink">Release readiness</p>
              <ul className="mt-3 space-y-2.5">
                {readinessRows.map(([label, value]) => (
                  <li key={label} className="flex items-center gap-2.5 text-xs">
                    <CheckDot className="h-4 w-4" />
                    <span className="flex-1 text-ink/80">{label}</span>
                    <span className="font-medium text-ink">{value}</span>
                  </li>
                ))}
              </ul>
            </div>
            <div className="flex flex-col justify-between rounded-xl border border-line p-4">
              <div>
                <p className="text-sm font-semibold text-ink">Release decision</p>
                <div className="mt-3 flex items-center gap-3 rounded-lg bg-pass-soft p-3">
                  <CheckDot className="h-9 w-9 [&>svg]:h-5 [&>svg]:w-5" />
                  <div>
                    <p className="text-xl font-bold text-pass">PASS</p>
                    <p className="text-[11px] text-ink-muted">All mandatory controls satisfied</p>
                  </div>
                </div>
              </div>
              <span className="mt-4 inline-flex h-9 items-center justify-center rounded-lg bg-brand text-xs font-semibold text-white">
                Export signed evidence pack
              </span>
            </div>
          </div>
        </div>
      </div>
    </ProductFrame>
  );
}

const evidenceRows = [
  { name: "Model Card v2.1", type: "Model card", controls: "Art. 11, Art. 13", status: "Indexed", added: "2 days ago", color: "bg-pass" },
  { name: "DPIA – Claims Triage", type: "DPIA", controls: "Art. 27", status: "Indexed", added: "4 days ago", color: "bg-review" },
  { name: "Human Oversight SOP", type: "Policy", controls: "Art. 14", status: "Indexed", added: "1 week ago", color: "bg-brand" },
  { name: "Risk control map", type: "Control map", controls: "Art. 9", status: "Indexed", added: "1 week ago", color: "bg-review" },
  { name: "Eval report run-2026-10-03-01", type: "Evaluation", controls: "Art. 15", status: "Indexed", added: "2 days ago", color: "bg-[#7c4dff]" },
  { name: "Data contract claims-intake-v2", type: "Contract", controls: "Art. 10", status: "Indexed", added: "1 week ago", color: "bg-[#0d9488]" },
  { name: "Release approval record", type: "Approval", controls: "Art. 14", status: "Indexed", added: "1 week ago", color: "bg-pass" },
  { name: "Vendor documentation", type: "Vendor doc", controls: "Art. 25", status: "In review", added: "2 weeks ago", color: "bg-[#7c4dff]" },
] as const;

/** Storyboard frame 06. */
export function EvidenceTableMock() {
  return (
    <ProductFrame label="Evidence library for a sample AI system" path="/evidence">
      <div className="grid md:grid-cols-[11rem_minmax(0,1fr)]">
        <aside className="hidden border-r border-line bg-mist/60 p-4 md:block">
          <p className="flex items-center gap-2 text-sm font-semibold text-ink">
            <DevernanceMark className="h-5 w-5" /> Evidence
          </p>
          <ul className="mt-4 space-y-1 text-xs text-ink-muted">
            {["All documents", "Model cards", "DPIAs", "Data contracts", "Policies", "Vendor documentation"].map((item, i) => (
              <li key={item} className={cn("rounded-md px-2 py-1.5", i === 0 && "bg-brand-soft font-semibold text-brand")}>
                {item}
              </li>
            ))}
          </ul>
        </aside>
        <div className="flex min-w-0 flex-col p-4 sm:p-5">
          <div className="flex gap-3">
            <div className="flex h-10 flex-1 items-center gap-2 rounded-lg border border-line px-3 text-xs text-ink-muted">
              <Search className="h-4 w-4" aria-hidden="true" />
              Search documents, controls, or ask a question…
            </div>
            <span className="inline-flex h-10 items-center gap-2 rounded-lg bg-brand px-4 text-xs font-semibold text-white">
              <Upload className="h-4 w-4" aria-hidden="true" /> Upload
            </span>
          </div>
          <div className="mt-4 overflow-x-auto">
            <table className="w-full min-w-[30rem] text-left text-xs">
              <thead className="text-ink-muted">
                <tr className="border-b border-line">
                  <th className="py-2 font-medium">Name</th>
                  <th className="py-2 font-medium">Type</th>
                  <th className="py-2 font-medium">Mapped controls</th>
                  <th className="py-2 font-medium">Status</th>
                  <th className="py-2 font-medium">Added</th>
                </tr>
              </thead>
              <tbody>
                {evidenceRows.map((row) => (
                  <tr key={row.name} className="border-b border-line/70 last:border-0">
                    <td className="py-2.5">
                      <span className="flex items-center gap-2 font-medium text-ink">
                        <span className={cn("flex h-6 w-6 items-center justify-center rounded-md text-white", row.color)}>
                          <FileText className="h-3.5 w-3.5" aria-hidden="true" />
                        </span>
                        {row.name}
                      </span>
                    </td>
                    <td className="py-2.5 text-ink-muted">{row.type}</td>
                    <td className="py-2.5 text-ink-muted">{row.controls}</td>
                    <td className="py-2.5">
                      <span
                        className={cn(
                          "rounded-full px-2 py-0.5 text-[11px] font-semibold",
                          row.status === "Indexed" ? "bg-pass-soft text-pass" : "bg-review-soft text-review",
                        )}
                      >
                        {row.status}
                      </span>
                    </td>
                    <td className="py-2.5 text-ink-muted">{row.added}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-auto flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4 text-xs">
            <span className="flex items-center gap-2 text-ink-muted">
              <CheckDot className="h-4 w-4 [&>svg]:h-2.5 [&>svg]:w-2.5" />
              Every required control for Claims Triage AI has cited evidence
            </span>
            <span className="inline-flex items-center gap-1 font-semibold text-brand">
              View evidence workspace <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
            </span>
          </div>
        </div>
      </div>
    </ProductFrame>
  );
}

const evalMetrics = [
  { name: "Faithfulness", score: 0.96, threshold: 0.85 },
  { name: "Relevance", score: 0.91, threshold: 0.85 },
  { name: "Safety (refusal)", score: 0.98, threshold: 0.9 },
  { name: "Bias (slice)", score: 0.92, threshold: 0.85 },
] as const;

/** Faithfulness over the last 12 runs; the dip below 0.85 is the run that was blocked. */
const runHistory = [0.88, 0.9, 0.89, 0.91, 0.83, 0.9, 0.92, 0.93, 0.92, 0.94, 0.95, 0.96];

/** Small outline button used inside mock-ups. */
function MockButton({ children }: { children: ReactNode }) {
  return (
    <span className="inline-flex h-8 shrink-0 items-center gap-1 rounded-lg border border-brand/30 px-3 text-xs font-semibold text-brand">
      {children}
      <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
    </span>
  );
}

/** Storyboard frame 07. */
export function EvalRunMock() {
  return (
    <ProductFrame label="Latest evaluation run for a sample AI system" path="/evals/claims-triage-ai">
      <div className="flex flex-col p-5 sm:p-6">
        <div className="grid grid-cols-4 rounded-lg bg-mist p-1 text-center text-xs">
          {["Latest run", "Trends", "Compare", "Thresholds"].map((tab, i) => (
            <span
              key={tab}
              className={cn(
                "rounded-md py-1.5",
                i === 0 ? "bg-white font-semibold text-brand shadow-sm ring-1 ring-brand/15" : "text-ink-muted",
              )}
            >
              {tab}
            </span>
          ))}
        </div>
        <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <CheckDot className="h-10 w-10 [&>svg]:h-5 [&>svg]:w-5" />
            <div>
              <p className="font-mono text-sm font-semibold text-ink">run-2026-10-03-01</p>
              <p className="text-xs text-ink-muted">Completed 2 days ago · scores from your own eval harness</p>
            </div>
          </div>
          <MockButton>View all runs</MockButton>
        </div>
        <ul className="mt-5 space-y-3.5">
          {evalMetrics.map((metric) => (
            <li key={metric.name} className="grid grid-cols-[6.5rem_1fr_auto] items-center gap-3 text-xs sm:grid-cols-[1fr_2fr_auto] sm:gap-4 sm:text-sm">
              <span className="flex items-center gap-2 text-ink/80">
                <CheckDot />
                {metric.name}
              </span>
              <span className="relative h-2 rounded-full bg-mist">
                <span className="absolute inset-y-0 left-0 rounded-full bg-pass" style={{ width: `${metric.score * 100}%` }} />
                <span
                  className="absolute -top-1 h-4 w-0.5 rounded bg-ink/40"
                  style={{ left: `${metric.threshold * 100}%` }}
                  aria-hidden="true"
                />
              </span>
              <span className="tabular-nums text-ink">
                {metric.score.toFixed(2)} <span className="text-ink-muted">≥ {metric.threshold.toFixed(2)}</span>
              </span>
            </li>
          ))}
        </ul>
        <div className="mt-6 rounded-xl border border-line p-4">
          <div className="flex items-center justify-between text-xs">
            <p className="text-sm font-semibold text-ink">Run history</p>
            <p className="text-ink-muted">Faithfulness, last 12 runs</p>
          </div>
          <div className="relative mt-3 flex h-20 items-end gap-1.5" aria-hidden="true">
            <span className="absolute inset-x-0 border-t border-dashed border-ink/30" style={{ bottom: "40%" }} />
            {runHistory.map((score, i) => (
              <span
                key={i}
                className={cn("flex-1 rounded-t", score >= 0.85 ? "bg-pass/80" : "bg-blocked/80")}
                style={{ height: `${(score - 0.75) * 400}%` }}
              />
            ))}
          </div>
          <p className="mt-2 text-[11px] text-ink-muted">Dashed line: threshold 0.85. One run blocked a release before the fix.</p>
        </div>
        <div className="mt-auto flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line p-4">
          <div>
            <p className="text-sm font-semibold text-ink">Threshold configuration</p>
            <p className="text-xs text-ink-muted">Fixed thresholds per metric, set for each system.</p>
          </div>
          <MockButton>View thresholds</MockButton>
        </div>
      </div>
    </ProductFrame>
  );
}

const contractChecks = [
  { what: "Schema check: 1 breaking change on customer_id", when: "5 min ago", dot: "bg-blocked" },
  { what: "Distribution check: drift 0.4%, within the contract limit", when: "5 min ago", dot: "bg-pass" },
  { what: "Distribution check: drift 0.3%, within the contract limit", when: "Yesterday", dot: "bg-pass" },
  { what: "Schema check: no changes", when: "Yesterday", dot: "bg-pass" },
] as const;

/** Storyboard frame 08. */
export function ContractHealthMock() {
  return (
    <ProductFrame label="Data contract health for a sample dataset" path="/contracts/claims-intake-v2">
      <div className="flex flex-col gap-3 bg-mist/60 p-4 sm:p-5">
      <div className="rounded-xl bg-white p-5 ring-1 ring-line">
        <div className="flex items-center gap-3">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-pass-soft">
            <Database className="h-5 w-5 text-pass" aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="font-semibold text-ink">claims-intake-v2</p>
            <p className="text-xs text-ink-muted">Schema contract · 1 open drift event</p>
          </div>
          <span className="hidden sm:inline-flex">
            <MockButton>View all contracts</MockButton>
          </span>
        </div>
        <dl className="mt-5 space-y-3 text-sm">
          <div className="grid grid-cols-[9rem_1fr_auto] items-center gap-3">
            <dt className="text-ink/80">Schema stability</dt>
            <dd className="h-2 rounded-full bg-mist">
              <span className="block h-2 w-[97%] rounded-full bg-pass" />
            </dd>
            <dd className="tabular-nums text-ink">97%</dd>
          </div>
          <div className="grid grid-cols-[9rem_1fr_auto] items-center gap-3">
            <dt className="text-ink/80">Distribution drift</dt>
            <dd className="h-2 rounded-full bg-mist">
              <span className="block h-2 w-[4%] rounded-full bg-review" />
            </dd>
            <dd className="tabular-nums text-ink">0.4%</dd>
          </div>
          <div className="flex items-center justify-between border-t border-line pt-3">
            <dt className="text-ink/80">Last checked</dt>
            <dd className="text-ink-muted">5 minutes ago</dd>
          </div>
        </dl>
      </div>
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-blocked/30 bg-blocked-soft p-4">
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-blocked text-white">
          <AlertTriangle className="h-4 w-4" aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-blocked">1 open breach blocks release</p>
          <p className="truncate font-mono text-xs text-ink-muted">customer_id: type changed (string → integer)</p>
        </div>
        <span className="rounded-full bg-white px-3 py-1 text-xs font-semibold text-blocked">BLOCKED</span>
        <span className="hidden sm:inline-flex">
          <MockButton>View details</MockButton>
        </span>
      </div>
      <div className="flex-1 rounded-xl bg-white p-5 ring-1 ring-line">
        <p className="text-sm font-semibold text-ink">Recent checks</p>
        <ol className="mt-3 divide-y divide-line text-xs">
          {contractChecks.map((c) => (
            <li key={c.what} className="flex items-center gap-3 py-2.5">
              <span className={cn("h-2 w-2 shrink-0 rounded-full", c.dot)} aria-hidden="true" />
              <span className="flex-1 text-ink/85">{c.what}</span>
              <span className="text-ink-muted">{c.when}</span>
            </li>
          ))}
        </ol>
      </div>
      </div>
    </ProductFrame>
  );
}

type FlowTone = "red" | "blue" | "violet" | "teal" | "amber";

const flowTone: Record<FlowTone, { card: string; icon: string; value: string }> = {
  red: { card: "border-[#f87171]/40 bg-[#3a1220]/70", icon: "bg-blocked", value: "text-[#fca5a5]" },
  blue: { card: "border-[#60a5fa]/40 bg-[#0f2350]/70", icon: "bg-[#2f6bff]", value: "text-[#93c5fd]" },
  violet: { card: "border-[#a78bfa]/40 bg-[#24164d]/70", icon: "bg-[#7c4dff]", value: "text-[#c4b5fd]" },
  teal: { card: "border-[#2dd4bf]/40 bg-[#0b3133]/70", icon: "bg-[#0d9488]", value: "text-[#5eead4]" },
  amber: { card: "border-[#fbbf24]/40 bg-[#3a2a0b]/70", icon: "bg-[#d97706]", value: "text-[#fcd34d]" },
};

function FlowCard({ icon: Icon, label, value, tone }: { icon: LucideIcon; label: string; value: string; tone: FlowTone }) {
  const t = flowTone[tone];
  return (
    <div className={cn("flex flex-col gap-2 rounded-xl border px-3 py-3 backdrop-blur sm:flex-row sm:items-center sm:gap-3 lg:px-4 lg:py-4", t.card)}>
      <span className={cn("flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-white lg:h-10 lg:w-10", t.icon)}>
        <Icon className="h-4 w-4 lg:h-5 lg:w-5" aria-hidden="true" />
      </span>
      <div className="min-w-0">
        <p className="text-[13px] leading-tight font-semibold text-white lg:text-sm">{label}</p>
        <p className={cn("text-[13px] font-semibold lg:text-sm", t.value)}>{value}</p>
      </div>
    </div>
  );
}

const line = "absolute bg-periwinkle/50 shadow-[0_0_8px_rgb(123_140_255/0.6)]";

/** Vertical stub at a horizontal position (percent of the row width). */
function Stub({ at, top, bottom }: { at: string; top: string; bottom: string }) {
  return (
    <span className={cn(line, "w-px")} style={{ left: at, top, bottom }} aria-hidden="true">
      {/* Stubs that end at a card get an arrowhead, as in storyboard frame 03. */}
      {bottom === "0" ? (
        <span className="absolute -bottom-px left-1/2 h-0 w-0 -translate-x-1/2 border-x-[4px] border-t-[6px] border-x-transparent border-t-periwinkle" />
      ) : null}
    </span>
  );
}

function Rail({ from, to, at }: { from: string; to: string; at: string }) {
  return <span className={cn(line, "h-px")} style={{ left: from, right: `calc(100% - ${to})`, top: at }} aria-hidden="true" />;
}

/** Storyboard frame 03: the system fans out into five signals that converge on one decision. */
export function SignalFlow() {
  return (
    <figure aria-label="Five signals from one AI system converge on one release decision" className="mx-auto w-full max-w-2xl">
      <div className="mx-auto w-3/5">
        <FlowCard icon={Bot} label="AI System" value="Claims Triage AI" tone="blue" />
      </div>
      <div className="relative h-8" aria-hidden="true">
        <Stub at="50%" top="0" bottom="50%" />
        <Rail from="16.66%" to="83.33%" at="50%" />
        <Stub at="16.66%" top="50%" bottom="0" />
        <Stub at="50%" top="50%" bottom="0" />
        <Stub at="83.33%" top="50%" bottom="0" />
      </div>
      <div className="grid grid-cols-3 gap-2.5">
        <FlowCard icon={ShieldAlert} label="Risk" value="High" tone="red" />
        <FlowCard icon={FileCheck2} label="Evidence" value="5/5" tone="blue" />
        <FlowCard icon={FlaskConical} label="Evaluations" value="0.92" tone="violet" />
      </div>
      <div className="relative h-8" aria-hidden="true">
        <Stub at="16.66%" top="0" bottom="50%" />
        <Stub at="50%" top="0" bottom="50%" />
        <Stub at="83.33%" top="0" bottom="50%" />
        <Rail from="16.66%" to="83.33%" at="50%" />
        <Stub at="25%" top="50%" bottom="0" />
        <Stub at="75%" top="50%" bottom="0" />
      </div>
      <div className="grid grid-cols-2 gap-2.5">
        <FlowCard icon={Database} label="Data contract" value="Healthy" tone="teal" />
        <FlowCard icon={ClipboardCheck} label="Approvals" value="3/3" tone="amber" />
      </div>
      <div className="relative h-8" aria-hidden="true">
        <Stub at="25%" top="0" bottom="50%" />
        <Stub at="75%" top="0" bottom="50%" />
        <Rail from="25%" to="75%" at="50%" />
        <Stub at="50%" top="50%" bottom="0" />
      </div>
      <div className="mx-auto w-3/4">
        <PassCard />
      </div>
    </figure>
  );
}
