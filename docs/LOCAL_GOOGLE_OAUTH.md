# Local Google Sign-In

Google OAuth is implemented (API + dashboard BFF). For a **local** project you only need:

1. A Google Cloud **OAuth 2.0 Web client** with the local redirect URI
2. Env vars on the **API** process
3. `ASSURANCE_API_BASE_URL=http://localhost:8080` on the dashboard

## 1. Redirect URI (required once)

In [Google Cloud Console → Clients](https://console.cloud.google.com/auth/clients) for your project, open the Web client and add:

```text
http://localhost:3000/api/auth/oauth/google/callback
```

Also useful (Authorized JavaScript origins):

```text
http://localhost:3000
```

Without this exact redirect URI, Google returns `redirect_uri_mismatch`.

## 2. Local env files (gitignored)

Root `.env` (used by `scripts/run-local-dev.sh` and manual export):

```bash
OAUTH_GOOGLE_CLIENT_ID=….apps.googleusercontent.com
OAUTH_GOOGLE_CLIENT_SECRET=…
OAUTH_REDIRECT_BASE_URL=http://localhost:3000
OAUTH_AUTO_PROVISION=true

ASSURANCE_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_SITE_URL=http://localhost:3000
```

`apps/dashboard/.env.local`:

```bash
ASSURANCE_API_BASE_URL=http://localhost:8080
NEXT_PUBLIC_SITE_URL=http://localhost:3000
```

If you have a downloaded `client_secret_*.json` from Google:

```bash
python3 - <<'PY'
import json
from pathlib import Path
src = Path.home() / "Downloads" / "client_secret_YOURFILE.json"
web = json.loads(src.read_text())["web"]
print("OAUTH_GOOGLE_CLIENT_ID=" + web["client_id"])
print("OAUTH_GOOGLE_CLIENT_SECRET=" + web["client_secret"])
PY
```

## 3. Run

```bash
# One script (API H2 + Next dev)
chmod +x scripts/run-local-dev.sh
./scripts/run-local-dev.sh
```

Or two terminals:

```bash
# Terminal 1 — API (must see OAUTH_* in the process env)
set -a && source .env && set +a
cd services/api && mvn spring-boot:run

# Terminal 2 — dashboard
cd apps/dashboard && npm run dev
```

Open: http://localhost:3000/login → **Continue with Google**

With `OAUTH_AUTO_PROVISION=true`, the first successful Google login creates a tenant + admin for that email.

## 4. Flow (local)

```text
Browser  →  GET localhost:3000/api/auth/oauth/google/start
Next BFF →  GET localhost:8080/auth/oauth/google/start (server-side, redirect not followed)
API      →  302 accounts.google.com (state signed); the BFF relays that Location to the browser
Google   →  302 localhost:3000/api/auth/oauth/google/callback?code&state
Next BFF →  POST localhost:8080/auth/oauth/google/callback
         →  Set session_access + session_refresh cookies
         →  Redirect /command
```

## 5. Troubleshooting

| Symptom | Fix |
|---|---|
| "Sign-in unavailable" right after clicking Google | The dashboard could not reach the API start endpoint: check `ASSURANCE_API_BASE_URL` on the dashboard (local: `.env.local`). |
| `auth_error=not_configured` | API process missing `OAUTH_GOOGLE_CLIENT_*` — restart API after sourcing `.env` |
| `redirect_uri_mismatch` | Add exact callback URI in Google Console |
| `auth_error=not_provisioned` | Set `OAUTH_AUTO_PROVISION=true` for local, or pre-create user with that email |
| Password login | Still works: bootstrap user from `BootstrapData` |

## 6. Production later

Production self-serve uses `OAUTH_AUTO_PROVISION=true` (the `postgres` default; each new user gets their own workspace). Set secrets on the **API host**, set `OAUTH_REDIRECT_BASE_URL=https://euassuranceai.souravamseekar.com`, and register that callback URI. See `docs/oauth-production-smoke-test.md`.
