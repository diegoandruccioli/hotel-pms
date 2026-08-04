# Changelog — Hotel PMS

Formato: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

---

## [Unreleased]

Lavoro reale da maggio 2026 ad oggi, non ancora raggruppato in una release numerata.

### Added

- **Consolidamento `frontdesk-service`** (ADR-001) — `inventory-service`,
  `reservation-service`, `stay-service` uniti in un unico servizio (rooms/
  reservations/stays come sotto-domini package-by-feature), 3 database ridotti a 1
- **`notification-service`** — email transazionali (conferma prenotazione, riepilogo
  checkout) via Feign sincrono + Resilience4j, servizio interno non instradato dal gateway
- **Backup automatizzato cifrato off-site** — pgBackRest, WAL archiving continuo (RPO in
  minuti, non più giornaliero), copia cifrata su Backblaze B2, restore verificato
  manualmente end-to-end su dati reali; drill CI automatizzato scritto ma attualmente
  bloccato dal cap giornaliero B2 free-tier
- **Fatturazione elettronica FatturaPA FPR12 — export production-ready**: validazione
  contro lo schema XSD ufficiale Agenzia delle Entrate, snapshot immutabile per ogni
  export (`invoice_fiscal_exports`: bytes+SHA-256+timestamp), blocco `409
  INVOICE_LOCKED_AFTER_EXPORT` sui campi fiscali dopo il primo export, endpoint batch
  ZIP+indice CSV per il commercialista. **Decisione**: nessuna trasmissione diretta a
  SDI (implica conservazione sostitutiva, sproporzionato pre-lancio) — vedi
  `backup/DECISIONS.md §2.5`
- **Audit di conformità normativa** (`docs/COMPLIANCE_AUDIT_2026-08.md`) — 11 aree
  legali verificate sul codice reale, 2 obblighi in vigore mai tracciati prima trovati
  assenti (imposta di soggiorno, corrispettivi telematici Horeca dal 2026-01-01)
- **Test esplorativo dal vivo** (`docs/EXPLORATORY_TEST_2026-08.md`) — 7 bug trovati
  testando lo stack reale fuori dall'ordine scriptato, il più grave: il fallback del
  circuit breaker su `billingClient.addCharge` assorbiva in silenzio anche i rifiuti
  legittimi (409), non solo i guasti di rete

### Changed

- Dedup cross-service: `FeignHeaderConfig`/`SecurityConfig`/`GlobalExceptionHandler`
  consolidati in librerie condivise (`internal-auth-lib`, `common-web-lib`) — trovati e
  corretti 2 bug RBAC reali nel processo (fb-service senza `@EnableMethodSecurity`,
  gestione `AccessDeniedException` mancante su fb/billing)
- `Invoice.invoiceNumber`: annotazione JPA corretta per matchare il vincolo DB reale
  (composito `hotel_id`+`invoice_number`, non `invoice_number` da solo)

### Fixed

- Bug `dateOfBirth`/409: walk-in check-in con ospite FAMILIARE/MEMBRO_GRUPPO senza data
  di nascita andava in 500 mappato fuorviantemente a 409
- Report finanziario Owner interrogava le fatture senza scoping `hotelId` (cross-tenant
  leak, T-BILL-04)
- `ThymeleafPdfTemplateRenderer`: font Noto Sans ri-letti dal classpath ad ogni
  generazione PDF, esauriva il Metaspace sotto carico (T-BILL-05)
- `FatturaPAServiceImpl` dichiarata `final` con `@Transactional` sui nuovi metodi —
  impediva a Spring di creare il proxy CGLIB, crash all'avvio (trovato testando il
  deploy live, non dai test unitari)
- Export batch FatturaPA: una singola fattura con dati incompleti bloccava l'intero
  export del periodo invece di essere isolata e segnalata nell'indice

---

## [0.1.0-pilot] — 2026-05-17

Prima release pilot-ready. Sistema completo per gestione operativa
di un singolo hotel con conformità normativa italiana (TULPS + GDPR).

### Added — Feature operative

- **Check-in da prenotazione** — form con campi Alloggiati PS completi
  (tipo alloggiato, documento, stato/comune nascita, lookup PS)
- **Check-in walk-in** — check-in senza prenotazione preesistente
- **Check-out** — con verifica fattura PAID obbligatoria
- **Gestione prenotazioni** — CRUD con overbooking prevention (`@Version`)
- **Gestione ospiti** — CRUD con ricerca full-text, GDPR soft delete
- **Food & Beverage POS** — ordini con addebito automatico su conto camera
- **Billing** — fatture per soggiorno, pagamenti multi-metodo, PDF scaricabile
- **Housekeeping** — gestione status camere (AVAILABLE/OCCUPIED/DIRTY/MAINTENANCE)
- **Calendar Planning** — planning board e vista mensile prenotazioni
- **Gestione camere** — CRUD tipologie e camere fisiche con tariffe
- **Alloggiati PS** — export file `.txt` 168-char conforme TULPS art. 109;
  invio SOAP automatico two-step (`GenerateToken` → `Send`/`Test`);
  toggle `alloggiatiAutoSend` per hotel; badge stato per riga soggiorno
- **PDF fattura** — generazione on-the-fly con PDFBox, header hotel brandizzato
- **Owner Dashboard** — revenue, occupancy, ADR, RevPAR; export CSV
- **Gestione utenti** — CRUD account (RECEPTIONIST/OWNER/ADMIN), reset password
- **Profilo hotel** — nome, indirizzo, P.IVA, CF, logo, toggle Alloggiati
- **Menu F&B per hotel** — CRUD voci menu isolate per hotel

### Added — Infrastruttura

- 9 microservizi Spring Boot 3.5 con database PostgreSQL separati
- API Gateway (Spring Cloud Gateway) con JWT validation, routing, rate limiting
- Spring Cloud Config Server — configurazione centralizzata
- Redis — rate limiting token bucket + refresh token blacklist
- Flyway — migration versionata su tutti i 9 servizi (V1–V8+)
- Docker Compose con 5 reti isolate, healthcheck, resource limits
- CI/CD GitHub Actions: build + JUnit + ESLint + Playwright E2E + Trivy

### Added — Osservabilità

- Zipkin — distributed tracing inter-service
- Prometheus — metriche applicative
- Grafana — dashboard (stack pronto, alert rule da configurare)
- Loki — log aggregation con AsyncAppender non-blocking
- `X-Correlation-ID` propagato via MDC su tutti i 9 servizi

### Added — Internazionalizzazione

- i18n EN/IT completo con 14 namespace JSON
- Zero stringhe hardcoded nel frontend (enforced da ESLint + code review)

### Added — Accessibilità

- WCAG 2.2 AA: contrasto 7:1 testo normale, 4.5:1 testo grande
- Focus rings visibili come elemento di design (non afterthought)
- Focus trap in tutti i modali
- Skip-to-main link in ogni pagina
- `vitest-axe` su ogni component test

### Security

- **JWT in httpOnly cookie** — SameSite=Strict, non accessibile via JavaScript
- **Token rotation** — refresh token ruotato ad ogni uso; blacklist Redis con TTL
- **tokenVersion** — invalidazione selettiva di tutti i token di un utente
- **Argon2id KDF** — password hashing con DelegatingPasswordEncoder
  (migrazione automatica BCrypt → Argon2id al primo login successivo)
- **Password policy** — ≥16 char, 2 maiusc, 2 cifre, 2 speciali
- **HMAC-SHA256 inter-service** — `X-Internal-Signature` su ogni chiamata Feign;
  confronto constant-time con `MessageDigest.isEqual`
- **RBAC doppio livello** — gateway route-level + `@PreAuthorize` endpoint-level
- **CSRF** — SameSite=Strict + CORS origin whitelist
- **Security headers** — HSTS `max-age=31536000`, CSP `default-src 'none'`,
  X-Frame-Options DENY
- **Rate limiting** — Redis token bucket per IP + path
- **Multi-tenancy** — `hotel_id NOT NULL` su tutte le entità; iniettato da JWT,
  mai da input client; IDOR-safe
- **GDPR** — soft delete, hard-anonymize, legal hold HTTP 451,
  export Art.20 strutturato, retention job, separazione PII per servizio
- **mustChangePassword** — forza cambio password al primo login e dopo reset
- **Actuator** — porta separata (:8090), protetto in produzione
- **Swagger** — disabilitato in produzione (`api-gateway-prod.yml`)

### Fixed (runtime bugs risolti prima del pilot)

- Stay-service: `hotelId` non estratto dal JWT → `Stay.hotelId=null`
- Stay-service: LoadBalancer config mancante → Feign non trovava altri servizi
- Stay-service: `InventoryClient` inviava `text/plain` invece di JSON
- fb-service: path `/api/stays` → `/api/v1/stays` (Feign 404)
- auth-service: admin password hash senza prefisso `{bcrypt}` → login falliva
- api-gateway: route predicate `/api/v1/auth/users` non matchava correttamente

### Infrastructure

- Spring Boot 3.4 → 3.5, Spring Cloud 2024 → 2025
- CVE-2025-41253: bump `spring-cloud-gateway-server` 4.3.2
- Grafana 11.5.0 (CVE-OSS accettati come rischio residuo — monitoring interno)
- PMD zero-warning policy enforced su tutti i 9 moduli backend
- JaCoCo coverage threshold ≥ 40% instruction enforced in CI
- Vitest coverage thresholds (stmt/branch/fn/lines) enforced in CI

---

## Note

I numeri di versione seguono [SemVer](https://semver.org/).
`0.x.y` indica che l'API pubblica non è ancora stabile — breaking changes possibili.
La versione `1.0.0` sarà rilasciata al completamento del Channel Manager (Sprint 3 E1).
La fattura elettronica (Sprint 3 E3) è chiusa nella modalità decisa (export FatturaPA
validato, non trasmissione diretta SDI — vedi `backup/DECISIONS.md §2.5`), non è più un
prerequisito bloccante per `1.0.0`.
