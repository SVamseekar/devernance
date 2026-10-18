### Artifact scan (`.github/workflows/artifact-scan.yml`)

```mermaid
flowchart LR
  trigger_0(["pull_request"])
  scan["Evgraph SARIF and release gate"]
  trigger_0 --> scan
```

### CI (`.github/workflows/ci.yml`)

```mermaid
flowchart LR
  trigger_0(["pull_request branches: main"])
  trigger_1(["push branches: main"])
  trigger_2(["workflow_dispatch"])
  pre_commit["Pre-commit"]
  changes["Detect changes"]
  secret_scan["Secret scan"]
  api_test["API tests"]
  api_postgres["API on Postgres (Flyway + smoke)<br/><i>if: needs.changes.outputs.api == 'true' || github.event_name != 'pull_request'</i>"]
  acceptance["Acceptance (Claims Triage)"]
  dashboard_build["Dashboard checks"]
  infra_test["Infra tests (compose, backups, updater)"]
  terraform_validate["Terraform validate"]
  e2e["E2E (login → register → evidence)<br/><i>if: needs.changes.outputs.api == 'true' || needs.changes.outputs.dashboard == 'true' || github.event_name != 'pull_request'</i>"]
  trigger_0 --> pre_commit
  trigger_1 --> pre_commit
  trigger_2 --> pre_commit
  trigger_0 --> changes
  trigger_1 --> changes
  trigger_2 --> changes
  trigger_0 --> secret_scan
  trigger_1 --> secret_scan
  trigger_2 --> secret_scan
  changes --> api_test
  changes --> api_postgres
  changes --> acceptance
  changes --> dashboard_build
  changes --> infra_test
  changes --> terraform_validate
  changes --> e2e
```

### Images (`.github/workflows/images.yml`)

```mermaid
flowchart LR
  trigger_0(["workflow_run branches: main"])
  trigger_1(["workflow_dispatch"])
  build["Build ${{ matrix.name }} (linux/arm64)<br/><i>if: github.event_name == 'workflow_dispatch' || (github.event.workflow_run.conclusion == 'success' && github.event.workflow_run.event == 'push')</i>"]
  trigger_0 --> build
  trigger_1 --> build
```

### PR title (`.github/workflows/pr-title.yml`)

```mermaid
flowchart LR
  trigger_0(["pull_request branches: main"])
  conventional["Conventional title"]
  no_coauthors["No co-authors"]
  trigger_0 --> conventional
  trigger_0 --> no_coauthors
```

### Release gate example (`.github/workflows/release-gate-example.yml`)

```mermaid
flowchart LR
  trigger_0(["workflow_call"])
  trigger_1(["workflow_dispatch"])
  release_gate["Assurance OS release gate"]
  trigger_0 --> release_gate
  trigger_1 --> release_gate
```

