# CSRF Audit — hotel-pms

Read-only audit. No code touched. Starting map: `security-audit/00-recon.md`. Cross-referenced
against `THREAT_MODEL.md` (T-GW-05, GAP-9).

---

## 1. Inventory — every `SecurityFilterChain` / CSRF posture

| # | Filter chain | File:line | CSRF | Session policy | Auth mechanism |
|---|---|---|---|---|---|
| 1 | api-gateway | `api-gateway/src/main/java/com/hotelpms/gateway/filter/CsrfFilter.java` (not a Spring Security `SecurityFilterChain` — a reactive `GlobalFilter`, see §3) | **Enabled** (custom double-submit-cookie, not Spring's built-in `CsrfFilter`) | N/A (gateway is stateless, reactive) | JWT cookie (`AuthenticationFilter`) + double-submit CSRF cookie |
| 2 | auth-service | `auth-service/src/main/java/com/hotelpms/auth/config/SecurityConfig.java:102` | `.csrf(AbstractHttpConfigurer::disable)` | `STATELESS` | JWT cookie read directly by controller (`@CookieValue`), not by Spring Security's session/CSRF machinery; HMAC (`InternalAuthFilter`) for `/users/**` |
| 3 | config-service | `config-service/src/main/java/com/hotelpms/config/SecurityConfig.java:38` | `.csrf(AbstractHttpConfigurer::disable)` | `STATELESS` | HTTP Basic (`CONFIG_SERVER_USERNAME`/`PASSWORD`) |
| 4 | internal-auth-lib (`InternalApiSecurityFilterChainFactory`, shared by billing/fb/frontdesk/guest/notification-service) | `internal-auth-lib/src/main/java/com/hotelpms/internalauth/security/InternalApiSecurityFilterChainFactory.java:51` | `.csrf(AbstractHttpConfigurer::disable)` | `STATELESS` | HMAC (`InternalAuthFilter`) only |
| 5 | guest-service | `guest-service/src/main/java/com/hotelpms/guest/security/SecurityConfig.java:74` → delegates to #4 | (same as #4) | `STATELESS` | HMAC |
| 6 | frontdesk-service | `frontdesk-service/src/main/java/com/hotelpms/frontdesk/security/SecurityConfig.java` → delegates to #4 | (same as #4) | `STATELESS` | HMAC |
| 7 | billing-service | `billing-service/src/main/java/com/hotelpms/billing/security/SecurityConfig.java` → delegates to #4 | (same as #4) | `STATELESS` | HMAC |
| 8 | fb-service | `fb-service/src/main/java/com/hotelpms/fb/security/SecurityConfig.java` → delegates to #4 | (same as #4) | `STATELESS` | HMAC |
| 9 | notification-service | `notification-service/src/main/java/com/hotelpms/notification/security/SecurityConfig.java` → delegates to #4 | (same as #4) | `STATELESS` | HMAC only, not routed by gateway at all |

Verified with `Grep '\.csrf\(' **/*.java`: exactly 3 explicit `AbstractHttpConfigurer::disable` call
sites in the whole repo (#2, #3, #4) — no other service builds its own filter chain outside this set.
Guest/frontdesk/billing/fb/notification-service all call
`InternalApiSecurityFilterChainFactory.build(...)` (confirmed by grepping the call site in all 5
files) rather than re-declaring `.csrf(...)`, so there is exactly one place (#4) implementing that
posture for all five, not five independent copies that could drift.

---

## 2. Justification check for every disabled-CSRF chain

### #4 — `InternalApiSecurityFilterChainFactory` (billing/fb/frontdesk/guest/notification-service)

**Already triaged, not a new finding.** This is exactly GAP-9 in `THREAT_MODEL.md`: CodeQL
(`java/spring-disabled-csrf-protection`, HIGH) flagged this and it was confirmed a false positive —
`sessionCreationPolicy(STATELESS)` (no session cookie, the vector CSRF protection exists to stop)
plus HMAC-signature auth (`InternalAuthFilter`) that a browser page cannot forge without
`INTERNAL_HMAC_SECRET`. I independently re-verified rather than just citing the doc:

- Confirmed all 5 services' controllers are only reachable through the gateway's routes (per
  `config-service/src/main/resources/config/api-gateway.yml`), and the gateway forwards its own
  freshly-computed `X-Internal-Signature`/`X-Auth-Timestamp`/`X-Auth-Nonce` on every proxied call
  (`AuthenticationFilter.java`) — a request arriving at, say, guest-service without a valid HMAC
  signature is rejected by `InternalAuthFilter` regardless of what cookies rode along with it.
- Confirmed the browser's ambient `jwt`/`refresh_token`/`csrf_token` cookies carry no weight at all
  in these 5 filter chains — there is no `@CookieValue`, no `HttpSession`, nothing that would let a
  forged cross-site request succeed even if the cookie were attached.
- In dev, these services' ports (8081/8083/8085/8086/8088) are exposed directly on the host
  (`docker-compose.yml`), so a browser *can* reach them without going through the gateway — but
  since auth there is HMAC-only, direct reachability is irrelevant to CSRF exploitability (no
  ambient credential to forge in the first place). `docker-compose.prod.yml` resets all these
  services' `ports: []` anyway, so production doesn't even have this surface.

No new finding here — citing GAP-9, not re-reporting.

### #3 — config-service (Basic Auth)

Same reasoning class as GAP-9 but a distinct threat: not routed by the gateway, not reachable by
the browser (recon §1, §7), and in `docker-compose.prod.yml` `config-server: ports: !reset []` — no
host exposure at all in prod. Only other backend services (Feign/Spring Cloud Config client) talk to
it, using `CONFIG_SERVER_USERNAME`/`PASSWORD` from env vars, not a browser-managed credential.

One caveat worth flagging for completeness, not as an actionable gap: HTTP Basic credentials, unlike
cookies, have no `SameSite` equivalent — a browser that has cached Basic credentials for an origin
will auto-attach them to *any* request to that origin, cross-site included. This would matter if a
human ever authenticated to config-service *through a browser* (e.g., visited its Actuator UI
directly and got a Basic Auth prompt). In practice this service is machine-to-machine only, never
routed to a browser context, and not exposed on the host in prod — so the precondition for this
caveat to matter doesn't exist today. Documenting it so it's re-checked if config-service's exposure
ever changes.

### #2 — auth-service

**Not covered by GAP-9** (GAP-9 is scoped explicitly to `InternalApiSecurityFilterChainFactory`).
This is the one filter chain in the "CSRF disabled" list that *is* reachable by a browser with an
ambient cookie — see §3 below for why this is still safe.

---

## 3. The one place a browser cookie reaches the app end-to-end: api-gateway

Confirmed live in code, not assumed: `frontend/nginx.conf` proxies `location /api/` to
`http://api-gateway:8080` for **every** `/api/*` path in the built/prod image — including
`/api/v1/auth/**`. `config-service/src/main/resources/config/api-gateway.yml` then routes
`/api/v1/auth/**` (and `/api/v1/auth/users/**` specifically) on to `auth-service:8087`. So in the
deployed topology, auth-service's cookie-authenticated, CSRF-disabled endpoints are still reached
*through* the gateway.

The gateway implements its own CSRF control — not Spring Security's built-in mechanism (the gateway
has no `SecurityFilterChain` at all; it's WebFlux + Spring Cloud Gateway) — as a **double-submit
cookie** pattern:

- `api-gateway/src/main/java/com/hotelpms/gateway/filter/CsrfFilter.java:60-138`: a `GlobalFilter`
  (applies to every route the gateway proxies, independent of route-specific filters like
  `AuthenticationFilter`) that, for any non-safe method (`POST`/`PUT`/`PATCH`/`DELETE`, safe methods
  `GET`/`HEAD`/`OPTIONS`/`TRACE` excluded at line 72-73/97-99) not on the pre-auth exclusion list
  (`/api/v1/auth/login`, `/register`, `/refresh` — line 79-82), requires the `csrf_token` cookie
  value to exactly match the `X-CSRF-Token` header (line 106-122), returning `403 FORBIDDEN`
  otherwise, with a `[CSRF] REJECTED` warn log distinguishing `MISSING_TOKEN` vs `TOKEN_MISMATCH`.
- Ordered `HIGHEST_PRECEDENCE + 2` (line 134-137) — runs immediately after `SecurityHeadersFilter`
  and before route-specific filters/proxying, so it gates the request before it ever reaches
  `AuthenticationFilter` or a downstream service.
- The `csrf_token` cookie is set by `auth-service` (not the gateway) on
  `login`/`register`/`refresh`/`change-password`/`logout` (`AuthController.java:82,108,137,179,218,`
  cleared with `maxAge=0` on logout), deliberately **not** `httpOnly` (`createCsrfCookie`,
  `AuthController.java:298-308`) so SPA JS can read it — the security property relies on same-origin
  policy blocking a foreign page from reading it, not on it being hidden from the app's own JS.

**This is a real, live control, not dead config**, confirmed end-to-end:

- `frontend/src/services/api.ts:19-34`: `getCsrfToken()` reads `document.cookie` for `csrf_token`
  and the Axios request interceptor injects it as `X-CSRF-Token` on every `POST`/`PUT`/`PATCH`/
  `DELETE` request, unconditionally (not feature-flagged, not commented out).
- `config-service/src/main/resources/config/api-gateway.yml:22-24`: `X-CSRF-Token` is explicitly in
  the gateway's CORS `allowedHeaders` list — required for the browser to allow the frontend JS to
  send it cross-origin-config (dev, `localhost:5173` vs `localhost:8080`); confirms this isn't a
  vestigial header nobody actually sends.
- `api-gateway/src/test/java/com/hotelpms/gateway/filter/CsrfFilterTest.java`: dedicated unit test
  class covering safe-method passthrough, excluded-path passthrough, missing/blank/mismatched
  token → 403, and valid matching token → passthrough.
- `frontend/e2e/security-auth.spec.ts` (GAP-2, `T-AUTH-SEC-04`): live Playwright E2E case exercising
  a 403 CSRF response end-to-end against the real built stack.
- Formalized in `THREAT_MODEL.md` as **T-GW-05** (status ✅ RISOLTO, row 92) and referenced again
  inside the GAP-9 entry (row 283) as the "real" browser-facing CSRF defense, distinguishing it from
  the internal-only HMAC chains.

**Why auth-service disabling CSRF at its own `SecurityFilterChain` (#2) is still safe**: the gateway's
`CsrfFilter` sits in front of it for all production/deployed traffic, so the double-submit check has
already happened by the time the request reaches auth-service. auth-service's own chain doesn't need
to repeat it. See §5 for one topology-specific caveat (dev-only) to this statement.

---

## 4. `SameSite` on the JWT cookie

`auth-service/src/main/java/com/hotelpms/auth/controller/AuthController.java`:

- `createCookie()` (line 270-281, used for `jwt` and `refresh_token`): `.httpOnly(true)`,
  `.secure(true)`, `.sameSite("Strict")` (`SAME_SITE_STRICT`, line 55).
- `createCsrfCookie()` (line 298-308, used for `csrf_token`): `.httpOnly(false)`, `.secure(true)`,
  `.sameSite("Strict")` — same attribute.

All three cookies set by this app use `SameSite=Strict`. This is itself a strong, independent CSRF
mitigation: `Strict` blocks the cookie from being attached to **any** cross-site request, including
top-level navigations (unlike `Lax`, which still allows the cookie on top-level GET navigation). For
an attacker's page on a genuinely different site (different registrable domain — the relevant unit
for `SameSite`, not scheme or port) to trigger a state-changing request that carries the victim's
`jwt` cookie, the browser would have to violate `SameSite=Strict`, which none currently do. No
`SameSite=None` cookie exists anywhere in the codebase (`Grep 'sameSite'` — 2 call sites, both
`Strict`).

Net effect: **two independent layers** block CSRF on the browser-facing path — `SameSite=Strict`
(cookie-level, applies at the browser regardless of app logic) and the double-submit `X-CSRF-Token`
check (application-level, T-GW-05). Either one alone would already stop the classic
cross-site-auto-submitting-form/fetch attack this audit is checking for.

---

## 5. Dev-only topology gap (informational, not exploitable)

`frontend/vite.config.ts:49-68` (dev server only, `npm run dev`): the Vite proxy routes
`/api/v1/auth/**` (except `/users/**`) **directly to `http://localhost:8087`** (auth-service),
bypassing api-gateway entirely — comment at line 57: *"Direct to auth-service for login/register/me
(no gateway HMAC needed)"*. `docker-compose.yml` (dev/base compose file) also exposes auth-service on
the host directly (`ports: ["8087:8087"]`, line 391-392).

This means in the **local dev topology only**, `POST /api/v1/auth/change-password` (and
`/logout`) never passes through `CsrfFilter` — auth-service's own chain has CSRF disabled (#2) and
there's no gateway in the path to enforce the double-submit check. In the **built/prod topology**
(`frontend/nginx.conf` → gateway → auth-service, `docker-compose.prod.yml` resetting auth-service's
host port exposure to none) this gap doesn't exist — confirmed in §3.

**Exploitability**: still effectively none, because `SameSite=Strict` (§4) is independent of this
proxy topology — it's a browser-enforced cookie attribute set by auth-service regardless of how the
request got there. A genuine cross-site attacker page (different registrable domain) cannot get the
`jwt` cookie attached to a forged request against `localhost:8087` any more than against the gateway,
`SameSite=Strict` blocks it either way. This is a dev/prod parity note (dev testing of the
double-submit-cookie path specifically would need to run against the full Docker Compose stack, not
`npm run dev`, to exercise `CsrfFilter` at all) rather than a security hole — flagging for
completeness since the task asked to verify, not assume, that HMAC-only chains are the only
CSRF-disabled surface reachable by a browser; this one is reachable by a browser (by design) and
still safe, for a different reason (`SameSite=Strict`) than the HMAC chains.

---

## 6. State-changing endpoints — would a same-site-cookie-only forged request work?

Sampled the state-changing endpoints called out in the task plus the ones recon flagged as missing
`@PreAuthorize` (access-control concern, not CSRF, but worth confirming CSRF-wise too):

| Endpoint | Reached via | SameSite=Strict blocks cross-site? | X-CSRF-Token required? | Cross-site exploitable? |
|---|---|---|---|---|
| `POST /api/v1/auth/change-password` | gateway → auth-service (prod); direct in dev (§5) | Yes | Yes (prod path) | No |
| `POST /api/v1/auth/logout` | gateway → auth-service | Yes | Yes | No |
| `POST /api/v1/reservations`, `PUT/DELETE .../{id}` | gateway → frontdesk-service | Yes (cookie never reaches frontdesk-service at all — HMAC-only, §2) | Yes (at gateway) | No |
| `DELETE /api/v1/guests/{id}` | gateway → guest-service | Yes | Yes | No |
| `POST /api/v1/invoices/{invoiceId}/payments` (no `@PreAuthorize`, recon flagged for access-control.md) | gateway → billing-service | Yes | Yes | No (CSRF-wise; the missing `@PreAuthorize` is a separate, non-CSRF, access-control question — out of scope here, already flagged for `access-control.md`) |
| `POST /api/v1/fb/orders/{id}/confirm` (no `@PreAuthorize`) | gateway → fb-service | Yes | Yes | No (same caveat as above) |
| `DELETE /api/v1/guests/{id}/documents/{documentId}` (no `@PreAuthorize`, no legal guard — recon T-GST-08) | gateway → guest-service | Yes | Yes | No (CSRF-wise; T-GST-08 is a separate pre-existing access-control gap, out of scope) |

For every one of these, a same-site-cookie-only forged request (the CSRF scenario: attacker page
auto-submitting a form/fetch using the victim's ambient cookie) is blocked twice over: `SameSite=
Strict` stops the cookie from ever being attached to the cross-site request, and even in the
counterfactual where it were attached, the gateway's `X-CSRF-Token` double-submit check would still
reject it since the attacker's page cannot read `csrf_token` (same-origin policy). None of the
endpoints checked have a genuine CSRF hole. Endpoints missing `@PreAuthorize` are real
access-control questions (already flagged in `00-recon.md` §8 for `access-control.md`) but not CSRF —
a same-site authenticated user (or an attacker who has otherwise obtained the victim's actual
cookies, which is session hijacking, not CSRF) hitting them is a different threat class than what
this audit covers.

---

## Summary (severity-sorted)

| Severity | Finding | Status |
|---|---|---|
| — | GAP-9: `InternalApiSecurityFilterChainFactory` (billing/fb/frontdesk/guest/notification-service) CSRF disabled | **Already triaged, false positive confirmed** — not a new finding, re-verified independently (§2) |
| INFO | config-service Basic Auth + CSRF disabled: safe today (not browser-routed, no host exposure in prod); HTTP Basic has no `SameSite` equivalent if that ever changes | No action needed now; re-check if config-service's network exposure ever changes |
| INFO | Dev-only (`npm run dev` + base `docker-compose.yml`): `CsrfFilter` (double-submit cookie) is bypassed for auth-service's own mutating endpoints because Vite proxies `/api/v1/auth/**` directly to `:8087`, skipping the gateway | Not exploitable — `SameSite=Strict` on `jwt`/`refresh_token`/`csrf_token` (§4) is topology-independent and still blocks genuine cross-site CSRF in dev. Production topology doesn't have this gap at all (§3). No fix required; noted for awareness only |
| — | api-gateway `CsrfFilter` (T-GW-05) + `SameSite=Strict` on all 3 app cookies | **Correctly implemented, verified live** — double-submit cookie pattern, unit-tested (`CsrfFilterTest.java`), E2E-tested (`security-auth.spec.ts` T-AUTH-SEC-04), wired end-to-end in `frontend/src/services/api.ts`, CORS-allowlisted (`X-CSRF-Token`). No gap found |

**Overall conclusion**: no new CSRF vulnerability found. Every filter chain with CSRF disabled is
either (a) GAP-9's already-triaged HMAC-only internal chains, (b) config-service's non-browser-
routed Basic Auth chain, or (c) auth-service's own chain, which is covered by the gateway's
independent double-submit-cookie filter (`CsrfFilter`, T-GW-05) plus `SameSite=Strict` on every
cookie the app sets. The one dev-only topology gap found (§5) does not translate into an exploitable
CSRF path because `SameSite=Strict` does not depend on which service terminates the request.
