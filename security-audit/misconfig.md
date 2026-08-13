# Security Misconfiguration Audit (OWASP A05:2021)

Read-only audit. Scope: `config-service/src/main/resources/config/*.yml`,
`*/src/main/resources/application*.yml`, `frontend/nginx.conf`, every
`GlobalExceptionHandler`/`AbstractProblemDetailAdvice`, `auth-service/data.sql` +
Flyway seed migrations, `docker-compose.yml`, `docker-compose.prod.yml`, Dockerfiles.

---

## 1. Actuator exposure

Checked `management.endpoints.web.exposure.include` and
`management.endpoint.health.show-details` in all 9 `config-service/config/*.yml`
files and all 6 service-local `application*.yml` overrides.

| Service | `exposure.include` | `show-details` |
|---|---|---|
| config-service (own `application.yml`) | `health,info` | `when-authorized` |
| api-gateway | `prometheus,health,info` | `when-authorized` |
| auth-service | `prometheus,health,info` | `when-authorized` |
| guest-service | `prometheus,health,info` | `when-authorized` |
| frontdesk-service | `prometheus,health,info` | `when-authorized` |
| billing-service | `prometheus,health,info` | `when-authorized` |
| fb-service | `prometheus,health,info` | `when-authorized` |
| notification-service | `prometheus,health,info` | `when-authorized` |

**GAP-13 (fb-service local override) — confirmed genuinely fixed.**
`fb-service/src/main/resources/application-fb-service.yml:21-29` now reads
`include: "prometheus,health,info"` / `show-details: when-authorized`, matching
`config-service`'s value, with an explanatory comment. No service exposes
`env`, `heapdump`, `beans`, `configprops`, `mappings`, or `*`. No other
`application-<service>.yml` file (auth/billing/guest — the other 3 that exist)
touches actuator config at all, so nothing else can silently drift from
`config-service`'s correct value.

**Defense in depth already in place, not just this config**: every service sets
`management.server.port: 8090`, a *separate* embedded server from the main app
port (8087/8081/8083/8085/8086/8088/8080). Neither the base `docker-compose.yml`
nor `docker-compose.prod.yml` publishes port 8090 for any service — actuator is
unreachable from the Docker host entirely in both dev and prod compose, on top
of the `when-authorized`/restricted-endpoint config. Only reachable from other
containers on the same Docker network (used by Prometheus scrape + the
container healthchecks themselves).

No finding here beyond the already-tracked GAP-13 (confirmed fixed).

---

## 2. CORS (`api-gateway.yml`)

`config-service/src/main/resources/config/api-gateway.yml:11-26`:

```yaml
cors-configurations:
  '[/**]':
    allowedOrigins: "${GW_CORS_ALLOWED_ORIGINS:http://localhost:5173}"
    allowedMethods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
    allowedHeaders: [Content-Type, X-CSRF-Token]
    allowCredentials: true
    maxAge: 3600
```

- `allowedOrigins` is never `*` — it's a single concrete origin, default
  `http://localhost:5173` (Vite dev server). Spring's `CorsConfiguration`
  itself throws at startup if `allowedOrigins` contains `*` together with
  `allowCredentials: true` (`CorsConfiguration.validateAllowCredentials()`),
  so even a future misconfiguration to a literal `"*"` would fail closed
  (app won't start) rather than silently serving a dangerous combination.
- **Finding (LOW / informational) — `docker-compose.yml:339`**: unlike every
  other configurable value in this file (`POSTGRES_PASSWORD`, `JWT_SECRET`,
  service URIs, etc., all `${VAR}` substitutions from `.env`), the CORS origin
  is a **hardcoded literal**: `GW_CORS_ALLOWED_ORIGINS=http://localhost:5173`
  (no `${...}` wrapper), with a comment "override for production frontend
  URL". `docker-compose.prod.yml` does not touch this env var either (it only
  resets `ports:`). Net effect: deploying with
  `docker-compose.yml -f docker-compose.prod.yml` without manually editing
  this line leaves `GW_CORS_ALLOWED_ORIGINS` pinned to the dev value. This is
  **not exploitable** (a single non-wildcard origin fails *closed* — it
  would just reject the real prod frontend's cross-origin calls, a
  functional bug, not a security hole; also, the real deployed architecture
  makes browser calls same-origin via the nginx `/api/` proxy, so this CORS
  config mostly only matters for `npm run dev` hitting the gateway directly).
  Still worth fixing for consistency/operability: change to
  `${GW_CORS_ALLOWED_ORIGINS:-http://localhost:5173}` so it can actually be
  overridden via `.env` like everything else, and note it in prod deployment
  docs.

---

## 3. Security headers

### `frontend/nginx.conf`

All three `location` blocks (`/`, `/assets/`, `/api/`) declare their **own**
full `add_header` set (CSP, X-Content-Type-Options, X-Frame-Options,
Referrer-Policy, Permissions-Policy, HSTS) — lines 34-45 (server-level, used
by nothing since every location overrides it), 75-82 (`/api/`), 89-96
(`/assets/`), 108-115 (`/`). This is the correct fix for the nginx
`add_header` inheritance gotcha documented in the file's own comment
(lines 26-33): a location that sets any `add_header` of its own silently
drops ALL server-level `add_header`s for that location. **Verified: no
location is missing headers** — the gotcha does not recur anywhere in the
current file. `/api/` intentionally uses `X-Frame-Options: SAMEORIGIN` /
`frame-ancestors 'self'` (GAP-10) instead of `DENY`/`'none'`, which is a
deliberate, narrowly-scoped exception for the hidden-iframe PDF download
pattern, not a regression.

No finding here — GAP-10 fix confirmed complete and not reintroduced
elsewhere in the file.

### Backend services (Spring Boot, directly reachable on their own ports)

None of the 7 `SecurityConfig` classes (auth/billing/fb/frontdesk/guest/
notification-service, config-service) call `.headers(...)` at all — neither
to configure nor to disable header writers. This means Spring Security's
**default** header writers stay active on every service:
`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
`Cache-Control: no-cache, no-store, max-age=0, must-revalidate` (+ `Pragma`/
`Expires`), and HSTS on HTTPS requests. None of them set a
Content-Security-Policy, Referrer-Policy, or Permissions-Policy — those exist
only in `nginx.conf`.

**Finding (INFORMATIONAL)**: in the base `docker-compose.yml` (dev), every
backend service publishes its app port directly to the host (8087, 8083,
8081, 8085, 8086, 8088) — a client can bypass nginx/gateway entirely and hit
a service directly, getting Spring Security's baseline headers but no
CSP/Referrer-Policy/Permissions-Policy. This is by design for local dev and
is **not** reachable this way in the prod-hardened config:
`docker-compose.prod.yml` resets `ports: []` for every one of these services,
so in the documented production topology only nginx (:80) and api-gateway
(:8080) are host-reachable, and neither is a "backend service directly"
case. Since these are pure JSON APIs (never rendered as HTML by a browser)
and CSRF is intentionally disabled with HMAC-signed internal auth replacing
it, the missing CSP/Referrer-Policy/Permissions-Policy on the backends
themselves is low-impact even in dev. No code change recommended; noting
for completeness per the audit brief.

---

## 4. Verbose errors / stack traces

- No service sets `server.error.include-stacktrace`, `include-message`, or
  `include-binding-errors` anywhere in any `application*.yml` or
  `config-service/config/*.yml` — Spring Boot's defaults apply everywhere
  (`never` / `never` / `never`). Verified via repo-wide grep, zero matches.
- `AbstractProblemDetailAdvice.handleGenericException`
  (`common-web-lib/src/main/java/com/hotelpms/commonweb/exception/AbstractProblemDetailAdvice.java:264-273`,
  the shared 500 catch-all inherited by all 6 services) returns a fixed
  `"INTERNAL_SERVER_ERROR"` detail string only — no exception message, no
  class name, no stack trace, no SQL, no file paths. The actual exception
  (with full stack trace) is logged server-side only
  (`LOG.error("Unhandled exception: {}", ex.getMessage(), ex)`).
  `frontdesk-service`'s override
  (`frontdesk-service/.../exception/GlobalExceptionHandler.java:245-258`)
  additionally attaches a random `traceId` UUID to both the log line and the
  response body for support correlation — no extra detail leaked, this is a
  safe pattern.
- Every per-service `GlobalExceptionHandler` (auth/billing/fb/frontdesk/
  guest/notification-service) only ever surfaces `ex.getMessage()` for
  **domain exceptions the service itself throws** (`NotFoundException`,
  `BillingValidationException`, `GuestConflictException`,
  `AlloggiatiValidationException`, etc.) — all controlled, human-authored
  messages, never a raw driver/JDBC exception message. `DataIntegrityViolationException`
  handling in frontdesk-service (`GlobalExceptionHandler.java:187-212`)
  deliberately discards the raw constraint-violation message from the
  response body (`ex.getMessage()` is only logged server-side) and returns a
  static `REQUIRED_FIELD_MISSING`/`RESOURCE_ALREADY_EXISTS` instead — good
  pattern, no SQL/constraint-name leakage to the client.

**Finding (LOW) — `FeignException`/`ExternalServiceException` handlers echo
`ex.getMessage()` verbatim into the 502 response body.**
`AbstractProblemDetailAdvice.handleFeignException`
(`common-web-lib/.../AbstractProblemDetailAdvice.java:138-145`) and the
per-service `ExternalServiceException` handlers (e.g.
`billing-service/.../GlobalExceptionHandler.java:73-81`,
`frontdesk-service/.../GlobalExceptionHandler.java:93-101`,
`fb-service/.../GlobalExceptionHandler.java:90-98`) put
`ex.getMessage()` directly into the `detail` field returned to the caller.
Feign's default `ErrorDecoder` message format
(`"[<status>] during [<method>] to [<url>] [<Feign method ref>]: [<body>]"`)
includes the **internal Docker-network URL of the downstream call**
(e.g. `http://billing-service:8085/api/v1/invoices/stay`) and can include
the downstream response body. Impact is limited — these are internal Docker
service hostnames, not secrets, and the downstream body itself already went
through this same sanitized `AbstractProblemDetailAdvice` before being
re-wrapped — but it's still unnecessary internal-topology disclosure to an
external caller and inconsistent with the "generic detail only, log the
rest" pattern used everywhere else in this same file. **Remediation**:
replace `ex.getMessage()` with a static `"EXTERNAL_SERVICE_ERROR"` (matching
the `RESOURCE_ALREADY_EXISTS`/`REQUIRED_FIELD_MISSING` pattern already used
for `DataIntegrityViolationException`) and log `ex.getMessage()` server-side
only.

---

## 5. Default credentials

**Finding (MEDIUM) — the `admin`/`password` seed is not a "leaked secret"
(correctly triaged as such previously) but IS a genuine default-credential
misconfiguration risk with no rotation safety net, confirmed by tracing the
actual seeding mechanism:**

- The authoritative seed is **not** `data.sql` — it's the Flyway migration
  `auth-service/src/main/resources/db/migration/V1__init_schema.sql:64-89`,
  which unconditionally `INSERT`s the `admin` row (BCrypt hash of
  `password`) as part of schema versioning. Flyway migrations run exactly
  once, automatically, on **any** fresh database — including a real
  from-scratch production deployment, not just local dev. `data.sql`
  (`auth-service/src/main/resources/data.sql`) duplicates the same upsert
  and is redundant with V1 (likely a leftover from before it was folded
  into the migration, or a local-dev convenience re-seed); either way it
  reinforces the same credential rather than being the only source.
  `V6__fix_admin_password_bcrypt_prefix.sql` further confirms this seed
  is treated as a first-class, permanent part of the schema, not a
  dev-only fixture.
- `must_change_password` for this row is **never set to `TRUE`** — it's
  omitted from both the V1 `INSERT` and `data.sql`'s column list, so it
  takes the column default `FALSE`
  (`V5__add_owner_role_and_must_change_password.sql:23`:
  `ADD COLUMN ... BOOLEAN NOT NULL DEFAULT FALSE`). The `mustChangePassword`
  enforcement mechanism that exists and works correctly for admin-created
  users (`UserManagementServiceImpl.java:62,110` sets it `true` on
  `create`/`reset-password`, and the gateway blocks all but 4 paths until
  cleared) is **never applied to the seed admin**.
- No startup warning, banner, log line, or health indicator anywhere in
  `auth-service` flags that the default admin credential is active and
  unchanged (searched for `CommandLineRunner`/`ApplicationRunner` — none
  exist in auth-service).
- **Net effect**: any deployment of this project — including a
  hypothetical real production rollout from a clean database — silently
  ships a `admin`/`password` ADMIN account that never expires, is never
  flagged for rotation, and produces no operational signal that it needs
  to be changed. Someone would have to know to manually deactivate/rotate
  it; nothing in the code prompts or enforces it.
- **Remediation options** (not applied — read-only audit): set
  `must_change_password = TRUE` on the seed row (forces rotation on first
  real login, reusing the existing enforcement path with zero new code);
  and/or add a startup log warning (`CommandLineRunner`) when the seed
  admin still has its original password hash; and/or gate the seed
  migration behind a profile so it never runs against a `prod` Spring
  profile at all, requiring an explicit bootstrap step instead.

### Postgres / Redis default credentials

- **Postgres**: `POSTGRES_PASSWORD` (`docker-compose.yml:39`) and every
  service's `SPRING_DATASOURCE_PASSWORD` are `${POSTGRES_PASSWORD}` with
  **no fallback default** — if `.env` doesn't set it, Compose leaves it
  empty/fails rather than silently using a weak default. Generated by
  `setup-hmac-secret.ps1`/`.sh`. No finding.
- **Redis — finding (MEDIUM)**: `docker-compose.yml:99-104` runs
  `redis:8.8.1-alpine` with **no `requirepass`/ACL configured at all** —
  no `command: redis-server --requirepass ...`, no
  `spring.data.redis.password` set in any service's config (verified via
  repo-wide grep across every `*.yml`, only `host`/`port` are ever set).
  Redis backs the gateway rate limiter, the auth-service refresh-token
  blacklist, and the shared anti-replay nonce store used by every
  `InternalAuthFilter` (T-GW-08) — i.e. it's integrity-sensitive
  infrastructure, not just a cache. In the base `docker-compose.yml`, port
  6379 is published to the host (`"6379:6379"`, line 104) with zero
  authentication: anyone who can reach the host can `redis-cli -h <host>`
  with full read/write access — flush the anti-replay nonce store (opening
  a replay window on internal HMAC calls), forge rate-limit counters, or
  clear the refresh-token blacklist (un-revoking a token that was supposed
  to be blacklisted). `docker-compose.prod.yml` resets Redis's `ports: []`
  (not host-reachable in the documented prod topology), which closes the
  external attack surface, but Redis is still unauthenticated **within**
  the Docker network in both dev and prod — any other compromised
  container, or a container added later that joins the same network,
  gets unrestricted access with no credential. Not previously tracked in
  `THREAT_MODEL.md` (checked — no existing T-CFG/GAP entry mentions Redis
  auth). **Remediation**: add `--requirepass ${REDIS_PASSWORD}` to the
  Redis service command and `spring.data.redis.password: ${REDIS_PASSWORD}`
  to every consumer's config (api-gateway, auth-service, and the 5
  `internal-auth-lib` consumers), same `${VAR}`-from-`.env` pattern already
  used for `POSTGRES_PASSWORD`.

---

## 6. Docker / infra

- **Port hardening — confirmed correct.** `docker-compose.prod.yml` resets
  `ports: []` for every service except `frontend` (`"80:8080"`, base file
  line 718) and `api-gateway` (`"8080:8080"`, base file line 325) — verified
  by reading the full file: postgres, redis, loki, grafana, zipkin,
  prometheus, alertmanager, config-server, auth/guest/frontdesk/billing/fb/
  notification-service are all listed under the reset block. Matches the
  file's own header comment claim exactly.
- **nginx non-root — confirmed fixed (GAP-14).** `frontend/Dockerfile` uses
  `nginxinc/nginx-unprivileged:alpine`, `USER root` only transiently for
  `apk update`/`upgrade`, then explicit `USER nginx` before `CMD`. Matches
  `THREAT_MODEL.md` GAP-14 entry.
- **Postgres root — confirmed accepted-risk, correctly documented (GAP-16).**
  `docker/postgres/Dockerfile` has no `USER` directive (root is the
  container's PID 1 via `entrypoint-wrapper.sh`), but every actual
  Postgres/pgBackRest command in that script runs under `gosu postgres`
  (`entrypoint-wrapper.sh`: `gosu postgres pg_isready`, `gosu postgres
  pgbackrest ... stanza-create`, `gosu postgres backup-scheduler.sh`) — the
  same privilege-drop pattern the official `postgres:15-alpine` image's own
  `docker-entrypoint.sh` uses internally, needed because pgBackRest requires
  transient root to fix permissions on a freshly-mounted volume and to
  `chown` `/etc/pgbackrest/pgbackrest.conf` to `postgres:postgres`. No
  unprivileged equivalent image exists for this use case (unlike nginx).
  Matches `THREAT_MODEL.md` GAP-16 exactly, correctly marked ⚠️ RISCHIO
  ACCETTATO rather than ✅.
- **Local-dev-only credential note (very low)**:
  `auth-service/src/main/resources/application-auth-service.yml:11-14`
  hardcodes `jdbc:postgresql://localhost:5432/hotel_auth` /
  `pms_user` / `pms_password` — this is a Spring profile
  (`SPRING_PROFILES_ACTIVE=auth-service`) apparently meant for running the
  service directly via `gradle bootRun` against a manually-installed local
  Postgres, outside Docker. It is **not** used by `docker-compose.yml`
  (which overrides `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` via env vars
  pointing at the `postgres` container with the real generated password).
  Not a production-reachable credential, but a hardcoded weak password in a
  committed file is still worth a mention; low priority.

---

## 7. TLS / HTTPS

**Finding (INFORMATIONAL / explicit production-readiness gap, expected for
a dev/exam project).** No TLS termination is configured anywhere in the
stack:
- `frontend/nginx.conf` only `listen 8080` (plain HTTP, mapped to host `:80`)
  — no `listen 443 ssl`, no certificate/key paths, no `ssl_*` directives.
- No service in `docker-compose.yml`/`docker-compose.prod.yml` mounts a
  certificate volume or sets `server.ssl.*` for any Spring Boot service.
- The HSTS header (`Strict-Transport-Security`, `nginx.conf:44`) is present
  and correctly commented as "harmless over HTTP (ignored by browsers)" —
  it's future-proofing, not currently effective.
- Related: `notification-service`'s `SMTP_STARTTLS` env var
  (`docker-compose.yml:674`) defaults to `false` — outbound transactional
  email (reservation confirmations, checkout receipts, containing guest
  PII) is sent in plaintext SMTP by default unless explicitly overridden.
- All inter-service traffic (gateway → services, Feign calls, HMAC-signed
  internal calls) is plaintext HTTP within the Docker network, all
  browser-to-nginx and nginx-to-gateway traffic is plaintext HTTP.

This is a reasonable, explicitly-scoped gap for a local/exam deployment
(the JWT/refresh cookies would need `Secure` to be meaningful only once TLS
exists — worth cross-checking against `auth-jwt.md`/`csrf.md` for the
cookie `Secure` flag status), but should be called out explicitly for any
"production readiness" reading of this repo: a real deployment needs a TLS
terminator (nginx `ssl_certificate`, or an upstream load balancer/ingress)
in front of `:80`/`:8080`, and `SMTP_STARTTLS` should default to `true` (or
the absence of TLS should at least be a documented, deliberate exception
rather than a silent default).

---

## Summary (severity-sorted)

| # | Finding | Severity | File(s) | Status |
|---|---|---|---|---|
| 1 | Seed admin (`admin`/`password`) ships with `must_change_password=FALSE` via Flyway `V1__init_schema.sql`, on **any** fresh DB including a real prod rollout; no startup warning | **MEDIUM** | `auth-service/src/main/resources/db/migration/V1__init_schema.sql:64-89`, `V5__add_owner_role_and_must_change_password.sql:23` | Open — new finding |
| 2 | Redis has no `requirepass`/ACL anywhere; port 6379 published to host in dev compose; unauthenticated even container-to-container in prod | **MEDIUM** | `docker-compose.yml:99-104`; no `spring.data.redis.password` in any `config-service/config/*.yml` | Open — new finding |
| 3 | `FeignException`/`ExternalServiceException` handlers put `ex.getMessage()` (incl. internal Docker service URLs) directly into the 502 response body | LOW | `common-web-lib/.../AbstractProblemDetailAdvice.java:138-145`; per-service `ExternalServiceException` handlers | Open — new finding |
| 4 | No TLS termination anywhere (nginx, gateway, inter-service); `SMTP_STARTTLS` defaults to `false` | INFORMATIONAL | `frontend/nginx.conf`, `docker-compose.yml:674` | Open — documented gap, expected for dev/exam scope |
| 5 | `GW_CORS_ALLOWED_ORIGINS` hardcoded literal (not `${VAR}`-substitutable) in `docker-compose.yml`, unlike every other secret/config value; fails closed, not exploitable | LOW / informational | `docker-compose.yml:339` | Open — hygiene/operability, not a vulnerability |
| 6 | Backend services rely solely on Spring Security defaults for headers (no CSP/Referrer-Policy/Permissions-Policy) when reached directly; only reachable in dev compose, not in documented prod topology | INFORMATIONAL | All 6 `SecurityConfig` classes | Open — no action recommended |
| 7 | Local-dev Gradle profile hardcodes weak Postgres creds (`pms_user`/`pms_password`), unused by Docker deployment | VERY LOW | `auth-service/src/main/resources/application-auth-service.yml:11-14` | Open — cosmetic |
| — | Actuator exposure (GAP-13, fb-service local override) | — | `fb-service/src/main/resources/application-fb-service.yml:21-29` | **Confirmed fixed** |
| — | nginx `add_header` inheritance gotcha (GAP-10) | — | `frontend/nginx.conf` | **Confirmed fixed, no recurrence** |
| — | nginx running as root (GAP-14) | — | `frontend/Dockerfile` | **Confirmed fixed** |
| — | Postgres container root (GAP-16) | — | `docker/postgres/Dockerfile` | **Confirmed accepted-risk, correctly documented** |
| — | CORS `allowedOrigins`/`allowCredentials` wildcard combination | — | `config-service/src/main/resources/config/api-gateway.yml:11-26` | **Confirmed not misconfigured** — single origin, Spring fails closed on `*`+credentials anyway |
| — | `server.error.include-stacktrace`/`include-message`/`include-binding-errors` | — | all `application*.yml` | **Confirmed never set to `always` anywhere** — Boot defaults (safe) apply |
| — | Generic 500 catch-all leaking stack trace/SQL/paths | — | `AbstractProblemDetailAdvice.java:264-273` + all 6 `GlobalExceptionHandler` | **Confirmed clean** — static message only, full detail server-log-only |
