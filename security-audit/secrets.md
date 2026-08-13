# Secrets Scan — hotel-pms

Read-only audit. Scope: current working tree (branch `feature/secure-coding-hardening`,
diverges from `main` only by the in-progress exception-handling work already listed in
`git status` — no secret-relevant files among those changes), plus full `git log --all`
history across every local branch (`main`, `feature/secure-coding-hardening`,
`feature/backup-hardening`, `feature/fiscal-export-hardening`, `feature/frontdesk-consolidation`,
`feature/frontend-development`, `pre-secure-coding`, all `origin/*` refs incl. dependabot/copilot
branches) — 670 commits total.

Methods used: `git grep` over the tracked tree for password/secret/API-key/AWS-key/PEM/JWT/
connection-string patterns; `git log --all -p` over `application*.yml`, `docker-compose*.yml`,
`*.env*`, and cert/key file extensions to catch anything committed and later removed; `git
ls-files` to verify `.env` was never tracked; line-by-line review of every `application*.yml`
and `docker-compose*.yml` currently in the tree; review of both `.github/workflows/*.yml` for
literal secrets vs. `${{ secrets.* }}`.

---

## Finding 1 — Hardcoded local-dev DB credentials in 4 currently-tracked `application-<service>.yml` files

**Severity: LOW** (real finding, not a false positive — but low practical exploitability, see below)

**Status: committed and still present** in the working tree today (not history-only).

Four service-specific Spring profile files hardcode a plaintext Postgres password instead of
reading `${POSTGRES_PASSWORD}` (unlike every other secret in the codebase, which is correctly
externalized — see the "no issue" section below):

| File | Line | Username | Password | Target |
|---|---|---|---|---|
| `auth-service/src/main/resources/application-auth-service.yml` | 14 | `pms_user` | `pms_password` | `jdbc:postgresql://localhost:5432/hotel_auth` |
| `billing-service/src/main/resources/application-billing-service.yml` | 5 | `postgres` | `password` | `jdbc:postgresql://localhost:5432/hotel_billing` |
| `fb-service/src/main/resources/application-fb-service.yml` | 7-8 | `root` | `root` | `jdbc:postgresql://localhost:5432/hotel_fb` |
| `guest-service/src/main/resources/application-guest-service.yml` | 7-8 | `postgres` | `password` | `jdbc:postgresql://localhost:5432/hotel_pms_guest` |

**Why it's real but low severity**: `docker-compose.yml` sets `SPRING_PROFILES_ACTIVE=<service>`
for each container (e.g. line 394 `SPRING_PROFILES_ACTIVE=auth-service`), which means Spring Boot
*does* load these `application-{profile}.yml` files at runtime — they are not dead files. However,
`docker-compose.yml` also injects `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` /
`SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}` as OS environment variables for every one of
these services (confirmed for auth-service at docker-compose.yml:397-399, same pattern for the
other three). Spring Boot's property-source precedence puts OS environment variables above
`application-{profile}.yml`, so **in the actual Docker deployment these hardcoded values are
always overridden and never actually connect to anything**. They only take effect if a developer
runs a service directly (`./gradlew :billing-service:bootRun`) against a **local, non-Dockerized**
Postgres on `localhost:5432` — i.e. the credential only "works" against infrastructure the
attacker would already need local access to reach.

Two things still make this worth fixing:
1. It's an inconsistency the recon step (`00-recon.md` §5) missed — it asserted secrets are
   "mai hardcoded" (never hardcoded, verified multiple times) — that claim is not accurate for
   these 4 files. `frontdesk-service` (the consolidated service) has no such file and doesn't have
   this problem, so the pattern is not project-wide, just a leftover in 4 of 8 services.
   `notification-service`, `api-gateway`, `config-service` also have no datasource block at all
   (no DB / config-server-only), so they're unaffected.
2. Defense-in-depth / hygiene: even a "won't be reached in Docker" hardcoded credential is a
   credential committed to source control, and `root`/`root` and `password`/`password` train bad
   habits and could bite someone who spins up a shared/network-exposed local Postgres instead of
   an isolated one.

**Remediation**: replace the hardcoded value with `${POSTGRES_PASSWORD:password}` (env var with
the current literal as a documented local-only fallback default) in all four files, for
consistency with how every other secret in the repo is handled. No rotation needed — these
passwords were never live/reachable outside a throwaway local dev DB, and are not derivable
attack surface against any deployed environment. Not blocking; low-priority cleanup.

---

## Finding 2 — `.gitignore` secret-file coverage: `*.p12` / `*.jks` / `*.pfx` not listed

**Severity: INFORMATIONAL**

`.gitignore` currently covers `.env`, `.env.local`, `.env.*.local`, `*.pem`, `*.key` (lines under
"Secrets & environment"). It does **not** list `*.p12`, `*.jks`, or `*.pfx`. No such files exist
in the tree and none were ever committed in the full 670-commit history across all branches (see
Verified Non-Findings below), so there is no live exposure — this is a preventive gap only, in
case a Java keystore is ever introduced (e.g. for mTLS between services).

**Remediation**: optionally add `*.p12`, `*.jks`, `*.pfx` to the "Secrets & environment" block of
`.gitignore` as a preventive measure. No urgency.

---

## Verified Non-Findings (checked, confirmed clean)

- **`.env` never tracked, ever**: `git ls-files | grep -i env` → only `.env.example` (a template
  with `CHANGE_ME_*` placeholders, no real values, safe to have committed). `git log --all
  --full-history -- .env .env.local '.env.*.local'` → zero commits, on any branch. The secret was
  never in git history to begin with; nothing to purge or rotate on this front.
- **No `.pem`/`.p12`/`.jks`/`.pfx` files ever committed**, on any branch, at any point in history
  (`git log --all --pretty=format:"%H" -- '**/*.pem' '**/*.p12' '**/*.jks' '**/*.pfx'` → empty).
- **No PEM private key blocks** (`-----BEGIN ... PRIVATE KEY-----`) anywhere in the current tree
  or in any historical diff across all 670 commits.
- **No AWS access key IDs** (`AKIA[0-9A-Z]{16}` pattern) anywhere in the current tree or history.
- **No embedded-credential connection strings** (`postgres://user:pass@...`,
  `mongodb://user:pass@...`, etc.) anywhere in the current tree.
- **No hardcoded JWT-looking strings** (`eyJ...eyJ...` pattern) in application source; none found
  at all, so there isn't even a test-fixture case to note.
- **`docker-compose.yml` / `docker-compose.prod.yml`**: every password/secret/API-key value is
  `${ENV_VAR}` interpolation, no literals.
- **`.github/workflows/ci.yml`** and **`.github/workflows/backup-restore-drill.yml`**: every
  secret is sourced via `${{ secrets.* }}` into a step/job `env:` block, then referenced as a
  shell variable — including the S3/pgBackRest credentials in `backup-restore-drill.yml:39-64`,
  which looked suspicious on a first pass (`echo "repo1-s3-key-secret=${S3_SECRET_ACCESS_KEY}"`)
  but trace back cleanly to `env: S3_SECRET_ACCESS_KEY: ${{ secrets.S3_SECRET_ACCESS_KEY }}` at
  line 43 — not a leak, standard GitHub Actions secret-to-file pattern for a config file consumed
  only within the same job.
- **No `frontend/.env*` files exist**, tracked or untracked.
- **`config-service/src/main/resources/config/*.yml`** (the Spring Cloud Config values actually
  served to every service in Docker): every secret (`JWT_SECRET`, `INTERNAL_HMAC_SECRET`,
  `CONFIG_SERVER_PASSWORD`) is `${ENV_VAR}` interpolation, no literals.
- **Known seed admin credential** (`auth-service/src/main/resources/data.sql:1-12`): still present,
  unchanged — static bcrypt hash `{bcrypt}$2a$10$8aXe/PIDoC/tOecWVAMxsu57InT1n4F4Uq2ObRGB4W8DhGowDrbMi`
  for `admin`/`password`/`admin@hotel.com`, documented in `CLAUDE.md` under "Default Credentials"
  as the intentional dev seed. Confirmed still the known/already-triaged false positive — not
  re-reported as a new finding, per task instructions.
- **Test-fixture HMAC secret** `test-hmac-secret-minimum-32-characters-for-unit-tests` — present
  in 5 `*/src/test/resources/application.yml` files (billing, fb, frontdesk, guest,
  notification-service). Obviously a fake placeholder (self-describing name, used only to satisfy
  a minimum-length validation in unit tests), not a real secret — noted, not treated as a finding.
- **`docs/Enterprise-Hotel-PMS.postman_collection.json`**: no hardcoded password/secret/token/
  apiKey values.
- **`docker/` directory** (Postgres init scripts, pgBackRest config) and
  `setup-hmac-secret.sh`/`.ps1`: no hardcoded secret-like literals — the setup scripts generate
  random values (`openssl rand` / equivalent), they don't embed one.
- **SQL injection / command injection** were out of scope for this task but were incidentally
  re-confirmed clean while reading migrations (parameterized JPA queries only, no
  `Runtime.exec`/`ProcessBuilder` in the repo) — consistent with `00-recon.md` §4, not re-audited
  in depth here.
- **Alloggiati per-hotel credentials** (`frontdesk-service/src/main/resources/db/migration/
  V4__add_alloggiati_credentials.sql`): columns are `alloggiati_password_encrypted` /
  `alloggiati_ws_key_encrypted` (AES-GCM encrypted at rest per the migration's own comment,
  encryption key from `${ALLOGGIATI_CREDENTIALS_ENCRYPTION_KEY}`), only `alloggiati_username` is
  plaintext and that's a portal login name, not a secret. No issue.
- **git history for config files broadly**: `git log --all -p` over every `application*.yml` and
  `application-*.yml` that ever existed (including the pre-consolidation
  `inventory-service`/`reservation-service`/`stay-service` files, now deleted) surfaced only the
  4 hardcoded local-dev DB passwords already covered in Finding 1 (all still present today, so
  categorized as "committed and still present," not a history-only leak) plus the test HMAC
  fixture. No other secret pattern matched across the full history sweep.

---

## Summary Table (severity-sorted)

| # | Finding | Category | Severity | Rotation needed? |
|---|---|---|---|---|
| 1 | Hardcoded local-dev Postgres passwords in `application-auth-service.yml`, `application-billing-service.yml`, `application-fb-service.yml`, `application-guest-service.yml` | Committed and still present | LOW | No — never live/reachable outside a throwaway local DB; fix by switching to `${POSTGRES_PASSWORD:password}` for consistency, no exposure to remediate |
| 2 | `.gitignore` missing `*.p12`/`*.jks`/`*.pfx` patterns | Preventive gap (nothing ever exposed) | INFORMATIONAL | No — add patterns proactively, no incident |
| — | Seed admin bcrypt hash in `data.sql` | Known/already-triaged, confirmed still present | N/A (not a finding) | No — documented dev seed |
| — | `.env` never committed, ever | Verified clean | N/A | No |
| — | No cert/key files ever committed, ever | Verified clean | N/A | No |
| — | docker-compose / config-service yml / CI workflows | Verified clean — all secrets via env var / `${{ secrets.* }}` | N/A | No |
