import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
import type { RiskClass, DataContractStatus } from "./types";

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
  return raw.toLowerCase() as RiskClass;
}

export function normaliseContractStatus(raw: string): DataContractStatus {
  return raw.toUpperCase() as DataContractStatus;
}

/** Deterministic UTC formatting so SSR and CSR match. */
export function formatDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "UTC",
  }).format(date);
}

/** Stable short stamp from an ISO string (no locale / timezone drift). */
export function formatIsoStamp(iso: string | null | undefined): string {
  if (!iso) return "—";
  // 2026-08-10T04:49:12.345Z → 2026-08-10 04:49
  return iso.slice(0, 16).replace("T", " ");
}

export function decisionColor(decision: "Pass" | "Review" | "Blocked"): string {
  return decision === "Pass"
    ? "text-emerald-700 dark:text-emerald-400"
    : decision === "Review"
    ? "text-amber-700 dark:text-amber-400"
    : "text-red-700 dark:text-red-400";
}

export function riskColor(risk: RiskClass): string {
  return risk === "high"
    ? "text-red-700 border-red-300 dark:text-red-400 dark:border-red-800"
    : risk === "limited"
    ? "text-amber-700 border-amber-300 dark:text-amber-400 dark:border-amber-800"
    : "text-emerald-700 border-emerald-300 dark:text-emerald-400 dark:border-emerald-800";
}

export function contractStatusColor(status: DataContractStatus): string {
  return status === "HEALTHY"
    ? "text-emerald-700 dark:text-emerald-400"
    : status === "WARNING"
    ? "text-amber-700 dark:text-amber-400"
    : "text-red-700 dark:text-red-400";
}
