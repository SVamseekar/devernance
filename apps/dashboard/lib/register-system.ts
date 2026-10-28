import { api } from "./api";
import { toApiRiskClass, type RiskAnswers } from "./risk-class";
import { deriveRegistration } from "./system-registration";
import type { RiskClass } from "./types";

export interface RegisterSystemInput {
  name: string;
  owner: string;
  purpose: string;
  sector: string;
  answers: RiskAnswers;
  /** Registry facts buyers ask about; optional so a system can be registered before they are known. */
  vendorName?: string;
  modelName?: string;
  modelVersion?: string;
  dataSources?: string;
  affectedUsers?: string;
  decisionImpact?: string;
}

/** One entry per comma or line; blanks dropped. */
export function splitList(text: string | undefined): string[] {
  return (text ?? "").split(/[\n,]/).map((item) => item.trim()).filter(Boolean);
}

const optional = (value: string | undefined) => (value && value.trim() ? value.trim() : undefined);

/** Creates the system and records the confirmed classification with its basis. */
export async function registerSystem(input: RegisterSystemInput): Promise<{ id: string; riskClass: RiskClass }> {
  const { riskClass, riskBasis } = deriveRegistration(input.answers, input.sector);
  const apiRisk = toApiRiskClass(riskClass);
  const created = await api.systems.create({
    name: input.name.trim(),
    owner: input.owner.trim(),
    purpose: input.purpose.trim(),
    riskClass: apiRisk,
    riskBasis,
    deploymentRegion: "EU",
    sector: input.sector || undefined,
    decisionImpact: optional(input.decisionImpact),
    vendorName: optional(input.vendorName),
    modelName: optional(input.modelName),
    modelVersion: optional(input.modelVersion),
    dataSources: splitList(input.dataSources),
    affectedUsers: splitList(input.affectedUsers),
  });
  await api.systems.classify(created.id, {
    riskClass: apiRisk,
    basis: riskBasis,
    humanOversightRequired: riskClass === "high",
    sector: input.sector || undefined,
    decisionImpact: optional(input.decisionImpact),
    affectedUsers: splitList(input.affectedUsers),
  });
  return { id: created.id, riskClass };
}
