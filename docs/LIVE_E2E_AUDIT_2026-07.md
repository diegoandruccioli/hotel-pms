# Audit E2E Browser Reale — Hotel PMS

**Data:** 2026-07-26
**Branch analizzato:** `main` (HEAD `46c39b3`)
**Scope:** Primo giro completo di navigazione con browser reale (Playwright MCP, stack Docker vivo, nessun mock) su tutte le route dell'app, con verifica esplicita delle voci già marcate ✅ in `docs/ROADMAP.md`.

---

## 0. Perché questo audit esisteva

Nessuna sessione precedente aveva mai fatto un giro di navigazione completo dell'app in un browser reale. **Tutti e 14 gli spec Playwright in `frontend/e2e/` mockano il backend** con `page.route` (da 2 a 14 intercettazioni per file, verificato spec per spec) — la suite E2E prova solo che il frontend renderizza correttamente risposte finte, **non prova nulla** sull'integrazione reale fra gli 8 microservizi, il gateway, i 5 database e Redis. Questo è esattamente il tipo di gap che ha già prodotto bug reali in passato (il bug `confirmationEmailFailed` del 19/07 era invisibile ai test perché mockavano `NotificationClient`, saltando la decodifica Feign reale).

Ambiente: Docker Desktop avviato (era spento), 17/17 container healthy, immagini allineate a HEAD. Mailpit come SMTP catcher dev-only. Password admin resettata (`TestAdmin2607!` — quella di default non era più valida). Utenti test creati: `test-owner-2607` (OWNER), `test-recept-2607` (RECEPTIONIST). Dati di test: camera `T2607A`, ospite "Test Verifica".

---

## 1. Cosa funziona — e come

- **Login**: cookie httpOnly per JWT/refresh, `csrf_token` leggibile per l'header anti-CSRF, redirect corretto
- **RBAC backend reale** (non solo UI): login diretto via API come RECEPTIONIST → `GET /reports/owner` e `GET /auth/users` → 403 su entrambi; stesso test come OWNER → 200 su entrambi (coerente con `Role.java`: OWNER ha lo stesso accesso di ADMIN nel proprio hotel). Sidebar RECEPTIONIST non mostra nemmeno il link "Analytics"; navigazione diretta a `/owner-dashboard`/`/admin/users` redirige a `/`
- **Dashboard**: KPI reali (ospiti in struttura, arrivi/partenze, camere disponibili, fatturato pending), room-grid con stato vero
- **Camere/Ospiti/Prenotazioni**: CRUD funzionante, validazione Zod attiva sul form Camere (errore inline `role=alert` su N° Camera troppo lungo), disponibilità camere reattiva alle date in prenotazione, ricerca server-side attiva in rete
- **Email conferma prenotazione (C1)**: verificata **davvero consegnata** su Mailpit — mittente, oggetto, saluto personalizzato "Gentile Test Verifica", dettagli soggiorno tutti corretti. Badge di retry su email fallita (prenotazione pre-esistente creata prima di Mailpit) confermato visibile e funzionante
- **Check-in Alloggiati**: pre-fill nome/cognome da profilo ospite, autocomplete cittadinanza/comune con validazione condizionale ben fatta (messaggio chiaro "comune di nascita obbligatorio per nati in Italia", campi che appaiono dinamicamente)
- **F&B → Fattura**: la sincronizzazione funziona — verificato che un ordine in stato `BILLED_TO_ROOM` compare come charge in fattura con IVA corretta (10%); un ordine lasciato in `PENDING` correttamente NON sincronizza (comportamento atteso, non un bug — errore mio nel test iniziale, non del prodotto)
- **Owner Analytics**: KPI corretti, tabella fatture scoped correttamente all'hotel — usa una query diversa da quella rotta (BUG-0), non ne risente
- **Gestione utenti admin**: lista corretta, badge "Cambio password richiesto" visibile, reset password funzionante
- **GuestFormModal (creazione ospite)**: gestione errori **corretta** — vedi nota di correzione sotto, il mio primo report la segnalava erroneamente come rotta

---

## 2. Correzione rispetto alla prima stesura di questo report

Dopo l'invio del report iniziale ho lanciato 3 agenti di ricerca per preparare i piani di fix. Due correzioni importanti sono emerse:

**"Nessun feedback su creazione ospite con nome contenente cifre" NON è un bug reale.** `GuestFormModal.tsx` ha già un `try/catch` completo con `addToast(errorMsg, 'error')`, confermato anche da test esistenti (`GuestFormModal.test.tsx:97,196`). L'osservazione dal vivo era quasi certamente un artefatto di timing: il toast sparisce dopo 4000ms e le chiamate successive (lettura console + ricerca nello snapshot) hanno probabilmente superato quella finestra prima dello screenshot. **Da ri-verificare con uno snapshot immediato per chiuderla formalmente, ma non genera un fix.**

Il bug reale imparentato è più preciso e più grave di come l'avevo descritto inizialmente: non è "zero feedback", è **messaggio sbagliato**. Vedi BUG-4 sotto.

---

## 3. Bug confermati — anche su voci marcate ✅ in ROADMAP

### BUG-0 (CRITICO) — Pagina Fatturazione completamente rotta: 500 su ogni caricamento — ✅ RISOLTO (vedi §5)
Voce ROADMAP: C12 parte 2, marcata ✅ il 18/07.
`GET /api/v1/invoices/search` fallisce **sempre**, con o senza filtri — verificato sia a vuoto sia con `query=test`, stesso errore identico in entrambi i casi. Log reale: `org.postgresql.util.PSQLException: ERROR: function lower(bytea) does not exist`. Causa: `LOWER(CONCAT('%', :query, '%'))` dentro un `OR` con `:query IS NULL` (`InvoiceRepository.java:133-134`) — il protocollo esteso di Postgres richiede che il tipo di ogni bind parameter sia inferibile al PARSE, e questa espressione è ambigua: Postgres assume `bytea` invece di `text`. **Nessuna fattura è oggi visualizzabile dalla UI, in nessuna condizione.**

**Bug bonus nella stessa query**: la clausola `OR i.guestId IN :guestIds` non è raggruppata dentro le parentesi con `:query IS NULL OR ...` — i filtri status/data vengono bypassati ogni volta che un guestId matcha, indipendentemente dai filtri attivi.

Perché non è mai stato scoperto: `billing.spec.ts` mocka l'endpoint — non ha mai eseguito questa query contro Postgres reale.

### BUG-0b (CRITICO) — Generare un PDF fattura crasha l'intero billing-service — ✅ RISOLTO (vedi §5)
Voce ROADMAP: C13 (PDF/UA), marcata ✅ il 19/07.
`GET /invoices/{id}/pdf` → 500 generico Spring Boot (non il formato ProblemDetail dell'app — segno che l'eccezione non è stata gestita normalmente ma dal crash del processo). Log: `WARN ... No servers available for service: guest-service` seguito immediatamente da `Terminating due to java.lang.OutOfMemoryError: Metaspace` — il container muore e viene riavviato da Docker. Verificato: 6 occorrenze storiche, RestartCount=3, non un incidente isolato di questo test.

**Causa radice più fondamentale, confermata dalla ricerca di follow-up**: `GuestClient` (Feign, billing→guest-service) **non ha alcuna configurazione di discovery** — a differenza di `HotelSettingsClient` che pinna un URL esplicito (`application.config.frontdesk-service-url`), `GuestClient` dichiara solo `@FeignClient(name = "guest-service")` senza `url`, con Eureka disabilitato (`eureka.client.enabled: false`) e nessuna istanza statica configurata né in `config-service` né in `docker-compose.yml`. Risultato: **ogni** chiamata a guest-service durante la generazione PDF fallisce con "No servers available" — ogni PDF fattura generato finora ha probabilmente mostrato "Unknown Guest" invece del nome reale dell'ospite, anche prima di arrivare al crash.

Causa dell'OOM stesso (concorrente, non alternativa): in `ThymeleafPdfTemplateRenderer.render()` (`pdf-template-engine`), i due font Noto Sans vengono riletti dal classpath e ri-registrati/ri-subsettati su un `PdfRendererBuilder` **nuovo ad ogni chiamata** — l'unico punto di allocazione per-richiesta nell'intera pipeline PDF (il `TemplateEngine`/`ClassLoaderTemplateResolver` è invece un singleton corretto, cache-abilitato, costruito una sola volta all'avvio). Limiti JVM del container molto stretti per questo carico: `-Xmx256m -XX:MaxMetaspaceSize=128m`.

### BUG-2 — Ricerca ospiti non trova "Nome Cognome" insieme — ✅ RISOLTO (vedi §5)
Pattern C12 condiviso da Guests/Billing/Reservations.
`GuestRepository.searchByKeywordAndHotelId` (righe 67-72) matcha un **singolo** `:keyword` con LIKE su firstName OR lastName OR email OR city, ciascuno indipendente — "Test Verifica" come stringa intera non è sottostringa né di firstName="Test" né di lastName="Verifica" presi singolarmente → 0 risultati, verificato sia su `/guests` diretta sia nel form prenotazione. Nessuna utility di tokenizzazione esiste nel codebase da riusare. Rischio concreto: l'utente non trova l'ospite esistente, clicca "Nuovo Ospite", crea un duplicato.

### BUG-3 — Ricerca comune Alloggiati non trova mai "ROMA" (il comune più popoloso d'Italia) — ✅ RISOLTO (vedi §5)
`AlloggiatiComuneRepository.searchActive` (righe 54-63): `ORDER BY c.descrizione` (alfabetico) + `LIMIT 20` (`AlloggiatiLookupServiceImpl.AUTOCOMPLETE_MAX_RESULTS = 20`) senza alcuna priorità su match esatto/prefisso. Con ~19 comuni omonimi/derivati che iniziano con lettere precedenti a "R" (Arcinazzo Romano... Montorio Romano), il cap si esaurisce prima di raggiungere "ROMA" alfabeticamente. Verificato in rete: `GET /comuni?q=Roma` risponde 200 con 20 elementi, nessuno è "ROMA". Bug ad alta severità su un adempimento legale obbligatorio (report Alloggiati/Questura).

### BUG-4 (revisionato) — Messaggio errato su errori 4xx/5xx in 11+ punti frontend
Non "zero feedback" come nella prima stesura — **messaggio sbagliato**. Pattern `err instanceof Error ? err.message : fallback` in `Stays.tsx` (×4: loadStays, handleCheckOut, retryInvoice, retryCheckoutEmail), `AlloggiatiReportSection.tsx` (×3), `CalendarPlanning.tsx`, `Housekeeping.tsx`, `Reservations.tsx`, `OwnerDashboard.tsx`. `AxiosError` **è** `instanceof Error`, quindi il ternario prende sempre `err.message` — una stringa tecnica tipo `"Request failed with status code 409"` — scartando sia il campo `detail` del backend (già tradotto dall'interceptor Axios per i codici `[A-Z_]+`) sia il fallback i18n. Osservato dal vivo su check-out con fattura non pagata (409 `BILLING_NOT_PAID`): un toast appare, ma con un messaggio incomprensibile invece del motivo vero.

Difetti minori collegati, stesso pattern: `GuestSearchAndCreate.tsx` (fallback hardcoded in inglese, mai tradotto), `WalkInCheckInForm.tsx` (catch nudo che scarta del tutto il `detail`).

### BUG-5 (sicurezza) — "Cambio password richiesto" non applicato lato backend
Utente RECEPTIONIST creato con `mustChangePassword=true` → frontend redirige correttamente a `/settings/password` → navigazione diretta ad altra URL bypassa il redirect → chiamata diretta `GET /api/v1/guests/search` con quella sessione → 200 OK, dati reali. Confermato dalla ricerca: `mustChangePassword` **non è nel JWT** (solo claim `role`/`hotelId`/`tv`), esiste solo come colonna DB, letta esclusivamente da `/auth/me`. Nessun filtro (gateway `AuthenticationFilter` o `InternalAuthFilter` nei singoli servizi) lo controlla mai. Il requisito "cambio password obbligatorio" è oggi puramente cosmetico.

---

## 4. Copertura non completata

PDF/FatturaPA/SDI a fondo (bloccato da BUG-0/0b), checkout finale (bloccato da fattura mai pagabile via UI), walk-in, calendario planning-board, pagine settings (profilo/hotel/sistema/password/aspetto/accessibilità), switch lingua IT/EN, accessibilità TAB-only estesa oltre l'osservazione dello skip-link.

---

## 5. Piano di correzione per ogni bug

Ogni piano indica: causa radice, fix proposto, file da toccare, verifica, branch di destinazione. **Nessuna correzione applicata in questo giro — solo pianificazione, implementazione da confermare separatamente.**

### BUG-0 — Fatturazione 500 sempre — ✅ RISOLTO
- **Fix**: cast esplicito del parametro in JPQL — `CAST(:query AS string)` (Hibernate lo traduce in `CAST(? AS text)` lato Postgres), che risolve l'inferenza di tipo indipendentemente dal valore/null passato
- **Bonus corretto nella stessa modifica**: raggruppate correttamente le parentesi — `OR i.guestId IN :guestIds` ora sta dentro il gruppo `(CAST(:query AS string) IS NULL OR (...))`, non più fuori — un filtro status/data non viene più bypassato da un guestId match
- **File**: `billing-service/src/main/java/com/hotelpms/billing/repository/InvoiceRepository.java:129-135`
- **Verifica**: aggiunti 2 test Testcontainers reali (non mockati, Postgres vero) in `InvoiceServiceIntegrationTest.java` — `searchInvoicesWithoutQuerySucceeds` (query null, ex-crash su `lower(bytea)`) e `searchInvoicesWithQueryFiltersByInvoiceNumberOnly` (query valorizzata + verifica che il filtro status non venga bypassato). `./gradlew :billing-service:build` verde (8/8 test, PMD/Checkstyle/SpotBugs/coverage tutti passati)
- **Branch**: `main` (bug fix ordinario, non tocca auth/RBAC/injection-mitigation)

### BUG-0b — PDF fattura crasha billing-service — ✅ RISOLTO
- **Fix parte 1 (causa radice del "No servers available")**: `GuestClient` pinnato a URL esplicito, coerente con `HotelSettingsClient` — nuova proprietà `application.config.guest-service-url: http://guest-service:8083` in `config-service/src/main/resources/config/billing-service.yml`, `@FeignClient(name = "guest-service", url = "${application.config.guest-service-url}", path = "/api/v1/guests")` in `GuestClient.java`. Nessun override in `docker-compose.yml` necessario (stesso pattern di `frontdesk-service-url`, risolto interamente da config-service)
- **Fix parte 2 (irrobustimento del rendering, causa concorrente dell'OOM)**: i byte dei font Noto Sans ora letti **una volta sola** in campi statici (`FONT_REGULAR_BYTES`/`FONT_BOLD_BYTES`, class-init) in `ThymeleafPdfTemplateRenderer.java`, `useFont` avvolge un `ByteArrayInputStream` invece di riaprire lo stream classpath ad ogni `render()`
- **Fix parte 3 (rete di sicurezza operativa)**: `-XX:MaxMetaspaceSize` per billing-service alzato 128m→192m in `docker-compose.yml`
- **File**: `billing-service/src/main/java/com/hotelpms/billing/client/GuestClient.java`, `config-service/src/main/resources/config/billing-service.yml`, `billing-service/src/test/resources/application.yml`, `docker-compose.yml`, `pdf-template-engine/src/main/java/com/hotelpms/pdftemplate/ThymeleafPdfTemplateRenderer.java`
- **Verifica**: `./gradlew :pdf-template-engine:build :billing-service:build :config-service:build` verde. Live: rebuild+restart `config-server`+`billing-service`, login admin via gateway, `GET /api/v1/invoices/{id}/pdf` su 2 fatture reali → 200, PDF valido, **"Intestatario: Mario Rossi"** (nome reale, non più "Unknown Guest"). 20 generazioni consecutive sulla stessa fattura → 20×200, container rimasto `healthy`, RestartCount invariato (7, tutti pre-fix), Metaspace 138MB/192MB committed, nessun nuovo crash
- **Branch**: `main`

### BUG-2 — Ricerca ospiti non trova "Nome Cognome" insieme — ✅ RISOLTO
- **Fix**: `GuestRepository` ora estende anche `JpaSpecificationExecutor<Guest>`; `searchByKeywordAndHotelId` è diventato un default method che delega a una nuova `GuestSearchSpecifications.matchingAllTokens(keyword, hotelId)` — splitta il termine su whitespace e richiede che OGNI token matchi almeno uno dei 4 campi (AND di OR per token, Criteria API dato che JPQL statico non supporta un numero di token variabile). Nessuna utility di tokenizzazione esisteva nel codebase, scritta ex-novo
- **File**: `guest-service/src/main/java/com/hotelpms/guest/repository/GuestRepository.java`, nuovo `GuestSearchSpecifications.java` (stesso package)
- **Verifica**: nuovo test Testcontainers reale `searchMatchesFirstNameAndLastNameTogether` (`GuestSearchIntegrationTest.java`) con "Mario Rossi", "rossi mario" (ordine invertito) e un caso negativo cross-guest; `./gradlew :guest-service:build` verde. Dal vivo via gateway: `GET /guests/search?query=Test%20Verifica` e `query=Verifica%20Test` → entrambe 200 con l'ospite reale trovato (prima: 0 risultati in entrambi i casi)
- **Impatto positivo indiretto**: beneficia anche Billing (cross-service guest-name resolution) e Reservations, che riusano lo stesso endpoint tramite `GuestClient.searchGuests`
- **Branch**: `main`

### BUG-3 — Ricerca comune Alloggiati non trova mai "ROMA" — ✅ RISOLTO
- **Fix**: `ORDER BY` in `AlloggiatiComuneRepository.searchActive` ora dà priorità a match esatto (0), poi prefisso (1), poi sottostringa (2), prima del cap di 20 — `ORDER BY CASE WHEN LOWER(descrizione) = LOWER(:term) THEN 0 WHEN LOWER(descrizione) LIKE LOWER(CONCAT(:term,'%')) THEN 1 ELSE 2 END, descrizione` (case-insensitive, coerente col `WHERE` esistente)
- **File**: `frontdesk-service/src/main/java/com/hotelpms/frontdesk/stays/repository/AlloggiatiComuneRepository.java:54-63`
- **Verifica**: nuovo `AlloggiatiComuneRepositoryIntegrationTest` (`@DataJpaTest` + Testcontainers, Postgres reale, stesso pattern usato per BUG-2) — seeda 20 comuni "Arcinazzo Romano N" alfabeticamente precedenti a "ROMA" più "ROMA" stesso: `exactMatchRanksFirstAndSurvivesCap` verifica che "ROMA" sia il primo risultato anche col cap a 20; `prefixMatchRanksAheadOfSubstringMatch` verifica che un prefisso ("Romano di Lombardia") batta un match di sola sottostringa. `./gradlew :frontdesk-service:build` verde (PMD/Checkstyle/SpotBugs/coverage tutti passati)
- **Branch**: `main`

### BUG-4 — Messaggio errato su errori 4xx/5xx
- **Fix**: nuovo helper condiviso (non esiste, da creare — es. `frontend/src/utils/errorMessage.ts`) che estrae `err.response?.data?.detail` prima di `err.message`, con fallback i18n finale; sostituire il pattern `err instanceof Error ? err.message : fallback` in tutti gli 11+ call site individuati
- **File principali**: `frontend/src/pages/Stays.tsx`, `frontend/src/pages/Stays/AlloggiatiReportSection.tsx`, `frontend/src/pages/CalendarPlanning.tsx`, `frontend/src/pages/Housekeeping.tsx`, `frontend/src/pages/Reservations.tsx`, `frontend/src/pages/OwnerDashboard.tsx`
- **Da toccare nello stesso giro (qualità minore, stesso pattern)**: `frontend/src/pages/Reservations/GuestSearchAndCreate.tsx` (fallback inglese hardcoded), `frontend/src/pages/Stays/WalkInCheckInForm.tsx` (catch nudo)
- **Verifica**: aggiornare i test esistenti su questi componenti per asserire il messaggio tradotto/dal backend, non la stringa Axios grezza
- **Branch**: `main`

### BUG-5 — `mustChangePassword` non applicato lato backend
- **Fix**: aggiungere `mustChangePassword` come claim JWT (accanto a `role`/`hotelId`) in `JwtService.java`, popolato a login/refresh/change-password. In `api-gateway/src/main/java/com/hotelpms/gateway/filter/AuthenticationFilter.java`, accanto a `isAccessAllowed(...)` (righe 253-278), nuovo controllo: se il claim `mustChangePassword=true` e il path richiesto non è nell'allow-list (`/api/v1/auth/change-password`, `/api/v1/auth/me`, `/api/v1/auth/logout`, `/api/v1/auth/refresh`), rispondere 403 prima di inoltrare la richiesta
- **Nota architetturale**: il gateway è l'unico punto che copre tutte le route business (billing/guest/frontdesk/fb) — `auth-service` non ha `AuthenticationFilter` sulle sue route pubbliche (login/refresh/change-password/me/logout sono già `permitAll` per design), quindi non va toccato
- **File**: `auth-service/src/main/java/com/hotelpms/auth/service/JwtService.java`, `api-gateway/src/main/java/com/hotelpms/gateway/filter/AuthenticationFilter.java`
- **Verifica**: utente con `mustChangePassword=true` deve ricevere 403 su qualunque endpoint fuori dall'allow-list; deve tornare 200 dopo il cambio password (nuovo token senza il claim attivo)
- **Branch**: `feature/secure-coding-hardening` (**obbligatorio** — tocca autorizzazione/JWT/RBAC per regola CLAUDE.md). Dopo il commit: aggiornare `THREAT_MODEL.md` + frammento LaTeX tramite skill `security-followup`

---

## 6. Priorità consigliata

1. **BUG-0 + BUG-0b** — critici, bloccano l'intera Fatturazione e rischiano un DoS involontario sul billing-service condiviso da tutti gli hotel
2. **BUG-5** — sicurezza, ma impatto limitato (richiede comunque credenziali valide, solo vanifica la rotazione forzata)
3. **BUG-2 + BUG-3** — UX/dati, rischio concreto di duplicati e rallentamento su un adempimento legale
4. **BUG-4** — qualità UX, nessun rischio funzionale
