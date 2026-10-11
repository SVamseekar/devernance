import type { FrameworkItemStatus } from "./types";

export const FRAMEWORK_UPGRADE_TEXT = "Available on Team and above";
export const FRAMEWORK_PENDING_TEXT = "Coming soon. This mapping is being reviewed against its source.";

const NIST_FUNCTIONS = ["GOVERN", "MAP", "MEASURE", "MANAGE"];

export interface FrameworkGroup {
  name: string;
  covered: number;
  items: FrameworkItemStatus[];
}

/** Group key: ISO Annex A ids group by objective (A.6.2.4 → A.6); others by first word (NIST function, statute). */
function groupKey(id: string): string {
  const annexA = /^(A\.\d+)\./.exec(id);
  return annexA ? annexA[1] : id.split(" ")[0];
}

/** Groups crosswalk items by function or objective, NIST functions first in framework order, others as listed. */
export function groupByFunction(items: FrameworkItemStatus[]): FrameworkGroup[] {
  const groups = new Map<string, FrameworkItemStatus[]>();
  for (const item of items) {
    const name = groupKey(item.id);
    groups.set(name, [...(groups.get(name) ?? []), item]);
  }
  const rank = (name: string) => {
    const i = NIST_FUNCTIONS.indexOf(name);
    return i === -1 ? NIST_FUNCTIONS.length : i;
  };
  return [...groups.entries()]
    .map(([name, groupItems], order) => ({ name, order, items: groupItems }))
    .sort((a, b) => rank(a.name) - rank(b.name) || a.order - b.order)
    .map(({ name, items: groupItems }) => ({
      name,
      items: groupItems,
      covered: groupItems.filter((i) => i.status === "covered").length,
    }));
}

export function statusLabel(status: FrameworkItemStatus["status"]): string {
  switch (status) {
    case "covered":
      return "Evidence linked";
    case "partial":
      return "Partial";
    default:
      return "No evidence yet";
  }
}
