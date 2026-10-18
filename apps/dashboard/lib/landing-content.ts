export const releaseDecisionMeanings = [
  {
    decision: "PASS",
    meaning:
      "Cited evidence, the eval, and contracts clear the bar, and no in-force WARNING, APPROVAL_REQUIRED, or BLOCKING control holds the release. The sealed pack can be exported.",
  },
  {
    decision: "REVIEW",
    meaning:
      "An in-force WARNING on an otherwise clear gate, or an unsigned APPROVAL_REQUIRED control, holds the release for a person. Owner, compliance, or legal still have to act.",
  },
  {
    decision: "BLOCKED",
    meaning:
      "A hard stop: an in-force BLOCKING control, an open contract BREACH, a prohibited class, or missing high-risk oversight. A missing approval timestamp stays inconclusive. The scan does not invent the field.",
  },
] as const;

export type RelatedLink = {
  href: string;
  title: string;
  description: string;
};

export const relatedByPath: Record<string, RelatedLink[]> = {
  "/faq": [
    {
      href: "/method",
      title: "Methodology",
      description: "Pinned corpus, accepted mappings, and checks that fail closed.",
    },
    {
      href: "/how-it-works",
      title: "How it works",
      description: "What PASS, REVIEW, and BLOCKED mean.",
    },
    {
      href: "/pricing",
      title: "Pricing",
      description: "Start free with one gated AI system.",
    },
  ],
};

export type FaqItem = {
  question: string;
  answer: string;
};

export const faqItems: FaqItem[] = [
  {
    question: "What is the EU AI Act?",
    answer:
      "Regulation (EU) 2024/1689 is the EU’s risk-based law for placing and using AI systems on the Union market. Duties depend on risk class and on whether you are a provider or a deployer. This product organises evidence against those duties. It is not legal advice.",
  },
  {
    question: "When do high-risk AI duties apply?",
    answer:
      "Standalone Annex III high-risk obligations apply from 2 December 2027 under Regulation (EU) 2026/1744. AI embedded in Annex I products applies from 2 August 2028. Article 50 transparency has applied since 2 August 2026 and was not deferred.",
  },
  {
    question: "What is Article 50 of the EU AI Act?",
    answer:
      "Article 50 is the transparency layer: disclose AI interaction when it is not obvious, mark synthetic content in a machine-readable way, and inform people exposed to certain emotion-recognition or biometric systems. It can apply even when the system is not high-risk.",
  },
  {
    question: "What is an EU AI Act risk classification?",
    answer:
      "The tier assigned to a system — prohibited, high, limited, or minimal — from its intended use, read against a pinned legal corpus. Annex III lists use cases presumed high-risk. The tier sets which controls and evidence this product requires before release.",
  },
  {
    question: "Does this certify my AI system or replace a notified body?",
    answer:
      "No. Devernance is software for your own release governance. It is not a notified body, not a CE-mark issuer, and not a legal certificate. Counsel still owns the determination of obligations.",
  },
  {
    question: "How is this different from a GRC platform?",
    answer:
      "Most GRC tools inventory policies and collect evidence across many frameworks. This is a fail-closed release gate. In-force controls use INFORMATIONAL, WARNING, APPROVAL_REQUIRED, or BLOCKING. Missing cited evidence, a failed eval, or an open contract BREACH does not pass. A missing approval timestamp stays inconclusive.",
  },
  {
    question: "What is Evgraph?",
    answer:
      "A separate Python library. This product pins evgraph-cli 0.1.3 and runs it on the promotion files and dataset manifest. The pack and the scan must show the same current gap. If an approval timestamp or dataset license is missing, the finding stays inconclusive. We do not invent the field.",
  },
  {
    question: "What is an evidence pack?",
    answer:
      "A sealed, exportable bundle (JSON plus a hashed PDF) of the documents, citations, eval results, contract status, approvals, and Evgraph artifacts behind a release decision — built for audit review, not as a legal Annex IV filing.",
  },
  {
    question: "Who needs to approve a high-risk AI system release?",
    answer:
      "Control mappings are proposals a person accepts. They are not auto-applied. High-risk systems also route through owner, compliance, and legal approval, and require documented human-oversight evidence before the release gate can pass.",
  },
  {
    question: "What counts as a data-contract drift event?",
    answer:
      "A drift event is recorded when an input source no longer matches its agreed schema or semantic contract. An open breach-severity event blocks the release gate until it is closed.",
  },
  {
    question: "How do you start?",
    answer:
      "Sign up for the free plan and register one AI system, or open the read-only demo workspace first. Team and Business plans are self-serve; Enterprise starts with a demo. Devernance is evidence and readiness tooling, not a legal certificate.",
  },
];
