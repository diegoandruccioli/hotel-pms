# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| `main` (latest) | ✅ Active |
| `pre-secure-coding` | ❌ Baseline snapshot only — not patched |

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Report findings privately to: **diegoandruccioli@gmail.com**

Include in your report:
- Affected component(s) and version / commit SHA
- Description of the vulnerability and its potential impact
- Step-by-step reproduction instructions
- Any proof-of-concept code (if applicable)

### What to expect

| Stage | Timeframe |
|---|---|
| Acknowledgement | Within 72 hours |
| Severity assessment | Within 7 days |
| Fix or mitigation | Depends on severity — critical: best effort ASAP |
| Public disclosure | After fix is merged, coordinated with reporter |

We follow responsible disclosure: once a fix is available, we will publish a GitHub Security Advisory crediting the reporter (unless anonymity is requested).

## Scope

### In scope

- Authentication and session handling (`auth-service`, `api-gateway` JWT/CSRF logic)
- Authorisation bypass / privilege escalation (RBAC, `@PreAuthorize`, HMAC validation)
- Multi-tenant data isolation failures (`hotel_id` enforcement)
- Injection vulnerabilities (SQL, OGNL, SpEL, header injection)
- Sensitive data exposure (PII, credentials in logs or responses)
- Insecure direct object references (IDOR on guest, reservation, stay, invoice endpoints)

### Out of scope

- Vulnerabilities in third-party Docker base images already tracked in `report-secure-coding.tex` (e.g. Grafana 11.5.0 Alpine CVEs — documented accepted risk)
- Denial-of-service attacks requiring sustained traffic above configured rate limits
- Social engineering
- Physical security
- Issues reproducible only with `ADMIN` credentials in a development environment

## Known accepted risks

See [`THREAT_MODEL.md`](THREAT_MODEL.md) for the full threat model and mitigation table.
Notable accepted residual risks:

| ID | Description | Mitigation |
|---|---|---|
| CVE-2026-42577 | Netty epoll DoS — fix requires Netty 4.2.x, incompatible with current Spring Boot 3.5.x BOM | Network-layer isolation; JDK NIO transport active |
| Grafana CVEs | Grafana 11.5.0 Alpine layer (OpenSSL, musl, zlib) | Internal-only tool, isolated Docker network, no guest PII |

## Security contacts

| Role | Contact |
|---|---|
| Primary maintainer | diegoandruccioli@gmail.com |
