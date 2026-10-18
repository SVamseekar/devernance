"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { siteConfig } from "@/lib/site-config";
import { useDashboard } from "@/context/dashboard-context";
import { useOpenWorkflows } from "@/hooks/use-open-workflows";
import {
  LayoutDashboard,
  Server,
  FileSearch,
  FlaskConical,
  GitBranch,
  ScrollText,
  ShieldCheck,
  ClipboardCheck,
  BadgeCheck,
  Newspaper,
  Users,
  Landmark,
  BookOpen,
  ListChecks,
} from "lucide-react";

const showPublicClaims = process.env.NEXT_PUBLIC_SHOW_PUBLIC_CLAIMS === "true";
const NAV_ITEMS = [
  { href: "/command", label: "Dashboard", icon: LayoutDashboard },
  { href: "/systems", label: "AI Systems", icon: Server },
  ...(showPublicClaims ? [{ href: "/public-claims", label: "Public claims", icon: Landmark }] : []),
  { href: "/readiness", label: "Readiness", icon: BadgeCheck },
  { href: "/reg-monitor", label: "Reg Monitor", icon: Newspaper },
  { href: "/corpus", label: "Law corpus", icon: BookOpen },
  { href: "/proposals", label: "Proposal queue", icon: ListChecks },
  { href: "/approvals", label: "Approvals", icon: ClipboardCheck },
  { href: "/evidence", label: "Evidence", icon: FileSearch },
  { href: "/evals", label: "Eval Gates", icon: FlaskConical },
  { href: "/contracts", label: "Contracts", icon: GitBranch },
  { href: "/audit", label: "Audit Log", icon: ScrollText },
  { href: "/settings", label: "Workspace", icon: Users },
];

interface SidebarProps {
  blockedCount: number;
}

export function Sidebar({ blockedCount }: SidebarProps) {
  const pathname = usePathname();
  const { activeRole, setActiveRole } = useDashboard();
  // Live open workflow count from API (same source as Approvals page).
  const { data: openWorkflows = [] } = useOpenWorkflows();
  const openCount = openWorkflows.filter((w) => w.status === "OPEN").length;

  return (
    <aside className="fixed inset-y-0 left-0 w-56 border-r border-border bg-card flex flex-col z-10">
      {/* Logo */}
      <div className="flex items-center gap-2.5 px-5 h-14 border-b border-border flex-shrink-0">
        <div className="w-7 h-7 rounded-lg bg-primary grid place-items-center flex-shrink-0">
          <ShieldCheck className="w-3.5 h-3.5 text-primary-foreground" />
        </div>
        <span className="font-semibold text-sm">{siteConfig.shortName}</span>
      </div>

      {/* Navigation */}
      <nav className="flex flex-col px-3 py-4 flex-1 overflow-y-auto">
        <p className="text-[10px] font-semibold uppercase tracking-widest text-muted-foreground px-2 mb-2">
          Navigation
        </p>
        {NAV_ITEMS.map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex items-center gap-2.5 px-2.5 py-2 text-sm rounded-lg transition-colors mb-0.5",
                active
                  ? "bg-accent text-primary font-medium"
                  : "text-muted-foreground hover:text-foreground hover:bg-muted/50 font-normal"
              )}
            >
              <Icon className={cn("w-4 h-4 flex-shrink-0", active ? "text-primary" : "text-muted-foreground/70")} />
              {item.label}
              {item.label === "Approvals" && openCount > 0 && (
                <span className="ml-auto text-[9px] font-bold bg-amber-500 text-white rounded-full w-4 h-4 flex items-center justify-center">
                  {openCount}
                </span>
              )}
            </Link>
          );
        })}
      </nav>

      {/* Demo actor label — local UI only; does not change API auth identity */}
      <div className="px-3 pb-4 border-t border-border pt-4 flex-shrink-0">
        <label
          htmlFor="demo-actor"
          className="text-[9px] font-bold uppercase tracking-wider text-muted-foreground block mb-1.5 px-1"
        >
          Demo actor (UI only)
        </label>
        <select
          id="demo-actor"
          value={activeRole}
          onChange={(e) => setActiveRole(e.target.value)}
          className="w-full text-xs font-semibold bg-muted border border-border rounded-lg px-2 py-1.5 focus:outline-none focus:ring-2 focus:ring-ring/50 transition-shadow text-foreground cursor-pointer"
          title="Changes local demo labels only. Session auth is unchanged."
        >
          <option value="actor-priya">Priya Nair (Compliance)</option>
          <option value="actor-marco">Marco Bianchi (Engineering)</option>
          <option value="actor-leo">Leo Hartmann (Legal)</option>
          <option value="actor-sofia">Sofia Andersen (Data Lead)</option>
        </select>
        <p className="mt-1.5 px-1 text-[10px] leading-snug text-muted-foreground">
          Does not switch your signed-in account.
        </p>
      </div>

      {/* Bottom status */}
      <div className="px-3 pb-5 flex-shrink-0">
        <div
          className={cn(
            "rounded-xl p-3 border",
            blockedCount > 0
              ? "bg-red-50 border-red-100 dark:bg-red-950/30 dark:border-red-900"
              : "bg-emerald-50 border-emerald-100 dark:bg-emerald-950/30 dark:border-emerald-900"
          )}
        >
          <p className={cn("text-[11px] font-medium", blockedCount > 0 ? "text-red-600 dark:text-red-400" : "text-emerald-600 dark:text-emerald-400")}>
            Release Gate
          </p>
          <p className={cn("text-xs font-semibold mt-0.5 truncate", blockedCount > 0 ? "text-red-800 dark:text-red-300" : "text-emerald-800 dark:text-emerald-300")}>
            {blockedCount > 0 ? `${blockedCount} blocked` : "All systems clear"}
          </p>
        </div>
      </div>
    </aside>
  );
}
