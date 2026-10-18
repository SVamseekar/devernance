import { FRAMEWORKS } from "@/lib/frameworks";

export interface CompareSource {
  title: string;
  url: string;
}

export interface CompareRow {
  aspect: string;
  us: string;
  them: string;
  /** Where the "them" cell comes from. Omitted only for category pages that describe a kind of tool, not a vendor. */
  source?: CompareSource;
}

export interface ComparePage {
  slug: string;
  title: string;
  /** Column heading for the other option. */
  themLabel: string;
  /** How "Choose … if" names the other option. */
  themChoice: string;
  summary: string;
  intro: string[];
  rows: CompareRow[];
  chooseUs: string[];
  chooseThem: string[];
  /** ISO date the "them" facts were last checked against the sources. */
  retrieved: string;
}

const crosswalks = FRAMEWORKS.filter((f) => /NIST|ISO/.test(f.short))
  .map((f) => `${f.short}${f.status === "live" ? "" : " (coming soon)"}`)
  .join(" and ");

const ourFrameworks = `EU AI Act and Digital Omnibus mapping today; ${crosswalks}.`;

const credoHome: CompareSource = { title: "Credo AI home page", url: "https://www.credo.ai/" };
const holisticHome: CompareSource = { title: "Holistic AI home page", url: "https://www.holisticai.com/" };
const holisticOss: CompareSource = {
  title: "holistic-ai/holisticai on GitHub",
  url: "https://github.com/holistic-ai/holisticai",
};

const sharedUs = {
  buying: "Self-serve signup. Free plan with no card; Team is $79 a month. Enterprise demo on request.",
  pricing: "Published on the pricing page.",
  gate: "A release-gate API returns PASS, REVIEW or BLOCKED with an exit code your CI job fails on.",
  oss: "evgraph, a BSD-3-Clause CLI that checks governance evidence locally and in CI.",
};

export const COMPARE_PAGES: ComparePage[] = [
  {
    slug: "release-gates-vs-document-generators",
    title: "Release gates vs AI Act document generators",
    themLabel: "AI Act document generator",
    themChoice: "a document generator",
    summary:
      "When a CI release gate fits and when an AI Act document generator fits. Both are useful; they answer different questions.",
    intro: [
      "Two kinds of self-serve tool help with the EU AI Act. Document generators ask a questionnaire and produce policies, risk assessments and technical documentation templates. Release gates read the evidence your engineering pipeline already produces and decide whether a release has what it needs.",
      "A document generator answers \"what should our documentation say?\". A release gate answers \"does this release have the evidence, and who approved it?\". Many teams need both at different stages.",
    ],
    rows: [
      {
        aspect: "Main input",
        us: "Engineering artifacts: model cards, eval runs, data contracts, registry records and approvals.",
        them: "Answers to a questionnaire about the organisation and the AI system.",
      },
      {
        aspect: "Main output",
        us: "A release decision (PASS, REVIEW or BLOCKED) with cited blockers, plus a signed evidence pack.",
        them: "Policy documents, risk assessments and documentation templates to edit and keep.",
      },
      {
        aspect: "Where it runs",
        us: "In CI/CD on every release, and in a web workspace for reviewers.",
        them: "In a web app, usually when documents are first written or reviewed.",
      },
      {
        aspect: "Who uses it most",
        us: "AI engineering teams, with compliance and legal reviewing evidence and approvals.",
        them: "Compliance generalists and founders writing documentation for the first time.",
      },
      {
        aspect: "Change over time",
        us: "Each release is checked again, so drift in evals or data contracts shows up as a gap.",
        them: "Documents are updated when someone revisits the questionnaire.",
      },
      {
        aspect: "Approvals",
        us: "Multi-stage sign-off recorded in a hash-chained ledger.",
        them: "Usually outside the tool, for example in email or a document workflow.",
      },
    ],
    chooseUs: [
      "You ship AI features often and want a check that runs on every release.",
      "Your evidence already lives in pipelines, registries and eval tools.",
      "Buyers or auditors ask for evidence you can hand over and they can verify.",
    ],
    chooseThem: [
      "You need first-draft policies and documentation, and no engineering pipeline exists yet.",
      "Your AI use is mostly bought-in tools, so there is little release evidence to check.",
      "You want templates more than a gate.",
    ],
    retrieved: "2026-10-05",
  },
  {
    slug: "credo-ai-alternative",
    title: "Credo AI alternative for engineering teams",
    themLabel: "Credo AI",
    themChoice: "Credo AI",
    summary:
      "How Devernance compares with Credo AI, using only facts from Credo AI's own site. Credo AI is an enterprise AI governance platform; Devernance is a self-serve release gate.",
    intro: [
      "Credo AI describes itself as an AI governance platform that helps enterprises adopt, scale and govern AI, with customers in insurance, financial services, federal and healthcare.",
      "Devernance is narrower: it gates AI releases in CI on real engineering evidence and hands out a signed evidence pack. If you need organisation-wide AI governance across many teams, Credo AI is built for that. If you need a release check you can start today, Devernance is.",
    ],
    rows: [
      {
        aspect: "Product focus",
        us: "Release gate and signed evidence packs for AI features.",
        them: "AI governance platform for enterprises, including agent governance.",
        source: credoHome,
      },
      {
        aspect: "How to buy",
        us: sharedUs.buying,
        them: "Talk to an expert or request a demo.",
        source: credoHome,
      },
      {
        aspect: "Pricing",
        us: sharedUs.pricing,
        them: "Not listed on the home page.",
        source: credoHome,
      },
      {
        aspect: "Frameworks",
        us: ourFrameworks,
        them: "EU AI Act, NIST AI RMF, ISO 42001, OMB M-25, Colorado ADMT and NAIC AI.",
        source: credoHome,
      },
      {
        aspect: "Integrations",
        us: "GitHub Actions, or any CI job that can run curl; evidence upload through the app and API.",
        them: "Snowflake, Databricks, AWS, Azure, ServiceNow, Jira, Confluence, Slack, GitHub and MLflow.",
        source: credoHome,
      },
      {
        aspect: "CI release gate",
        us: sharedUs.gate,
        them: "Not described on the home page.",
        source: credoHome,
      },
    ],
    chooseUs: [
      "You are an AI engineering team that wants a gate in CI this week.",
      "You prefer public pricing and self-serve signup.",
      "You want an open-source CLI alongside the hosted product.",
    ],
    chooseThem: [
      "You need governance across a large AI portfolio, including agents and bought-in AI.",
      "You need many regulatory frameworks mapped today.",
      "You want deep integrations with data platforms and enterprise ticketing.",
    ],
    retrieved: "2026-10-05",
  },
  {
    slug: "holistic-ai-alternative",
    title: "Holistic AI alternative for engineering teams",
    themLabel: "Holistic AI",
    themChoice: "Holistic AI",
    summary:
      "How Devernance compares with Holistic AI, using only facts from Holistic AI's own pages. Holistic AI is an enterprise AI governance platform; Devernance is a self-serve release gate.",
    intro: [
      "Holistic AI describes itself as the enterprise AI governance platform, covering AI discovery and inventory, AI testing and risk management, and AI policy and compliance. It also publishes an Apache-2.0 library for assessing AI trustworthiness.",
      "Devernance is narrower: it gates AI releases in CI on real engineering evidence and hands out a signed evidence pack. If you need discovery and governance across the whole organisation, Holistic AI is built for that. If you need a release check you can start today, Devernance is.",
    ],
    rows: [
      {
        aspect: "Product focus",
        us: "Release gate and signed evidence packs for AI features.",
        them: "Enterprise AI governance: discovery and inventory, testing and risk management, policy and compliance.",
        source: holisticHome,
      },
      {
        aspect: "How to buy",
        us: sharedUs.buying,
        them: "Book a demo.",
        source: holisticHome,
      },
      {
        aspect: "Pricing",
        us: sharedUs.pricing,
        them: "Not listed on the home page.",
        source: holisticHome,
      },
      {
        aspect: "Frameworks",
        us: ourFrameworks,
        them: "Controls mapped to EU AI Act, NIST AI RMF and ISO/IEC 42001.",
        source: holisticHome,
      },
      {
        aspect: "Integrations",
        us: "GitHub Actions, or any CI job that can run curl; evidence upload through the app and API.",
        them: "AWS, Azure, GitHub and Databricks, with 20+ integrations.",
        source: holisticHome,
      },
      {
        aspect: "Open source",
        us: sharedUs.oss,
        them: "holisticai, an Apache-2.0 library for bias, explainability, robustness, security and efficacy checks.",
        source: holisticOss,
      },
    ],
    chooseUs: [
      "You are an AI engineering team that wants a gate in CI this week.",
      "You prefer public pricing and self-serve signup.",
      "You want a release decision with cited blockers that a CI job can act on.",
    ],
    chooseThem: [
      "You need to discover and inventory AI use across the organisation.",
      "You need oversight for agentic AI at runtime.",
      "You want bias and robustness testing in the same platform as governance.",
    ],
    retrieved: "2026-10-05",
  },
];

export function getComparePage(slug: string): ComparePage | undefined {
  return COMPARE_PAGES.find((p) => p.slug === slug);
}
