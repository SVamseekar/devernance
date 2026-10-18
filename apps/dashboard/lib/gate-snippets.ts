import type { CodeTab } from "@/components/marketing/code-tabs";

/** Host-neutral: ASSURANCE_URL is your workspace URL (hosted or self-hosted), set as a CI variable. */
const gatePath = "/api/v1/ci/release-gate";

/** Published GitHub Action (github.com/SVamseekar/devernance-release-gate); it calls the same endpoint. */
export const releaseGateAction = "SVamseekar/devernance-release-gate@v1";

/** Real integration samples for the documented CI endpoint (docs/API.md, "CI release gate"). */
export const gateSnippets: CodeTab[] = [
  {
    label: "GitHub Actions",
    filename: ".github/workflows/ai-release-gate.yml",
    code: `name: AI release gate
on: [push, pull_request]
permissions:
  contents: read
jobs:
  gate:
    runs-on: ubuntu-latest
    steps:
      - uses: ${releaseGateAction}
        with:
          api-key: \${{ secrets.ASSURANCE_API_KEY }}
          system-id: \${{ vars.ASSURANCE_SYSTEM_ID }}
          api-url: \${{ vars.ASSURANCE_URL }}`,
  },
  {
    label: "cURL",
    code: `curl -fsS \\
  -H "X-Api-Key: $ASSURANCE_API_KEY" \\
  "$ASSURANCE_URL${gatePath}?systemId=$SYSTEM_ID"`,
  },
  {
    label: "API response",
    code: `{
  "systemName": "Claims Triage AI",
  "decision": "PASS",
  "blockers": [],
  "evalScore": 92,
  "evidenceCoverage": 100,
  "dataContractStatus": "HEALTHY",
  "riskClass": "HIGH",
  "exitCode": 0,
  "content": "Release gate PASS — no blockers."
}`,
  },
];

export const exitCodes = [
  { decision: "PASS", code: 0, detail: "All mandatory controls satisfied." },
  { decision: "REVIEW", code: 2, detail: "Non-blocking warnings or pending approvals." },
  { decision: "BLOCKED", code: 1, detail: "Missing evidence, failed evaluations, open breaches or missing approvals." },
] as const;
