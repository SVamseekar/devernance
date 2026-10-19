import { releaseGateAction } from "./gate-snippets";
import type { CorpusProvision, RiskClass } from "./types";

/** Evidence the release gate looks for, by risk class. A guide to what to upload first, not a full checklist. */
export function evidenceTypesFor(riskClass: RiskClass): string[] {
  if (riskClass === "high") return ["DPIA", "Model card", "Policy", "Control map", "Vendor documentation"];
  if (riskClass === "limited") return ["Policy", "Model card"];
  return ["Policy"];
}

export interface DutyDate {
  date: string;
  status: CorpusProvision["forceStatus"];
}

/** Earliest force date of the Article 50 and Annex III provisions in the pinned corpus. */
export function firstDutyDates(provisions: CorpusProvision[]): {
  article50: DutyDate | null;
  annexIII: DutyDate | null;
} {
  const earliest = (matches: CorpusProvision[]): DutyDate | null => {
    const first = [...matches].sort((a, b) => a.forceFrom.localeCompare(b.forceFrom))[0];
    return first ? { date: first.forceFrom, status: first.forceStatus } : null;
  };
  return {
    article50: earliest(provisions.filter((p) => p.article === "50")),
    annexIII: earliest(provisions.filter((p) => p.annex === "III")),
  };
}

/** GitHub Actions step that fails the build unless the release gate passes. The key stays in a secret. */
export function releaseGateWorkflow(systemId?: string, appUrl?: string): string {
  return `# .github/workflows/ai-release-gate.yml
- name: Devernance release gate
  uses: ${releaseGateAction}
  with:
    api-key: \${{ secrets.ASSURANCE_API_KEY }}
    system-id: ${systemId ?? "<SYSTEM_ID>"}
    api-url: ${appUrl ?? "https://<your-app-domain>"}`;
}
