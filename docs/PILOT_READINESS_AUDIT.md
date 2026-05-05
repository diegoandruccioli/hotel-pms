# Audit Tecnico-Funzionale — Hotel PMS Pilot Readiness

**Data:** 2026-05-05  
**Branch analizzato:** `feature/frontend-development`  
**Scope:** Valutazione completa della readiness per pilot con albergatori reali

---

## 1. Executive Assessment

Il progetto è **quasi pronto per un pilot controllato** ma con una condizione non negoziabile: prima di farlo usare a un albergatore reale servono 2–3 settimane di hardening mirato su gap specifici, non su refactoring strutturale.

L'architettura è genuinamente solida: 8 microservizi con responsabilità ben separate, JWT httpOnly + HMAC internal auth + CSRF + rate limiting Redis, hotel_id in tutte le repository, Flyway in tutti i servizi, GlobalExceptionHandler RFC 7807 ovunque, CircuitBreaker su tutti i Feign client critici, Zipkin + Prometheus + Grafana + Loki già configurati, docker-compose production-grade con network isolation, healthcheck e resource limits su tutti i container. Non è un progetto di facciata.

I bloccanti reali sono tre. Primo: il sistema di ruoli è incompleto e incoerente — il Role enum backend ha solo `ADMIN`, `RECEPTIONIST`, `GUEST`, ma il frontend dichiara anche `OWNER` e `MANAGER`, l'unico `@PreAuthorize` nel codebase referenzia `OWNER` (ruolo inesistente nel backend), e il gateway non fa nessun enforcement per route: un receptionist autenticato può chiamare qualsiasi endpoint di qualsiasi servizio. Secondo: il check-in walk-in è strutturalmente impossibile — `StayRequest.reservationId` è `@NotNull` e il form frontend richiede `reservationId` nell'URL; un ospite che si presenta senza prenotazione non può essere gestito. Terzo: nessuna UI di gestione utenti — l'ADMIN non può creare o disattivare account receptionist dall'interfaccia.

Il resto — qualità del codice, testing (483 test totali), osservabilità, sicurezza infrastrutturale, UX dei flussi principali — è a un livello superiore alla media dei progetti universitari e sufficiente per un pilot reale con hotel di piccole-medie dimensioni.

---

## 2. Bloccanti prima del Pilot

| # | Area | Rischio | Impatto | Fix consigliato |
|---|---|---|---|---|
| **B1** | RBAC — ruoli incoerenti e non enforced | **Critico** | Un receptionist può cancellare prenotazioni, accedere ai report finanziari, modificare camere. Dato il multi-tenancy, è anche un rischio di data leakage tra hotel. | Aggiungere `OWNER` al Role enum backend; aggiungere route-level enforcement nel gateway (`X-Auth-Role` → whitelist per path pattern); aggiungere `@PreAuthorize` sugli endpoint di gestione (delete guest, delete reservation, financial reports). |
| **B2** | Walk-in check-in mancante | **Alto** | Gli hotel fanno il 15–40% dei check-in come walk-in (ospiti senza prenotazione). Il sistema li esclude completamente. | Rimuovere `@NotNull` da `reservationId` in `StayRequest`, aggiungere route `/stays/check-in` senza parametro, creare form walk-in nel frontend che raccoglie dati camera e ospite direttamente. |
| **B3** | Nessuna UI gestione utenti | **Alto** | L'ADMIN di un hotel non può creare account receptionist né disattivarli. Deve farlo via API con token JWT in mano. Impossibile per un albergatore reale. | Aggiungere pagina `/admin/users` visibile solo ad ADMIN: lista utenti, crea utente, disattiva/riattiva (soft delete). |
| **B4** | Credenziali default in produzione | **Alto** | `admin` / `password` è documentato nel README. Se un albergatore avvia il sistema senza cambiarlo, chiunque conosce il progetto ha accesso completo. | Forzare il cambio password al primo login (flag `mustChangePassword` su `UserAccount`); bloccare operazioni se non cambiata. |
| **B5** | `ALLOGGIATI_*` senza `.env.example` | **Medio** | Un installatore non sa quali variabili impostare. Il servizio parte con placeholder CI e registra ospiti senza mai notificare la PS. | Creare `.env.example` con tutte le variabili richieste e un commento per ciascuna; aggiungere startup check che logghi WARN se ancora ci sono placeholder. |

---

## 3. Gap Enterprise — Semaforo

| Area | Stato | Note |
|---|---|---|
| **Architettura e modularità** | 🟢 Verde | 8 microservizi con confini chiari, database separati per servizio, Spring Cloud Config, API Gateway centralizzato. Pronto per scaling orizzontale. |
| **Qualità del codice** | 🟢 Verde | PMD `gradle-java-qa` zero-warning enforced in build, Checkstyle, SpotBugs, Lombok, MapStruct. Frontend TypeScript strict, ESLint zero-warning. |
| **Sicurezza** | 🟡 Giallo | Infrastruttura solida (JWT httpOnly, HMAC inter-service, CSRF, rate limiting, TLS verificato). Gap reale: RBAC quasi assente (1 solo `@PreAuthorize`), ruolo `GUEST` nel Role enum senza semantica operativa definita, `OWNER`/`MANAGER` nel frontend type ma inesistenti nel backend. |
| **Gestione configurazione e segreti** | 🟢 Verde | `.env` in `.gitignore`, Spring Cloud Config centralizzato, secrets via environment variable, nessun hardcoding nei YAML versionati. Unico gap: manca `.env.example`. |
| **Migrazioni DB** | 🟢 Verde | Flyway in tutti e 7 i servizi, 25 migration totali, versioning sequenziale corretto. |
| **Logging e osservabilità** | 🟡 Giallo | Zipkin + Prometheus + Grafana + Loki presenti e configurati nel docker-compose. `@Slf4j` su tutti i servizi. Gap: nessun MDC/correlationId propagato nei log inter-service — debuggare un errore distribuito richiede correlazione manuale dei trace ID. |
| **Test automatici** | 🟡 Giallo | 262 `@Test` backend su 25 classi, 221 test Vitest frontend su 37 file, 5 file Playwright E2E. Copertura funzionale buona sui flussi principali. Gap: coverage disomogenea (stay-service ha 5 classi di test, fb-service ne ha 2), Playwright copre solo 1 scenario booking→check-in. |
| **UX operativa receptionist** | 🟡 Giallo | Tutti i flussi principali hanno UI (prenotazione, check-in, checkout, billing, F&B, housekeeping, camere). i18n EN/IT. Gap: walk-in impossibile, nessuna vista "occupazione del giorno" con status rapido camera per camera, gestione utenti assente. |
| **Resilienza a errori di rete** | 🟢 Verde | CircuitBreaker Resilience4j su 14 endpoint Feign client in 5 servizi, fallback dichiarati, GlobalExceptionHandler in tutti i servizi. Auto-submit Alloggiati non blocca il check-in in caso di SOAP failure. |
| **Documentazione tecnica** | 🟢 Verde | `ALLOGGIATI_README.md`, `DOCUMENTAZIONE_TECNICA_ALLOGGIATI_PS.md`, Swagger aggregato all'API Gateway (`/swagger-ui.html`), `backup/DECISIONS.md`, `backup/SUMMARY.md`, `THREAT_MODEL.md`. |
| **Documentazione utente** | 🔴 Rosso | Zero documentazione per l'utente finale (receptionist/proprietario). Nessun manuale operativo, nessun tooltip contestuale nell'UI, nessuna schermata di onboarding. Un albergatore non tecnico non sa come iniziare. |
| **Deploy locale/staging** | 🟢 Verde | `docker-compose.yml` production-grade (15 servizi, 5 reti isolate, healthcheck, resource limits), script `start.sh/.ps1/.bat`, `setup-hmac-secret.sh/.ps1`. Avvio con un comando dopo aver configurato `.env`. |
| **Multi-hotel / multi-tenant** | 🟡 Giallo | `hotel_id` presente in tutte le entity JPA, tutte le repository filtrano per `hotel_id`, il JWT porta `hotelId` e viene verificato via HMAC. Gap: la UI non supporta ancora la selezione/cambio hotel per un utente ADMIN di una catena; ogni istanza serve un solo hotel in pratica. |
| **Compliance operativa Alloggiati** | 🟡 Giallo | Export file 168-char conforme, CRLF corretto, limite 1000 righe, SOAP two-step implementato. Gap: SOAPAction e namespace sono inferiti dal WSDL e non confermati con credenziali reali; `ALLOGGIATI_DRY_RUN=true` di default protegge da invii accidentali ma il collaudo reale è ancora da fare. |

---

## 4. Flussi Core

| Flusso | Stato | Note |
|---|---|---|
| **Gestione camere** | ✅ Completo | CRUD room type, CRUD room con status (AVAILABLE / OCCUPIED / DIRTY / MAINTENANCE), RoomFormModal, RoomTypeFormModal. |
| **Prenotazione** | ✅ Completo | ReservationForm con GuestSearchAndCreate e RoomSelection, validazione date lato backend, overbooking prevention via ConflictException, cancellazione con dialog conferma. |
| **Check-in (da prenotazione)** | ✅ Completo | CheckInForm con lookup Alloggiati, pre-fill da soggiorno precedente, saga OCCUPIED, auto-submit PS se abilitato. |
| **Check-in walk-in** | ❌ Mancante | `StayRequest.reservationId @NotNull`. Bloccante reale. |
| **Check-out** | ✅ Completo | Verifica billing PAID, marca camera DIRTY, aggiorna stato prenotazione. |
| **Gestione ospiti** | ✅ Completo | CRUD con GDPR soft-delete, guard 451 per soggiorni attivi, ricerca full-text, batch fetch ottimizzato. |
| **Report / export** | 🟡 Parziale | Export Alloggiati .txt e JSON presente. Report finanziario proprietario presente. Mancano: report occupazione per periodo, statistiche RevPAR, export fatture in PDF/CSV. |
| **Configurazione hotel** | 🟡 Parziale | `HotelSettings` con toggle `alloggiatiAutoSend`. Manca configurazione profilo hotel (nome, indirizzo, PIVA), politiche di cancellazione, tariffe predefinite. |
| **Autenticazione** | ✅ Completo | Login, refresh token silenzioso, logout, cambio password (UI in Profile). JWT access 15min, refresh 7gg, token version per invalidazione selettiva. |
| **Autorizzazione** | 🔴 Frammentata | L'autenticazione funziona. L'autorizzazione no: gateway propaga il ruolo ma non lo usa per bloccare rotte; un solo `@PreAuthorize` nel backend (OwnerReportController, che referenzia `OWNER` — ruolo inesistente nell'enum backend). |

---

## 5. Rischi da Progetto Universitario

**Naming e convenzioni:**
- `AlloggiatiRowDto` è un record Java ma il nome suggerisce un "file row" non un oggetto di dominio — in una codebase enterprise si chiamerebbe `AlloggiatiGuestRecord` o simile.
- `StayGuest` e `Guest` coesistono con semantica sovrapposta non completamente documentata per il team.
- Il frontend usa `_statoDiNascita` (underscore prefix, mix italiano/inglese) come campo UI locale — convenzione non dichiarata.

**Hardcoded e assunzioni implicite:**
- `CODICE_ITALIA = "100000100"` duplicato in `AlloggiatiReportServiceImpl` e `CheckInForm.tsx` — non esiste una fonte di verità unica.
- `DEFAULT_PAGE_SIZE = 20` ripetuto in 6 controller diversi senza costante condivisa.
- Il Role enum ha `GUEST` che non viene mai usato in `@PreAuthorize` né per limitare accessi. Non è chiaro se sia un placeholder o un ruolo operativo futuro.
- `@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")` referenzia `OWNER` che non esiste nel backend — compila e passa i test perché i test del `OwnerReportController` mockano la security, ma il vincolo in produzione si riduce di fatto a solo `ADMIN`.

**Setup fragile:**
- Il primo avvio richiede la generazione manuale dell'HMAC secret (`setup-hmac-secret.sh`) — se dimenticata, tutti i servizi falliscono silenziosamente con errori HMAC validation. Non c'è un check di startup esplicito che blocchi il boot con un messaggio chiaro.
- `AlloggiatiLookupDataLoader` scarica i CSV dalla PS al primo avvio: se il portale PS è irraggiungibile (VPN, firewall, maintenance), le tabelle restano vuote e il check-in funziona ma la validazione lookup è silenziosa. Non c'è un health endpoint che esponga lo stato delle lookup tables.

**UX incompleta:**
- Nessuna pagina di onboarding: un albergatore che installa il sistema per la prima volta non vede nessun wizard di configurazione iniziale (nome hotel, camere, tipo documento accettati).
- Il calendario (`CalendarPlanning.tsx`, `PlanningBoard.tsx`) esiste come pagina ma non ha test — impossibile valutare la stabilità senza aprirlo.
- Le notifiche toast sono presenti ma nessun flusso prevede notifiche push o email per eventi critici (prenotazione in scadenza, camera non pulita a orario check-in).

**Test mancanti:**
- `CalendarPlanning.tsx` e `PlanningBoard.tsx` non hanno nessun test.
- `Rooms/index.tsx` non ha test.
- I test Playwright coprono 1 scenario su N critici (no walk-in, no checkout, no split billing, no F&B completo).
- Nessun integration test che verifichi il flusso multi-servizio end-to-end (reservation → stay → billing → checkout).

**Dipendenze poco governate:**
- Il lock file era rimasto con axios 1.15.0 vulnerabile per alcune settimane dopo il rilascio della patch 1.16.0. Nessun processo di aggiornamento automatico (Dependabot auto-PR non configurato).
- `commons-csv:1.9.0` è una versione non-latest mantenuta per un vincolo di `commons-io:2.14.0` — il vincolo non è documentato nel `build.gradle.kts` con un commento esplicito e un futuro sviluppatore potrebbe aggiornare casualmente rompendo il build.

---

## 6. Piano di Hardening

### Must-have prima del pilot

1. **Completare il sistema ruoli**: aggiungere `OWNER` al Role enum backend; implementare route-level RBAC nel gateway (almeno: bloccare `/api/v1/rooms`, `/api/v1/room-types`, `/api/v1/users` a ADMIN/OWNER; proteggere i report finanziari).
2. **Walk-in check-in**: rimuovere `@NotNull` da `reservationId` in `StayRequest`, aggiungere percorso frontend `/stays/check-in` standalone.
3. **UI gestione utenti**: pagina `/admin/users` con creazione e disattivazione account (ADMIN only).
4. **Cambio password obbligatorio al primo accesso**: flag `mustChangePassword` su `UserAccount`, redirect al primo login.
5. **`.env.example`**: documentare tutte le variabili richieste con valori di esempio e commenti.
6. **Startup check HMAC**: log ERROR bloccante se `INTERNAL_HMAC_SECRET` è il valore di default o ha lunghezza insufficiente.
7. **Collaudo Alloggiati reale**: verificare SOAPAction e namespace contro il portale PS con `DRY_RUN=true` + credenziali reali; eventualmente correggere.

### Should-have durante il pilot

8. **MDC correlationId**: aggiungere propagazione `X-Correlation-ID` dal gateway ai microservizi nei log, in modo che un errore distribuito sia tracciabile con un solo ID.
9. **Vista occupazione del giorno**: dashboard receptionist con status camera real-time (libera / occupata / da pulire).
10. **Dependabot auto-PR**: attivare su GitHub per aggiornamenti automatici patch/minor con review obbligatoria.
11. **Test Playwright**: aggiungere scenari checkout, walk-in, F&B completo.
12. **Documentazione utente minima**: guida operativa per receptionist (3–4 pagine: avvio, prenotazione, check-in, checkout).
13. **Profilo hotel configurabile**: nome, indirizzo, PIVA/CF, logo (necessario per le fatture).
14. **Commento vincolo `commons-csv:1.9.0`** in `build.gradle.kts` con spiegazione del motivo.

### Nice-to-have dopo il pilot

15. **Notifiche**: email/push per prenotazioni in scadenza, camere da pulire prima del check-in.
16. **Export fatture PDF**: invoice scaricabile come PDF branded.
17. **Report occupazione e RevPAR**: dashboard proprietario con trend temporale.
18. **Wizard di onboarding**: configurazione guidata al primo avvio (camere, tariffe, utenti).
19. **Multi-hotel per ADMIN di catena**: selezione hotel attivo nella navbar.
20. **Test integration end-to-end** cross-service (Testcontainers o similar).

---

## 7. Deliverable Concreti — Checklist Prioritizzata

### Priorità 1 — Bloccanti (prima del pilot, ~2 settimane)

- [ ] **D1** Aggiungere `OWNER` al Role enum + allineare frontend Role type al backend
- [ ] **D2** Aggiungere RBAC route-level nel gateway (whitelist per ruolo per path prefix)
- [ ] **D3** Aggiungere `@PreAuthorize` agli endpoint amministrativi (rooms, users, reports)
- [ ] **D4** Rimuovere `@NotNull` da `StayRequest.reservationId` + backend walk-in support
- [ ] **D5** Creare `CheckInFormWalkIn.tsx` + route `/stays/check-in` (senza reservationId)
- [ ] **D6** Creare pagina `/admin/users` (CRUD utenti, solo ADMIN)
- [ ] **D7** Aggiungere flag `mustChangePassword` su `UserAccount` + redirect al primo login
- [ ] **D8** Creare `.env.example` con tutte le variabili documentate
- [ ] **D9** Aggiungere startup check HMAC secret e Alloggiati placeholder warning

### Priorità 2 — Qualità operativa (prime 4 settimane di pilot)

- [ ] **D10** Collaudo SOAP Alloggiati con credenziali PS reali in `DRY_RUN=true`
- [ ] **D11** Propagare `X-Correlation-ID` nei log (MDC) dal gateway a tutti i servizi
- [ ] **D12** Aggiungere test Playwright: checkout, walk-in, billing completo
- [ ] **D13** Aggiungere test per `CalendarPlanning.tsx` e `Rooms/index.tsx`
- [ ] **D14** Creare pagina `/profile/hotel` (nome, indirizzo, PIVA — richiesto per le fatture)
- [ ] **D15** Vista occupazione del giorno nel dashboard receptionist (status camere)
- [ ] **D16** Attivare Dependabot auto-PR sul repository GitHub
- [ ] **D17** Documentare vincolo `commons-csv:1.9.0` nel `build.gradle.kts`

### Priorità 3 — Post-pilot

- [ ] **D18** Guida operativa receptionist (PDF o pagina `/help` nell'app)
- [ ] **D19** Export fattura in PDF con header hotel configurabile
- [ ] **D20** Report occupazione e RevPAR nel dashboard proprietario
- [ ] **D21** Email di conferma prenotazione all'ospite (SMTP configurabile)
- [ ] **D22** Wizard onboarding al primo avvio (hotel setup, prima camera, primo utente)
- [ ] **D23** Notifiche in-app per eventi operativi (check-in atteso, camera sporca)
- [ ] **D24** Multi-hotel UI per ADMIN di catena alberghiera
