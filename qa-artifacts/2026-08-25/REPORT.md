# QA browser esaustivo — Hotel PMS, round 2026-08-25/27

Piano: `viglio-che-ora-vai-composed-brooks.md`. Ambiente: stack Docker locale (16 container),
frontend build nginx su `http://localhost`. Corsia A = Chrome MCP (esplorazione reale), Corsia B =
Playwright live (`frontend/e2e-live/qa2508/`, congela quanto verificato).

**Stato: round in corso — questo documento viene aggiornato incrementalmente ad ogni blocco chiuso.**

---

## Difetti trovati e risolti

### 🔴 CRITICO — Logout non invalida la sessione lato server
**File**: `frontend/src/layouts/MainLayout.tsx:132` (ora :132-142)
**Trovato**: Blocco 1, via Chrome MCP — dopo click su "Esci" (redirect a `/login` corretto), una
`fetch('/api/v1/auth/me')` diretta restituiva ancora 200 con l'identità dell'utente. Confermato anche
`POST /api/v1/auth/refresh` post-logout riusciva.
**Causa**: `handleLogout` chiamava solo `logout()` (pulizia stato Zustand locale) + `navigate('/login')`.
Non chiamava mai `authService.logout()` → `POST /api/v1/auth/logout`, l'unico meccanismo che
blacklista il refresh token (`RefreshTokenService.blacklist`, Redis `rt:blacklist:<jti>`).
L'access token non ha comunque revoca (stateless, TTL 15 min) — per design — ma senza il fix nemmeno
il refresh token veniva mai invalidato: la sessione restava valida fino a 7 giorni dopo "Esci" su un
dispositivo condiviso.
**Fix**: `handleLogout` ora chiama `authService.logout()` prima di pulire lo stato locale, tollerando
fallimento di rete (redirect e pulizia locale avvengono comunque).
**Regressione**: `frontend/e2e-live/qa2508/01-shell-navigation.spec.ts` — "logout blacklists the
refresh token server-side" — verde dopo il fix, verificato manualmente rosso prima del fix.

### 🟡 MODERATO — Contatori Pulizie non si aggiornano dopo cambio stato
**File**: `frontend/src/hooks/queries/useRooms.ts` (`useUpdateRoomStatus`, `useBulkUpdateRoomStatus`)
**Trovato**: Blocco 3, via Chrome MCP — cambiando lo stato di una camera da Sporca a Pulita, la card
si aggiornava correttamente ma il badge riepilogativo "PULITA: 25" restava fermo anche dopo il
pulsante "Aggiorna" esplicito. Confermato via API diretta: il backend contava correttamente 26.
**Causa**: i badge leggono `useDaySheet()` (query key `['dashboard','day-sheet',...]`), non la lista
camere. Né la mutation singola né quella bulk invalidavano quella query; "Aggiorna" richiama solo
`useRoomsList`.
**Fix**: aggiunta `invalidateDaySheet()` in `onSuccess` di entrambe le mutation.
**Regressione**: `frontend/e2e-live/qa2508/03-calendar-housekeeping-rates.spec.ts` — 2 test, verdi.

### 🟡 MODERATO — Download Alloggiati mostra sempre "successo", anche se la richiesta fallisce
**File**: `frontend/src/services/stayService.ts` (`downloadAlloggiatiReport`, `downloadAlloggiatiJson`)
**Trovato**: Blocco 5 (D1), durante l'ispezione del codice del download — le due funzioni erano
sincrone (`: void`), innescavano l'iframe nascosto e ritornavano subito; il chiamante
(`AlloggiatiReportSection.tsx`) faceva `await` su un non-Promise (risolve istantaneamente) e mostrava
il toast di successo **prima ancora che la richiesta HTTP nell'iframe fosse completata o fallita**.
Un report Alloggiati fallito (credenziali errate, dati ospite malformati, 5xx) sarebbe stato comunque
segnalato allo staff come "scaricato" — rischio di compliance TULPS art. 109 silenzioso.
**Contrasto**: lo stesso codebase risolve correttamente questo problema per FatturaPA XML
(`billingService.validateFatturaPAXml` — preflight che valida prima di innescare l'iframe,
`InvoiceDetailModal.tsx:56-58` lo documenta esplicitamente) — il pattern non era stato applicato ad
Alloggiati.
**Fix**: `downloadAlloggiatiReport`/`downloadAlloggiatiJson` ora sono `async`, fanno un GET reale
sullo stesso endpoint prima di innescare l'iframe — se fallisce, l'errore propaga al blocco catch
già esistente nel chiamante (nessuna modifica lato chiamante necessaria, faceva già `await`).
**Regressione**: `frontend/e2e-live/qa2508/05-alloggiati-portal.spec.ts` — 2 test (fallimento mostra
errore, successo continua a funzionare).

### 🟢 MINORE — Testo d'aiuto Imposta di Soggiorno fuorviante sulla stessa categoria
**File**: `frontend/src/locales/{it,en}/settings.json` (`city_tax_rates_section_desc`), UI in
`SettingsCityTax.tsx` (`CityTaxRatesSection`)
**Trovato**: Blocco 5 (D3) — il testo sotto "Tariffe imposta di soggiorno" dichiara "Registrare una
nuova tariffa per la stessa categoria chiude automaticamente quella corrente", ma il backend non ha
questa logica di sostituzione per intervalli aperti: una seconda tariffa per la stessa categoria,
senza `validTo` sulla prima, è un vero overlap e viene correttamente respinta con 409
(`excl_city_tax_rates_no_overlap`), mostrato con il toast tradotto "Esiste già una regola attiva per
questa categoria in questo periodo" — non un errore grezzo, solo un testo d'aiuto impreciso. Non
bloccante: la gestione dell'errore è corretta, cambia solo l'aspettativa comunicata all'utente
(che si aspetterebbe una sostituzione silenziosa e invece riceve un rifiuto).
**Non risolto in questo round** (cosmetico/copy, non logica applicativa) — segnalato per revisione del
testo o della logica di sostituzione, a scelta del team.
**Verificato da**: `frontend/e2e-live/qa2508/05-city-tax-portal.spec.ts` — "DEFECT 🟢: help text
claims re-registering a category auto-closes the current rate, but it actually 409s".

### 🟡 MODERATO — Contrasto colore insufficiente sulle etichette giorno nei calendari
**File**: `frontend/src/pages/Rates/RateCalendar.tsx:270`, `frontend/src/pages/PlanningBoard.tsx:271`
**Trovato**: Blocco 2, axe (`wcag2aa`) sullo spot-check di `/rates` — etichetta giorno della
settimana ("mer", "gio", "ven"...) con contrasto 4,35:1 contro sfondo `bg-surface-container-low`,
sotto la soglia minima WCAG 2 AA di 4,5:1 (`color-contrast`, impatto `serious`).
**Causa**: `opacity-60` applicata a un colore di testo ereditato invece di un token di design
verificato (`text-on-surface-variant`, già usato altrove nell'app per testo secondario). Stesso
identico markup duplicato in due punti (Calendario tariffe e Calendario planning).
**Fix**: sostituito `opacity-60` con la classe `text-on-surface-variant` in entrambi i file.
**Regressione**: `frontend/e2e-live/qa2508/02-route-sweep.spec.ts` — "axe accessibility spot-check
across key routes (ADMIN, IT)" — verde dopo il fix (era l'unico fallimento reale rimasto nel Blocco 2).

### 🔴 CRITICO — Modifica di una prenotazione esistente (PUT) sempre fallita
**File**: `frontdesk-service/.../reservations/service/impl/ReservationServiceImpl.java` (`updateReservation`,
`applyResolvedPrices`), `.../reservations/mapper/ReservationMapper.java` (`updateEntityFromRequest`)
**Trovato**: Blocco 6, `frontend/e2e-live/qa2508/07-service-resilience.spec.ts` — test di concorrenza
su due "tab" che modificano la stessa prenotazione. **100% riproducibile**: qualunque `PUT
/api/v1/reservations/{id}` falliva sempre con `400 REQUIRED_FIELD_MISSING`
(`null value in column "price" of relation "reservation_line_items" violates not-null constraint`).
Il Blocco 3 non aveva mai esercitato il salvataggio effettivo di una modifica prenotazione (solo
apertura del form), quindi il difetto era rimasto invisibile fino a questo blocco.
**Causa radice** (confermata con logging diagnostico mirato e rebuild isolato del solo
frontdesk-service): il metodo aggiungeva le nuove `ReservationLineItem` (senza prezzo) alla collezione
gestita e cascade-persistita di `existingReservation` **prima** di calcolare e impostare il prezzo.
Hibernate può "fotografare" i valori da inserire (INSERT) nel momento in cui un'entità nuova diventa
raggiungibile via cascade da un genitore già managed — una `setPrice()` successiva sull'oggetto Java
non arriva più alla query SQL già preparata, che quindi inserisce `price = NULL`.
**Fix**: le nuove line item vengono ora create e **prezzate mentre sono ancora oggetti transienti**,
e solo dopo aggiunte alla collezione di `existingReservation` (`ReservationServiceImpl.java:222-238`).
`ReservationMapper.updateEntityFromRequest` ora esclude esplicitamente `lineItems`
(`@Mapping(target = "lineItems", ignore = true)`) per evitare che il mapper le tocchi prima del fix.
**Regressione**: `frontend/e2e-live/qa2508/07-service-resilience.spec.ts` — "reservation concurrent
edits" — rosso prima del fix (400 su ogni PUT), verde dopo.

### 🔴 CRITICO — Modifica di una prenotazione, mantenendo la stessa camera, respinta con falso conflitto
**File**: `frontdesk-service/.../reservations/service/impl/ReservationServiceImpl.java` (`updateReservation`)
**Trovato**: stesso test di cui sopra, immediatamente dopo aver risolto il difetto del prezzo — con
il prezzo corretto, il `PUT` falliva comunque con `409 ROOM_UNAVAILABLE_DATES` anche modificando una
prenotazione **senza cambiare camera o date**, cioè il caso di modifica più comune in assoluto
(es. solo il numero di ospiti attesi).
**Causa radice**: il vincolo di esclusione a livello DB `excl_reservation_line_items_no_overlap`
(V14) filtra su `active AND booking_blocking`, e la "cancellazione" della vecchia line item è in
realtà un `UPDATE ... SET active = false` (`@SQLDelete`). Ma l'ordine di flush di Hibernate esegue
gli INSERT **prima** dei DELETE all'interno dello stesso flush: la nuova riga (stessa camera, stesse
date, `active = true`) veniva inserita mentre la vecchia riga era ancora `active = true` per una
frazione di transazione, facendo scattare il vincolo di esclusione contro se stessa.
**Fix**: la rimozione delle vecchie line item viene ora flushata separatamente
(`reservationRepository.saveAndFlush`) **prima** di aggiungere le nuove, garantendo che il soft-delete
sia già committed quando il nuovo INSERT viene eseguito (`ReservationServiceImpl.java:236-248`).
**Regressione**: stesso test di cui sopra — verde dopo entrambi i fix, incluso il caso realistico di
modifica-senza-cambio-camera.

### 🟡 MODERATO — api-gateway ritorna un 500 grezzo (non tradotto) quando un servizio a valle è completamente irraggiungibile
**File**: `api-gateway/src/main/java/com/hotelpms/gateway/exception/GlobalErrorWebExceptionHandler.java` (nuovo)
**Trovato**: Blocco 6, `frontend/e2e-live/qa2508/06-guest-fb-service-down.spec.ts` — `docker stop fb-service`
poi `GET /api/v1/fb/menu-items`: risposta `500` con corpo
`{"timestamp":...,"path":...,"status":500,"error":"Internal Server Error","requestId":...}` — il formato
di errore grezzo di default di Spring Boot, non il ProblemDetail coerente (`type`/`title`/`status`/`detail`)
usato ovunque nel resto dell'app. Log di api-gateway: `java.net.UnknownHostException: Failed to resolve
'fb-service'`.
**Causa**: `AbstractProblemDetailAdvice` (common-web-lib), che traduce ogni fallimento a valle in un
502 `EXTERNAL_SERVICE_ERROR` pulito, è basato su Spring MVC (`@RestControllerAdvice`) e **non si applica
alle route dell'API Gateway**, che girano interamente sulla filter chain reattiva WebFlux di Spring
Cloud Gateway — mai attraverso un `@RestController`. Nessuna delle route del gateway (verificato in
`config-service/.../api-gateway.yml`: nessun filtro Resilience4j su nessuna route) aveva quindi alcuna
traduzione dell'errore quando il servizio a valle è del tutto irraggiungibile — a differenza delle
chiamate Feign interne (frontdesk→guest-service, frontdesk→billing-service), che *sono* già tradotte
correttamente (vedi test `guest-service` nello stesso file, verde). Il corpo grezzo rompe il contratto
di errore su cui si basa l'interceptor Axios del frontend (`api.ts` traduce solo un `detail` che
combacia con `^[A-Z_]+$` — qui `detail` non esiste nemmeno).
**Fix**: nuovo `GlobalErrorWebExceptionHandler` (`@Order(-2)`, precede il default handler di Spring
Boot) che intercetta i fallimenti di routing del gateway e risponde con lo stesso formato ProblemDetail
del resto dell'app — 502 `EXTERNAL_SERVICE_ERROR` per host irraggiungibile/connessione rifiutata, 504
`GATEWAY_TIMEOUT` per timeout — senza mai esporre l'host Docker interno nel corpo (stesso principio del
Finding #17, security-report.md).
**Regressione**: `frontend/e2e-live/qa2508/06-guest-fb-service-down.spec.ts` — "F&B order while
fb-service is down" — rosso prima del fix (500 grezzo), verde dopo (502 con `error.toLowerCase()` privo
di "exception", nessuna cascata sulle chiamate non correlate).

---

## Falsi allarmi verificati e scartati (per trasparenza sul processo)

- **Drawer mobile con testo troncato**: screenshot catturato a metà dell'animazione CSS
  `animate-slide-in-right`. Verificato via `getBoundingClientRect()` — geometria DOM corretta,
  nessun bug reale.
- **Logout apparentemente non funzionante al primo tentativo**: click su `ref_57` di un `find()`
  precedente, probabilmente scaduto — errore di automazione, non del prodotto. Rifatto con coordinate
  dirette, funziona (e poi si è scoperto il bug reale sopra, verificato con `fetch()` diretto).
  Tre tessere "€270 totale" nel form preventivo mentre altre 87 mostravano "€90"**: non un bug di
  selezione multipla — sono rate mancanti per tipi camera orfani lasciati da round QA precedenti
  (nessuna tariffa risolvibile per quelle date → fallback al prezzo base). Nota UX minore, non
  bloccante: il fallback non distingue visivamente "prezzo/notte non risolto" da "prezzo/notte reale".
- **GET Alloggiati riportato come 503 dallo strumento di rete dell'estensione Chrome**: il log nginx
  (fonte autorevole) mostra 200 con corpo vuoto per la stessa richiesta — comportamento corretto per
  D1.10 (nessun arrivo quel giorno). Lo strumento di rete del browser ha dato una lettura inaffidabile;
  verificare sempre contro i log server quando un'anomalia di rete sembra critica.
- **Molti fallimenti nelle prime iterazioni del Blocco 2**, nessuno un difetto applicativo:
  (a) un ID preventivo fixture soft-deleted riciclato da un round precedente (404 corretto — creata
  fixture propria del round in `bootstrap.ts::ensureFixtureIds`); (b) un leak di browser context
  quando il login falliva dentro `pageFor()` (contesti mai chiusi, degradavano le pagine successive
  fino al timeout — fixato con try/catch/cleanup); (c) pattern di intestazione indovinati male contro
  le traduzioni EN reali (`/rooms` → "Inventory" non "Rooms"; `/admin/users` → "User Management" non
  "Users"; `/settings/city-tax` → "Tourist Tax" non "City Tax"; `/owner-dashboard` → manca "Pannello
  Proprietario" in IT; `/quotations/:id` → nome ospite dinamico, non testo statico); (d) **la causa
  principale**, che per tre run consecutivi ha fatto sembrare l'app "bloccata" in locale IT (timeout
  di 120-240s con "browser has been closed"): il login helper condiviso `qa2408/support/roles.ts`
  usa `getByLabel(/username/i)` — un selettore solo-inglese. Sotto locale IT forzato il campo si
  chiama "Nome utente" e quel selettore non risolve mai; Playwright ritenta silenziosamente finché
  non scade il timeout esterno, producendo un errore che sembra un crash del browser ma è solo un
  login mai riuscito. Isolato leggendo lo snapshot della pagina al momento del timeout (mostrava il
  form di login in italiano, non un'app bloccata) — creato `uiLoginAsLocaleAware()` in
  `qa2508/support/roles.ts` (selettore bilingue) senza toccare l'helper condiviso con `qa2408`, che
  gira sempre in EN; (e) il test dei parametri non permetteva i 400/404 attesi per ID
  inesistenti/malformati, trattandoli come anomalie invece che come esito corretto.

## Nota operativa (non un difetto software)

Il database di sviluppo contiene centinaia di record fixture residui (`E2E-LIVE-*`, `B3-*`, ecc.) da
round QA precedenti — camere, tipi camera, ospiti. Questo appesantisce visibilmente le liste (es. 90+
camere nel selettore del form preventivo) e non riflette una scala realistica di produzione. Non
richiede fix di codice, ma andrebbe ripulito prima di un prossimo round o di una demo.

**Camera "101" condivisa tra spec**: `03-calendar-housekeeping-rates.spec.ts` (cambia stato camere per
testare i badge Pulizie) e `03-reservations.spec.ts` (seleziona la camera "101" per una nuova
prenotazione via UI) usano entrambi la stessa camera seed, senza isolamento — se eseguiti nello stesso
processo, il primo può lasciare "101" in stato `DIRTY`/`OCCUPIED` e il secondo fallisce
(`element is not enabled`, timeout). Non è un difetto applicativo (una camera sporca è correttamente
non prenotabile), ma una lacuna di isolamento tra le spec di questo round — da correggere facendo
creare a `03-reservations.spec.ts` una propria camera pulita (come già fa `07-service-resilience.spec.ts`
via `createCleanRoom`) invece di dipendere dalla "101" condivisa.

## Gap ambientale — invio email reale strutturalmente impossibile in locale

Durante il Blocco 6 (`notification-service` fermato via `docker stop` per testare il circuit breaker
Resilience4j): il checkout **non si blocca mai** quando il servizio email è irraggiungibile (verificato
con successo — comportamento corretto, `checkoutEmailFailureReason` registrato). Ma anche a servizio
riavviato, il retry non riusciva mai a inviare l'email realmente. Causa: `notification-service.yml`
punta SMTP a `${SMTP_HOST:-mailpit}`, e `.env` imposta `SMTP_HOST=mailpit` — ma **nessun container
`mailpit` esiste in `docker-compose.yml`** di questo repository (confermato nei log:
`UnknownHostException: mailpit`). L'invio email reale è quindi strutturalmente impossibile in questo
stack Docker così com'è, indipendentemente da qualunque test — non un difetto di codice introdotto in
questo round, ma una dipendenza di sviluppo mai cablata nel compose file. Chi vuole verificare l'invio
email reale deve avviare un server SMTP di test (es. `docker run -d -p 1025:1025 --name mailpit
axllent/mailpit`) sulla stessa rete Docker, oppure puntare `SMTP_HOST` a un servizio SMTP reale.

---

## Copertura per blocco

| Blocco | Stato | Note |
|---|---|---|
| 0 — Bootstrap | ✅ completo | utenti QA25, snapshot config, infrastruttura di supporto |
| 1 — Shell/navigazione | ✅ completo | 8/8 spec verdi, 1 difetto 🔴 trovato e risolto |
| 2 — Route sweep (30×3×2) | ✅ completo | 9/9 verdi, ~2 min. 1 difetto 🟡 (contrasto colore) trovato e risolto |
| 3 — Sweep elementi per area | ✅ completo | Ospiti, Prenotazioni, Camere/Tipologie, Pulizie (+1 difetto 🟡), Fatturazione, Ristorante (menu CRUD + dialog nativi), Rates (selezione da tastiera), Calendario (vista/navigazione) — tutte con CRUD reali |
| 4 — Dati negativi | ✅ completo | HotelProfile (VAT/CAP/logoUrl) 8/8 verdi |
| 5 — Matrice portali | ✅ completo | D1 Alloggiati (1 difetto 🟡 risolto) · D2 FatturaPA · D3 City tax (1 difetto 🟢 documentato) · D4 Email — nessun nuovo difetto oltre ai già trovati |
| 6 — Interruzioni | ✅ sostanzialmente completo | `docker stop` reale di notification-service, billing-service, guest-service e fb-service, uno alla volta — tutti i circuit breaker Resilience4j confermati funzionanti (checkout/check-in mai bloccati, retry post-ripristino recupera lo stato); scoperto gap ambientale `mailpit` mancante (vedi sopra). Fault injection UI (`06-interruptions.spec.ts`, 7/7 verdi): abort+retry ospite, 500 su prenotazione, doppio-click, CSRF rimosso, sessione scaduta a metà form, back-navigation durante POST in volo. Fault injection API (`07-service-resilience.spec.ts` + `06-guest-fb-service-down.spec.ts`, 7/7 verdi): pagamento midflight, ordine F&B doppio, submit Alloggiati midflight, concorrenza su modifica prenotazione, guest-service/fb-service giù — **3 difetti 🔴/🟡 trovati e risolti** (prezzo mai salvato su modifica prenotazione, falso conflitto camera, 500 grezzo dal gateway quando un servizio a valle è irraggiungibile). Non testato: `docker stop` di auth-service (romperebbe la sessione della suite stessa) e di frontdesk-service (è il servizio centrale, spegnerlo degrada l'intera app per definizione) |
| 7 — Flussi end-to-end | ⏳ non iniziato | |
| 8 — Chaos | ⏳ non iniziato | infrastruttura (`chaosWalker.ts`) pronta |
| 9 — RBAC/IDOR | ⏳ non iniziato | |
