# backend-dell.ps1 — Docker-only backend on the Dell lab host.
# No Maven, no manual service starts. Postgres + API via Compose only.
#
# Run on Dell (PowerShell) from the repo root:
#   cd D:\Projects\eu-ai-assurance-os
#   .\scripts\backend-dell.ps1
#   .\scripts\backend-dell.ps1 -Down
#   .\scripts\backend-dell.ps1 -Logs
#
# Host ports (avoid MaSoVa): API :9080, Postgres :5433
# Mac frontend: ./scripts/dev-frontend-dell.sh

param(
    [switch]$Down,
    [switch]$Logs,
    [switch]$Status
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

$ComposeArgs = @(
    "-f", "infra/docker-compose.yml",
    "-f", "infra/docker-compose.dell.yml",
    "--env-file", ".env.dell"
)

if (-not (Test-Path ".env.dell")) {
    if (Test-Path ".env.dell.example") {
        Copy-Item ".env.dell.example" ".env.dell"
        Write-Host "==> Created .env.dell from .env.dell.example" -ForegroundColor Yellow
    } else {
        throw "Missing .env.dell and .env.dell.example — clone/sync the full repo first"
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker not found — install Docker Desktop on the Dell"
}

if ($Down) {
    Write-Host "==> docker compose down" -ForegroundColor Cyan
    & docker compose @ComposeArgs down
    exit $LASTEXITCODE
}

if ($Logs) {
    & docker compose @ComposeArgs logs -f --tail=200 api
    exit $LASTEXITCODE
}

if ($Status) {
    & docker compose @ComposeArgs ps
    try {
        Invoke-RestMethod -Uri "http://127.0.0.1:9080/actuator/health" -TimeoutSec 5 | ConvertTo-Json
    } catch {
        Write-Host "API health not reachable on :9080 yet" -ForegroundColor Yellow
    }
    exit 0
}

Write-Host "==> Devernance — Docker backend only (Dell)" -ForegroundColor Cyan
Write-Host "    Project: eu-ai-assurance-dell"
Write-Host "    API host port: 9080  Postgres host port: 5433"
Write-Host ""

& docker compose @ComposeArgs up -d --build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "==> Waiting for API health..." -ForegroundColor Cyan
$ok = $false
for ($i = 1; $i -le 36; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:9080/actuator/health" -UseBasicParsing -TimeoutSec 3
        if ($r.StatusCode -eq 200) { $ok = $true; break }
    } catch {
        Start-Sleep -Seconds 5
    }
}

if ($ok) {
    Write-Host "API healthy: http://192.168.50.88:9080/actuator/health" -ForegroundColor Green
} else {
    Write-Host "API not healthy yet — check: .\scripts\backend-dell.ps1 -Logs" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "On Mac:" -ForegroundColor Green
Write-Host "  ./scripts/dev-frontend-dell.sh"
Write-Host "  open http://localhost:3000"
