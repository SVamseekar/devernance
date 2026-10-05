import type { FrameworkItemStatus } from "./types";

export const FRAMEWORK_UPGRADE_TEXT = "Available on Team and above";

const NIST_FUNCTIONS = ["GOVERN", "MAP", "MEASURE", "MANAGE"];

export interface FrameworkGroup {
  name: string;
  covered: number;
  items: FrameworkItemStatus[];
}

/** Groups crosswalk items by the id's first word (the NIST function), NIST functions first in framework order. */
export function groupByFunction(items: FrameworkItemStatus[]): FrameworkGroup[] {
  const groups = new Map<string, FrameworkItemStatus[]>();
  for (const item of items) {
    const name = item.id.split(" ")[0];
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
