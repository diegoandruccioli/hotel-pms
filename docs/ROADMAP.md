# Roadmap — Hotel PMS

**Versione:** 1.5 — 2026-08-04 (FatturaPA export chiuso, E3/E3bis; audit normativo aggiunge E18-E24 — imposta di soggiorno, corrispettivi telematici, antiriciclaggio, allergeni F&B, retention GDPR `StayGuest`, cifratura `documentNumber`, RBAC ospiti)  
**Branch:** `main`

Questo documento raccoglie le implementazioni future pianificate,
organizzate per priorità e orizzonte temporale. Lo stato attuale
del sistema è **Production-ready** su installazione
mono-hotel Docker Compose. La roadmap descrive il percorso verso
**Enterprise SaaS**.

---

## Stato attuale

| Dimensione | Livello | Note |
|---|---|---|
| Sicurezza | 9.0/10 | Argon2id, HMAC anti-replay nonce+timestamp (E7bis ✅), RBAC doppio livello, GDPR, CodeQL extended (P13 ✅) — gap residui: 2FA (E9), rate limiting per-utente (E14) |
| Affidabilità | 8.5/10 | Circuit breaker, saga checkIn, `@Version` Invoice, backup pg_dump 24h retain 14gg (P3 parz. — manca copia off-site) — no K8s HA (SPOF singolo host) |
| Osservabilità | 8.5/10 | Zipkin + Prometheus + Loki + Alertmanager con 6 alert rule (P4 ✅) + Runbook (P5 ✅) — Zipkin → Tempo rimane C9 opzionale |
| Scalabilità | 7.0/10 | 8 servizi, GIN pg_trgm, frontdesk consolidato (ADR-001 ✅), Dependabot (P9 ✅) — SimpleDiscovery statico, no K8s (E5) |
| Qualità codice | 9.0/10 | PMD zero, Testcontainers billing+frontdesk (P7 ✅), coverage gate 90/80/88/92% (P15 ✅), Zod validation (P11 ✅), SRP refactor (P10 ✅) |
| Operabilità | 7.5/10 | Docker Compose prod hardening (P0 ✅), backup automatizzato locale (P3 parz.), alert+runbook (P4/P5 ✅), branch protection+CodeQL (P12/P13 ✅) — no K8s, no secrets manager |
| Conformità normativa | 7.0/10 | Alloggiati SOAP nativo, GDPR (backend), numerazione fattura YYYY/NNNN (C2 ✅), IVA disaggregata (C3 ✅), dati fiscali ospite (E12 parz. ✅), export FatturaPA FPR12 validato XSD, immutabile post-export, batch per commercialista (E3 ✅) — trasmissione diretta SDI (E3bis) e audit log immutabile generico (E13) ancora aperti. Voto abbassato da 9.5 a 7.5 dopo `docs/COMPLIANCE_AUDIT_2026-08.md` (2026-08-03): due obblighi di legge in vigore, mai tracciati prima, risultati completamente assenti — imposta di soggiorno (E18) e corrispettivi telematici Horeca, obbligatorio dal 2026-01-01 (E19). Abbassato ulteriormente a 7.0 dopo la verifica indipendente del 2026-08-04 sullo stesso audit: retention GDPR incompleta su `StayGuest` (E22), `documentNumber` non cifrato a riposo (E23), `GuestController`/`GuestPrivacySettingsController` senza `@PreAuthorize` (E24), allergeni F&B mai coperti (E21) |
| UX e funzionalità | 8.5/10 | Flussi core completi (prenotazione→check-in→F&B→fattura→checkout), dashboard KPI+room-grid, sort/filter, WCAG 2.2 AA, settings multi-pagina, verticale billing (C2/C3/FatturaPA), email conferma/checkout con personalizzazione (C1 ✅) — no mobile, no SMS |

**Punto di forza unico rispetto ai competitor:**
Implementazione Alloggiati PS nativa (SOAP, DRY_RUN, auto-send,
badge status) — tutti i PMS commerciali italiani usano plugin
di terze parti per questa funzionalità obbligatoria per legge (art. 109 TULPS).

---

## Sprint 1 — Da Pilot-ready a Production-ready
*Orizzonte: 4-6 settimane, 1 sviluppatore*

Prerequisiti bloccanti per il primo hotel reale in produzione.

| # | Implementazione | Priorità | Effort | Note |
|---|---|---|---|---|
| P1 | ~~`@Version` su `Invoice` + migration Flyway~~ | ✅ **Fatto** | — | Implementato: `Invoice.java` campo `@Version Long version`, Flyway V3. Audit Sprint 1 (2026-07-25): trovato e corretto un gap reale — `billing-service` non aveva l'`@ExceptionHandler(ObjectOptimisticLockingFailureException.class)` (presente invece in frontdesk-service), quindi un conflitto di scrittura concorrente su fattura tornava HTTP 500 invece di 409. Aggiunto handler + test reale (2 letture della stessa riga, 1a save ok, 2a save stale → eccezione verificata su Testcontainers) + unit test sul mapping a 409 |
| P2 | ~~`restart: unless-stopped` in docker-compose~~ | ✅ **Fatto** | — | Tutti i 17 container in `docker-compose.yml` già configurati (numero corretto 2026-07-25, era stale a 16 da prima di Alertmanager/notification-service) |
| P0 | ~~Hardening porte Docker — compose dev/prod separati~~ | ✅ **Fatto** | — | `docker-compose.prod.yml` creato: usa il merge-tag `!reset` per azzerare `ports` su Postgres/Redis/Prometheus/Zipkin/Loki/Grafana/config-server/tutti i backend — solo frontend (:80) e api-gateway (:8080) restano pubblicati sull'host. `docker-compose.yml` invariato (dev). Verificato con `docker compose ... config`. Uso: `docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile observability --profile backup up -d` (2026-07-17: loki/grafana/zipkin/alertmanager/prometheus/db-backup sono ora dietro profilo opt-in in `docker-compose.yml` per uno stack dev più leggero — i flag `--profile` sono obbligatori anche in prod, un override non riattiva un servizio gated). OWASP A05 |
| P3 | ~~Backup PostgreSQL automatizzato (pg_dump cron)~~ | 🟡 **Parziale** | — | Container `db-backup` in `docker-compose.yml`: `pg_dumpall` ogni 24h (configurabile), gzip, retention 14gg, volume dedicato `postgres_backups`, nessuna porta host. Verificato con dump reale (240K, 46 CREATE TABLE/DATABASE). RPO 24h accettato esplicitamente, non PITR (`backup/DECISIONS.md §3.5`). Restano da fare: copia esterna cifrata automatica (S3/B2) — oggi il volume Docker resta single-host, single-disk, single point of failure fisico — e un test di restore end-to-end reale (piano di rifinitura Fase 6, item 4/6) |
| P4 | ~~Prometheus alert rules (error rate, latency, restarts)~~ | ✅ **Fatto** | — | `alert_rules.yml` con 6 regole (ServiceDown/HighErrorRate/HighLatencyP99/JvmHeapHigh/CircuitBreakerOpen/DbConnectionPoolNearExhaustion); Alertmanager v0.27 aggiunto a compose; histogrammi Micrometer abilitati via shared `config/application.yml` |
| P5 | ~~Operations Runbook~~ | ✅ **Fatto** | — | `docs/OPERATIONS_RUNBOOK.md` creato con 10 procedure operative |
| P6 | ~~GIN index + `pg_trgm` su `GuestRepository`~~ | ✅ **Fatto** | — | Flyway V7 `V7__add_trgm_search_indexes.sql`: 4 indici GIN su first_name/last_name/email/city, espressione `lower(col)` verificata combaciare esattamente con la query JPQL. Gap noto (audit 2026-07-25): guest-service non ha Testcontainers, quindi V7 (unica migration con `CREATE EXTENSION`, operazione privilegiata) non è mai validata contro Postgres reale in CI |
| P7 | ~~Testcontainers su billing-service e frontdesk-service~~ | ✅ **Fatto** | — | `InvoiceServiceIntegrationTest` (billing, `@DataJpaTest`) + `RoomTypeServiceIntegrationTest` (frontdesk, `@SpringBootTest`) su PostgreSQL reale via Testcontainers. `@Testcontainers(disabledWithoutDocker=true)` skippa localmente (quindi "CI verde" non garantisce che siano girati, se il runner non ha Docker). CI verde dopo 4 fix iterativi su contesto full-boot frontdesk (config placeholders, mock bean Alloggiati, health Redis, `RedisReactiveAutoConfiguration` exclude). Commit `1c171cf`. |
| P8 | ~~Credenziali Alloggiati PS configurabili da UI~~ | ✅ **Fatto** | — | Ogni hotel può configurare username/password/WsKey propri da `HotelProfile`; `AlloggiatiWebSenderServiceImpl` risolve le credenziali per-hotel ad ogni invio, fallback automatico alle env var globali se non configurate (nessuna rottura per il pilota attuale). Password/WsKey cifrate at-rest (AES-256-GCM, Spring Security Crypto `Encryptors.delux`), write-only (mai restituite dalla GET, blank in PUT = non modificare). Flyway V4. Verificato end-to-end: cifratura reale in DB, persistenza dopo reload, semantica "blank=non modificare" |
| P9 | ~~Dependabot auto-PR per aggiornamenti dipendenze~~ | ✅ **Fatto** | — | `.github/dependabot.yml` creato (gradle, npm, github-actions, docker × 9 directory — notification-service aggiunto 2026-07-25, mancava); `dependabot_security_updates` abilitato via API (verificato live: `automated-security-fixes` enabled, `vulnerability-alerts` enabled); alert vulnerabilità già attivi. Gap noto: le immagini infra di `docker-compose.yml` (postgres/redis/loki/grafana/zipkin/prometheus/alertmanager) non sono monitorate, solo i Dockerfile dei servizi |
| P11 | ~~Validazione frontend con Zod~~ | ✅ **Fatto** | — | `zod` 4.4.3 aggiunto come dipendenza diretta (ADR-002 verificato: MIT, mantenuto attivamente, 1 sola CVE storica 2023 patchata). Adottato su GuestFormModal, RoomFormModal, RoomTypeFormModal, HotelProfile (P.IVA/codice fiscale/URL) — errori inline accessibili (aria-invalid/aria-describedby). Completato anche su ReservationForm (date + ospiti previsti, chiuso un gap reale: nessun controllo checkout>checkin) e CheckInForm/WalkInCheckInForm (validazione Alloggiati condivisa via `validateAlloggiatiGuests`, deduplica un loop di 25 righe copiato identico nei due file) |
| P10 | ~~Refactor file grossi (SRP) — `StayServiceImpl`, `Stays.tsx`, `SettingsModal.tsx`, `AdminUsers.tsx`, `StayGuestFieldSection.tsx`~~ | ⚠️ **Parziale** (corretto 2026-07-25 dopo audit — la voce precedente sovrastimava il lavoro fatto) | — | `SettingsModal.tsx` **davvero eliminata** (sostituita da `pages/Settings/`). `AdminUsers.tsx` e `StayGuestFieldSection.tsx`: i sotto-component citati (CreateUserModal/ResetPasswordModal/UserRow; LookupOptionItem/LookupAutocomplete/StatoSelect/ComuneAutocomplete) esistono e sono usati, ma sono `const` interne allo **stesso file** (422 e 497 righe) — **non è mai stato estratto nulla in file separati**, nonostante la voce originale dicesse "già splittata". `StayServiceImpl.java`: helper privati presenti, `CheckInContext` è file separato ma pre-esisteva da ADR-001 (non estratto da questo lavoro) — il file **non è mai stato ridotto**, anzi è cresciuto 543→591 righe nei commit successivi. `Stays.tsx`: l'estrazione di `AlloggiatiReportSection` è reale (129 righe, test propri) ma i numeri qui sopra erano sbagliati (il commit `eadb7a7` ha portato Stays.tsx da 500→393 righe, non 495→440) — e da allora è **ricresciuto a 472 righe** (~74% del guadagno perso). Nessun lavoro di refactor aggiuntivo pianificato ora — item chiuso solo come "onestà del tracciamento", il debito tecnico resta |
| P12 | ~~Branch protection su `main`~~ | ✅ **Fatto** | — | Required status check: Backend + Frontend CI job (non i job matrix Trivy/CodeQL — nomi dinamici, fragili da pinnare); no force-push/delete; `enforce_admins=false` (owner unico, evita self-lockout) |
| P13 | ~~CodeQL query suite `default` → `security-extended`~~ | ✅ **Fatto** | — | Default setup aggiornato via API (`query_suite=extended`), verificato con run completo su 3 linguaggi (java-kotlin, javascript-typescript, actions) |
| P14 | ~~Alloggiati Web SOAP client custom (RestTemplate + XML manuale)~~ | ✅ **Fatto** | — | **Decisione**: mantieni implementazione custom. Solo 2 operazioni SOAP (GenerateToken/SendTest) contro un WSDL Polizia di Stato non standard — Spring-WS/CXF sarebbe overkill (ADR-002). Rivalutare solo se il numero di operazioni SOAP cresce. |
| P15 | ~~Coverage frontend: soglia ratchet-down 63/50/58/66% invece del 95% mandato~~ | ✅ **Fatto** | — | Soglia reale ≥80% accettata come target (non 95%, deroga esplicita): Stmts 90.72/Branches 80.12/Funcs 88.57/Lines 92.68%. `vite.config.ts` thresholds aggiornati a 90/80/88/92. CI ora gira `npm run test:coverage` (non più `npm run test` senza coverage) — gate enforced. Trovato e corretto in corso d'opera un bug reale pre-esistente nelle form P11 Zod: `<form>` senza `noValidate` faceva bloccare il submit dalla validazione nativa HTML5 (`type=email`/`type=number` min=) prima che Zod validasse mai (GuestFormModal, RoomTypeFormModal) |
| E0bis | ~~**Consolidamento `frontdesk-service`** (merge inventory+reservation+stay) — ADR-001~~ | ✅ **Fatto** | — | Mergiato su `main`. Bounded context coeso sul ciclo-vita camera (Room↔Reservation↔Stay) ora in un solo servizio, con FK reali **a livello database** (`fk_rli_room`, `fk_stays_room`, ecc. — no più 3 round-trip Feign interni). Da 9 a **8** deployable (corretto 2026-07-25: `pdf-template-engine` è una libreria Java, non un servizio deployable — non 7). Precisazione (audit 2026-07-25): le FK sono reali solo a livello schema/DB — il grafo JPA non è stato remappato, solo `Room→RoomType` e `ReservationLineItem→Reservation` sono `@ManyToOne` reali; `Stay` non ha alcuna relazione JPA e resta `UUID roomId/reservationId/guestId` grezzo, esattamente come quando erano microservizi separati. Verificato con smoke-test browser end-to-end (prenotazione→check-in→F&B→fattura→checkout) sul servizio consolidato, oltre a build/test automatici |

---

## Sprint 2 — Quick wins commerciali
*Orizzonte: 2-3 mesi, team 2 persone*

Feature necessarie per la vendibilità del prodotto.

| # | Implementazione | Priorità | Effort | Impatto |
|---|---|---|---|---|
| C1 | ~~Email/SMS conferme prenotazione (notification-service)~~ | ✅ **Fatto** | — | `notification-service` (porta 8088): reservation-confirmed + checkout email (Thymeleaf it/en, Resilience4j fallback non-bloccante). Toggle granulare per tipo email + subject/saluto/logo personalizzabili da Impostazioni→Sistema. Retry manuale su fallimento (badge UI). Check-in email **non implementata** (mai cablata in `StayServiceImpl`, out of scope esplicito) — solo SMS resta non fatto |
| C2 | ~~Numerazione fattura sequenziale certificata~~ | ✅ **Fatto** | — | `InvoiceSequence` + lock pessimistico PESSIMISTIC_WRITE; formato `YYYY/NNNN` per hotel+anno; Flyway V5; constraint unico su (hotel_id, invoice_number) |
| C3 | ~~IVA disaggregata nella fattura PDF~~ | ✅ **Fatto** | — | `computeVatBreakdown` raggruppa charges per aliquota; PDF mostra righe `Imponibile X%` + `IVA X%` solo per FATTURA; Flyway V6 aggiunge `vat_rate` su `invoice_charges` |
| C4 | Report KPI avanzati (RevPAR, ADR, GOPPAR, Occupancy) | 🟡 Alta | 1-2 sett | Dashboard ha KPI operativi base (arrivi/partenze/camere) — mancano RevPAR/ADR/GOPPAR |
| C5 | Mobile PWA (responsive ottimizzato) | 🟡 Alta | 2-4 sett | 70% già fatto — housekeeping e front desk usano telefono |
| C6 | Wizard onboarding primo avvio | 🟡 Media | 1 sett | Sequenza configurazione non guidata — rischio errori setup |
| C7 | ~~Vista occupazione rapida nella dashboard~~ | ✅ **Fatto** | — | `RoomCell` grid per status camere implementata in `Dashboard.tsx` |
| C8 | Multi-currency (campo `currency` in HotelSettings) | 🟢 Bassa | 3-5gg | Hotel in zona turistica internazionale o al confine |
| C9 | Grafana Tempo + OpenTelemetry (migrazione da Zipkin) | 🟢 Bassa | 3-5gg | Loki ✅ già presente — manca solo migrazione Zipkin → Tempo per distributed tracing |
| C10 | TailwindCSS 3 → 4 | 🟢 Bassa | 1-2gg | Breaking changes CSS — pianificare con tempo dedicato (`backup/DECISIONS.md §7.1`) |
| C11 | ~~CONTRIBUTING.md — guida contribuzione e onboarding dev~~ | ✅ **Fatto** | — | `CONTRIBUTING.md` presente nella root del repository |
| C12 | ~~Ricerca e paginazione server-side (Guests, Billing, Reservations)~~ | ✅ **Fatto** | — | **Guests** (`81abdc8`): `getAllGuests()` teneva solo `.content` di un endpoint già paginato (default 20), mostrando sempre e solo i primi 20 ospiti senza modo di vederne altri; ricerca filtrava lato client dentro quella slice troncata. **Billing** (`4e84fbb`): nuovo `GET /invoices/search` combinabile (status, intervallo date, query testuale su invoiceNumber o nome/email ospite via cross-service call a guest-service). **Reservations** (`acc362b`): nuovo `GET /reservations/search` combinabile (upcomingOnly, query su nome/email ospite); scope limitato alla sola lista tabellare, planning-board/dashboard restano su `getAllReservations` invariato (serve loro l'intero set per il calendario). Pattern comune ai 3: cross-service guest-name resolution via `searchGuests`+`getGuestsBatch`, mai una query N+1. |
| C13 | ~~Personalizzazione strutturale layout PDF fattura~~ | ✅ **Fatto** | — | Deciso (LLM Council + scelta esplicita utente): **niente builder self-service** — struttura dev-authored, modifiche su richiesta cliente passano dal programmatore (nessuna superficie di editor/XSS/CSS-injection da proteggere). Nuovo modulo Gradle riutilizzabile `pdf-template-engine` (libreria Java pura, no Spring — Thymeleaf standalone + `openhtmltopdf` 1.1.40, LGPL-2.1+, verificato mantenuto/no-CVE prima dell'adozione), API generica `PdfTemplateRenderer.render(templateName, context)`. `PdfInvoiceServiceImpl` riscritto da disegno PDFBox imperativo a costruzione di un context map + 2 template Thymeleaf (`invoice-fattura.html` con scorporo IVA, `invoice-ricevuta.html` senza — `DocumentType` già esistente, non nuova funzionalità) in `billing-service/src/main/resources/templates/pdf/`. Logo: **mai admin-uploaded** (superficie di attacco: path traversal, decompression bomb, dimensioni incontrollate) — asset classpath dev-only (`static/pdf/logo.png`, opzionale, embedded come data URI, nessuna risoluzione filesystem/rete nel renderer). **Aggiunto PDF/UA (ISO 14289)**: `usePdfUaAccessibility(true)` sempre attivo nel renderer (non opt-in), font Noto Sans (SIL OFL-1.1) embeddato — PDF/UA vieta i font built-in non embeddati — `<h1>` semantico per il titolo documento, `<caption>` sulle tabelle addebiti/pagamenti, `alt` descrittivo sul logo, `lang="it"`/`<title>`/meta dinamici. Verificato con PDFBox reale (non solo dichiarato): `/MarkInfo` marked=true, `/StructTreeRoot` presente, `/Lang` propagato — test dedicato nel modulo. Commit `715499a` + refactor PDF/UA successivo. |

---

## Sprint 3 — Feature enterprise core
*Orizzonte: 3-6 mesi, team 2-3 persone*

Feature che abilitano la competizione con PMS commerciali.

| # | Implementazione | Priorità | Effort | Dipendenze | Impatto |
|---|---|---|---|---|---|
| E1 | Channel Manager OTA (via intermediario SiteMinder/RateTiger) | 🔴 Critica | 1-2 mesi | Certificazione OTA 2-6 mesi | Sblocca 80% mercato — senza, non nella shortlist di valutazione |
| E2 | ~~Booking Engine + pagamento online (Stripe Checkout)~~ | ⚫ **Fuori scope per design** | — | — | Decisione esplicita in `backup/DECISIONS.md §2.3`: nessuna integrazione con gateway di pagamento online (Stripe/Nexi/PayPal) — superficie di attacco (PCI DSS, gestione carte) non giustificata per il perimetro attuale del prodotto. Contraddiceva questa riga, ora allineata (verificato 2026-07-27) |
| E3 | ~~Fattura elettronica: export FatturaPA FPR12~~ | ✅ **Fatto** (2026-08-03) | — | — | Validazione XSD contro lo schema ufficiale AE, snapshot immutabile per export (`invoice_fiscal_exports`: bytes+SHA-256+timestamp), fattura bloccata da mutazione (`INVOICE_LOCKED_AFTER_EXPORT`) dopo il primo export, endpoint batch ZIP+indice CSV per periodo. **Decisione**: hotel-pms non trasmette a SDI direttamente — produce export corretti pronti per import nel gestionale del cliente/commercialista (TeamSystem, Zucchetti, Danea, ...). Motivo: possedere la trasmissione implica possedere la conservazione sostitutiva (accreditamento, responsabilità legale 10 anni) — costo sproporzionato pre-lancio. Vedi E3bis per il disegno (non implementato) della trasmissione diretta. |
| E3bis | SdiProvider — trasmissione diretta SDI | 🟢 Bassa (finché non richiesta) | 1-2 sett | E3 | Interfaccia pensata ma non scritta: A-Cube come prima scelta (verificato — sandbox self-service reale, unico provider con Preservation+corrispettivi+SDI in un account), credenziali per-hotel cifrate sullo stesso modello già usato per Alloggiati Web. Non implementare finché non richiesta da un cliente pagante o come feature premium — un'interfaccia senza implementazione è solo codice morto da mantenere. |
| E4 | API pubblica documentata + webhook system | 🟡 Alta | 2-3 sett | Nessuna | Ecosistema ISV — senza, nessun partner può integrarsi |
| E5 | Migrazione **K3s** (non K8s pieno) — vedi ADR-003 in `backup/DECISIONS.md` | 🟡 Alta | 1-2 sett | Container già stateless e K8s-ready by design; consolidamento frontdesk (E0bis) completato prima | Scaling orizzontale, rolling update, failover automatico, a costo ridotto (no control plane gestito). Trigger migrazione a K8s pieno (GKE Standard): soglia clienti (~140, breakeven Professional), soglia carico RPS/CPU, o necessità multi-region/HA (E6) |
| E6 | PostgreSQL HA (replica + failover automatico) | 🟡 Alta | 1-2 sett | E5 (K8s) o Patroni | Single node attuale = SPOF per i dati |
| E7 | Secrets → HashiCorp Vault o cloud KMS | 🟡 Alta | 2-3gg | E5 | Env var su disco non adeguato per server condivisi/prod |
| E7bis | ~~HMAC service-to-service: anti-replay timestamp+nonce~~ | ✅ **Fatto** | — | Payload firmato esteso a `username:role:hotelId:timestamp:nonce`; finestra tolleranza 60s + `NonceStore` backed Redis (SETNX, TTL 120s) su tutti e 5 i servizi validator. Migrazione completa a mTLS/OAuth2 client-credentials resta possibile in futuro ma non più urgente — il gap di replay indefinito è chiuso (T-GW-08, commit `5dd8ed8`) |
| E8 | Online check-in guest self-service | 🟡 Media | 1-2 mesi | C1 (notification-service) | Dati Alloggiati pre-compilati — riduce tempo al check-in |
| E9 | Two-factor authentication (TOTP/FIDO2) | 🟡 Media | 1-2 sett | auth-service | GDPR Art. 32 — accesso ADMIN a PII senza 2FA è gap enterprise |
| E10 | Spring Cloud Contract (consumer-driven contract testing) | 🟢 Media | 1 sett | Nessuna | Rileva breaking change tra microservizi prima del deploy |
| E11 | Multi-hotel UI per catena alberghiera | 🟢 Media | M | `hotel_id` già pronto — serve solo selector UI | Clienti catena — architettura multi-tenant già predisposta |
| E12 | Configurazione IVA per tipologia (10% camere, 22% F&B) | 🟡 Alta | M | C2 (numerazione fattura) | Conformità fiscale italiana — aliquote diverse per categoria |
| E13 | Audit log immutabile append-only (GDPR Art. 30) | 🟡 Media | M | Nessuna | Registro trattamenti PII non modificabile — GDPR enterprise. Caso d'uso concreto: snapshot immutabile del payload Alloggiati (TXT/JSON) al momento esatto della generazione/invio reale a Questura — oggi entrambi i formati sono ricalcolati on-demand dal DB live, quindi una rigenerazione successiva può non coincidere più con ciò che fu davvero trasmesso se i dati ospite sono cambiati nel frattempo. Risolve il gap e fornisce prova storica di cosa è stato dichiarato. Tocca categorie "audit logging" — va su `feature/secure-coding-hardening` |
| E14 | Rate limiting per-utente granulare | 🟢 Media | S | Redis già presente | Credential stuffing da IP distribuiti non bloccato dall'attuale per-IP |
| E15 | SLA monitoring e status page | 🟢 Media | M | Prometheus già presente | Contratto SaaS richiede uptime dichiarato |
| E16 | Google Hotel Ads integration | 🟡 Media | 2-3 sett | E2 (Booking Engine) | Google Hotels mostra il "prezzo diretto" se il booking engine è accreditato |
| E17 | ROSS1000 — rilevazione statistica turistica regionale (ISTAT/SISTAN) | 🟢 Bassa | 3-5gg | Cliente reale in una regione coperta | Obbligo di legge (D.Lgs. 322/1989 art. 7, sanzioni fino a €2.500/mese) ma copre solo ~13/20 regioni italiane via piattaforma GIES (Piemonte, Veneto, Emilia-Romagna, Marche, Lombardia, Calabria, Sardegna, Liguria, Basilicata, Lazio, Molise, Abruzzo, Toscana-Firenze/Pistoia/Prato) — le altre regioni usano sistemi diversi non ancora ricercati. Oggi l'adempimento manuale via portale regionale resta legale e sufficiente. Protocollo verificato: SOAP, un WSDL pubblico per regione (`.../ws/checkinV2?wsdl`), autenticazione HTTP Basic, payload giornaliero (apertura/camere occupate-disponibili/letti + arrivi/partenze/prenotazioni/rettifiche idempotenti) — riusa le stesse tabelle Comuni/Nazioni/TipoAlloggiato Polizia di Stato già implementate per Alloggiati Web (`AlloggiatiCsvParser` + repository). Mancano 6 campi dominio (tipoturismo, mezzotrasporto, canaleprenotazione, titolostudio, professione, esenzioneimposta) in Guest/Reservation. **Non implementare finché non c'è un cliente pagante in una delle regioni coperte che lo richiede esplicitamente** — verdetto LLM Council 2026-06-22: costo di opportunità pre-revenue troppo alto, copertura regionale parziale, nessuna libreria terza disponibile (stesso caso Alloggiati: stub generabile da WSDL reale invece di XML a mano, quando attivato) |
| E18 | Imposta di soggiorno (tassa di soggiorno comunale) | 🔴 Alta | 1-2 sett | C2 (numerazione fattura) | Trovato da `docs/COMPLIANCE_AUDIT_2026-08.md` §4 — **gap mai tracciato prima**, non un'ipotesi. Il gestore è responsabile diretto della riscossione e del versamento (Cassazione 1527/2026, Modello 21 abolito), dichiarazione telematica annuale entro il 30 giugno, versamento mensile via portale comunale. `ChargeType` non ha alcuna voce dedicata, nessun calcolo per notte/ospite, nessuna esenzione, nessuna configurazione per-comune (le regole variano per regolamento comunale, nessuno standard nazionale unico) |
| E19 | Corrispettivi telematici (Horeca) | 🔴 Alta | Da definire con un commercialista prima di stimare | Nessuna | Trovato da `docs/COMPLIANCE_AUDIT_2026-08.md` §5 — **obbligo di legge già in vigore dal 2026-01-01** per l'intero settore Horeca (hotel+ristorazione), non un gap futuro. Prima verificare con un commercialista se/come il pernottamento stesso vi ricade (oggi il sistema non ha comunque un caso d'uso "vendita F&B diretta a non alloggiato", `stayId` è `NOT NULL` su ogni ordine) — poi eventualmente implementare collegamento POS↔registratore telematico via portale "Fatture e Corrispettivi" AE |
| E20 | Antiriciclaggio (D.Lgs. 231/2007) — valutazione scritta | 🟢 Bassa | Poche ore (solo valutazione, non implementazione) | Nessuna | Trovato da `docs/COMPLIANCE_AUDIT_2026-08.md` §9 — rischio sostanziale basso (identificazione ospite già coperta da TULPS/Alloggiati, che vieta pure la conservazione di copie documento), ma **zero valutazione scritta** in tutto il progetto, a differenza di conservazione sostitutiva e PCI-DSS che hanno entrambe una decisione esplicita motivata. Item quasi gratuito: basta produrre e archiviare la stessa valutazione già fatta qui, non serve altro codice |
| E21 | Allergeni F&B (Reg. UE 1169/2011) | 🟡 Media | 2-3gg | Nessuna | Trovato da `docs/COMPLIANCE_AUDIT_2026-08.md` §9bis (aggiornamento 2026-08-04) — `MenuItem` non ha alcun campo allergeni/ingredienti strutturato, obbligo informativo sui 14 allergeni maggiori per alimenti non preimballati. Flyway + campo strutturato (non testo libero in `description`) + visualizzazione in `Restaurant.tsx`/`OrderFormModal.tsx` |
| E22 | GDPR — retention `StayGuest` (frontdesk-service) | 🔴 Alta | 3-5gg | `GuestRetentionJobServiceImpl` esistente come pattern | Trovato da `docs/COMPLIANCE_AUDIT_2026-08.md` §3 (aggiornamento 2026-08-04) — il job notturno di anonimizzazione copre solo `guest-service.Guest`; `frontdesk-service/.../StayGuest` conserva per sempre una copia indipendente della PII Alloggiati (documento+cittadinanza) per ogni soggiorno storico, mai toccata. Va su `feature/secure-coding-hardening` (tocca audit/PII trattamento). **Ancora aperto** — non toccato da T-GST-06 (quello era la guardia che *blocca* l'anonimizzazione di `Guest`, non l'assenza di un job equivalente su `StayGuest`, bug distinto) |
| E23 | GDPR Art. 32 — cifratura a riposo `documentNumber` | 🟡 Media | 3-5gg | Nessuna | Trovato da `docs/COMPLIANCE_AUDIT_2026-08.md` §3 (aggiornamento 2026-08-04) — in chiaro in `guest-service.IdentityDocument` e `frontdesk-service.StayGuest`, zero `AttributeConverter` nel repo (a differenza delle credenziali Alloggiati, già cifrate). Va su `feature/secure-coding-hardening` |
| E24 | ~~RBAC — `@PreAuthorize` su `GuestController`/`GuestPrivacySettingsController`~~ | ✅ **Fatto** | — | — | Trovato da `docs/COMPLIANCE_AUDIT_2026-08.md` §3, confermato dal round 2 del test esplorativo (bug #6/#7) e risolto come T-GST-07 (`a87d329`, `feature/secure-coding-hardening` → `main`): `@PreAuthorize("hasAnyRole('ADMIN','OWNER')")` su `deleteGuest`/`exportGuestData` e a livello classe su `GuestPrivacySettingsController`. Lo stesso giro ha risolto anche T-BILL-07 (RBAC su `InvoiceController`, trovato solo dal test esplorativo, non dall'audit normativo) |

---

## Sprint 4 — Revenue e differenziazione
*Orizzonte: 6-12 mesi, team 3-4 persone*

Feature di differenziazione competitiva ad alto impatto ROI.

| # | Implementazione | Effort | Impatto |
|---|---|---|---|
| R1 | Revenue Management con dynamic pricing | XL (4-6 mesi) | +10-20% RevPAR stimato |
| R2 | CRM ospiti avanzato (preference history, segmentazione, loyalty) | L (2-3 mesi) | Retention e upsell post-stay |
| R3 | Marketplace integrazioni (serrature smart, contabilità, CRM esterni) | L (post E4 API pubblica) | Ecosistema partner |
| R4 | Housekeeping mobile ottimizzata (task assignment, foto prova) | M (2-3 sett) | Personale pulizie usa telefono — gap operativo reale |
| R5 | Notifiche push/email operativi (camera pronta, check-in atteso) | M (1-2 sett) | Coordinamento front desk — housekeeping |
| R6 | Multi-region / HA cross-AZ | L | Uptime enterprise, disaster recovery geografico |
| R7 | AI/ML pricing engine avanzato | XL (6-9 mesi) | Ottimizzazione tariffe basata su dati storici, comp set, eventi — oltre le regole statiche di R1 |

---

## Confronto con PMS commerciali

| Funzionalità | hotel-pms | Mews / Opera Cloud | Gap |
|---|---|---|---|
| Gestione prenotazioni | ✅ Completa | ✅ + group, waitlist | Group bookings, waitlist |
| Channel Manager OTA | ❌ Assente | ✅ 400-1000+ canali | **Gap commerciale critico** |
| Revenue Management | ❌ Assente | ✅ Dynamic pricing | +10-20% RevPAR |
| Fatturazione | ✅ PDF + export FatturaPA validato XSD/immutabile, IVA disaggregata, numerazione legale | ✅ Trasmissione SDI diretta, multi-valuta, folio | Trasmissione diretta SDI (E3bis), multi-valuta |
| CRM ospiti | ⚠️ Profilo base | ✅ Loyalty, marketing | Retention, upsell |
| Housekeeping | ⚠️ Desktop only | ✅ App mobile nativa | Mobile, task assignment |
| F&B / POS | ✅ Nativo integrato | ✅ + table mgmt, kitchen | Gestione tavoli, stampante cucina |
| Alloggiati PS | ✅ **Nativo SOAP** | ⚠️ Plugin terze parti | **hotel-pms vince** |
| Sicurezza auth | ✅ Argon2id + HMAC | ⚠️ Varia per vendor | **hotel-pms superiore** |
| Osservabilità | ✅ Stack completo | ⚠️ Varia per vendor | **hotel-pms superiore** |
| API pubblica | ❌ Assente | ✅ 400-1000+ partner | Ecosistema integrazioni |
| Mobile | ❌ PWA non implementata | ✅ App nativa iOS/Android | Gap operativo reale |
| Multi-property | ✅ Architettura pronta | ✅ Dashboard cross-hotel | Solo UI selector mancante |

---

## Timeline e investimento per Enterprise SaaS

| Fase | Durata | Team | Output |
|---|---|---|---|
| Production-ready | ~~4-6 sett~~ **~1 sett residua** | 1 dev | ~~@Version ✅~~ ~~restart ✅~~ ~~runbook ✅~~ ~~GIN ✅~~ — resta: backup DB, alert rules, Dependabot |
| Quick wins commerciali | 2-3 mesi | 2 persone | Email, mobile, KPI avanzati, fattura legale (C2 sequenziale) |
| Enterprise core | 3-6 mesi | 2-3 persone | Channel manager, booking engine, K8s |
| Enterprise SaaS | 6-12 mesi | 3-4 persone | API pubblica, revenue mgmt, HA |

**Investimento anno 1 stimato (team 4-5 FTE):** €265.000-330.000

**Punto di pareggio:** ~140 clienti a €199/mese (Professional tier)
— mercato di riferimento: 350.000 strutture ricettive registrate in Italia.

---

## Pricing commerciale SaaS

Modello di riferimento per il lancio commerciale.

| Piano | Target | Canone | Setup | Utenti | F&B POS | Channel Manager* |
|---|---|---|---|---|---|---|
| **Starter** | B&B, 1-15 camere | **€99/mese** | €500 | 3 | ❌ | ❌ |
| **Professional** | Hotel, 15-50 camere | **€199/mese** | €1.500 | 10 | ✅ | ✅ |
| **Enterprise** | Hotel/catena, 50+ | **€349/mese** | €3.000+ | Illimitati | ✅ | ✅ |

*\*Quando sviluppato (Sprint 3 E1)*

| Piano | Revenue/anno | Costo infra/anno | Margine |
|---|---|---|---|
| Starter | €1.188 | ~€600 | **49%** |
| Professional | €2.388 | ~€900 | **62%** |
| Enterprise | €4.188 | ~€1.200 | **71%** |

**Strategia lancio:** Primi 10 hotel pilota a €149/mese con setup al 50%.
Dopo 10 clienti, prezzo pieno €199/mese. I pilota mantengono il prezzo per 12 mesi.

**Costi extra:** personalizzazioni €80/ora · migrazione dati €500-1.500 ·
formazione €150/sessione · hotel aggiuntivo (catena) +€79/mese.

---

## Documentazione enterprise mancante

Documenti necessari per il livello enterprise che richiedono produzione
(alcuni necessitano revisione legale prima della pubblicazione).

| Documento | Priorità | Chi lo produce | Effort | Obbligatorio |
|---|---|---|---|---|
| Privacy Policy (GDPR Art. 13/14 — informativa agli interessati) | 🔴 Alta | Legal | 2-4h con template | **Sì — GDPR** |
| Data Processing Agreement (DPA GDPR Art. 28) | 🔴 Alta | Legal | 4-8h | **Sì — GDPR** |
| Cookie Policy (JWT httpOnly + CSRF + refresh token) | 🔴 Alta | Legal | 1-2h | **Sì — ePrivacy Directive** |
| Terms of Service | 🟡 Media | Legal + Product | 4h | No (ma atteso da clienti enterprise) |
| SLA document (uptime, tempi risposta supporto) | 🟡 Media | Product + DevOps | 2h | No (ma atteso da clienti enterprise) |
| Capacity Planning (n. hotel per server, dimensionamento) | 🟢 Bassa | DevOps | 4h | No |

*I documenti contrassegnati come obbligatori richiedono revisione legale prima della pubblicazione.*

### Scoping tecnico (Fase 8 piano di rifinitura, item 19 — 2026-07-30, brief consegnabile 2026-07-31)

Il contenuto dei tre documenti obbligatori è materia legale, non generabile
da questo lavoro. Il brief completo e consegnabile a un consulente legale è
**`docs/legal/PRIVACY_DPA_COOKIE_BRIEF.md`** — dati raccolti per categoria di
interessato, basi giuridiche (TULPS vs contratto/consenso), retention reale
(5 anni TULPS / 10 anni fiscale, `THREAT_MODEL.md` T-GST-05), sub-processor
reali (incluso Backblaze B2, ora effettivamente configurato — Fase 6 item 4,
region UE ma società USA, domanda aperta sul trasferimento extra-UE),
inventario cookie, misure tecniche verificabili per il DPA Art. 32, e i
deliverable esatti attesi (formato, dove verranno esposti).

**Esposizione in UI (deciso, non ancora implementato)**: route lazy-loaded
`/legal/privacy` e `/legal/cookies` (pattern identico alle altre pagine
lazy in `App.tsx`), link dal footer di `AuthLayout.tsx` (visibile
pre-login) e dall'hub `Settings.tsx`. **Nessuna di queste route/link va
creata prima che il testo reale sia disponibile** — un placeholder legale
esposto in produzione (anche con disclaimer "bozza") è un rischio peggiore
di nessun link, essendo un documento che utenti e ospiti potrebbero
legittimamente fare affidamento. Il lavoro tecnico residuo quando il testo
arriva è ~1-2h (componente pagina + route + 2 link), non bloccante.

---

*Documento aggiornato 2026-08-04. Fare riferimento a
[`docs/FINAL_AUDIT_ULTRA_SEVERE.md`](FINAL_AUDIT_ULTRA_SEVERE.md)
per i gap tecnici specifici e le evidenze da codice.*
