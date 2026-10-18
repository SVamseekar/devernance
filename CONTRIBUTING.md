# Contributing to Devernance

Thanks for helping improve the governance control plane for EU AI Act–oriented release assurance.

Solo maintainer: self-review through a PR plus CI is enough. Do not add
fake multi-person approval requirements.

## Git workflow

1. Update local `main` from `origin/main`. Do not develop features on `main`.
2. **Branch** with a descriptive name:
   - `feature/…` — new capability
   - `fix/…` — bug fix
   - `chore/…` — tooling, CI, docs-only
3. **Keep commits focused** — one logical change per commit when practical.
4. **Conventional Commits** (required locally and on the PR title):

   `feat:` `fix:` `refactor:` `perf:` `test:` `docs:` `build:` `ci:` `chore:` `revert:`

   Example: `ci: check commit attribution and PR titles`

   Squash-merge uses the **PR title** as the commit subject on `main`.

   Authorship is the maintainer only. Never add `Co-authored-by`,
   `Co-committed-by`, or any agent/tool attribution. The commit-msg hook and CI reject them.
   Dependabot `Signed-off-by: dependabot[bot]` is allowed.
5. **Open a pull request** into `main`.
6. **Wait for CI.** Required check names must match job names exactly:
   `API tests`, `Dashboard checks`, `Secret scan`, `Pre-commit`,
   `Conventional title`, `No co-authors`.
7. **Do not force-push to `main`.** Feature branches may use
   `--force-with-lease` (never `--force`) if they have not been shared widely.
8. Delete the branch after merge unless there is a reason to keep it.

Treat Dependabot as a queue of proposed version floors, not merge buttons.
Combine intended bumps on current `main`, run the same checks CI will run,
open one PR, then close bot PRs with a supersede comment.

## Local setup

First time:

```bash
pip install pre-commit
pre-commit install
```

`pre-commit install` sets up both the `pre-commit` and `commit-msg` hooks.
GitHub Actions re-runs those file hooks on the PR diff, so skipping locally
with `--no-verify` still fails CI.

### API (Spring Boot 4.1 / Java 17)

```bash
cd services/api
mvn test
mvn spring-boot:run
# → http://localhost:8080
```

Default H2 profile needs no external database. `EVAL_CALLBACK_SECRET` may be empty for local H2; set a real secret in any shared environment.

### Dashboard (Next.js 16)

```bash
cd apps/dashboard
npm ci
npx tsc --noEmit
npm run build
npm run dev
# → http://localhost:3000
```

The dashboard proxies authenticated API traffic through `/api/proxy` to `ASSURANCE_API_BASE_URL` (default `http://localhost:8080`). When the API is unreachable, many views fall back to seeded mock data.

## Required checks before opening a PR

| Area | Command |
|---|---|
| File hygiene + policy | `pre-commit run` (or CI Pre-commit job) |
| Attribution unit tests | `python3 tests/test_check_commit_attribution.py` |
| API unit/integration tests | `cd services/api && mvn test` |
| Dashboard types | `cd apps/dashboard && npx tsc --noEmit` |
| Dashboard production build | `cd apps/dashboard && npm run build` |

## Never commit

- `.env`, `.env.*`, API keys, JWT signing material, or production credentials
- `.local/` (lab SSH env, host IPs, operator runbooks)
- `.worktrees/`
- Outreach queues, named contact lists, invoices, or signed customer contracts
- Tenant customer data or real personal data used as fixtures
- Large binary dumps unrelated to the product (prefer Git LFS only if deliberately adopted)
- `Co-authored-by` / agent trailers

Do not use `git commit --no-verify` unless the maintainer explicitly asks.

Public-claims packs in `services/api/src/main/resources/public-claims/` **are** in git: they are reconstructed from cited public pages, labelled as teasers, and are not customers. Do not seed them on customer postgres (`ASSURANCE_PUBLIC_CLAIMS=false`).

## Branch protection (maintainers)

`main` should require:

- A pull request (0 approving reviews is OK for solo admin)
- `required_review_thread_resolution: true`
- `strict_required_status_checks_policy: true` (branch must be up to date)
- Status checks: **API tests**, **Dashboard checks**, **Secret scan**,
  **Pre-commit**, **Conventional title**, **No co-authors**
- No force-push / no branch deletion

If you rename a CI job, protection silently stops requiring it.

Apply the JSON in GitHub Settings (or via the ruleset API) after the named
jobs exist on `main`. If the GitHub plan or permissions block rulesets,
document the gap and re-apply when available. Solo admins may retain bypass
for emergency hotfixes — prefer a follow-up PR the same day.

## Repo settings (maintainers)

- Homepage URL: `https://euassuranceai.souravamseekar.com`
- License: MIT
- Topics: keep AI Act / Spring Boot / Next.js topics accurate

## Security reports

See [SECURITY.md](./SECURITY.md). Do not open public issues for undisclosed vulnerabilities.
