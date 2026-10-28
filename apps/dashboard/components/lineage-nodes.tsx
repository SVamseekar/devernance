import { Handle, Position, type NodeProps } from "@xyflow/react";
import { cn, contractStatusColor } from "@/lib/utils";
import type { DataContractStatus } from "@/lib/types";

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
  const d = data as unknown as SourceNodeData;
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
  const d = data as unknown as ContractNodeData;
  return (
    <div
      className={cn(
        "bg-card border rounded-lg px-3 py-2 shadow-sm min-w-[160px]",
        d.status === "BREACH"
          ? "border-red-400 dark:border-red-700"
          : d.status === "WARNING"
          ? "border-amber-400 dark:border-amber-700"
          : "border-emerald-400 dark:border-emerald-700"
      )}
    >
      <Handle type="target" position={Position.Left} className="!bg-border" />
      <p className="text-xs font-bold uppercase text-muted-foreground mb-0.5">Contract</p>
      <p className={cn("text-xs font-bold mb-0.5", contractStatusColor(d.status))}>{d.status}</p>
      <p className="text-sm font-mono font-semibold">{d.label}</p>
      <p className="text-xs text-muted-foreground">
        v{d.version} · {d.coverage}% coverage
      </p>
      <Handle type="source" position={Position.Right} className="!bg-border" />
    </div>
  );
}

export function SystemNode({ data }: NodeProps) {
  const d = data as unknown as SystemNodeData;
  return (
    <div
      className={cn(
        "bg-card border rounded-lg px-3 py-2 shadow-sm min-w-[160px]",
        d.decision === "Blocked"
          ? "border-red-400 dark:border-red-700"
          : d.decision === "Review"
          ? "border-amber-400 dark:border-amber-700"
          : "border-emerald-400 dark:border-emerald-700"
      )}
    >
      <Handle type="target" position={Position.Left} className="!bg-border" />
      <p className="text-xs font-bold uppercase text-muted-foreground mb-0.5">AI System</p>
      <p
        className={cn(
          "text-xs font-bold mb-0.5",
          d.decision === "Blocked"
            ? "text-red-700 dark:text-red-400"
            : d.decision === "Review"
            ? "text-amber-700 dark:text-amber-400"
            : "text-emerald-700 dark:text-emerald-400"
        )}
      >
        {d.decision}
      </p>
      <p className="text-sm font-semibold">{d.label}</p>
      <p className="text-xs text-muted-foreground">{d.owner}</p>
    </div>
  );
}
