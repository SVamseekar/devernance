# OAuth production smoke test (Google + Microsoft)

Run after deploying API + dashboard env changes for Part 4 OAuth.

Related: root [`.env.example`](../.env.example), [DEPLOYMENT.md](./DEPLOYMENT.md), [SECURITY.md](./SECURITY.md).

## Preconditions

1. **API env** (Spring):
   - `OAUTH_GOOGLE_CLIENT_ID` / `OAUTH_GOOGLE_CLIENT_SECRET`
   - `OAUTH_MICROSOFT_CLIENT_ID` / `OAUTH_MICROSOFT_CLIENT_SECRET`
   - `OAUTH_REDIRECT_BASE_URL` = public dashboard origin (no trailing slash), e.g.
     `https://euassuranceai.souravamseekar.com` or `http://localhost:3000`
   - `OAUTH_AUTO_PROVISION`: `true` by default under the `postgres` profile (self-serve sign-up: only provider-verified emails are provisioned, each into its own workspace); set `false` for invite-only
   - Optional: `OAUTH_STATE_SECRET` (defaults to `AUDIT_CHAIN_SECRET` if unset)
2. **Dashboard env** (Vercel / Next):
   - `ASSURANCE_API_BASE_URL` pointing at the Spring API
   - Same public origin as `OAUTH_REDIRECT_BASE_URL`
3. **Google Cloud OAuth client** authorized redirect URIs (exact match):
   - `http://localhost:3000/api/auth/oauth/google/callback`
   - `https://euassuranceai.souravamseekar.com/api/auth/oauth/google/callback`
4. **Azure AD / Entra app registration** redirect URIs:
   - `http://localhost:3000/api/auth/oauth/microsoft/callback`
   - `https://euassuranceai.souravamseekar.com/api/auth/oauth/microsoft/callback`
5. API restarted after env change; dashboard redeployed.

## Redirect URI design

| Step | URL |
|------|-----|
| Start (browser) | `{dashboard}/api/auth/oauth/{google\|microsoft}/start` |
| Start (API, called server-side by the BFF) | `{api}/auth/oauth/{provider}/start` → IdP URL, relayed to the browser |
| Callback (browser / registered URI) | `{dashboard}/api/auth/oauth/{provider}/callback` |
| Token exchange (server) | `POST {api}/auth/oauth/{provider}/callback` |

Cookies (`session_access`, `session_refresh`) are set only on the **dashboard** origin by the Next BFF, same as password login.

## Smoke test (manual)

| Step | Action | Expected |
|------|--------|----------|
| 1 | Open dashboard `/login` | Login form + **Continue with Google** / **Continue with Microsoft** |
| 2 | Click Google with an **unprovisioned** account (`OAUTH_AUTO_PROVISION=false`) | IdP consent, then back to `/login?auth_error=not_provisioned` with clear message |
| 3 | Pre-create user (same email) in the target tenant, or link `oauth_provider`/`oauth_subject`; retry Google | Lands on `/` with session cookies |
| 4 | DevTools → Application → Cookies | `session_access` + `session_refresh` on dashboard host: `HttpOnly`, `Secure` (prod), `SameSite=Lax` |
| 5 | Call `/api/proxy/systems` (or open Systems) | `200` with tenant-scoped data |
| 6 | Sign out | Cookies cleared; protected routes redirect to `/login` |
| 7 | Password login still works | Existing email/password issues the same cookie names |
| 8 | Repeat steps 2–5 for **Microsoft** | Same outcomes |

## Local HTTP dev

```bash
# API
export OAUTH_GOOGLE_CLIENT_ID=...
export OAUTH_GOOGLE_CLIENT_SECRET=...
export OAUTH_MICROSOFT_CLIENT_ID=...
export OAUTH_MICROSOFT_CLIENT_SECRET=...
export OAUTH_REDIRECT_BASE_URL=http://localhost:3000
export OAUTH_AUTO_PROVISION=true   # optional local only — creates tenant+admin for new IdP users

# Dashboard
export ASSURANCE_API_BASE_URL=http://localhost:8080
```

```bash
cd services/api && mvn spring-boot:run
cd apps/dashboard && npm run dev
# → http://localhost:3000/login
```

## Automated checks (CI / laptop)

```bash
cd services/api
mvn test -Dtest=OAuthServiceTest,OAuthControllerTest,OAuthStateServiceTest,OAuthProviderProfileTest,AuthControllerTest,TenantContextFilterTest
```

Password login regression must stay green (`AuthControllerTest`).

## Rollback

- Set `OAUTH_AUTO_PROVISION=false` to stop new IdP users from creating workspaces; existing users can still sign in.
- Clear client IDs/secrets to disable social buttons effectively (start redirects to `not_configured` / unavailable).
- Revert redirect URIs in Google/Azure if the callback domain was wrong.
- Clear stale `session_*` cookies before retesting.

## Sign-off

| Date | Environment | Google | Microsoft | Password still OK | Operator |
|------|-------------|--------|-----------|-------------------|----------|
| _yyyy-mm-dd_ | local / staging / prod | pass/fail | pass/fail | pass/fail | _name_ |

Do **not** claim “full enterprise SSO” (SAML/Okta marketplace) until those IdPs are implemented. This part is **Google + Microsoft OIDC only**.
