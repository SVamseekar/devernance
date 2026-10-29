import { resolveSectorPackId } from "./sector-packs";
import { suggestRiskClass, type RiskAnswers } from "./risk-class";
import type { RiskClass } from "./types";

export const QUESTIONNAIRE = [
  {
    id: "q_biometrics",
    label: "Biometric & Critical Infrastructure",
    text: "Is the system used for real-time remote biometric identification, or for control/safety of critical physical infrastructure (e.g., power grid)?",
  },
  {
    id: "q_essential",
    label: "Essential Services & Access Control",
    text: "Does the system evaluate creditworthiness, compute insurance eligibility, prioritize claims routing, or routing access to essential welfare benefits?",
  },
  {
    id: "q_hr",
    label: "Employment & HR Pipelines",
    text: "Is the system used for recruitment, screening resumes, shortlisting job applicants, or evaluating worker performance/promotions?",
  },
  {
    id: "q_interaction",
    label: "Customer Interaction & Natural Persons",
    text: "Does the system directly interact with natural persons, or generate content that could be mistaken as human-written (e.g., chat copilots, generation tools)?",
  },
] as const;

export interface Registration {
  riskClass: RiskClass;
  riskBasis: string;
  obligations: string[];
}

/** Questionnaire defaults implied by a sector pack, or null when the sector implies none. */
export function sectorAnswerDefaults(sector: string): Partial<RiskAnswers> | null {
  if (sector === "insurance" || sector === "finance") return { q_essential: true, q_hr: false };
  if (sector === "hr") return { q_hr: true, q_essential: false };
  return null;
}

/** Starting answers for a sector, so the default selection already carries its pack's hints. */
export function initialAnswers(sector: string): RiskAnswers {
  return {
    q_biometrics: false,
    q_essential: false,
    q_hr: false,
    q_interaction: false,
    ...(sectorAnswerDefaults(sector) ?? {}),
  };
}

/** A suggestion only — the user confirms the class and the API records the basis. Not a legal determination. */
export function deriveRegistration(answers: RiskAnswers, sector: string): Registration {
  const riskClass = suggestRiskClass(answers);
  let riskBasis =
    "No specific EU AI Act obligations suggested by these answers. Article 4 AI literacy and general law still apply.";
  if (riskClass === "high") {
    riskBasis =
      "Art. 6(2) Annex III — System falls under high-risk critical infrastructure, essential services, or HR hiring evaluation categories.";
  } else if (riskClass === "limited") {
    riskBasis = "Article 50 transparency duties may apply — the system interacts with natural persons or generates content.";
  }

  const packId = resolveSectorPackId(sector);
  const packObligations =
    packId === "insurance"
      ? [
          "Insurance pack: claims fairness testing (INS_CLAIMS_FAIRNESS)",
          "Insurance pack: human review of adverse claim decisions",
          "Insurance pack: claims model card documentation",
        ]
      : packId === "hr"
        ? ["HR pack: hiring/ranking transparency", "HR pack: employment human oversight + candidate notice"]
        : packId === "finance"
          ? ["Finance pack: elevated KYC/fraud logging intensity", "Finance pack: human review of fraud/KYC flags"]
          : [];

  const obligations =
    riskClass === "high"
      ? [
          "Index Technical Documentation (Art. 11) & Model Cards",
          "Establish Human Oversight SOP (Art. 14) with manual override route",
          "Keep automatic event logs (Art. 12)",
          "Run continuous evaluation runs (faithfulness, bias) and pass 85% threshold gate",
          "Monitor data-contract drift on schema inputs",
          ...packObligations,
        ]
      : riskClass === "limited"
        ? [
            "Disclose AI interaction and label AI-generated content (Article 50)",
            "Identify content generation origins explicitly",
            ...packObligations,
          ]
        : [
            "Optional compliance with voluntary industry code of conduct models",
            "Maintain baseline privacy data policies",
          ];

  return { riskClass, riskBasis, obligations };
}
