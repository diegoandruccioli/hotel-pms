# 🏨 Hotel PMS — Report Completo | Parte 1

**Data:** 2026-05-11 | **Branch:** `main` (post-merge security hardening)

---

## 1. Executive Summary

| Dimensione | Voto /10 | Giudizio |
|---|---|---|
| Architettura | 9.0 | Microservizi reali con DB separati, saga pattern, circuit breaker |
| Sicurezza | 9.5 | STRIDE 40+ mitigazioni, HMAC inter-service, OWASP 8/10 |
| Qualità Codice | 8.0 | PMD zero-warning, TS strict, zero `any` — coverage non enforced |
| Completezza Funzionale | 7.5 | Tutti i flussi core — mancano channel manager, revenue mgmt |
| UX / Frontend | 7.5 | M3 design system, i18n, a11y WCAG 2.2 AA — non mobile-first |
| Documentazione | 9.0 | 15+ documenti tecnici, threat model, user manual |
| Operatività / DevOps | 7.0 | CI 5-job, Docker 703 righe — no K8s, no backup, no alerting |
| Compliance Legale | 8.5 | GDPR Art. 17/20, TULPS Art. 109, Alloggiati SOAP nativo |
| **MEDIA PESATA** | **8.2** | **Pilot-ready, pre-production** |

---

## 2. Valutazione Dettagliata

### 2.1 Architettura — 9.0/10

- 9 microservizi con bounded context (auth, guest, inventory, reservation, stay, billing, F&B, config, gateway)
- Database-per-service reale (9 PostgreSQL separati via Flyway)
- API Gateway reactive con JWT validation, rate limiting Redis, CORS, security headers
- Saga pattern con rollback reale su check-in
- Circuit breaker Resilience4j su ogni Feign client
- 5 reti Docker isolate (DMZ, gateway, backend, db, observability)
- `@Version` optimistic locking su Reservation

**Gap:** Config-service SPOF, nessun API contract test, N+1 latente su Invoice.charges

### 2.2 Sicurezza — 9.5/10

| Layer | Misura |
|---|---|
| Autenticazione | JWT httpOnly + SameSite=Strict |
| Token rotation | Refresh con jti UUID + Redis blacklist |
| Inter-service | HMAC-SHA256 constant-time compare |
| CSRF | Double Submit Cookie |
| Headers | HSTS, CSP, X-Frame-Options DENY |
| Rate limiting | Redis Token Bucket per-user + per-IP |
| Multi-tenant | `hotel_id NOT NULL` + JWT claim |
| GDPR | Hard-anonymize + guardia TULPS 5 anni |
| CVE scanning | Trivy CI su 10 immagini + 3 third-party |
| RBAC | Gateway + `@PreAuthorize` (3 ruoli) |

**OWASP Top 10:** A01 ✅ A02 ✅ A03 ✅ A04 ✅ A05 ✅ A06 ✅ A07 ✅ A09 ✅

### 2.3 Qualità Codice — 8.0/10

- ✅ PMD zero-warning enforced (build fallisce)
- ✅ ESLint zero-warning policy
- ✅ TypeScript `strict: true`, zero `any`
- ✅ Entity/DTO separation con MapStruct + Record
- ✅ Layer: Controller → Service (Interface+Impl) → Repository → Entity
- ⚠️ Coverage ~60% backend, ~71% frontend — nessun threshold enforced
- ❌ Zero integration test (Testcontainers)

### 2.4 UX Frontend — 7.5/10

**Punti di forza:** M3 component library, dark mode, WCAG 2.2 AA (contrast 7:1, focus rings 3:1), `focus-trap-react`, `vitest-axe`, React.lazy + Suspense, Zustand 5 stores, Inter + Outfit fonts, skeleton loading

**Gap:** Desktop-first (non mobile-first), no ErrorBoundary, no wizard onboarding, no WebSocket/real-time, currency hardcoded USD in Dashboard

### 2.5 Compliance — 8.5/10

- ✅ TULPS Art. 109: SOAP two-step, export .txt 168-char, auto-send toggle
- ✅ GDPR Art. 17: Hard-anonymize con guardia legale
- ✅ GDPR Art. 20: Data export strutturato
- ✅ PII isolation: `hotel_id` + no cross-tenant leak
- ⏳ Collaudo SOAP reale PS: richiede VPN Questura

---

## 3. Stato Implementazioni (dai file .md)

### 3.1 Feature Completate ✅

| Feature | Tipo | Fonte |
|---|---|---|
| F&B → Conto Camera (saga) | Backend + Frontend | IMPLEMENTATION_PLAN F1 |
| Alloggiati auto-send SOAP | Backend + Frontend | IMPLEMENTATION_PLAN F2 |
| Billing: registrazione pagamento | Frontend | IMPLEMENTATION_PLAN F3 |
| Saga Pattern check-in con rollback | Backend | IMPLEMENTATION_PLAN F4 |
| Archivio JSON alloggiati | Backend + Frontend | IMPLEMENTATION_PLAN F5 |
| Restaurant: form creazione ordine | Backend + Frontend | IMPLEMENTATION_PLAN F9 |
| Restaurant: dettaglio ordine | Frontend | IMPLEMENTATION_PLAN F10 |
| Fix valuta USD→EUR | Frontend | IMPLEMENTATION_PLAN F11 |
| Delete ospite con guardia GDPR | Frontend | IMPLEMENTATION_PLAN F12 |
| Annulla prenotazione | Frontend + Service | IMPLEMENTATION_PLAN F13 |
| 20 gap funzionali (B1-1 → B4-4) | Tutti | GAP_ANALYSIS.md (tutti ✅) |

### 3.2 Task Pendenti Documentati nei .md

#### 🔴 Critici (bloccanti produzione) — ~1 settimana

| # | Task | Fonte | Sforzo |
|---|---|---|---|
| 1 | Merge branch security → main | IMPL_PLAN F6 | 1 giorno |
| 2 | JaCoCo thresholds enforced (80%) | FINAL_AUDIT §7.5 | 2 ore |
| 3 | Vitest coverage thresholds (85%) | FINAL_AUDIT §7.6 | 15 min |
| 4 | Backup PostgreSQL schedulato | FINAL_AUDIT §7.13 | 4 ore |
| 5 | ErrorBoundary React a livello route | FINAL_AUDIT §7.7 | 1 ora |

#### 🟡 Alta priorità — ~3-4 settimane

| # | Task | Fonte | Sforzo |
|---|---|---|---|
| 6 | Testcontainers (stay + billing) | FINAL_AUDIT §7.9 | 3-5 giorni |
| 7 | GIN index + pg_trgm su Guest | DECISIONS §1.2 | 4 ore |
| 8 | Prometheus alert rules | FINAL_AUDIT §7.14 | 1-2 giorni |
| 9 | Operations Runbook | FINAL_AUDIT §6 | 1 giorno |
| 10 | CONTRIBUTING.md | FINAL_AUDIT §6 | 4 ore |
| 11 | Secrets → Vault/KMS | FINAL_AUDIT §7.15 | 2-3 giorni |
| 12 | Kubernetes manifests | FINAL_AUDIT §7.12 | 1-2 settimane |

#### 🟠 Media priorità — miglioramenti infra

| # | Task | Fonte | Sforzo |
|---|---|---|---|
| 13 | Zipkin → Grafana Tempo + OTel | DECISIONS §7.2 | 3-5 giorni |
| 14 | TailwindCSS 3 → 4 (Oxide) | DECISIONS §7.1 | 2-3 giorni |
| 15 | Spring Cloud Contract | FINAL_AUDIT §7.19 | 1-2 settimane |

---

## 4. Confronto Competitor — Matrice Completa

| Feature | **Tuo PMS** | **Opera Cloud** | **Mews** | **Cloudbeds** | **Little Hotelier** | **RoomRaccoon** |
|---|---|---|---|---|---|---|
| **Prezzo** | €0 self / €149-199 SaaS | €€€€€ | €300+/m | €300-600/m | $109+/m | Custom |
| Microservizi reali | ✅ 9 servizi | ✅ | ✅ | ❓ | ❌ | ❌ |
| Check-in/out + Walk-in | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Prenotazioni + Overbooking prevention | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Billing / Fatturazione | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| F&B / POS integrato | ✅ | ✅ (MICROS) | ✅ (plugin) | ⚠️ 3rd party | ❌ | ❌ |
| Housekeeping | ✅ Web | ✅ Mobile | ✅ Mobile | ✅ | ⚠️ Basic | ⚠️ |
| Alloggiati PS nativo | ✅ SOAP | ⚠️ Plugin | ⚠️ Plugin | ⚠️ Plugin | ⚠️ Plugin | ⚠️ Plugin |
| GDPR nativo | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| i18n | ✅ EN/IT | ✅ 20+ | ✅ 30+ | ✅ 20+ | ✅ 15+ | ✅ 10+ |
| Dark mode | ✅ | ❓ | ✅ | ❌ | ❌ | ❌ |
| WCAG 2.2 AA | ✅ 7:1 contrast | ❓ | ❓ | ❓ | ❓ | ❓ |
| Distributed tracing | ✅ Zipkin | ❓ Interno | ❓ | ❌ | ❌ | ❌ |
| Threat model pubblico | ✅ STRIDE | ❌ | ❌ | ❌ | ❌ | ❌ |
| CVE scan CI | ✅ Trivy SARIF | ✅ Interno | ✅ Interno | ❓ | ❓ | ❓ |
| Open source | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Channel Manager** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Booking Engine** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Revenue Management** | ❌ | ✅ | ✅ | ✅ | ⚠️ | ✅ |
| **Email/SMS conferme** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **PDF fatture** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Mobile app/PWA** | ❌ | ✅ | ✅ | ✅ | ✅ | ⚠️ |
| **Online check-in guest** | ❌ | ✅ | ✅ | ✅ | ⚠️ | ⚠️ |
| **Report RevPAR/ADR** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Marketplace integrazioni** | ❌ | ✅ Enterprise | ✅ 1000+ | ✅ 400+ | ⚠️ | ⚠️ |
| **Multi-currency** | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Contabilità/fiscale** | ❌ | ✅ | ✅ | ✅ | ⚠️ | ⚠️ |

> **Legenda:** Le righe in **grassetto** sono le feature dove rimani indietro. Sono analizzate in dettaglio nella Parte 2.

---

*Continua nella [Parte 2](ENTERPRISE_REPORT_PART2.md) — Feature gap dettagliate, analisi mobile-first, pricing commerciale, roadmap*
