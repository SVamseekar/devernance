import Link from "next/link";

import { MarketingPageShell } from "@/components/landing/marketing-page-shell";
import { CodeTabs } from "@/components/marketing/code-tabs";
import { Band, Container, PageHeader } from "@/components/marketing/primitives";
import { CtaBand } from "@/components/marketing/sections";
import { exitCodes, gateSnippets } from "@/lib/gate-snippets";
import { marketingMetadata, webPageJsonLd } from "@/lib/seo";
import { siteConfig } from "@/lib/site-config";

const title = "Docs: gate your first AI release";
const description =
  "Quickstart for Devernance: sign up, register an AI system, create an API key, add the release gate to CI, read the decision, and verify a signed evidence pack.";
const path = "/docs";

export const metadata = marketingMetadata({
  title,
  description,
  path,
  keywords: ["Devernance docs", "AI release gate quickstart", "evidence pack verification", "evgraph CLI"],
});

const verifyScript = `import json, sys, urllib.request
from jose import jws            # pip install python-jose

pack = json.load(open(sys.argv[1]))
jwks = json.load(urllib.request.urlopen(sys.argv[2]))
header = jws.get_unverified_header(pack["signature"])
key = next(k for k in jwks["keys"] if k["kid"] == header["kid"])
claims = json.loads(jws.verify(pack["signature"], key, algorithms=["RS256"]))
assert claims["contentSha256"] == pack["contentSha256"], "hash mismatch"
print("signature valid for system", claims["systemId"], "generated", claims["generatedAt"])`;

const evgraphScan = `pip install evgraph evgraph-cli

# Check that a model card, approval and deployment record agree
evgraph scan model_card.json approval.json deployment.json --format markdown

# Same check as a CI gate: exit code 1 on unmet evidence, --strict also trips on inconclusive findings
evgraph scan-promotion \\
  --model-card model_card.json \\
  --approval approval.json \\
  --deployment deployment.json \\
  --format sarif --gate --strict > evgraph-promotion.sarif`;

const steps = [
  { id: "sign-up", title: "Sign up" },
  { id: "register", title: "Register an AI system" },
  { id: "api-key", title: "Create an API key" },
  { id: "ci", title: "Add the gate to CI" },
  { id: "decision", title: "Read the decision" },
  { id: "verify", title: "Export and verify a pack" },
  { id: "evgraph", title: "Using evgraph locally" },
];

function Step({ n, id, title, children }: { n: number; id: string; title: string; children: React.ReactNode }) {
  return (
    <section id={id} className="scroll-mt-28">
      <h2 className="flex items-center gap-3 text-2xl font-bold tracking-tight text-ink">
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand text-sm text-white">
          {n}
        </span>
        {title}
      </h2>
      <div className="mt-4 space-y-4 text-ink/85">{children}</div>
    </section>
  );
}

const code = "rounded bg-mist px-1.5 py-0.5 font-mono text-[0.85em] text-ink";

export default function DocsPage() {
  return (
    <MarketingPageShell
      jsonLd={webPageJsonLd({
        name: `${title} — ${siteConfig.shortName}`,
        description,
        path,
        crumbs: [
          { name: "Home", path: "/" },
          { name: "Docs", path },
        ],
      })}
    >
      <Band tone="light" muted>
        <Container className="pt-10 pb-20 sm:pt-12">
          <PageHeader tone="light" eyebrow="Docs" title="Gate your first AI release." description={description} />

          <div className="mt-12 grid gap-12 lg:grid-cols-[14rem_minmax(0,1fr)]">
            <nav aria-label="On this page" className="hidden lg:block">
              <ol className="sticky top-28 space-y-2 text-sm">
                {steps.map((s, i) => (
                  <li key={s.id}>
                    <a href={`#${s.id}`} className="text-ink-muted hover:text-brand">
                      {i + 1}. {s.title}
                    </a>
                  </li>
                ))}
              </ol>
            </nav>

            <div className="min-w-0 max-w-3xl space-y-14">
              <Step n={1} id="sign-up" title="Sign up">
                <p>
                  <Link href="/signup" className="font-medium text-brand hover:underline">
                    Create a free workspace
                  </Link>
                  . The Free plan needs no card and gates one AI system.
                </p>
              </Step>

              <Step n={2} id="register" title="Register an AI system">
                <p>
                  Open <strong>Systems</strong> and add the AI feature you want to gate. Answer the obligation
                  questionnaire; it suggests a risk class and the obligations that likely apply, and a person confirms
                  them. Then attach evidence: model cards, evaluation runs, data contracts and approvals.
                </p>
                <p>
                  Copy the system ID from the system page. Store it as a CI variable named{" "}
                  <code className={code}>ASSURANCE_SYSTEM_ID</code>.
                </p>
              </Step>

              <Step n={3} id="api-key" title="Create an API key">
                <p>
                  In <strong>Settings → API keys</strong>, create a key for CI. It is shown once. Store it as a CI secret
                  named <code className={code}>ASSURANCE_API_KEY</code>, and set{" "}
                  <code className={code}>ASSURANCE_URL</code> to your workspace URL.
                </p>
              </Step>

              <Step n={4} id="ci" title="Add the gate to CI">
                <p>
                  On GitHub, add the{" "}
                  <a href="https://github.com/SVamseekar/assurance-os-release-gate" className="font-medium text-brand hover:underline">
                    Assurance OS release gate action
                  </a>
                  : one step, no scripts. It fails the job on <code className={code}>BLOCKED</code> and writes the
                  blockers to the job summary. Any other CI can call the endpoint with curl.
                </p>
                <CodeTabs tabs={gateSnippets.filter((t) => t.label !== "API response")} />
              </Step>

              <Step n={5} id="decision" title="Read the decision">
                <p>
                  The response carries <code className={code}>decision</code>, a list of{" "}
                  <code className={code}>blockers</code> naming what is missing, and an{" "}
                  <code className={code}>exitCode</code> the job exits with.
                </p>
                <CodeTabs tabs={gateSnippets.filter((t) => t.label === "API response")} compact />
                <ul className="divide-y divide-line rounded-2xl border border-line bg-white text-sm">
                  {exitCodes.map((e) => (
                    <li key={e.decision} className="flex items-start gap-4 px-4 py-3">
                      <code className={`${code} shrink-0`}>
                        {e.decision} = {e.code}
                      </code>
                      <span>{e.detail}</span>
                    </li>
                  ))}
                </ul>
              </Step>

              <Step n={6} id="verify" title="Export and verify a pack">
                <p>
                  Export the evidence pack from the system page, or call{" "}
                  <code className={code}>GET /api/v1/systems/&#123;systemId&#125;/evidence-pack</code>. Each JSON pack
                  carries <code className={code}>contentSha256</code> and an RS256{" "}
                  <code className={code}>signature</code>. Public keys are at{" "}
                  <code className={code}>/.well-known/jwks.json</code> on your workspace host.
                </p>
                <p>Anyone holding the pack can check it, with no account:</p>
                <CodeTabs tabs={[{ label: "verify_pack.py", code: verifyScript }]} compact />
                <p className="text-sm text-ink-muted">
                  A valid signature proves Devernance produced the pack and it was not edited after export. It is not a
                  legal certification.
                </p>
              </Step>

              <Step n={7} id="evgraph" title="Using evgraph locally">
                <p>
                  <a href={siteConfig.evgraphUrl} className="font-medium text-brand hover:underline">
                    evgraph
                  </a>{" "}
                  is our open-source (BSD-3-Clause) governance-evidence checker. Run it on your laptop or in CI
                  without an account, before or alongside the hosted gate. It reports what evidence exists, is missing or disagrees; it does not give a
                  compliance verdict.
                </p>
                <CodeTabs tabs={[{ label: "Terminal", code: evgraphScan }]} compact />
                <p className="text-sm text-ink-muted">Output formats: json, markdown, sarif and oscal.</p>
              </Step>
            </div>
          </div>
        </Container>
      </Band>
      <CtaBand />
    </MarketingPageShell>
  );
}
