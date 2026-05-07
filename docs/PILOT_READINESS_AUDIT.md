# Audit Tecnico-Funzionale — Hotel PMS Pilot Readiness

**Data audit iniziale:** 2026-05-05  
**Ultimo aggiornamento:** 2026-05-07  
**Branch analizzato:** `feature/frontend-development`  
**Scope:** Valutazione completa della readiness per pilot con albergatori reali

---

## 1. Executive Assessment

**Aggiornamento 2026-05-07 — READY FOR PILOT WITH MINOR RESIDUAL RISKS**

Tutti i bloccanti identificati nell'audit iniziale (B1–B5) sono stati risolti. Il sistema è ora pronto per un pilot controllato con albergatori reali. Residuano due elementi non bloccanti: il collaudo SOAP Alloggiati con credenziali PS reali (richiede accesso VPN alla Questura), e la mancanza di Dependabot per aggiornamenti automatici delle dipendenze.

**Assesment originale 2026-05-05:** Il progetto era *quasi pronto* ma con condizioni non negoziabili da soddisfare in 2–3 settimane. I bloccanti reali erano: RBAC assente, walk-in impossibile, nessuna UI gestione utenti, credenziali default non protette, `.env.example` mancante. L'architettura era già genuinamente solida: 8 microservizi con responsabilità ben separate, JWT httpOnly + HMAC internal auth + CSRF + rate limiting Redis, hotel_id in tutte le repository, Flyway in tutti i servizi, GlobalExceptionHandler RFC 7807, CircuitBreaker su tutti i Feign client, Zipkin + Prometheus + Grafana + Loki.

---

## 2. Bloccanti prima del Pilot

| # | Area | Rischio | Impatto | Stato |
|---|---|---|---|---|
| **B1** | RBAC — ruoli incoerenti e non enforced | Critico | Receptionist poteva accedere a qualsiasi endpoint | ✅ Risolto — `OWNER` aggiunto al Role enum, route-level RBAC nel gateway, `@PreAuthorize` su endpoint amministrativi |
| **B2** | Walk-in check-in mancante | Alto | 15–40% dei check-in impossibili | ✅ Risolto — `reservationId` nullable in `StayRequest`, route `/stays/walk-in`, `WalkInCheckInForm.tsx` con payload Alloggiati completo |
| **B3** | Nessuna UI gestione utenti | Alto | ADMIN non poteva creare/disattivare account receptionist | ✅ Risolto — pagina `/admin/users` con CRUD utenti, soft delete, ruolo/hotel assignment |
| **B4** | Credenziali default senza protezione | Alto | `admin`/`password` accessibile senza cambio obbligatorio | ✅ Risolto — flag `mustChangePassword` su `UserAccount`, redirect al primo login, blocco operazioni |
| **B5** | `ALLOGGIATI_*` senza `.env.example` | Medio | Installatore non sapeva quali variabili configurare | ✅ Risolto — `.env.example` creato con tutte le variabili e commenti; startup check HMAC bloccante |

---

## 3. Gap Enterprise — Semaforo

| Area | Stato | Note |
|---|---|---|
| **Architettura e modularità** | 🟢 Verde | 8 microservizi con confini chiari, database separati, Spring Cloud Config, API Gateway centralizzato. Pronto per scaling orizzontale. |
| **Qualità del codice** | 🟢 Verde | PMD `gradle-java-qa` zero-warning enforced, Checkstyle, SpotBugs, Lombok, MapStruct. Frontend TypeScript strict, ESLint zero-warning. |
| **Sicurezza** | 🟡 Giallo | JWT httpOnly, HMAC inter-service, CSRF, rate limiting Redis, RBAC multi-ruolo, `mustChangePassword`. Residuale: ruolo `GUEST` nell'enum non usato operativamente; gateway RBAC protegge le route admin ma non fa enforcement granulare su tutti gli endpoint di ogni servizio. |
| **Gestione configurazione e segreti** | 🟢 Verde | `.env` in `.gitignore`, Spring Cloud Config, secrets via env var, nessun hardcoding. `.env.example` presente con documentazione variabili. |
| **Migrazioni DB** | 🟢 Verde | Flyway in tutti i servizi, versioning sequenziale corretto. |
| **Logging e osservabilità** | 🟢 Verde | Zipkin + Prometheus + Grafana + Loki configurati. `X-Correlation-ID` propagato via MDC dal gateway a tutti i servizi — errori distribuiti tracciabili con un solo ID. |
| **Test automatici** | 🟢 Verde | 317 test Vitest su 50 file, copertura >95% su tutti i servizi frontend. Backend: controller e service test su tutti i 9 servizi, copertura >95%. Playwright: walk-in, admin users, checkout, billing completi. |
| **UX operativa receptionist** | 🟢 Verde | Tutti i flussi principali coperti: walk-in, check-in, checkout, billing, F&B, housekeeping, camere, gestione utenti, profilo hotel. i18n EN/IT. Residuale: nessun wizard onboarding al primo avvio, nessuna notifica push. |
| **Resilienza a errori di rete** | 🟢 Verde | CircuitBreaker Resilience4j su tutti i Feign client, fallback dichiarati, GlobalExceptionHandler RFC 7807 in tutti i servizi. Alloggiati SOAP failure non blocca il check-in. |
| **Documentazione tecnica** | 🟢 Verde | `ALLOGGIATI_README.md`, `DOCUMENTAZIONE_TECNICA_ALLOGGIATI_PS.md`, `INTERACTION_FLOWS.md`, `SECURITY_AND_PRIVACY.md`, Swagger aggregato all'API Gateway, `backup/DECISIONS.md`, `THREAT_MODEL.md`. |
| **Documentazione utente** | 🟡 Giallo | `USER_MANUAL.md` creato con flussi operativi e procedure passo-passo. Residuale: nessun tooltip contestuale nell'UI, nessuna pagina `/help` integrata nell'app. |
| **Deploy locale/staging** | 🟢 Verde | `docker-compose.yml` production-grade (15 servizi, 5 reti isolate, healthcheck, resource limits), script `start.sh/.ps1/.bat`, `setup-hmac-secret.sh/.ps1`. |
| **Multi-hotel / multi-tenant** | 🟢 Verde | `hotel_id` NOT NULL su tutte le entity rilevanti (incluso `Room.hotelId` con migration Flyway), tutte le repository filtrano per `hotel_id`, JWT porta `hotelId` verificato via HMAC. Residuale: nessuna UI per selezione hotel in un account multi-hotel. |
| **Compliance operativa Alloggiati** | 🟡 Giallo | Export 168-char conforme, CRLF corretto, SOAP two-step implementato, piano collaudo completo in `ALLOGGIATI_COLLAUDO_REALE.md`. Residuale: collaudo reale con credenziali PS ancora da eseguire (richiede accesso VPN Questura). |

---

## 4. Flussi Core

| Flusso | Stato | Note |
|---|---|---|
| **Gestione camere** | ✅ Completo | CRUD room type e room con status (AVAILABLE / OCCUPIED / DIRTY / MAINTENANCE). Multi-tenant: `hotelId` NOT NULL, scoping corretto. |
| **Prenotazione** | ✅ Completo | ReservationForm con GuestSearchAndCreate e RoomSelection, validazione date, overbooking prevention, cancellazione con conferma. |
| **Check-in (da prenotazione)** | ✅ Completo | CheckInForm con lookup Alloggiati, pre-fill da soggiorno precedente, saga OCCUPIED, auto-submit PS se abilitato. |
| **Check-in walk-in** | ✅ Completo | `WalkInCheckInForm.tsx` con selezione ospite/camera, sezione Alloggiati completa, validazione per-guest, payload `guests[]` corretto. |
| **Check-out** | ✅ Completo | Verifica billing PAID, marca camera DIRTY, aggiorna stato prenotazione. |
| **Gestione ospiti** | ✅ Completo | CRUD con GDPR soft-delete, guard 451 per soggiorni attivi, ricerca full-text, batch fetch ottimizzato, email uniqueness per hotel. |
| **Food & Beverage** | ✅ Completo | Creazione ordini con selezione menu, conferma ordine → addebito automatico su invoice soggiorno via HMAC. |
| **Billing** | ✅ Completo | Invoice aperta al check-in, addebiti F&B, pagamento con metodo, checkout bloccato se non PAID. |
| **Alloggiati PS** | ✅ Completo | Export .txt 168-char e JSON, invio SOAP two-step, badge `alloggiatiSent` per riga, toggle `alloggiatiAutoSend` nel profilo hotel. |
| **Report / export** | 🟡 Parziale | Report finanziario proprietario (OwnerDashboard) con export CSV. Mancano: report occupazione per periodo, export fatture PDF. |
| **Configurazione hotel** | ✅ Completo | Profilo hotel con nome, indirizzo, PIVA/CF, logo, toggle `alloggiatiAutoSend`. |
| **Autenticazione** | ✅ Completo | Login, refresh token silenzioso, logout, cambio password, `mustChangePassword` con blocco. JWT access 15min, refresh 7gg. |
| **Autorizzazione** | 🟡 Parziale | RBAC con ruoli ADMIN/OWNER/RECEPTIONIST. Route-level enforcement nel gateway per le route admin. `@PreAuthorize` su endpoint amministrativi. Residuale: enforcement non esaustivo su tutti i singoli endpoint di ogni microservizio. |
| **Gestione utenti** | ✅ Completo | Pagina `/admin/users` (solo ADMIN): lista, crea, disattiva/riattiva account. |

---

## 5. Rischi Residui

**Naming e convenzioni (non bloccanti):**
- `AlloggiatiRowDto` è un record Java ma il nome suggerisce un "file row" — in una codebase enterprise si chiamerebbe `AlloggiatiGuestRecord`.
- `StayGuest` e `Guest` coesistono con semantica sovrapposta non completamente documentata.
- Il frontend usa `_statoDiNascita` (underscore prefix locale per campo UI) — convenzione funzionante ma non dichiarata formalmente.

**Hardcoded residui:**
- `CODICE_ITALIA = "100000100"` ancora duplicato in `AlloggiatiReportServiceImpl` e `CheckInForm.tsx`.
- `DEFAULT_PAGE_SIZE = 20` ripetuto in più controller senza costante condivisa.
- Ruolo `GUEST` nell'enum non usato in nessun `@PreAuthorize` — semantica futura non definita.

**Setup e infrastruttura:**
- `AlloggiatiLookupDataLoader` scarica i CSV dalla PS al primo avvio: se il portale è irraggiungibile, le tabelle restano vuote senza health endpoint dedicato.
- Dependabot non configurato — aggiornamenti dipendenze manuali.
- Vincolo `commons-csv:1.9.0` / `commons-io:2.14.0` non documentato nel `build.gradle.kts`.

**UX:**
- Nessun wizard di onboarding al primo avvio.
- Nessuna notifica push/email per eventi critici (prenotazione in scadenza, camera da pulire).
- Nessuna pagina `/help` integrata nell'app.

---

## 6. Piano di Hardening — Stato Aggiornato

### Completati ✅

1. ✅ **RBAC sistema ruoli** — `OWNER` aggiunto al Role enum, route-level gateway RBAC, `@PreAuthorize` su endpoint amministrativi (`dfa3403`)
2. ✅ **Walk-in check-in** — backend nullable `reservationId`, frontend `WalkInCheckInForm.tsx` con Alloggiati (`f7e4e5e`, `f1387c3`)
3. ✅ **UI gestione utenti** — pagina `/admin/users` CRUD (`e312dcf`)
4. ✅ **mustChangePassword** — flag + redirect + blocco operazioni (`3575bf0`)
5. ✅ **`.env.example`** — tutte le variabili documentate (`3575bf0`)
6. ✅ **Startup check HMAC** — log ERROR bloccante se secret è placeholder (`3575bf0`)
7. ✅ **MDC correlationId** — `X-Correlation-ID` propagato via MDC dal gateway (`33597cf`)
8. ✅ **Test Playwright** — walk-in, admin users, checkout, billing (`8913029`)
9. ✅ **Test CalendarPlanning e Rooms** — unit test aggiunti (`8913029`)
10. ✅ **Profilo hotel configurabile** — nome, indirizzo, PIVA/CF, logo, toggle Alloggiati (`f053531`, `a849107`)
11. ✅ **Documentazione utente** — `docs/USER_MANUAL.md`, `docs/INTERACTION_FLOWS.md`, `docs/SECURITY_AND_PRIVACY.md`

### Pendenti ⏳

- ⏳ **Collaudo SOAP Alloggiati reale** — richiede credenziali PS reali e accesso VPN Questura; piano dettagliato in `docs/ALLOGGIATI_COLLAUDO_REALE.md`
- ⏳ **Vista occupazione del giorno** — CalendarPlanning/PlanningBoard presenti ma nessun widget dedicato "occupazione rapida" nella dashboard
- ⏳ **Dependabot auto-PR** — da attivare sul repository GitHub
- ⏳ **Commento vincolo `commons-csv:1.9.0`** in `build.gradle.kts`

### Post-pilot (Nice-to-have)

- **Notifiche**: email/push per prenotazioni in scadenza, camere da pulire prima del check-in
- **Export fatture PDF**: invoice scaricabile come PDF branded con header hotel
- **Report occupazione e RevPAR**: dashboard proprietario con trend temporale
- **Wizard di onboarding**: configurazione guidata al primo avvio
- **Multi-hotel UI**: selezione hotel attivo nella navbar per ADMIN di catena
- **Integration test end-to-end** cross-service (Testcontainers)
- **Email conferma prenotazione** all'ospite (SMTP configurabile)

---

## 7. Deliverable Concreti — Checklist Aggiornata

### Priorità 1 — Bloccanti (tutti completati)

- [x] **D1** Aggiungere `OWNER` al Role enum + allineare frontend Role type al backend
- [x] **D2** Aggiungere RBAC route-level nel gateway (whitelist per ruolo per path prefix)
- [x] **D3** Aggiungere `@PreAuthorize` agli endpoint amministrativi (rooms, users, reports)
- [x] **D4** Rimuovere `@NotNull` da `StayRequest.reservationId` + backend walk-in support
- [x] **D5** Creare `WalkInCheckInForm.tsx` + route `/stays/walk-in`
- [x] **D6** Creare pagina `/admin/users` (CRUD utenti, solo ADMIN)
- [x] **D7** Aggiungere flag `mustChangePassword` su `UserAccount` + redirect al primo login
- [x] **D8** Creare `.env.example` con tutte le variabili documentate
- [x] **D9** Aggiungere startup check HMAC secret e Alloggiati placeholder warning

### Priorità 2 — Qualità operativa

- [ ] **D10** Collaudo SOAP Alloggiati con credenziali PS reali in `DRY_RUN=true` ⏳
- [x] **D11** Propagare `X-Correlation-ID` nei log (MDC) dal gateway a tutti i servizi
- [x] **D12** Aggiungere test Playwright: checkout, walk-in, billing completo
- [x] **D13** Aggiungere test per `CalendarPlanning.tsx` e `Rooms/index.tsx`
- [x] **D14** Creare pagina `/profile/hotel` (nome, indirizzo, PIVA, logo)
- [ ] **D15** Vista occupazione del giorno nel dashboard receptionist (status camere) ⏳
- [ ] **D16** Attivare Dependabot auto-PR sul repository GitHub ⏳
- [ ] **D17** Documentare vincolo `commons-csv:1.9.0` nel `build.gradle.kts` ⏳

### Priorità 3 — Post-pilot

- [x] **D18** Guida operativa receptionist (`docs/USER_MANUAL.md`)
- [ ] **D19** Export fattura in PDF con header hotel configurabile
- [ ] **D20** Report occupazione e RevPAR nel dashboard proprietario
- [ ] **D21** Email di conferma prenotazione all'ospite (SMTP configurabile)
- [ ] **D22** Wizard onboarding al primo avvio (hotel setup, prima camera, primo utente)
- [ ] **D23** Notifiche in-app per eventi operativi (check-in atteso, camera sporca)
- [ ] **D24** Multi-hotel UI per ADMIN di catena alberghiera
