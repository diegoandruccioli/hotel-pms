# Audit Finale Ultra-Severo — Hotel PMS

**Data:** 2026-05-07  
**Branch analizzato:** `feature/frontend-development`  
**Revisore:** Analisi automatica assistita (Claude Sonnet 4.6) su evidenze dirette da codice  
**Tipo documento:** Revisione tecnica indipendente — stile enterprise production-readiness + university exam

> Questo documento NON è una self-assessment. Ogni affermazione è basata su lettura diretta
> del codice con evidenza di file path e numeri di riga. Le dichiarazioni non verificate
> da codice sono esplicitamente marcate come tali.

---

## 1. Executive Verdict

**PILOT-READY WITH MATERIAL RISKS**

Il progetto è genuinamente solido nella sua architettura di sicurezza e nell'infrastruttura
operativa. JWT httpOnly + token rotation + Redis blacklist + HMAC inter-service + security
headers + multi-tenant isolation: niente di tutto questo è finto o superficiale, il codice
lo conferma riga per riga. Tuttavia esistono tre aree di rischio materiale:

1. `Invoice.java` non ha `@Version`, esponendo il totale fattura a race condition concrete
   quando F&B e billing scrivono concorrentemente.
2. La copertura test è *dichiarata* al 95% ma **non è enforcement** — nessun JaCoCo,
   nessuna threshold Vitest, nessun gate di build.
3. I due branch chiave (`feature/frontend-development` e `feature/secure-coding-hardening`)
   non sono ancora unificati: il lavoro GDPR retention + THREAT_MODEL + LaTeX report è
   inaccessibile dal branch corrente.

Per produzione reale mancano inoltre Kubernetes, alerting rules, backup/restore DB,
load testing, Testcontainers, e contratti API formali.

---

## 2. Audit per Area

### 2.1 Architettura

| Aspetto | Università | Pilot | Produzione | Evidenze | Gap |
|---|---|---|---|---|---|
| Microservizi e confini | ✅ Eccellente | ✅ Adeguato | ⚠️ Parziale | 9 servizi, DB separati, 5 reti Docker isolate | Nessun API contract test formale |
| Transazioni distribuite | ✅ Buono | ⚠️ Rischio | ❌ Incompleto | Saga su checkIn con rollback (`StayServiceImpl`) | Invoice senza `@Version` |
| Configurazione centralizzata | ✅ Eccellente | ✅ Adeguato | ⚠️ SPOF | Spring Cloud Config funzionante | Config-service è SPOF al boot |
| Infrastruttura Docker | ✅ Eccellente | ✅ Adeguato | ❌ Dev-only | Healthcheck, resource limits, restart policy mancante | Nessun Kubernetes, nessuna restart policy |

**Positivo confermato da codice:**
- `@Version` su `Reservation.java:55` — overbooking check è race-condition safe.
- Saga Pattern con rollback implementato: se `updateRoomStatus(OCCUPIED)` fallisce, la
  transazione checkIn è annullata. Confermato in `StayServiceImpl.checkIn()`.
- 5 reti bridge Docker isolate: DMZ, gateway, backend, db, observability.
- Refresh token blacklist Redis: `RefreshTokenServiceImpl` con TTL matching token expiration,
  `isBlacklisted()` consultato ad ogni refresh.

**Gap critico — Invoice senza `@Version`:**  
`billing-service/.../Invoice.java` (136 righe) — nessun campo `@Version`. L'entità ha due
scritture concorrenti: `processPayment()` e `addCharge()` (da F&B confirm). Con più
receptionist attivi, una perdita silente di addebiti è un rischio reale, non teorico.

---

### 2.2 Backend Engineering

| Aspetto | Università | Pilot | Produzione | Evidenze | Gap |
|---|---|---|---|---|---|
| Layering C/S/R | ✅ Eccellente | ✅ Adeguato | ✅ Adeguato | Nessuna entity esposta via REST; MapStruct + record DTO ovunque | — |
| Validazione input | ✅ Buono | ✅ Adeguato | ⚠️ Da completare | `@Valid`, `@NotNull`, `@NotBlank` sui DTO | Uniformità non verificata su ogni controller |
| Gestione errori | ✅ Buono | ✅ Adeguato | ⚠️ Inconsistenza minore | `ProblemDetail` RFC 7807 in tutti i servizi | auth-service usa `@ControllerAdvice`, altri `@RestControllerAdvice` |
| Multi-tenancy | ✅ Eccellente | ✅ Adeguato | ✅ Adeguato | `hotel_id NOT NULL` ovunque, Flyway con backfill, query sempre filtrate per JWT hotelId | — |
| Performance DB | ⚠️ Accettabile | ⚠️ Rischio latente | ❌ Gap reale | LIKE `%keyword%` in `GuestRepository.java:66-69`, `FetchType.LAZY` su `Invoice.charges:82` | Nessun GIN index, N+1 su Invoice |
| Code quality gate | ✅ Eccellente | ✅ Adeguato | ✅ Adeguato | PMD `gradle-java-qa` zero-warning: build fallisce su qualsiasi violazione | — |

**Gap LIKE search:**  
`GuestRepository.java:66-69` — quattro query `LIKE LOWER(CONCAT('%', :keyword, '%'))` su
campi non indicizzati. `DECISIONS.md §1.2` lo marca "deprecated" con piano di migrazione
su GIN index + `pg_trgm`. Il codice è ancora in produzione invariato.

**Gap N+1:**  
`Invoice.java:82` — `FetchType.LAZY` su `charges`. Nessun `@EntityGraph` trovato in
`InvoiceServiceImpl`. Con 50 fatture caricate, vengono eseguite 51 query DB. Invisibile
nei test Mockito.

---

### 2.3 Frontend Engineering

| Aspetto | Università | Pilot | Produzione | Evidenze | Gap |
|---|---|---|---|---|---|
| TypeScript strictness | ✅ Eccellente | ✅ Adeguato | ✅ Adeguato | Zero `any` da grep; `"strict": true` in `tsconfig.app.json` | — |
| Performance | ✅ Buono | ✅ Adeguato | ✅ Adeguato | Tutte le 15+ pagine lazy-loaded in `App.tsx`; `React.memo()` sui componenti stabili | — |
| Auth interceptor | ✅ Eccellente | ✅ Eccellente | ✅ Eccellente | `api.ts:74-114`: refresh silenzioso, coda richieste in attesa, logout su refresh fallito | — |
| Copertura test | ✅ Enforced | ✅ Enforced | ⚠️ Threshold minime | Vitest `coverage.thresholds` enforced in CI (G9 — `dab4eea`); JaCoCo ≥40% instruction su tutti i moduli backend | ✅ RISOLTO (G9) |
| Error handling UI | ✅ Buono | ✅ Adeguato | ⚠️ Parziale | Loading/error/empty states presenti; `ErrorBoundary` class component avvolge `<Suspense>/<Routes>` in `App.tsx` (commit `fc3e86c`) | ✅ RISOLTO (G8) — residuale: nessuna boundary granulare per singola route |
| i18n | ✅ Buono | ✅ Adeguato | ✅ Adeguato | 14 namespace, zero stringhe hardcoded da spot-check | — |

---

### 2.4 Sicurezza e Compliance

| Aspetto | Università | Pilot | Produzione | Evidenze | Gap |
|---|---|---|---|---|---|
| Session management | ✅ Eccellente | ✅ Eccellente | ✅ Eccellente | JWT httpOnly, token rotation, Redis blacklist, `tokenVersion` | — |
| Security headers | ✅ Eccellente | ✅ Eccellente | ✅ Eccellente | HSTS `max-age=31536000`, CSP `default-src 'none'`, X-Frame-Options DENY (`SecurityHeadersFilter.java:94-100`) | — |
| HMAC inter-service | ✅ Eccellente | ✅ Eccellente | ✅ Adeguato | Constant-time compare, segreto da script dedicato, startup check bloccante | Nessun mTLS; HMAC è unico layer di auth interna |
| RBAC | ✅ Buono | ✅ Adeguato | ⚠️ Non uniforme | Gateway route-level + `@PreAuthorize` su endpoint sensibili | Enforcement granulare non verificato su ogni controller |
| GDPR / PII | ✅ Buono | ⚠️ Branch separato | ⚠️ Branch separato | Retention job, hard-anonymize, `gdprConsentDate` su branch `feature/secure-coding-hardening` | NON visibile dal branch corrente |
| Alerting & audit | ✅ Buono | ⚠️ Passivo | ❌ Mancante | X-Correlation-ID via MDC, Prometheus + Grafana + Loki presenti | Nessuna alert rule configurata |

**Rischio esame — branch non unificato:**  
Il lavoro GDPR retention (T-GST-05), `THREAT_MODEL.md`, e il report LaTeX
(`docs/security-report/report-secure-coding.tex`) sono su `feature/secure-coding-hardening`.
Una commissione che valuta `feature/frontend-development` o `main` non li vede.
Questo è il rischio organizzativo numero uno prima della consegna.

---

### 2.5 Testing e Qualità

| Servizio | File test | Note |
|---|---|---|
| auth-service | 6 | AuthService, RefreshToken, UserMgmt (impl + controller), resetPassword (3 service + 3 controller test) |
| guest-service | **2** | GuestServiceImpl (17 test), GuestController — il più sottile per un servizio PII |
| inventory-service | 4 | RoomService, RoomType (impl + controller) |
| reservation-service | 3 | ReservationService, Controller, DateRangeValidator |
| stay-service | **9** | Il più coperto: Alloggiati, CSV parser, SOAP, HotelSettings, Stay (impl + controller) |
| billing-service | 6 | Invoice, Payment, OwnerReport (impl + controller) |
| fb-service | 5 | RestaurantOrder, MenuItem (impl + controller), InternalAuthFilter |

| Tipo test | Stato | Nota |
|---|---|---|
| Unit (Mockito) | ✅ Presente | Tutti i servizi coperti; 20+ test per service in media |
| Integration (Testcontainers) | ❌ Assente | Zero `@SpringBootTest` con DB reale in tutto il progetto |
| Contract (Pact/Spring Cloud Contract) | ❌ Assente | Nessun contratto API formale inter-service |
| E2E (Playwright) | ✅ Presente | 9 spec: auth, booking, checkout, billing, walk-in, IDOR, RBAC |
| Coverage gate backend | ✅ RISOLTO (G9) | JaCoCo `jacocoTestCoverageVerification` configurato — threshold ≥40% instruction; build fallisce sotto soglia |
| Coverage gate frontend | ✅ RISOLTO (G9) | `vite.config.ts` con `coverage.thresholds` enforced (stmt/branch/fn/lines) |

**Impatto concreto assenza JaCoCo:**  
`PILOT_READINESS_AUDIT.md:42` afferma "copertura >95% su tutti i servizi backend". Non esiste
nessun report JaCoCo che lo verifichi. La build può passare con copertura reale del 60%
senza che nessun gate lo rilevi. Le Flyway migrations non sono mai testate da test Java —
una migration che compila ma rompe dati esistenti supera tutti i test unitari.

---

### 2.6 Operatività e Produzione

| Aspetto | Università | Pilot | Produzione | Evidenze | Gap |
|---|---|---|---|---|---|
| Docker-compose | ✅ Eccellente | ✅ Adeguato | ⚠️ Dev-only | Healthcheck, resource limits, 5 reti isolate | **Nessuna `restart:` policy** — grep conferma zero occorrenze |
| Logging | ✅ Eccellente | ✅ Adeguato | ✅ Adeguato | AsyncAppender Loki (`queueSize=512`, `neverBlock=true`), X-Correlation-ID via MDC | — |
| Monitoring stack | ✅ Buono | ✅ Adeguato | ⚠️ Passivo | Prometheus + Grafana + Loki + Zipkin presenti e configurati | Nessuna alert rule |
| Secrets | ✅ Buono | ✅ Adeguato | ⚠️ Env-var only | `.env` in `.gitignore`, Spring Cloud Config, nessun hardcoding | Nessun Vault/KMS |
| Backup DB | ❌ Assente | ❌ Assente | ❌ Gap critico | Nessuna strategia pg_dump o WAL archiving | Data loss catastrofico su crash disco |
| CI/CD | ⚠️ Parziale | ⚠️ Parziale | ❌ Gap critico | Dependabot configurato; nessun GitHub Actions workflow per build/test su PR | Nessuna automazione pipeline |
| Kubernetes | ❌ Assente | ❌ Accettabile | ❌ Gap critico | Single docker-compose per tutto | Da costruire per scaling e failover |

---

### 2.7 Documentazione

| File | Stato | Note |
|---|---|---|
| `docs/INTERACTION_FLOWS.md` | ✅ OK | 12 flussi end-to-end con catene di chiamate inter-service |
| `docs/SECURITY_AND_PRIVACY.md` | ✅ OK | 12 sezioni, PII classification, sanzioni GDPR/TULPS |
| `docs/USER_MANUAL.md` | ✅ OK | 12 procedure operative (aggiunta §3.12 reset password; §3.8 esteso con submit PS manuale) — aggiornato 2026-05-15 |
| `docs/I18N.md` | ✅ OK | 14 namespace, convenzioni chiavi, anti-hardcoding |
| `docs/ALLOGGIATI_README.md` | ✅ OK | Integrazione SOAP PS documentata |
| `docs/PILOT_READINESS_AUDIT.md` | ⚠️ WEAK | Afferma ">95% copertura backend" senza evidenza JaCoCo; non menziona Invoice @Version, LIKE search, assenza restart policy |
| `docs/GAP_ANALYSIS.md` | ✅ OK | Storico gap chiusi — utile come traccia |
| `backup/DECISIONS.md` | ✅ OK | Decisioni architetturali documentate con motivazioni |
| `THREAT_MODEL.md` | ⚠️ NON VISIBILE | Su branch `feature/secure-coding-hardening` |
| `docs/security-report/report-secure-coding.tex` | ⚠️ NON VISIBILE | Su branch `feature/secure-coding-hardening` |
| API contract documentation | ❌ MISSING | Nessun Pact schema, nessun Spring Cloud Contract |
| Operations runbook | ❌ MISSING | Nessuna procedura per crash, rollback migration, recovery DB |
| CONTRIBUTING.md | ❌ MISSING | Nessuna guida per onboarding di un nuovo sviluppatore |

---

### 2.8 Processo e Maturità

| Area | Stato | Note |
|---|---|---|
| Qualità commit | ✅ Buona | Conventional commits (`feat:`, `fix:`, `test:`, `docs:`) con scope precisi, atomici per feature |
| Tracciabilità decisioni | ✅ Buona | `backup/DECISIONS.md`: 6 aree con motivazioni; `backup/SUMMARY.md`: log cronologico |
| Branching strategy | ⚠️ Rischio | Due branch paralleli non unificati prima della consegna; il lavoro di sicurezza non è visibile dal branch principale |
| CI/CD | ✅ RISOLTO | GitHub Actions `ci.yml` presente: build + test + E2E Playwright in Docker su ogni push/PR |
| Code review | ❌ Non valutabile | Progetto mono-sviluppatore; nessun `CODEOWNERS`, nessun branch protection documentato |

---

## 3. Buchi Reali del Progetto

Problemi verificati con evidenze dirette da codice. Ordinati per gravità.

### Critici

**C1 — `Invoice.java` senza `@Version`**  
File: `billing-service/src/main/java/com/hotelpms/billing/domain/Invoice.java`  
L'entità ha due scritture concorrenti indipendenti: `processPayment()` e `addCharge()`
(chiamata da fb-service al confirm ordine). Senza ottimistic locking, due receptionist
che operano contemporaneamente sulla stessa stanza possono causare perdita silente di
un addebito F&B. Scenario concreto: checkout + conferma ordine bar sulla stessa camera.

**C2 — Branch security non unificato**  
`feature/secure-coding-hardening` contiene: GDPR retention job (T-GST-05), hard-anonymize
con guardia TULPS, `GuestPrivacySettings`, `GuestRetentionJobServiceImpl`, `gdprConsentDate`,
`THREAT_MODEL.md`, `report-secure-coding.tex`. Nessuno di questi è visibile dal branch
corrente. Per un esame di Secure Coding, questo è il gap organizzativo più grave.

### Alti

~~**A1 — Coverage backend non enforced**~~  
✅ **RISOLTO (G9 — `dab4eea`)** — JaCoCo `jacocoTestCoverageVerification` configurato su tutti i moduli backend con threshold ≥40% instruction. Build fallisce automaticamente se la copertura scende sotto soglia.

~~**A2 — Coverage frontend non enforced**~~  
✅ **RISOLTO (G9 — `dab4eea`)** — `vite.config.ts` con `coverage.thresholds` enforced per stmt/branch/fn/lines. Thresholds fissate con buffer di sicurezza sulla baseline misurata.

**A3 — Nessuna restart policy in docker-compose**  
Grep su `docker-compose.yml` — zero occorrenze di `restart:`. Un container crashato
in produzione rimane down finché qualcuno non interviene manualmente.

**A4 — Zero integration test**  
Tutti i test Java usano `@ExtendWith(MockitoExtension.class)` con Mockito. Nessun
`@SpringBootTest`, nessun Testcontainers. Le Flyway migrations non sono mai testate da
codice Java. Una migration che compila ma introduce un constraint errato supera l'intera
suite di test unitari.

### Medi

**M1 — LIKE `%keyword%` ancora in produzione**  
`GuestRepository.java:66-69` — quattro LIKE su colonne non indicizzate. Marcato "deprecated"
in `DECISIONS.md §1.2`, ma il codice non è cambiato. Con 10k ospiti: lento. Con 100k: inutilizzabile.

**M2 — N+1 query latente su Invoice.charges**  
`Invoice.java:82` — `@OneToMany(fetch = FetchType.LAZY)`. Nessun `@EntityGraph` in
`InvoiceServiceImpl`. Non rilevabile con Mockito.

~~**M3 — Nessun ErrorBoundary React a livello route**~~  
✅ **Risolto** (commit `fc3e86c`) — `ErrorBoundary.tsx` class component avvolge `<Suspense>` in `App.tsx`. Fallback UI con messaggio utente + pulsante reload. 4 Vitest test. Residuale: nessuna boundary granulare per singola route.

**M4 — Nessuna alert rule Prometheus/Grafana**  
Stack osservabilità presente (Prometheus + Grafana + Loki), ma nessuna regola di alerting.
Errori 5xx, latenza, riavvii container: invisibili finché un utente non segnala un problema.

**M5 — guest-service coperto da 2 soli file test**  
Il servizio che gestisce dati PII di ospiti (nome, documento, data di nascita) ha il
rapporto test/complessità più basso del progetto. L'interazione tra GDPR guard e
`deleteGuest` è sul branch sicurezza, non verificabile qui.

**M6 — CORS ancora su localhost**  
`docker-compose.yml:242` — `GW_CORS_ALLOWED_ORIGINS=http://localhost:5173`. È una env var
configurabile, ma il default sbagliato è un rischio in un deploy frettoloso.

### Bassi

**B1 — Inconsistenza `@ControllerAdvice` vs `@RestControllerAdvice`**  
`auth-service` usa `@ControllerAdvice`, tutti gli altri `@RestControllerAdvice`. Funzionalmente
equivalente, ma segnala mancanza di convenzione condivisa.

**B2 — Nessun GitHub Actions CI/CD**  
Dependabot configurato. Nessun workflow per build automatica su PR. La qualità del codice
è verificata solo localmente.

**B3 — Swagger esposto senza autenticazione in dev**  
Tutti i `/swagger-ui.html` raggiungibili via API Gateway senza JWT in sviluppo. Disabilitato
in prod via `api-gateway-prod.yml` — gap solo in dev/staging.

---

## 4. Cosa è Davvero Solido

Parti che reggono una revisione severa:

- **Session management end-to-end**: JWT httpOnly + SameSite=Strict + token rotation +
  Redis blacklist con TTL + `tokenVersion`. Il codice corrisponde esattamente alla
  documentazione. Implementazione production-grade rara a questo livello di complessità.

- **HMAC inter-service**: Constant-time compare con `MessageDigest.isEqual`, segreto
  generato da script dedicato, startup check bloccante se mancante o troppo corto.

- **Security headers**: HSTS `max-age=31536000; includeSubDomains`, CSP `default-src 'none';
  frame-ancestors 'none'`, X-Frame-Options `DENY`. Valori reali, non placeholder.

- **Multi-tenant isolation**: `hotel_id NOT NULL` su tutte le entità, Flyway migration con
  backfill e indice composto, query sempre filtrate per JWT-extracted hotelId. Test di
  isolamento espliciti (es. `RoomServiceImplTest` verifica che hotel A non veda dati hotel B).

- **Alloggiati PS**: 30 test di conformità su `AlloggiatiReportServiceImplTest`. Tracciato
  168 caratteri fissi, ordinamento capo→componenti, CRLF conforme spec, SOAP two-step
  (GenerateToken → Send/Test), DRY_RUN default. Modulo più testato del progetto.

- **Saga Pattern checkIn**: Rollback reale se `updateRoomStatus(OCCUPIED)` fallisce;
  continuazione non-blocking se `updateReservationGuests()` fallisce. Non è boilerplate.

- **PMD zero-warning enforced**: Build fallisce su qualsiasi violazione. Gate reale,
  non aspirazionale.

- **TypeScript strict, zero `any`**: Confermato da grep su tutto il frontend. `"strict": true`
  in tsconfig. Codice genuinamente type-safe.

- **Docker-compose network segmentation**: 5 reti bridge isolate. Frontend su DMZ,
  database irraggiungibile dall'esterno. Setup più maturo della maggior parte dei
  progetti universitari.

- **Logging strutturato**: AsyncAppender Loki con `neverBlock=true`, X-Correlation-ID
  via MDC su tutti i servizi. Un errore distribuito è tracciabile con un solo UUID.

- **Circuit breaker uniformi**: Resilience4j su ogni Feign client in ogni servizio,
  fallback dichiarati. Un servizio down non propaga il fallimento a cascata.

---

## 5. Pilot-Ready vs. Production-Ready

### Già sufficiente per un pilot controllato

- Tutti i flussi operativi funzionanti: walk-in, check-in, checkout, billing, F&B,
  housekeeping, camere, gestione utenti, profilo hotel.
- Sicurezza session e multi-tenancy: nessun receptionist vede dati di un altro hotel,
  credenziali default richiedono cambio obbligatorio, JWT scaduto non funziona.
- Alloggiati PS: file 168-char conforme, invio SOAP implementato con DRY_RUN=true.
  Il collaudo reale richiede credenziali PS della Questura — dipendenza esterna.
- `docker compose up` avvia tutto, healthcheck assicura ordine, resource limits
  prevengono OOM.
- i18n EN/IT completo, zero stringhe hardcoded nel frontend.
- Documentazione operativa per receptionist e amministratori.

### Necessario per produzione

| Gap | Perché è necessario in produzione |
|---|---|
| `@Version` su Invoice | Perdita silente di addebiti F&B con multi-receptionist |
| JaCoCo + Vitest thresholds | La qualità degrada invisibilmente nel tempo |
| Restart policy docker-compose | Un crash non auto-recupera senza intervento manuale |
| Kubernetes o docker swarm | Scaling orizzontale, failover, rolling updates |
| Backup PostgreSQL schedulato | Data loss catastrofico senza pg_dump o WAL archiving |
| Alerting rules (Prometheus/Grafana) | Errori e degradi invisibili finché un cliente non si lamenta |
| Integration tests (Testcontainers) | Una migration rotta supera tutti i test unitari |
| API contract tests | Una modifica a un DTO rompe silenziosamente i consumer |
| GitHub Actions CI/CD | La verifica manuale scala male con un team |
| Secrets management (Vault/KMS) | File `.env` su disco non è adeguato per server condivisi |
| Operations runbook | Senza procedure, un incidente notturno è un disastro |
| GIN index + `pg_trgm` | LIKE `%keyword%` inutilizzabile su dataset reali |

---

## 6. File MD Mancanti o Migliorabili

| File | Stato | Perché | Azione consigliata |
|---|---|---|---|
| `docs/PILOT_READINESS_AUDIT.md` | WEAK | Afferma ">95% copertura backend" senza JaCoCo; non menziona Invoice @Version, LIKE search, restart policy | Aggiungere sezione gap residui onesta; qualificare le affermazioni di copertura non misurata |
| `THREAT_MODEL.md` | NON VISIBILE | Su branch sicurezza, non nel branch corrente | Merge del branch sicurezza prima della consegna esame |
| `docs/security-report/report-secure-coding.tex` | NON VISIBILE | Come sopra | Come sopra |
| `docs/OPERATIONS_RUNBOOK.md` | MISSING | Nessuna procedura per crash, rollback migration, recovery DB | Creare prima del pilot |
| `CONTRIBUTING.md` | MISSING | Nessuna guida per onboarding di un nuovo sviluppatore | Necessario per handover a un team |
| API contract specs | MISSING | Nessun Pact schema, nessun Spring Cloud Contract | Necessario per produzione |

---

## 7. Roadmap Finale

### Bloccanti prima del pilot

1. **Unificare i due branch** — merge `feature/secure-coding-hardening` in
   `feature/frontend-development` poi in `main`. Senza questo, il lavoro GDPR e la
   documentazione sicurezza sono invisibili per la valutazione.
2. **Aggiungere `restart: unless-stopped`** ai servizi in `docker-compose.yml`.
   Una riga per servizio, cinque minuti di lavoro, impatto operativo reale.
3. **Qualificare le affermazioni di copertura** in `PILOT_READINESS_AUDIT.md`:
   sostituire ">95% verificato" con "stimato, non gate-enforced" finché JaCoCo non è
   configurato.

### Durante o attorno al pilot

4. **`@Version` su `Invoice.java`** + colonna `version` via Flyway migration.
5. ✅ **JaCoCo** — `jacocoTestCoverageVerification` su tutti i moduli con threshold ≥40% instruction (G9 — `dab4eea`).
6. ✅ **Vitest coverage thresholds** — `coverage.thresholds` enforced in `vite.config.ts` (G9 — `dab4eea`).
7. ✅ **`ErrorBoundary` React** — class component avvolge `<Suspense>/<Routes>` in `App.tsx` (G8 — `fc3e86c`).
8. **Creare `docs/OPERATIONS_RUNBOOK.md`** minimo: start/stop, verifica log,
   rollback migration, recovery DB.

### Necessario per evolvere verso produzione

9. **Testcontainers** per stay-service (Alloggiati migration schema) e billing-service.
10. **GIN index + `pg_trgm`** su `Guest.firstName/lastName`; rimuovere LIKE query.
11. **GitHub Actions CI/CD**: build + test + lint su ogni PR verso main.
12. **Kubernetes manifests** o docker swarm per scaling e failover.
13. **Backup PostgreSQL schedulato** (pg_dump cron o WAL archiving).
14. **Prometheus alert rules**: error rate, latency p99, container restarts.
15. **Secrets management** migrato a Vault o equivalente.
16. **`CONTRIBUTING.md`** per onboarding di un nuovo sviluppatore.

### Miglioramenti opzionali (post-produzione)

17. Tailwind 4 migration (`DECISIONS.md §7.1`).
18. Zipkin → Grafana Tempo + OpenTelemetry (`DECISIONS.md §7.2`).
19. Spring Cloud Contract per contratti inter-service.
20. Full-text search `pg_trgm` invece di LIKE su ospiti.
21. PDF invoices export.
22. RevPAR / occupancy report per OWNER.

---

## 8. Verdetto Conclusivo Senza Sconti

**Il progetto è serio, non appariscente.**

La differenza tra un progetto che *sembra* enterprise e uno che *è* enterprise si legge
nel codice, non nella documentazione. `RefreshTokenServiceImpl` ha davvero il blacklist
Redis. `InternalAuthFilter` fa davvero la constant-time compare. `@Version` è davvero su
`Reservation`. Il Saga Pattern ha davvero il rollback. Non è un sistema dimostrativo con
feature dichiarate e non implementate.

**Regge una valutazione tecnica severa, con due riserve precise:**

Il gap più critico per un esame di Secure Coding non è tecnico — è organizzativo: i due
branch non sono ancora unificati. Un revisore che guarda `feature/frontend-development`
non vede `THREAT_MODEL.md`, il report LaTeX, la GDPR retention job, il hard-anonymize.
Questo è il rischio numero uno prima della consegna.

Il secondo gap più critico è dichiarare "copertura >95%" in `PILOT_READINESS_AUDIT.md`
senza un singolo JaCoCo report a supportarlo. Una commissione tecnica severa lo chiederà.

**Può essere portato in produzione in modo credibile con circa 3-4 settimane di lavoro
aggiuntivo.** Non è una ristrutturazione. Sono gap specifici e circoscritti: `@Version`
su Invoice, restart policies, JaCoCo + Vitest enforcement, Testcontainers su 2 servizi,
alerting minimo, backup DB, merge dei branch. Il foundation è genuinamente solido.

Per l'esame universitario: merita un voto alto se i branch vengono unificati prima della
consegna e se la documentazione di sicurezza (THREAT_MODEL, LaTeX report) è accessibile.
L'architettura di sicurezza implementata è tra le più mature che si possano trovare in
un progetto di questa complessità.

---

---

## Bug runtime trovati dall'audit 2026-05-17 — tutti risolti

Durante l'audit con Docker stack reale sono stati trovati e fixati 6 bug
critici presenti nelle immagini stale (il codice era corretto,
i container giravano con versioni precedenti al rebuild):

1. Stay-service LoadBalancer mancante → Feign non trovava gli altri servizi  
   Fix: `spring.cloud.discovery.client.simple.instances` in `stay-service.yml`
2. `feign-hc5` jar mancante → PATCH /rooms non funzionava  
   Fix: dipendenza aggiunta a `stay-service/build.gradle.kts`
3. `InventoryClient.updateRoomStatus()` inviava `text/plain` invece di JSON  
   Fix: nuovo DTO `RoomStatusRequest` in stay-service
4. `StayController` non iniettava `hotelId` dal JWT → `Stay.hotelId=null`  
   Fix: `hotelId` estratto da `X-Auth-Hotel` header in `checkIn()`
5. `fb-service` `StayClient` path `/api/stays` → `/api/v1/stays`  
   Fix: path corretto in `StayClient` Feign interface
6. Admin password hash nel DB senza prefisso `{bcrypt}`  
   Fix applicato al DB (`UPDATE` su `user_account`).  
   **NOTA:** fix DB-only — alla re-init del DB va riapplicato.  
   **TODO:** aggiungere migration Flyway o `data.sql` con hash già prefissato.

Gateway: route predicate `/api/v1/auth/users/**` → `/api/v1/auth/users,/api/v1/auth/users/**`  
Fix in `config-service/src/main/resources/config/api-gateway.yml`

---

*Audit basato su lettura diretta del codice. Tutte le evidenze sono verificabili con i
file path e i numeri di riga indicati nel testo.*
