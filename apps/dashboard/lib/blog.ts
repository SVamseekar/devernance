/**
 * Blog posts for /blog. Add a post by appending to POSTS; newest first is handled by `sortedPosts`.
 * Covers: put a licensed image in `public/marketing/blog/` and set `cover`. Without one the card shows artwork.
 * Copy rules apply: cite law, never claim compliance or certification, no invented customers or case studies.
 */
export type BlogCategory = "guide" | "update" | "engineering" | "case-study";

export type BlogBlock =
  | { type: "p"; text: string }
  | { type: "h2"; text: string }
  | { type: "ul"; items: string[] }
  | { type: "code"; text: string };

export interface BlogPost {
  slug: string;
  title: string;
  excerpt: string;
  category: BlogCategory;
  /** ISO date. */
  published: string;
  readMinutes: number;
  cover: string | null;
  coverAlt: string;
  body: BlogBlock[];
  sources: { title: string; url: string }[];
}

export const categoryLabel: Record<BlogCategory, string> = {
  guide: "Guide",
  update: "Update",
  engineering: "Engineering",
  "case-study": "Case study",
};

const AI_ACT = { title: "Regulation (EU) 2024/1689 — EUR-Lex", url: "https://eur-lex.europa.eu/eli/reg/2024/1689/oj" };
const OMNIBUS = { title: "Regulation (EU) 2026/1744 — EUR-Lex", url: "https://eur-lex.europa.eu/eli/reg/2026/1744/oj" };

export const POSTS: BlogPost[] = [
  {
    slug: "eu-ai-act-guide-for-engineering-teams",
    title: "A practical guide to the EU AI Act for engineering teams",
    excerpt:
      "What the risk tiers mean for the code you ship, which dates matter now, and which records to keep next to each release.",
    category: "guide",
    published: "2026-10-05",
    readMinutes: 6,
    cover: "/marketing/blog/eu-guide.jpg",
    coverAlt: "European Union flag waving against a blue sky",
    body: [
      {
        type: "p",
        text: "The EU AI Act (Regulation (EU) 2024/1689) sets duties by risk tier and by role. For an engineering team the useful question is simple: which records must exist before this feature ships, and who signs them? This guide covers that question. It is not legal advice; your counsel decides how the law applies to your systems.",
      },
      { type: "h2", text: "Start with an inventory" },
      {
        type: "p",
        text: "Every later step reads from a register of AI systems: owner, intended purpose, deployment context, model and vendor, data sources, and who is affected by the output. Without it you cannot classify risk or show what changed between releases.",
      },
      { type: "h2", text: "Know the four tiers" },
      {
        type: "ul",
        items: [
          "Prohibited practices (Article 5) — not allowed on the EU market.",
          "High-risk systems — the use cases in Annex III, and AI in the regulated products of Annex I.",
          "Transparency duties (Article 50) — for example, telling people they are talking to an AI system, and marking synthetic content.",
          "Minimal risk — no specific duties beyond general law, though AI literacy (Article 4) applies to providers and deployers.",
        ],
      },
      { type: "h2", text: "The dates that matter" },
      {
        type: "ul",
        items: [
          "Article 50 transparency duties apply from 2 August 2026.",
          "Article 50(2) marking of AI-generated content applies from 2 August 2026; systems already on the market before that date have until 2 December 2026.",
          "Annex III high-risk duties apply from 2 December 2027, after the Digital Omnibus on AI.",
          "Annex I embedded high-risk duties apply from 2 August 2028.",
        ],
      },
      { type: "h2", text: "Keep evidence next to the release" },
      {
        type: "p",
        text: "For high-risk systems the Act expects technical documentation, record keeping, human oversight, and accuracy and robustness testing. In practice that means a model card, a data protection impact assessment where personal data is involved, an oversight procedure, evaluation results with thresholds, and a record of who approved the release. Store them with the system, not in a shared drive.",
      },
      { type: "h2", text: "Make missing evidence fail the build" },
      {
        type: "p",
        text: "A checklist in a wiki drifts. A release gate in CI does not: it reads the current evidence, evaluation scores, data-contract status and approvals, and returns PASS, REVIEW or BLOCKED. A blocked release stops the pipeline until someone fixes the gap.",
      },
    ],
    sources: [AI_ACT, OMNIBUS],
  },
  {
    slug: "digital-omnibus-on-ai-what-changed",
    title: "Digital Omnibus on AI: what changed for release teams",
    excerpt:
      "The Omnibus moved the high-risk dates but left Article 50 in place. Here is the new timeline and what it means for your backlog.",
    category: "update",
    published: "2026-09-28",
    readMinutes: 4,
    cover: "/marketing/blog/omnibus-update.jpg",
    coverAlt: "European Union flags in front of a modern office building",
    body: [
      {
        type: "p",
        text: "Regulation (EU) 2026/1744, the Digital Omnibus on AI, amends the EU AI Act. For teams that ship AI features, three changes matter most.",
      },
      { type: "h2", text: "High-risk duties moved" },
      {
        type: "ul",
        items: [
          "Annex III high-risk duties now apply from 2 December 2027.",
          "Annex I embedded high-risk duties now apply from 2 August 2028.",
        ],
      },
      { type: "h2", text: "Transparency did not move" },
      {
        type: "p",
        text: "Article 50 transparency duties apply from 2 August 2026, and the Article 50(2) marking duty for AI-generated content applies with them. Only systems already on the market before 2 August 2026 have until 2 December 2026 to add marking. A chatbot or content feature that is not high-risk can still be in scope today.",
      },
      { type: "h2", text: "What to do with the extra time" },
      {
        type: "p",
        text: "Use it to build the habit, not to wait. Register your systems, record a risk class with its rationale, and start collecting evaluation results and approvals on every release. Evidence gathered over a year of releases is far stronger than a file assembled the month before a deadline.",
      },
    ],
    sources: [OMNIBUS, AI_ACT],
  },
  {
    slug: "fail-closed-release-gate-github-actions",
    title: "Wiring a fail-closed AI release gate into GitHub Actions",
    excerpt:
      "One HTTP call, three exit codes. How to stop a deploy when evidence is missing, and how to treat REVIEW in your pipeline.",
    category: "engineering",
    published: "2026-09-21",
    readMinutes: 5,
    cover: "/marketing/blog/release-gate.jpg",
    coverAlt: "Laptop showing a code editor and a passing pipeline at night",
    body: [
      {
        type: "p",
        text: "The release gate is a single read-only endpoint. Your pipeline calls it with an API key and the system's ID, and the response tells the job whether to continue.",
      },
      { type: "h2", text: "Exit codes" },
      {
        type: "ul",
        items: [
          "PASS — exit code 0. All mandatory controls are satisfied.",
          "REVIEW — exit code 2. Non-blocking warnings or approvals still pending.",
          "BLOCKED — exit code 1. Missing evidence, failed evaluations, an open contract breach, or missing approvals. Also returned when the decision cannot be read, so the gate fails closed.",
        ],
      },
      { type: "h2", text: "The workflow step" },
      {
        type: "code",
        text: `- name: Check Devernance release gate
  env:
    API_KEY: \${{ secrets.ASSURANCE_API_KEY }}
    SYSTEM_ID: \${{ vars.ASSURANCE_SYSTEM_ID }}
  run: |
    res=$(curl -fsS -H "X-Api-Key: $API_KEY" \\
      "https://<your-host>/api/v1/ci/release-gate?systemId=$SYSTEM_ID")
    echo "$res" | jq -r .content
    exit "$(echo "$res" | jq -r .exitCode)"`,
      },
      { type: "h2", text: "Deciding what REVIEW means for you" },
      {
        type: "p",
        text: "Any non-zero exit fails a GitHub Actions job. If you want REVIEW to warn rather than stop the deploy, map exit code 2 to success in a wrapper step and post the blockers to the pull request instead. BLOCKED should always stop the job.",
      },
      { type: "h2", text: "Keep keys scoped" },
      {
        type: "p",
        text: "Create a dedicated API key for CI from Settings. A key acts with the permissions of the user who created it and cannot create or revoke other keys, so a leaked CI key cannot mint replacements. Store it as an encrypted repository secret.",
      },
    ],
    sources: [],
  },
];

export function sortedPosts(posts: BlogPost[] = POSTS): BlogPost[] {
  return [...posts].sort((a, b) => b.published.localeCompare(a.published));
}

export function postBySlug(slug: string): BlogPost | undefined {
  return POSTS.find((p) => p.slug === slug);
}

export function formatPostDate(iso: string): string {
  return new Date(`${iso}T00:00:00Z`).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
}
