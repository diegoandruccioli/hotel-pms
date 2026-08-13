# Authentication & JWT Audit — hotel-pms

Scope: `auth-service` (JWT issuance, login/register/refresh/logout, lockout, password
hashing) and `api-gateway` (JWT validation for browser traffic), plus the frontend token
handling in `frontend/src/services/api.ts`. Read-only audit — no files modified besides
this one. Cross-checked against `security-audit/00-recon.md` and `THREAT_MODEL.md`.

---

## CRITICAL findings

### F1 — Unauthenticated privilege escalation via `POST /api/v1/auth/register` (mass assignment of `role` and `hotelId`)

**Files**:
- `auth-service/src/main/java/com/hotelpms/auth/dto/RegisterRequest.java:20-27`
- `auth-service/src/main/java/com/hotelpms/auth/mapper/UserAccountMapper.java:12-25`
- `auth-service/src/main/java/com/hotelpms/auth/service/AuthServiceImpl.java:57-83`
- `auth-service/src/main/java/com/hotelpms/auth/controller/AuthController.java:71-84`

`RegisterRequest` is:

```java
public record RegisterRequest(
        @NotBlank String username,
        @NotBlank @Pattern(...) String password,
        @NotBlank @Email String email,
        @NotNull Role role,
        @NotNull UUID hotelId) {
}
```

`role` and `hotelId` are client-supplied, mandatory fields. `UserAccountMapper.toEntity()`
maps the whole DTO onto the `UserAccount` entity with only `passwordHash` excluded — `role`
and `hotelId` pass straight through. `AuthServiceImpl.register()` performs zero
authorization check on either field before persisting the user and issuing a token pair.
`POST /api/v1/auth/register` is `permitAll()` in `SecurityConfig.java:107-111` and is not
routed through the gateway's `AuthenticationFilter` (confirmed in `00-recon.md` §3 and in
`api-gateway.yml`'s `auth-service` route, which carries no `AuthenticationFilter`).

**Attack scenario**: a fully unauthenticated attacker calls:

```
POST /api/v1/auth/register
{
  "username": "attacker",
  "password": "SuperSecretP@ss1122!!",
  "email": "attacker@evil.com",
  "role": "ADMIN",
  "hotelId": "<any-existing-hotel-uuid>"
}
```

and immediately receives `jwt`/`refresh_token` cookies for a brand-new **ADMIN** account
scoped to **any hotel tenant they choose** — including a tenant they were never given
access to. This is a complete bypass of the entire RBAC model documented in
`00-recon.md` §3 (gateway `OPERATIONAL_ROLES`, `@PreAuthorize` per-controller, HMAC
internal auth all become irrelevant once the attacker can self-mint an ADMIN JWT) and of
the multi-tenant isolation model (`hotelId` is attacker-chosen, so this is also a
cross-tenant breach, not merely local privilege escalation).

`hotelId` values are UUIDs but are not a secret — they appear in URLs, are visible to any
existing low-privileged user of a hotel (RECEPTIONIST tokens carry `hotelId` in the JWT
payload, which is base64, not encrypted), and could plausibly be enumerated/guessed in a
real deployment, making cross-tenant ADMIN takeover practical, not just theoretical.

**Remediation**:
- Remove `role` and `hotelId` from `RegisterRequest` entirely. Public self-registration
  should always create a fixed, minimally-privileged role (or none — `GUEST`/pending) for
  a hotel resolved server-side (e.g. from an invite token, a tenant-specific registration
  link, or disabled outright in favor of the existing ADMIN-only
  `UserManagementController.create` at `POST /api/v1/auth/users`, which is already
  correctly gated `@PreAuthorize` ADMIN/OWNER per `00-recon.md` §7).
- If self-registration must remain public, require an out-of-band invite/approval step
  before the account becomes usable, and never accept `role`/`hotelId` as client input —
  derive both server-side.
- Add a regression test asserting that `POST /api/v1/auth/register` with
  `"role":"ADMIN"` in the body never results in a user record with `role=ADMIN`.

This is not covered by `THREAT_MODEL.md`'s `T-GST-04` (mass assignment, already marked
✅ RISOLTO — that entry is about guest-service DTOs, unrelated to this endpoint) — it
appears to be a genuinely new, unaddressed gap.

---

## HIGH findings

### F2 — Per-IP rate limiting (login/register/refresh) is trivially bypassable: leftmost `X-Forwarded-For` entry is trusted, and it is attacker-controlled

**Files**:
- `api-gateway/src/main/java/com/hotelpms/gateway/config/RateLimiterConfig.java:51-64` (`remoteAddrKeyResolver`), also affects the fallback branch of `userKeyResolver` at `:85-91`
- `config-service/src/main/resources/config/api-gateway.yml:42-52` (`auth-service` route — 5 req/s, burst 10, keyed by `remoteAddrKeyResolver`)
- `frontend/nginx.conf:72` (`proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`)
- `docker-compose.prod.yml:15-18` (api-gateway `:8080` is one of only two ports intentionally exposed to the host in the hardened prod compose override, alongside frontend `:80`)

```java
final String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
if (forwarded != null && !forwarded.isBlank()) {
    return Mono.just(forwarded.split(",")[0].trim());   // <-- leftmost = client-supplied
}
```

Two independent ways to defeat this:

1. **Direct-to-gateway path**: `docker-compose.prod.yml` deliberately publishes
   `api-gateway:8080` on the host (documented as intentional: "the two meant to be
   reachable from outside the host: frontend (:80) and api-gateway (:8080)"). An attacker
   hitting the gateway directly controls the entire `X-Forwarded-For` header with no
   trusted proxy in front to correct it. Rotating the header value on every request
   (`X-Forwarded-For: 1.2.3.4`, `X-Forwarded-For: 1.2.3.5`, ...) gives an independent
   rate-limit bucket per request, i.e. no effective rate limiting at all.
2. **Even through nginx (browser path)**: nginx uses `$proxy_add_x_forwarded_for`, which
   *appends* the real client address to any `X-Forwarded-For` the client already sent
   (`nginx.conf:72`) rather than overwriting it. So a client-supplied
   `X-Forwarded-For: 1.2.3.4` arrives at the gateway as `X-Forwarded-For: 1.2.3.4,
   <real client IP>`. The resolver reads `split(",")[0]` — the **leftmost**, i.e. the
   attacker-supplied value — not the rightmost hop that nginx actually appended. The
   trustworthy value is discarded in favor of the spoofed one.

**Attack scenario**: the `auth-service` gateway route (`api-gateway.yml:42-52`) rate-limits
`/api/v1/auth/**` (login, register, refresh) to 5 req/s / burst 10 per resolved key. By
sending a fresh, unique `X-Forwarded-For` value on every login attempt, an attacker gets an
effectively unlimited request rate against `/api/v1/auth/login` — credential stuffing
across many usernames at high speed, or hammering `/api/v1/auth/register` to mass-create
accounts (compounding with F1), none of it throttled. `THREAT_MODEL.md`'s `T-GW-02`
("Rate limiting insufficiente o bypassabile per certi endpoint") is marked ✅ RISOLTO, but
the code as it stands today reintroduces exactly that bypass.

Note this does **not** bypass the per-account lockout in `AuthServiceImpl` (that is keyed
by username, not IP — see F3 for why that is itself a problem), but it does remove the one
layer that was supposed to slow down distributed guessing across many different accounts,
and removes the throttle on `/register` entirely (relevant to F1 exploitation).

**Remediation**:
- Do not trust client-supplied `X-Forwarded-For` at all unless the request arrived from a
  known, trusted proxy hop. At minimum, take the **rightmost** untrusted-adjacent entry
  (the one the nearest trusted proxy actually appended), never the leftmost.
- Prefer Spring Cloud Gateway's `ForwardedHeaderFilter`/`X-Forwarded-*` trust
  configuration (`server.forward-headers-strategy`) or resolve via `RemoteAddress`
  populated by a `ProxyProtocol`/trusted-hop count, rather than hand-parsing the header.
  If nginx is the only legitimate entry point, prefer `X-Real-IP` (`nginx.conf:71`,
  `$remote_addr`, not attacker-influenceable) over `X-Forwarded-For`.
  entry.
- If `api-gateway:8080` is genuinely meant to be reachable directly (bypassing nginx) in
  production, either remove that direct exposure (route everything through nginx, which at
  least appends a truthful hop) or make the gateway itself the trust boundary and never
  honor client-supplied `X-Forwarded-For` on requests that didn't arrive from a configured
  set of trusted proxy IPs.

### F3 — Account lockout is an unauthenticated, cheap denial-of-service primitive against known usernames (e.g. `admin`)

**File**: `auth-service/src/main/java/com/hotelpms/auth/service/AuthServiceImpl.java:95-124`

```java
private static final int MAX_FAILED_ATTEMPTS = 5;
private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
...
if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
    final int newAttempts = user.getFailedAttempts() + 1;
    final Instant lockedUntil = newAttempts >= MAX_FAILED_ATTEMPTS
            ? Instant.now().plus(LOCKOUT_DURATION) : null;
    userRepository.updateFailedAttempts(user.getUsername(), newAttempts, lockedUntil);
    ...
    throw new BadCredentialsException(INVALID_CREDENTIALS);
}
```

Lockout is keyed purely by the `username` string supplied in the request body — no binding
to source IP, device, or session. Combined with F2 (per-IP rate limit bypassable) there is
no meaningful throttle on an attacker repeatedly sending 5 wrong-password requests for a
single target username every 15 minutes, indefinitely.

**Attack scenario**: the project's own documented default credential is
`admin`/`password` (`CLAUDE.md` "Default Credentials", seeded in
`auth-service/src/main/resources/data.sql`) — i.e. the username `admin` is known/likely to
exist in any deployment following this pattern, or is simply the most obvious guess
regardless. An anonymous attacker sends 5 requests with `username=admin` and any wrong
password; the real `admin` account is locked for 15 minutes. Repeating this every 15
minutes locks the primary administrative account indefinitely, at negligible cost
(5 cheap HTTP requests per cycle), with **zero authentication required** and no CAPTCHA or
other proof-of-work gate in front of it. This is a persistent, low-effort availability
attack against exactly the account an operator would need during an incident.

**Remediation**:
- Add a per-source-IP (correctly resolved, see F2) counter in addition to the per-account
  one, and/or a CAPTCHA/proof-of-work challenge after N failed attempts from the same
  source regardless of target username.
- Consider not fully locking privileged accounts (ADMIN/OWNER) out of *all* access —
  e.g. exponential backoff instead of a hard 15-minute lock, or allow a secondary
  verified channel (email link, TOTP) to bypass the lock for the account owner.
- At minimum, alert/log when a lockout is triggered against an ADMIN/OWNER account so
  operators can distinguish "targeted DoS in progress" from routine noise (structured
  logging already exists per `T-AUTH-05`, so wiring an alert on this specific log line is
  low effort).

---

## MEDIUM findings

### F4 — Account-lockout response creates a user-enumeration oracle (partial regression of T-AUTH-01)

**Files**:
- `auth-service/src/main/java/com/hotelpms/auth/exception/GlobalExceptionHandler.java:25-32` (`AccountLockedException` → HTTP 429, title "Account Temporarily Locked", detail `ACCOUNT_TEMPORARILY_LOCKED`)
- `auth-service/src/main/java/com/hotelpms/auth/exception/GlobalExceptionHandler.java:40-47` (`BadCredentialsException` → HTTP 401, detail `INVALID_CREDENTIALS`)
- `auth-service/src/main/java/com/hotelpms/auth/service/AuthServiceImpl.java:98-108`

`THREAT_MODEL.md` marks `T-AUTH-01` ("uniform error messages, no user enumeration") as
✅ RISOLTO, and indeed a bad password and a nonexistent username both currently produce
identical `401 INVALID_CREDENTIALS`. However, the lockout mechanism (T-AUTH-02)
reintroduces a distinguishable third state: a nonexistent username can **never** reach the
lockout branch (the `orElseThrow` on `findByUsername` fires before any attempt counting),
so it will always return `401`. A real username will, after 5 wrong-password attempts,
start returning `429 TOO_MANY_REQUESTS / ACCOUNT_TEMPORARILY_LOCKED` instead.

**Attack scenario**: for any candidate username, send 5 requests with an obviously wrong
password. A `429` response on attempt 5/6 confirms the account exists; a persistent `401`
confirms it does not. Cost: 5-6 cheap requests per candidate, enumerable at scale (and,
per F2, without even being IP-rate-limited).

**Remediation**: make the locked-account response indistinguishable from the
invalid-credentials response for unauthenticated callers (same status code, same generic
body), while still enforcing the lock server-side. If a distinct locked-state UX is
wanted, only reveal it to a caller who has already proven they know the correct current
password (which defeats the purpose for an attacker), or add a matching artificial delay /
generic 401 for the not-found path so timing/status cannot distinguish the two either (see
F5).

### F5 — Timing side-channel enables login user enumeration independent of response body

**File**: `auth-service/src/main/java/com/hotelpms/auth/service/AuthServiceImpl.java:97-124`

```java
final UserAccount user = userRepository.findByUsername(request.username())
        .orElseThrow(() -> { ...; return new BadCredentialsException(INVALID_CREDENTIALS); });
...
if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) { ... }
```

For a nonexistent username, the request fails fast on the DB lookup — no password hashing
occurs. For an existing username, `passwordEncoder.matches()` runs a full Argon2id (or
legacy BCrypt) verification, which is deliberately expensive (that's the point of a
memory-hard KDF: ARGON2_MEMORY=19MiB, ARGON2_ITERATIONS=2 in `SecurityConfig.java:57-59`).
The resulting response-time gap (sub-millisecond DB miss vs. tens-of-milliseconds Argon2id
verification) is a classic, measurable timing oracle for username enumeration, independent
of and in addition to F4.

**Remediation**: perform a dummy password-hash verification (against a fixed, non-secret
hash) on the not-found path so both branches take comparable time, e.g.:
```java
.orElseGet(() -> { passwordEncoder.matches(request.password(), DUMMY_HASH); throw ...; })
```
(with the dummy match's result discarded). This is a standard mitigation and low effort
given Argon2id is already wired in.

---

## LOW / INFORMATIONAL findings

### F6 — Access tokens are not server-side revocable; logout/password-change leave a stolen access token valid for up to 15 minutes

**Files**:
- `api-gateway/src/main/java/com/hotelpms/gateway/filter/AuthenticationFilter.java:160-184` (no `tv`/token-version check, only `role`/`hotelId`/`mustChangePassword` are read from the access-token claims)
- `auth-service/src/main/java/com/hotelpms/auth/service/AuthServiceImpl.java:161-193` (the `tv` check exists only in `refresh()`)
- `auth-service/src/main/java/com/hotelpms/auth/service/impl/RefreshTokenServiceImpl.java` (blacklist only covers refresh JTIs, never access tokens)

Refresh tokens are correctly single-use/rotated and blacklisted on logout/rotation, and the
`tv` (token version) claim correctly invalidates refresh tokens after a password change.
But the **access token** itself carries no revocation check anywhere: the gateway's
`AuthenticationFilter` never consults the blacklist or the `tv` claim for access tokens, so
a captured access token (e.g. via XSS, despite the project's `dangerouslySetInnerHTML`
lockdown, or via a compromised machine) remains fully usable for the rest of its 15-minute
lifetime even after the legitimate user explicitly logs out or changes their password.

This is a common and generally accepted tradeoff for stateless JWT access tokens (checking
a blacklist on every gateway request would reintroduce a stateful lookup on the hot path),
and the 15-minute bound limits the blast radius, but it does not appear to be explicitly
called out as a residual risk anywhere in `THREAT_MODEL.md`'s `T-AUTH-04`/`T-AUTH-04
residuo` entries, which read as if revocation is fully solved. Recommend documenting the
15-minute worst-case exposure window explicitly, or — if a tighter bound is required for
compliance reasons — adding a lightweight access-token version/blacklist check at the
gateway (e.g. a Redis `EXISTS` per request, or shortening `jwt.expiration` further).

### F7 — `JwtService.isTokenValid()` performs a tautological self-comparison (dead code, not exploitable)

**File**: `auth-service/src/main/java/com/hotelpms/auth/service/JwtService.java:128-131`

```java
public boolean isTokenValid(final String token, final String username) {
    final String tokenUsername = extractUsername(token);
    return tokenUsername.equals(username) && !isTokenExpired(token);
}
```

Both call sites (`AuthController.java:204-206` in `changePassword`, and
`AuthController.java:240-243` in `getMe`) derive `username` by calling
`jwtService.extractUsername(token)` on the *same* token immediately before calling
`isTokenValid(token, username)` — so `tokenUsername.equals(username)` is true by
construction. The `!isTokenExpired(token)` half is also unreachable in practice:
`extractUsername()` (and any other `extractClaim` call) already runs
`Jwts.parserBuilder()...parseClaimsJws(token)`, which throws `ExpiredJwtException` for an
expired token before `isTokenValid` is ever reached — the `catch (JwtException |
IllegalArgumentException)` blocks around these call sites are what actually enforce
expiry, not this method.

Not exploitable as-is (no code path relies on this check catching anything it currently
fails to catch), but it is misleading — a future call site could pass an
independently-sourced `username` expecting this method to actually validate token
ownership, and it would still trivially "work" only because both current callers happen to
source `username` from the token itself. Recommend removing the method (or making it take
only `token` and rely purely on successful parsing + explicit `tv`/expiry checks) to avoid
that trap.

---

## Verified SECURE (no finding)

For completeness, per the specific checks requested:

1. **Algorithm confusion / `alg: none`** — **not exploitable**. Both `auth-service`
   (`JwtService.java:253-259`) and `api-gateway` (`JwtUtil.java:55-61`) use JJWT `0.11.5`
   (`auth-service/build.gradle.kts:36`, `api-gateway/build.gradle.kts:26`) via
   `Jwts.parserBuilder().setSigningKey(Key).build().parseClaimsJws(token)`, where `Key` is
   an HMAC `SecretKey` from `Keys.hmacShaKeyFor(...)`. JJWT 0.10+'s `parserBuilder` API
   binds the parser to the algorithm family implied by the key type supplied — a token
   whose header claims `alg: none` or `alg: RS256` is rejected
   (`UnsupportedJwtException`/`SignatureException`) rather than accepted with the HMAC key
   reinterpreted as an RSA public key or the signature check skipped. No legacy/deprecated
   `Jwts.parser().setSigningKey(String)` API is used anywhere in the codebase.
2. **Secret strength/source** — **no unsafe fallback**. `jwt.secret: "${JWT_SECRET}"` in
   both `config-service/src/main/resources/config/auth-service.yml:5` and
   `config-service/src/main/resources/config/api-gateway.yml:180` has **no** `:default`
   suffix, so Spring fails fast at startup if `JWT_SECRET` is unset. `JwtUtil.java:31-46`
   additionally throws explicitly on a null/blank secret or on a literal unresolved
   `${JWT_SECRET}` placeholder. `setup-hmac-secret.ps1` generates `JWT_SECRET` as
   Base64-encoded 48 cryptographically-random bytes (384 bits) — comfortably above the
   256-bit minimum recommended for HS256.
3. **Expiration & refresh** — **sound**. 15 min access / 7 day refresh
   (`auth-service.yml:6-7`). Refresh tokens are single-use and rotated: each successful
   `refresh()` blacklists the consumed JTI in Redis before issuing a new pair
   (`AuthServiceImpl.java:195-197`, `RefreshTokenServiceImpl.java:37-43`), logout does the
   same (`AuthServiceImpl.java:269-281`), and a `tv` (token version) claim additionally
   invalidates all outstanding refresh tokens on password change
   (`AuthServiceImpl.java:182-193`, `:243-249`).
4. **Token storage** — **sound**. `jwt` and `refresh_token` cookies are both
   `httpOnly(true)`, `secure(true)`, `sameSite("Strict")`
   (`AuthController.java:270-281`); `refresh_token` is additionally path-scoped to
   `/api/v1/auth`. `frontend/src/services/api.ts` confirmed to never touch
   `localStorage`/`sessionStorage`, uses `withCredentials: true`, and the only
   non-httpOnly cookie (`csrf_token`) intentionally carries no bearer-token value by
   design (double-submit CSRF pattern).
5. **Password policy & hashing** — **adequate**. Argon2id 19 MiB / 2 iterations /
   parallelism 1 (`SecurityConfig.java:57-59`) matches OWASP's documented minimum
   baseline. Complexity enforced via `@Pattern` (≥16 chars, ≥2 uppercase, ≥2 digits, ≥2
   special) on both `RegisterRequest` and `ChangePasswordRequest`. The BCrypt legacy
   encoder is used only for verifying pre-existing hashes during lazy-rehash
   (`AuthServiceImpl.java:127-132`) — `DelegatingPasswordEncoder("argon2", ...)` always
   encodes *new* hashes with Argon2id, so there is no downgrade path.
6. **Brute-force threshold/duration** — present and functioning as designed (5 attempts,
   15 min, reset on success) — see F3 for why the *design* itself is exploitable as a DoS
   vector against known usernames, and F2 for why the IP-based layer meant to slow this
   down is bypassable.
7. **User enumeration via response body** — the login response body/status pairing for
   "user not found" vs "wrong password" is identical (both `401 INVALID_CREDENTIALS`,
   `BadCredentialsException` in both `AuthServiceImpl.java:99-102` and `:110-124`) — see
   F4 and F5 for the two *other* channels (lockout status code, timing) that still leak
   this.

---

## Summary table (severity-sorted)

| ID | Severity | Finding | File(s) |
|----|----------|---------|---------|
| F1 | CRITICAL | Public `/register` accepts client-supplied `role` + `hotelId` — unauthenticated ADMIN privilege escalation, cross-tenant | `RegisterRequest.java`, `UserAccountMapper.java`, `AuthServiceImpl.java`, `AuthController.java` |
| F2 | HIGH | Rate-limiter trusts leftmost (attacker-controlled) `X-Forwarded-For`; gateway `:8080` also directly internet-exposed in prod | `RateLimiterConfig.java`, `api-gateway.yml`, `nginx.conf`, `docker-compose.prod.yml` |
| F3 | HIGH | Username-keyed account lockout is an unauthenticated DoS primitive against known accounts (e.g. `admin`) | `AuthServiceImpl.java` |
| F4 | MEDIUM | Lockout response (429 vs 401) reintroduces user enumeration, partial regression of T-AUTH-01 | `GlobalExceptionHandler.java`, `AuthServiceImpl.java` |
| F5 | MEDIUM | Timing side-channel (DB-miss vs Argon2id) enables login user enumeration | `AuthServiceImpl.java` |
| F6 | LOW | Access tokens have no server-side revocation; logout/password-change leave up to 15 min of validity on a stolen access token | `AuthenticationFilter.java`, `AuthServiceImpl.java`, `RefreshTokenServiceImpl.java` |
| F7 | INFO | `JwtService.isTokenValid()` is a tautological/dead check at both current call sites | `JwtService.java`, `AuthController.java` |
