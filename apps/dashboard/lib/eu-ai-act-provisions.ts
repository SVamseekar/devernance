import corpus from "@/content/corpus/provisions.json";

export interface ProvisionPage {
  slug: string;
  label: string;
  provisionKey: string;
  title: string;
  excerpt: string;
  forceStatus: "IN_FORCE" | "FUTURE" | string;
  forceFrom: string;
  scopeNote: string | null;
  sourceUrl: string;
}

export interface ProvisionGuide {
  /** Control codes from the Devernance catalog that this provision maps to. */
  controls: string[];
  evidence: string[];
  gate: string;
}

export const CORPUS_ATTRIBUTION: string = corpus.attribution;
export const CORPUS_VERSION: string = corpus.corpusVersion;
export const PROVISIONS: ProvisionPage[] = corpus.provisions;

const HIGH_RISK_GATE =
  "When a system is classified high-risk, the release gate requires cited evidence for this control before it returns PASS. Missing or stale evidence shows up as a named blocker in CI.";

/** Evidence guide per page, keyed by slug. Control codes match the baseline catalog in the API. */
export const PROVISION_GUIDES: Record<string, ProvisionGuide> = {
  "article-4": {
    controls: [],
    evidence: ["Training plan and attendance records for staff who build or operate the system", "Role-specific guidance for reviewers and operators"],
    gate: "Upload literacy records as evidence on the system so they travel in the evidence pack. Article 4 is not a release-gate control today.",
  },
  "article-5": {
    controls: [],
    evidence: ["A documented check that the intended purpose is not a prohibited practice", "Sign-off from legal or compliance on that check"],
    gate: "The free applicability check and the system questionnaire flag answers that point to a prohibited practice so a person reviews them before release.",
  },
  "article-6": {
    controls: [],
    evidence: ["The system's intended purpose and sector", "A recorded classification decision, with the reasoning and who made it"],
    gate: "The obligation questionnaire suggests a risk class from your answers. A person accepts the class, and the gate then applies the controls for that class.",
  },
  "article-9": {
    controls: ["RISK_MANAGEMENT"],
    evidence: ["Risk register for the system, with owners and mitigations", "Records that the register was reviewed for this release"],
    gate: HIGH_RISK_GATE,
  },
  "article-10": {
    controls: ["DATA_GOVERNANCE"],
    evidence: ["Data contracts for training, validation and test sets", "Dataset manifests and bias checks", "Contract check history showing no breaking drift"],
    gate: "Data contracts are checked for schema and distribution changes. A failing contract check becomes a blocker on the release.",
  },
  "article-11": {
    controls: ["TECHNICAL_DOCUMENTATION"],
    evidence: ["Model card and system description", "Architecture, training and evaluation summaries (see Annex IV)"],
    gate: HIGH_RISK_GATE,
  },
  "article-12": {
    controls: ["RECORD_KEEPING"],
    evidence: ["Logging design showing which events are recorded", "Retention settings and a sample log export"],
    gate: HIGH_RISK_GATE,
  },
  "article-13": {
    controls: ["TRANSPARENCY"],
    evidence: ["Instructions for use given to deployers", "Known limitations and expected accuracy"],
    gate: "The transparency control needs cited evidence before release when the system's risk class requires it.",
  },
  "article-14": {
    controls: ["HUMAN_OVERSIGHT"],
    evidence: ["Oversight procedure, including how a person can stop or override the system", "Approval records from the people who oversee releases"],
    gate: "Multi-stage approvals are recorded in a hash-chained ledger. The gate checks that the required approvals exist for this release.",
  },
  "article-15": {
    controls: ["ACCURACY_ROBUSTNESS", "CYBERSECURITY"],
    evidence: ["Evaluation runs with metrics against agreed thresholds", "Robustness and security test results"],
    gate: "Evaluation runs are checked against thresholds you set. A metric below its threshold blocks the release.",
  },
  "article-26": {
    controls: ["HUMAN_OVERSIGHT", "RECORD_KEEPING"],
    evidence: ["Records that the system is used as the provider's instructions describe", "Named people assigned to oversight, and log retention"],
    gate: "Deployers can register bought-in AI systems and gate their own rollouts on the same oversight and record-keeping evidence.",
  },
  "article-27": {
    controls: [],
    evidence: ["A fundamental rights impact assessment for the deployment", "Who was consulted and when it was reviewed"],
    gate: "Upload the assessment as evidence on the system so it is cited in the evidence pack. A dedicated control for Article 27 is not in the catalog yet.",
  },
  "article-50": {
    controls: ["TRANSPARENCY"],
    evidence: ["Screenshots or copy showing people are told they are talking to an AI system", "How AI-generated content is marked in a machine-readable way"],
    gate: "Article 50 rules from the obligation questionnaire map to the transparency control. The gate needs cited evidence for it before PASS.",
  },
  "article-72": {
    controls: ["RECORD_KEEPING"],
    evidence: ["A post-market monitoring plan", "Recurring evaluation runs and contract checks after release"],
    gate: "Evaluations and data contract checks keep running after release, so drift after launch shows up on the next gate run.",
  },
  "annex-iii": {
    controls: [],
    evidence: ["The use case and sector for each AI system", "A recorded decision on whether an Annex III area applies"],
    gate: "The questionnaire asks the questions that place a system in an Annex III area. A person confirms the result before the high-risk controls apply.",
  },
  "annex-iv": {
    controls: ["TECHNICAL_DOCUMENTATION"],
    evidence: ["General description, intended purpose and version", "Design, data, evaluation and monitoring details"],
    gate: HIGH_RISK_GATE,
  },
};

export function provisionBySlug(slug: string): ProvisionPage | undefined {
  return PROVISIONS.find((p) => p.slug === slug);
}
