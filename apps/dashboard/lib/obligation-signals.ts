import type { AiSystem } from "./types";

/**
 * What Devernance's own data says about each high-risk article (EU AI Act Art. 9-15). Never a pass: the gate inputs
 * can show a gap or a supporting signal, and articles Devernance holds no data for are "not assessed here".
 * Thresholds are the release gate's (ReleaseGateService): eval pass 85, hard block 78, evidence coverage 82.
 */
export type ObligationSignalStatus = "GAP" | "REVIEW" | "SIGNAL" | "NOT_ASSESSED";

export interface ObligationSignal {
  article: string;
  name: string;
  status: ObligationSignalStatus;
  detail: string;
}

const EVAL_PASS = 85;
const EVAL_HARD_BLOCK = 78;
const EVIDENCE_PASS = 82;

const NOT_ASSESSED = "Devernance holds no data for this article; review it with your own evidence.";

function hasGap(system: AiSystem, test: (gap: string) => boolean): boolean {
  return system.openGaps.some((gap) => test(gap.toLowerCase()));
}

export function obligationSignals(system: AiSystem): ObligationSignal[] {
  if (String(system.riskClass).toLowerCase() !== "high") {
    return [];
  }

  const contract = system.dataContractStatus;
  const missingEvidence = system.openGaps.filter((gap) => gap.startsWith("Missing evidence: "));
  const oversightGap = hasGap(system, (gap) => gap.includes("human") || gap.includes("oversight"));

  let documentation: ObligationSignal;
  if (missingEvidence.length > 0) {
    documentation = {
      article: "Art. 11",
      name: "Technical documentation",
      status: "GAP",
      detail: missingEvidence.join("; "),
    };
  } else if (system.evidenceCoverage < EVIDENCE_PASS) {
    documentation = {
      article: "Art. 11",
      name: "Technical documentation",
      status: "REVIEW",
      detail: `Evidence coverage ${system.evidenceCoverage}% is below the gate's ${EVIDENCE_PASS}%.`,
    };
  } else {
    documentation = {
      article: "Art. 11",
      name: "Technical documentation",
      status: "SIGNAL",
      detail: `Required evidence types are indexed (coverage ${system.evidenceCoverage}%). Content is not judged.`,
    };
  }

  let accuracy: ObligationSignal;
  if (system.evalScore === 0) {
    accuracy = {
      article: "Art. 15",
      name: "Accuracy, robustness and cybersecurity",
      status: "GAP",
      detail: "No completed eval run.",
    };
  } else if (system.evalScore < EVAL_HARD_BLOCK) {
    accuracy = {
      article: "Art. 15",
      name: "Accuracy, robustness and cybersecurity",
      status: "GAP",
      detail: `Eval score ${system.evalScore}% is below the gate's hard block (${EVAL_HARD_BLOCK}%).`,
    };
  } else if (system.evalScore < EVAL_PASS) {
    accuracy = {
      article: "Art. 15",
      name: "Accuracy, robustness and cybersecurity",
      status: "REVIEW",
      detail: `Eval score ${system.evalScore}% is below the gate's ${EVAL_PASS}%.`,
    };
  } else {
    accuracy = {
      article: "Art. 15",
      name: "Accuracy, robustness and cybersecurity",
      status: "SIGNAL",
      detail: `Latest eval score ${system.evalScore}% meets the gate's ${EVAL_PASS}%. Robustness and cybersecurity are not assessed here.`,
    };
  }

  return [
    { article: "Art. 9", name: "Risk management system", status: "NOT_ASSESSED", detail: NOT_ASSESSED },
    {
      article: "Art. 10",
      name: "Data and data governance",
      status: contract === "BREACH" ? "GAP" : contract === "WARNING" ? "REVIEW" : "SIGNAL",
      detail:
        contract === "BREACH"
          ? "A data contract is in breach."
          : contract === "WARNING"
            ? "A data contract has warnings."
            : "Data contracts are healthy. Data quality and bias are not assessed here.",
    },
    documentation,
    { article: "Art. 12", name: "Record-keeping (logging)", status: "NOT_ASSESSED", detail: NOT_ASSESSED },
    { article: "Art. 13", name: "Transparency to deployers", status: "NOT_ASSESSED", detail: NOT_ASSESSED },
    {
      article: "Art. 14",
      name: "Human oversight",
      status: oversightGap ? "GAP" : "NOT_ASSESSED",
      detail: oversightGap ? "Human oversight evidence is an open gap." : NOT_ASSESSED,
    },
    accuracy,
  ];
}
