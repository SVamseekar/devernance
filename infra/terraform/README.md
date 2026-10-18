# Terraform skeleton (Part 9)

Non-destructive **layout** for a future production deploy of Devernance. Modules are placeholders (`null` provider) so CI and local developers can run:

```bash
cd infra/terraform
terraform fmt -check -recursive
terraform init -backend=false
terraform validate
```

**This skeleton is not applied by default.** Do not point it at real cloud credentials until you replace modules with provider-backed resources (AWS RDS + ECS/Cloud Run, or GCP Cloud SQL + Cloud Run, etc.).

## Layout

```text
infra/terraform/
  versions.tf              # required_version + null provider
  main.tf                  # wires modules
  variables.tf / outputs.tf
  terraform.tfvars.example
  modules/
    network/               # VPC / private networking stubs
    database/              # managed Postgres (+ pgvector) stubs
    secrets/               # secret manager name list (no values)
    compute/               # API service + optional object bucket stubs
```

## What real modules should provision

| Module | Intent |
|---|---|
| `network` | VPC, private subnets, egress for pulls, private DB access |
| `database` | Postgres 16+, optional `vector` extension, encryption at rest, backups |
| `secrets` | `EVAL_CALLBACK_SECRET`, `AUDIT_CHAIN_SECRET`, DB password, OAuth client secrets |
| `compute` | Run `infra/Dockerfile` image; inject env from secrets; health `/actuator/health` |

Dashboard remains on **Vercel** unless you deliberately self-host (`infra/Dockerfile.dashboard`).

## Validation without cloud credentials

```bash
# Host terraform, or:
docker run --rm -v "$PWD":/work -w /work/infra/terraform hashicorp/terraform:1.9 \
  sh -c 'terraform fmt -check -recursive && terraform init -backend=false && terraform validate'
```

CI runs the same commands (see `.github/workflows/ci.yml` job `terraform-validate`).

## Apply path (operators only)

1. Choose a provider and replace `null_resource` modules.
2. Configure a **remote backend** with encryption and state locking.
3. Copy `terraform.tfvars.example` → private `terraform.tfvars` (never commit secrets).
4. `terraform plan` → review → `terraform apply` only with change control.
5. Run Flyway via API boot against the new DB; then point Vercel `ASSURANCE_API_BASE_URL` at the service URL.

See `docs/DEPLOYMENT.md` for the full runbook and env matrix.
