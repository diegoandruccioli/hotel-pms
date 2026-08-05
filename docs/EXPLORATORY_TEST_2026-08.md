> **STATO: REPORT — nessun fix applicato in questo giro.** Test esplorativo dal vivo
> richiesto dall'utente dopo l'audit normativo (`docs/COMPLIANCE_AUDIT_2026-08.md`):
> "uso reale hotel" sullo stack Docker in esecuzione, senza seguire un ordine scriptato
> di azioni, per stanare bug che un test felice non troverebbe. Eseguito via browser
> (Claude in Chrome) con chiamate dirette alle API autenticate come sessione reale
> (admin + un utente RECEPTIONIST creato ad hoc), non script di fuzzing automatico.
> HEAD al momento del test: `db81d29`.

# Test esplorativo dal vivo — hotel-pms (2026-08)

## Context

Copertura: gate di ruolo, F&B, camere/housekeeping, fatturazione/pagamenti, ospiti/GDPR,
prenotazioni, isolamento multi-hotel, sessione/utenti, Alloggiati. Ogni bug qui sotto è
stato riprodotto dal vivo (non ipotizzato) con richiesta HTTP e, dove utile, log del
servizio a supporto.

---

## Bug trovati

### 1. 🔴 Fallimento silenzioso degli addebiti su rifiuto legittimo di billing-service (il più grave) — ✅ RISOLTO (Fase B3 piano fix-order)

**Dove**: `fb-service/.../client/BillingClient.java` (`addChargeFallback`) e
`frontdesk-service/.../client/BillingClient.java` (stesso pattern, `addChargeFallback` +
`createInvoiceForStayFallback`).

**Cosa succede**: `@CircuitBreaker(fallbackMethod = "addChargeFallback")` cattura
**qualunque** eccezione dalla chiamata Feign a `billing-service`, non solo timeout/
connessione rifiutata — inclusi i rifiuti legittimi 409 (`INVOICE_NOT_OPEN`, o il nuovo
`INVOICE_LOCKED_AFTER_EXPORT` introdotto in questa stessa sessione di lavoro). Il fallback
ritorna `null`; il chiamante logga un semplice WARN e **prosegue come se tutto fosse
andato a buon fine**.

**Riprodotto**: ordine F&B (`espresso`, stay `8ef43c54-...`) confermato con successo
(`200 OK`, `status: BILLED_TO_ROOM`) su un soggiorno la cui fattura (`2026/0003`) era già
`PAID`. La charge non compare mai in fattura — verificato rileggendo `GET
/invoices/{id}`: solo la `ROOM_NIGHT` originale, nessuna traccia dell'ordine F&B.
L'operatore non ha alcun segnale visibile (l'unico indizio è un log WARN in fb-service,
mai esposto in UI).

**Impatto**: perdita di ricavo silenziosa. Ogni ordine F&B (o charge camera al check-in,
via frontdesk-service) confermato su un soggiorno la cui fattura non è più `ISSUED`
(pagata, cancellata, o — dal lavoro di questa sessione — già esportata fiscalmente) risulta
"fatturato" nell'interfaccia ma non genera mai un vero addebito.

**Fix suggerito (non applicato)**: distinguere nel fallback tra eccezioni di
disponibilità (`FeignException.ServiceUnavailable`, timeout, connessione rifiutata — quelle
per cui il circuit breaker ha senso) e risposte 4xx del servizio remoto (che vanno
propagate come errore reale al chiamante, non assorbite in silenzio).

---

### 2. 🟠 Nessuna validazione dello stato del soggiorno negli ordini F&B — ✅ RISOLTO (Fase B3 piano fix-order)

**Dove**: `fb-service/.../service/impl/RestaurantOrderServiceImpl.java` (creazione e
conferma ordine).

**Riprodotto**: creato e confermato con successo un ordine F&B su uno stay già
`CHECKED_OUT` (ospite non più in hotel). Nessun controllo sullo stato del soggiorno prima
di accettare l'ordine — si combina con il bug #1: l'ordine viene "confermato" ma
l'addebito fallisce silenziosamente, quindi l'esito pratico è un ordine fantasma che non
risulta né bloccato né fatturato.

---

### 3. 🟠 Crash 500 su camere con nome > 20 caratteri — ✅ RISOLTO (Fase B3 piano fix-order)

**Dove**: `fb-service/src/main/resources/db/migration/...` (`restaurant_orders.room_number
VARCHAR(20)`) vs `frontdesk-service/.../V1__frontdesk_baseline.sql`
(`rooms.room_number VARCHAR(50)`, `stays.room_number VARCHAR(50)`).

**Riprodotto**: `POST /api/v1/fb/orders` su un soggiorno la cui camera ha un
`roomNumber` di 22 caratteri (`E2E-LIVE-1785525795223`, dato di test da una sessione E2E
precedente, ma un nome camera fino a 50 caratteri è perfettamente valido alla creazione
in frontdesk-service) → `500` grezzo, `DataException: value too long for type character
varying(20)`. Nessuna validazione applicativa, l'errore arriva grezzo dal database.

**Fix suggerito**: allineare la lunghezza colonna (`VARCHAR(50)` anche in fb-service) o
validare/troncare esplicitamente lato applicazione con un errore leggibile invece di un
500.

---

### 4. 🟠 Nessun enforcement sulle transizioni di stato camera — ✅ RISOLTO (Fase B4 piano fix-order)

**Dove**: `frontdesk-service/.../rooms/controller/RoomController.java` (`PATCH
/{id}/status`), `RoomStatus.java` (il commento javadoc dichiara: *"OCCUPIED ... set by
the check-in Saga ... Housekeeping staff cannot change this status directly"*).

**Riprodotto**: una camera con soggiorno `CHECKED_IN` reale in corso (`OCCUPIED`) è stata
spostata a `MAINTENANCE` via API con successo (`200 OK`). Anche il transito manuale
**verso** `OCCUPIED` è stato accettato senza obiezioni, bypassando completamente la Saga
di check-in che dovrebbe essere l'unico punto abilitato a impostarlo. **Zero enforcement
reale**, nonostante la documentazione nel codice dichiari il contrario.

**Impatto**: un receptionist/housekeeping può mettere fuori servizio una camera
attualmente occupata da un ospite pagante, con conseguenze operative dirette
(disponibilità, planning, confusione booking).

**Fix applicato**: `RoomService` ora espone due metodi distinti — `updateRoomStatus`
(invariato, entry point fidato riservato alla Saga di check-in/check-out in
`StayServiceImpl`) e il nuovo `updateHousekeepingStatus` (usato da `PATCH
/rooms/{id}/status`), che rifiuta `OCCUPIED` come target (400
`OCCUPIED_SET_BY_CHECKIN_SAGA_ONLY`) e rifiuta qualunque cambio quando la camera è già
`OCCUPIED` (409 `ROOM_OCCUPIED_CLEARED_BY_CHECKOUT_SAGA_ONLY`). Verificato dal vivo:
camera `OCCUPIED` reale → `MAINTENANCE` ora risponde `409`; impostazione manuale di
`OCCUPIED` su camera `CLEAN` ora risponde `400`; una normale transizione housekeeping
(`DIRTY` → `CLEAN`) continua a funzionare (`200`); la Saga di check-in/check-out non è
stata toccata e resta l'unico percorso abilitato a impostare/rimuovere `OCCUPIED`.

**Follow-up (revisione di sicurezza sul commit `64917ce`)**: la revisione automatica del
commit ha trovato due gap sul fix appena descritto, entrambi corretti nello stesso
passaggio:
- **Sibling-path gate parity**: `PUT /rooms/{id}` (`updateRoom`, aggiornamento completo)
  poteva impostare `status` in `RoomRequest` con **zero enforcement**, riaprendo esattamente
  lo stesso bypass appena chiuso su `PATCH /status` — bastava usare l'endpoint sbagliato.
  Stessi due guard ora applicati anche lì, ma solo quando `status` cambia realmente
  (un aggiornamento di `roomNumber`/`roomType` su una camera già `OCCUPIED`, senza toccare
  lo status, resta permesso).
- **TOCTOU race**: né `Room` ha un campo `@Version` né la lettura di guardia usava un lock,
  quindi tra il controllo "la camera non è `OCCUPIED`" e la scrittura della Saga di
  check-in poteva intercorrere un commit concorrente che imposta `OCCUPIED` — l'update di
  housekeeping, già in volo, l'avrebbe sovrascritto senza mai vederlo. Aggiunto
  `RoomRepository.findByIdAndActiveTrueAndHotelIdForUpdate` (`SELECT ... FOR UPDATE`,
  `@Lock(PESSIMISTIC_WRITE)`), usato da entrambi i path guardati: Postgres blocca la
  `UPDATE` della Saga finché la transazione di guardia non committa, eliminando la finestra.

Verificato dal vivo dopo il follow-up: `PUT` su camera `OCCUPIED` → `MAINTENANCE` ora
risponde `409`; `PUT` su camera `CLEAN` → `OCCUPIED` ora risponde `400`; `PUT` che lascia
lo status invariato su una camera `OCCUPIED` (solo rename) resta `200`; `PATCH /status`
riverificato senza regressioni.

---

### 5. 🟡 `documentType` modificabile su fattura già `PAID` — ✅ RISOLTO (Fase B2 piano fix-order)

**Dove**: `billing-service/.../service/impl/InvoiceServiceImpl.java`
(`updateDocumentType`).

**Riprodotto**: `PATCH /invoices/{id}/document-type` su una fattura `PAID` (`2026/0003`,
270€, già saldata e chiusa da tempo) → `200 OK`, conversione da `FATTURA` a `RICEVUTA`
riuscita senza obiezioni. Il guard esistente blocca solo lo stato `CANCELLED` (vedi
`CANNOT_UPDATE_CANCELLED_INVOICE`), non `PAID`.

**Impatto**: un documento fiscale già emesso e saldato può essere silenziosamente
declassato a non-fiscale — rilevante non solo come bug applicativo ma come problema di
integrità del dato fiscale (si somma al gap "nota di credito" già noto in
`THREAT_MODEL.md` T-BILL-06, ma è un problema distinto: qui non serve nemmeno un export
fiscale pregresso per essere colpiti, basta lo stato `PAID`).

---

### 6. 🟢 Cancellazione ospite con prenotazioni attive: 500 invece di 409 — ✅ RISOLTO (Fase B1 piano fix-order)

**Dove**: `guest-service` (handler generico, log: `Unhandled exception:
GUEST_HAS_ACTIVE_RESERVATIONS`, `java.lang.IllegalStateException`).

**Riprodotto**: `DELETE /guests/{id}` su un ospite con una prenotazione `CONFIRMED`
attiva → `500 Internal Server Error`. La logica di business è **corretta** (l'ospite non
viene effettivamente cancellato, verificato con una `GET` successiva), ma l'eccezione è un
`IllegalStateException` generico non mappato a un tipo gestito da
`AbstractProblemDetailAdvice`, quindi arriva come 500 invece di un `409 Conflict` pulito
con dettaglio esplicito.

**Impatto**: basso (nessuna corruzione dati), ma rumore falso per monitoring/alerting
sui 5xx da un'azione utente del tutto normale e prevista, e un messaggio di errore
inutile per l'operatore in UI.

---

## Finding di sicurezza/conformità (non un "bug" in senso stretto)

### 7. `GuestController` senza alcun `@PreAuthorize` — ✅ RISOLTO (T-GST-07, Fase A piano fix-order)

Nessun controllo di ruolo su nessun metodo del controller, incluso `GET
/guests/{id}/export` (dump GDPR Art. 20 completo: anagrafica, documento, storico
soggiorni/fatture aggregato). Riprodotto: un utente `RECEPTIONIST` appena creato ha
richiamato con successo l'export completo di un ospite. L'unica protezione è la whitelist
per-prefisso nell'api-gateway — funziona, ma è un singolo punto di fallimento invisibile
al codice del servizio stesso, e nessun ruolo è escluso dall'operazione di export bulk.
Già segnalato in `docs/COMPLIANCE_AUDIT_2026-08.md` §3.

---

## Verificato corretto (nessun bug, per completezza)

| Scenario | Esito |
|---|---|
| Overbooking: due prenotazioni sulla stessa camera/date | ✅ Bloccato, `400 ROOM_UNAVAILABLE_DATES` |
| Doppia conferma dello stesso ordine F&B | ✅ Bloccata al secondo tentativo, `400 INVALID_ORDER_STATUS` |
| Gate ADMIN/OWNER (rooms, fb/menu-items, reports/owner, auth/users, alloggiati/submit) da RECEPTIONIST | ✅ Tutti `403` |
| Gate `mustChangePassword` (BUG-5) | ✅ Blocca tutto tranne change-password/me/logout/refresh, confermato dal vivo |
| Pagamento parziale → saldo (due pagamenti che completano il totale) | ✅ Stato `ISSUED`→`PAID` corretto |
| Pagamento oltre il saldo residuo | ✅ Bloccato, `400 INVOICE_ALREADY_PAID` |
| Pagamento negativo / zero | ✅ Bloccato dalla validazione Bean Validation |
| IDOR cross-hotel (ospite/fattura/soggiorno di un altro hotel per ID) | ✅ `404` su tutti e 3, isolamento multi-tenant tenuto |
| Report Alloggiati per data senza check-in / lookup comuni con query vuota o accentata | ✅ Nessun crash, liste vuote |
| Submit Alloggiati per data senza soggiorni | ✅ `200`, no-op |
| Cancellazione articolo di menù usato in un ordine storico | ✅ Nome articolo denormalizzato all'ordine, sopravvive alla cancellazione — nessun impatto su `GET /orders/stay/{stayId}` |
| Login con utente disattivato | ✅ `401 INVALID_CREDENTIALS` |
| Retry email conferma prenotazione, chiamato due volte | ✅ Nessun errore, idempotente nel senso di non fallire (reinvio atteso per un'azione di retry esplicita) |

---

## Priorità consigliate (nessun fix applicato qui)

1. **#1 (fallback silenzioso addebiti)** — il più grave, impatto economico diretto e
   silenzioso, tocca sia fb-service sia frontdesk-service
2. **#4 (transizioni stato camera)** — impatto operativo diretto su ospiti reali
3. **#3 (crash 500 room_number)** — riproducibile con dati di produzione plausibili
   (nome camera descrittivo > 20 caratteri), non solo dati di test
4. **#5 (documentType su fattura PAID)** — integrità documento fiscale
5. **#2 (F&B su stay CHECKED_OUT)** — si attenua se #1 viene risolto insieme a una
   validazione esplicita, ma resta comunque da bloccare a monte
6. **#6 (500 invece di 409 su delete ospite)** — cosmetico/osservabilità, bassa urgenza
7. **#7 (GuestController senza `@PreAuthorize`)** — già in coda con le altre voci di
   `docs/COMPLIANCE_AUDIT_2026-08.md`

---

## Round 2 (2026-08-04)

## Context

Secondo giro di test esplorativo, richiesto dopo la chiusura del lavoro FatturaPA e
l'aggiornamento di `docs/COMPLIANCE_AUDIT_2026-08.md` (4 nuovi gap normativi). Copertura:
export FatturaPA e vicolo cieco nota di credito (priorità massima — è la parte appena
sviluppata), verifiche live dei due gap GDPR appena scritti nell'audit normativo, aree UI
mai toccate dal round 1 (dashboard, calendario, Owner Analytics + CSV, profilo hotel),
ricerca con query anomale, concorrenza. Metodo: sessione autenticata via cookie
(`admin` + tre utenti creati ad hoc `r2admin0804`/`r2owner0804`/`r2receptionist0804`,
stesso hotelId di `admin`), chiamate HTTP dirette (`curl`) per la maggior parte dei test —
più affidabili e ripetibili di un browser per fuzzing/concorrenza — più un passaggio
browser reale (Claude in Chrome) per le aree puramente UI. Verifiche di stato DB via
`docker exec hotel_postgres psql`. Ogni bug è riprodotto dal vivo con richiesta/risposta
HTTP reale e, dove rilevante, log di servizio o query SQL a supporto. Nessun fix
applicato (per decisione esplicita dell'utente, invariata dal round 1). HEAD al momento
del test: `fd91fbb`.

**Nota metodologica**: per il test di anonimizzazione GDPR (bug #1 sotto) è stato
necessario un guest reale con soggiorni/fatture recenti (2026) ancora entro le finestre
legali TULPS (5 anni) e fiscale (10 anni) — cioè qualunque guest normale nel sistema,
dato che tutti i dati risalgono al 2026. Non è stata fatta alcuna manipolazione diretta
del database per aggirare i vincoli legali: l'anonimizzazione è stata invocata tramite
la vera API (`DELETE /api/v1/guests/{id}`) su un guest reale (Mario Rossi, dati residui
del round 1), esattamente come la userebbe un ADMIN reale.

## Bug trovati

### 1. 🔴 Guardia legale GDPR (TULPS/fiscale) completamente non funzionante — anonimizzazione riuscita nonostante vincoli attivi (il più grave) — ✅ RISOLTO (T-GST-06, Fase A piano fix-order)

**Dove**: catena di 3 cause indipendenti che si sommano:
- `guest-service/.../client/BillingServiceClient.java:29` — il metodo Feign
  `getLastInvoiceDate` chiama `/api/v1/invoices/guest/{guestId}/last-invoice-date`, ma la
  rotta reale del controller è `/api/v1/invoices/guest/{guestId}/last-date`
  (`billing-service/.../controller/InvoiceController.java:176`) — **mismatch di path,
  404 garantito ad ogni chiamata**.
- `frontdesk-service/.../stays/service/impl/StayServiceImpl.java:611` (metodo
  `getLastStayDateForGuest`) e `billing-service/.../service/impl/InvoiceServiceImpl.java:343`
  (metodo `getLastInvoiceDateForGuest`) sono gli **unici due metodi `public final`** nelle
  rispettive classi (tutti gli altri metodi sono `public` semplice) — con il proxying
  CGLIB di default di Spring Boot (`proxy-target-class=true`), un metodo `final` non può
  essere sovrascritto dalla sottoclasse proxy: la chiamata finisce per eseguire il bytecode
  originale su un'istanza proxy mai inizializzata via dependency injection, con
  `this.stayRepository`/`this.invoiceRepository` **null** → `NullPointerException` → `500`,
  riprodotto al 100% su ogni guestId testato (log reali sotto).
- Il fallback del circuit breaker (`StayServiceClient.java:41-44`,
  `BillingServiceClient.java:41-44`) dichiara nel Javadoc di essere "fail-safe... block
  deletion" (ritorna `hasStays/hasInvoices=true`), ma la logica che lo consuma
  (`GuestServiceImpl.computeTulpsExpiry:265-271`, `computeFiscalExpiry:273-278`, e
  `GuestRetentionJobServiceImpl.shouldAnonymise:114,125`) tratta `lastStayDate()==null`
  (sempre vero nel fallback) come "nessun vincolo" — **il "fail-safe" dichiarato è in
  realtà fail-open**: il vincolo non viene mai applicato quando il fallback scatta.

**Riprodotto**: guest `Mario Rossi` (`45ebe6ff-ab3a-4eb9-a338-5ba367e3441b`, dati residui
round 1) con 2 soggiorni `CHECKED_OUT` reali e 2 fatture `PAID` reali, tutti datati 2026
(quindi ben dentro sia la finestra TULPS 5 anni sia quella fiscale 10 anni) →
`DELETE /api/v1/guests/45ebe6ff-ab3a-4eb9-a338-5ba367e3441b` come ADMIN →
**`204 No Content`** (atteso: `451` con `unlocksAt`/`legalBasis`). Verificato in DB:

```
hotel_guest.guests: first_name=GDPR, last_name=ERASED_45ebe6ff, email=NULL,
                     phone=NULL, address=NULL, active=f
```

Log `frontdesk-service` al momento della chiamata:
```
ERROR ... Unhandled exception: Cannot invoke
"StayRepository.findTopByGuestIdAndHotelIdOrderByActualCheckInTimeDesc(...)"
because "this.stayRepository" is null
	at StayServiceImpl.getLastStayDateForGuest(StayServiceImpl.java:614)
```
Log `billing-service` (stesso pattern, causa diversa — mismatch di path, non NPE):
```
ERROR ... NoResourceFoundException: No static resource
api/v1/invoices/guest/45ebe6ff-ab3a-4eb9-a338-5ba367e3441b/last-invoice-date
```
Riprodotto anche direttamente (`GET /api/v1/stays/guest/{qualunque-guestId}/last-date` →
sempre `500`, testato su 3 guest diversi, incluso uno mai toccato prima da questo round).

**Bonus — conferma dal vivo del gap `COMPLIANCE_AUDIT_2026-08.md` §3 "Retention
automatica"**: nonostante l'anonimizzazione lato `guest-service`, `hotel_frontdesk.stay_guests`
per gli stessi due soggiorni conserva **ancora** `first_name=Mario, last_name=Rossi,
document_number=AB1234567, citizenship=100000100` — invariato prima e dopo la DELETE.

**Impatto**: la doppia guardia legale che `COMPLIANCE_AUDIT_2026-08.md` §3 valuta
✅ Implementato ("blocco esplicito HTTP 451... doppia guardia legale TULPS 5 anni +
fiscale 10 anni") **non protegge nulla nel sistema live attuale**: qualunque ADMIN può
cancellare irreversibilmente l'identità di un ospite in qualunque momento, anche con
soggiorni/fatture recentissimi, esponendo l'hotel a sanzioni per mancato rispetto degli
obblighi di conservazione TULPS/civilistici. La stessa logica rotta è usata dal job
notturno `GuestRetentionJobServiceImpl` (`@Scheduled 0 0 2 * * *`) — oggi dormiente
perché pre-filtra su `gdprConsentDate < oggi-10anni` (nessun guest esistente lo supera
ancora), ma quando lo supererà userà la stessa catena rotta.

**Fix suggerito (non applicato)**: (1) correggere il path Feign in
`BillingServiceClient.java` per farlo combaciare con `/last-date`; (2) rimuovere `final`
da entrambi i metodi (o comunque evitare il self-proxy CGLIB su questi bean); (3) — il
fix concettualmente più importante — quando `hasStays()`/`hasInvoices()` è `true` ma la
data è sconosciuta (incluso da fallback), il vincolo legale va trattato come *bloccante
per indeterminazione*, mai come "nessun vincolo": la semantica attuale converte un fail
dichiaratamente fail-closed in un fail-open silenzioso.

---

### 2. 🔴 Export FatturaPA genera un documento fiscalmente non valido (Partita IVA fittizia) senza bloccare l'operazione — ✅ RISOLTO (Fase B2 piano fix-order)

**Dove**: `billing-service/.../service/impl/FatturaPAServiceImpl.java`,
`validateFiscalAddress()` (righe 255-262) controlla solo `hotel.cap()/comune()/provincia()`,
mai `hotel.vatNumber()/hotelName()/address()`. I fallback di `sanitize()` (righe 303, 329,
337, 341) sostituiscono silenziosamente placeholder (`"HOTELPMS"`, `"00000000000"`,
`"Hotel"`, `"-"`) quando i dati reali sono `null`.

**Riprodotto**: `hotel_settings` per l'hotel corrente ha `vat_number`, `fiscal_code`,
`hotel_name`, `address` tutti `NULL` (verificato via `psql`; solo `cap/comune/provincia`
sono valorizzati). `GET /api/v1/invoices/{id}/fatturaPA` → **`200 OK`**, XML
schema-valido (passa la validazione XSD ufficiale AE) con:
```xml
<CedentePrestatore>
  <IdFiscaleIVA><IdPaese>IT</IdPaese><IdCodice>00000000000</IdCodice></IdFiscaleIVA>
  <Anagrafica><Denominazione>Hotel</Denominazione></Anagrafica>
  ...
  <Sede><Indirizzo>-</Indirizzo>...
```
L'export viene registrato e la fattura bloccata (`invoice_fiscal_exports`, hash SHA-256
persistito) esattamente come se fosse un export legittimo.

**Impatto**: un hotel che non ha ancora completato il proprio profilo fiscale (plausibile
in fase di onboarding/pilot — è esattamente lo stato dell'hotel di test corrente) può
generare e **bloccare permanentemente** (via `INVOICE_LOCKED_AFTER_EXPORT`, vedi bug #3)
quello che sembra un export fiscale riuscito, ma porta una Partita IVA fittizia — nella
realtà un documento del genere sarebbe respinto da SDI, ma l'applicazione non segnala
nulla e considera l'operazione conclusa con successo.

**Fix suggerito (non applicato)**: estendere `validateFiscalAddress` (o una nuova
`validateFiscalIdentity`) a richiedere anche `hotel.vatNumber()` (o `fiscalCode()`) e
`hotel.hotelName()` non vuoti, con un errore esplicito (es. `HOTEL_FISCAL_IDENTITY_INCOMPLETE`)
prima di generare/bloccare l'export.

---

### 3. 🟠 Il blocco `INVOICE_LOCKED_AFTER_EXPORT` è incompleto: pagamenti e stato SDI restano mutabili dopo l'export, producendo ri-export divergenti sullo stesso numero fattura — 🟡 PARZIALMENTE RISOLTO (Fase B2 piano fix-order)

> **Nota di chiusura**: `addPayment` ora bloccato dopo l'export (`INVOICE_LOCKED_AFTER_EXPORT`,
> verificato dal vivo) — i pagamenti alimentano `DatiPagamento` nell'XML, quindi un pagamento
> post-export causerebbe davvero un ri-export divergente sullo stesso numero fattura.
> `updateSdiStatus` **deliberatamente lasciato non bloccato**: verificato che `sdiStatus` non è
> mai letto da `FatturaPAServiceImpl` in fase di generazione XML — è puro metadata di
> tracciamento trasmissione (NOT_SENT/SENT/ACCEPTED/REJECTED), ed è esattamente il passo che
> l'operatore deve compiere **subito dopo** che `generateXml()` blocca la fattura (registrare
> l'esito della trasmissione appena fatta). Bloccarlo anche lì avrebbe reso il campo bloccato per
> sempre al suo default, rompendo il flusso invece di proteggerlo — correzione rispetto alla
> lettura letterale del bug report originale.

**Dove**: `billing-service/.../service/impl/InvoiceServiceImpl.java` — `assertNotFiscallyLocked()`
è invocato solo da `addCharge()` (riga 111) e `updateDocumentType()` (riga 289);
`PaymentServiceImpl.addPayment()` (nessun controllo) e `updateSdiStatus()` (nessun
controllo) restano completamente liberi.

**Riprodotto** (fattura di test 2026/0024, 200,00€, `ROOM_NIGHT`): export FatturaPA
(blocca) → `addCharge` → **`409 INVOICE_LOCKED_AFTER_EXPORT`** (corretto) →
`updateDocumentType` → **`409`** (corretto) → `addPayment 200,00€ CASH` →
**`201 Created`**, l'invoice passa `ISSUED`→`PAID` (**non bloccato — atteso invece**) →
`updateSdiStatus` → **`200 OK`** (**non bloccato**). Ri-esportando la stessa fattura dopo
il pagamento: `DatiPagamento` cambia da un placeholder (`MP05`/importo pieno non pagato)
a `MP01 CASH 200.00` — hash SHA-256 **diverso** dal primo export, ma `invoice_fiscal_exports`
conserva **entrambi** gli snapshot senza alcun flag su quale sia (o sia mai stato)
davvero trasmesso. Inoltre: **nessun endpoint `DELETE`/cancellazione esiste affatto** su
`InvoiceController` — una fattura non può mai essere annullata via API, a prescindere
dallo stato di lock.

**Impatto**: è la misurazione precisa del "vicolo cieco nota di credito" (T-BILL-06).
Contrariamente a quanto lascia intendere `COMPLIANCE_AUDIT_2026-08.md` §2
("immutabile post export"), il contenuto finanziario della fattura **non è affatto
congelato** all'export: un operatore può continuare a registrare pagamenti dopo lo
snapshot fiscale, e l'insieme degli export ufficiali per "fattura 2026/0024" contiene ora
due versioni fiscalmente incoerenti tra loro, senza modo di stabilire quale sia quella
davvero trasmessa al commercialista/SDI.

**Fix suggerito (non applicato)**: estendere `assertNotFiscallyLocked()` anche a
`addPayment()` e `updateSdiStatus()` (oppure, se i pagamenti post-export devono restare
possibili per il cash-flow, marcare esplicitamente lo snapshot precedente come
superato/stale invece di lasciarlo coesistere silenziosamente).

---

### 4. 🟠 `GET /invoices/export` (batch) blocca fiscalmente in modo permanente e silenzioso ogni fattura toccata, senza dry-run né conferma — ✅ RISOLTO (Fase B2 piano fix-order)

**Dove**: `billing-service/.../service/impl/FatturaPAServiceImpl.java`,
`generateBatchZip()`/`appendInvoiceToBatch()` (righe 166-220) chiama `generateXml()` per
ogni fattura eleggibile nel periodo, e `generateXml()` chiama incondizionatamente
`recordExport()` (blocca la fattura) alla fine.

**Riprodotto**: `GET /api/v1/invoices/export?from=2000-01-01&to=2030-12-31` come ADMIN →
`200`, ZIP con XML per ogni fattura FATTURA con indirizzo hotel/ospite completo nel
periodo; ciascuna di queste fatture ha ricevuto una **nuova riga** in
`invoice_fiscal_exports` (confermato per `2026/0019`, che aveva già 1 export da una
sessione precedente e ne ha ricevuto un secondo da questa singola chiamata batch).
In più: il frontend (`frontend/src/services/billingService.ts`) non espone alcun metodo
verso questo endpoint — oggi è raggiungibile solo via API/Postman diretta, non da alcun
pulsante UI (riduce ma non elimina il rischio per script/integrazioni future).

**Impatto**: una singola chiamata `GET` (che si presenta come un'azione di sola lettura,
"vediamo come sarebbe un export dell'anno") blocca irreversibilmente ogni fattura che
riesce a processare, senza alcun avviso, conferma o modalità anteprima.

**Fix suggerito (non applicato)**: aggiungere una modalità dry-run/preview esplicita,
o quantomeno richiedere una conferma esplicita per questo endpoint (es. `POST` invece di
`GET`, parametro di intento esplicito), ed esporlo in UI con un dialog di avviso prima di
cablarlo.

---

### 5. 🟠 Il ruolo RECEPTIONIST ha accesso libero a tutte le operazioni fiscali sulle fatture (nessun RBAC su `InvoiceController`/gateway) — ✅ RISOLTO (T-BILL-07, Fase A piano fix-order)

**Dove**: `billing-service/.../controller/InvoiceController.java` non ha alcun
`@PreAuthorize` (a differenza di `OwnerReportController.java:45`, che lo usa
correttamente); `api-gateway/.../AuthenticationFilter.java` — `WRITE_RESTRICTED_PREFIXES`
(riga 81) e `FULLY_RESTRICTED_PREFIXES` (riga 89) non includono `/api/v1/invoices`.

**Riprodotto dal vivo** con `r2receptionist0804` (ruolo RECEPTIONIST): `PATCH
/invoices/{id}/document-type` → `200`; `GET /invoices/{id}/fatturaPA` → `200`, genera e
blocca un vero export fiscale; `GET /invoices/export` (batch) → `200`. Per confronto,
`GET /api/v1/reports/owner` con lo stesso utente → correttamente `403` (qui il gateway
funziona, `FULLY_RESTRICTED_PREFIXES` include `/api/v1/reports`).

**Impatto**: qualunque receptionist può generare/bloccare documenti fiscali ufficiali,
cambiare FATTURA↔RICEVUTA e impostare lo stato di trasmissione SDI, senza alcun controllo
di autorizzazione aggiuntivo lungo tutto il percorso.

**Fix suggerito (non applicato)**: aggiungere `@PreAuthorize("hasAnyRole('ADMIN','OWNER')")`
almeno sugli endpoint fiscalmente sensibili (`document-type`, `fatturaPA`, `export`,
`sdi-status`) in `InvoiceController`, e/o aggiungere `/api/v1/invoices` a
`WRITE_RESTRICTED_PREFIXES` nel gateway.

---

### 6. 🟡 RECEPTIONIST può modificare la policy di retention GDPR dell'hotel — ✅ RISOLTO (T-GST-07, Fase A piano fix-order)

**Dove**: `guest-service/.../controller/GuestPrivacySettingsController.java`, nessun
`@PreAuthorize` su classe o metodo.

**Riprodotto**: `r2receptionist0804` → `PUT /api/v1/guests/settings
{"guestRetentionYears":7}` → **`200 OK`** (era 5, diventa 7). Conferma dal vivo il delta
#4 di `COMPLIANCE_AUDIT_2026-08.md` §3 (aggiornamento 2026-08-04). Il floor TULPS di 5
anni resta comunque garantito lato service (`Math.max`), quindi non è possibile scendere
sotto il minimo legale, ma la configurazione hotel-wide resta modificabile da un ruolo
non amministrativo.

**Fix suggerito (non applicato)**: `@PreAuthorize("hasAnyRole('ADMIN','OWNER')")` su
`GuestPrivacySettingsController`.

---

### 7. 🟡 Export CSV di Owner Analytics: header in inglese hardcoded, stato non tradotto, nessun BOM UTF-8/separatore adatto a Excel-IT

**Dove**: `frontend/src/services/billingReportService.ts`, `exportToCsv()` (righe 17-38).

**Riprodotto**: header hardcoded in inglese (`"Invoice #"`, `"Amount (€)"`, `"Status"`,
...) — viola la regola i18n del progetto ("ALL user-facing strings via i18n keys — zero
hardcoded text"); `inv.status` scritto grezzo (`PAID`/`ISSUED`/`CANCELLED`) invece
dell'etichetta tradotta — confermato anche a schermo: la tabella Owner Analytics mostra
gli stessi badge grezzi "PAID"/"ISSUED" (screenshot), a differenza per esempio del
Calendario che mostra correttamente "Confermata"/"In Attesa"/ecc. Il `Blob` è costruito
con `type: 'text/csv;charset=utf-8;'` ma **senza BOM** (`﻿`) e con separatore virgola
— su Excel Windows in locale italiano (dove la virgola è il separatore decimale),
l'apertura diretta del file tipicamente produce sia caratteri accentati/€ illeggibili sia
il mancato riconoscimento delle colonne (tutto finisce in una singola colonna A).

**Fix suggerito (non applicato)**: instradare header ed etichette di stato via i18n;
anteporre `﻿` al contenuto del `Blob`; valutare separatore `;` o documentare
l'importazione come "UTF-8 delimitato da virgola".

---

### 8. 🟡 Widget "Stato Camere" in dashboard: testo di numero camera e stato sovrapposto e illeggibile per nomi camera lunghi

**Dove**: pagina Bacheca (`/`), componente griglia stato camere. Confrontato con
`/calendar` (Calendario Planning), che gestisce correttamente gli stessi nomi camera
lunghi andando a capo su due righe, senza sovrapposizioni.

**Riprodotto**: screenshot dal vivo — le celle per camere con nomi generati lunghi (es.
`E2E-LIVE-1785431995278`, dato di test da sessioni E2E precedenti) sovrappongono il testo
sulla cella adiacente, rendendo la griglia illeggibile. Stessa causa radice dei nomi
camera lunghi già nota dal round 1 (bug #3, mismatch `VARCHAR(20)` in fb-service), ma
sintomo nuovo e distinto: qui è un problema di layout/CSS lato frontend nella dashboard,
non un crash backend.

**Fix suggerito (non applicato)**: applicare alle celle della griglia stato camere in
dashboard lo stesso trattamento di wrap/troncamento già usato nel Planning board
(`/calendar`), oppure `max-width` + ellissi con tooltip.

---

### 9. 🟢 `MissingServletRequestParameterException` mappata a 500 invece di 400 (sistemico, common-web-lib) — ✅ RISOLTO (Fase B1 piano fix-order)

**Dove**: `common-web-lib/.../exception/AbstractProblemDetailAdvice.java` — nessun
`@ExceptionHandler(MissingServletRequestParameterException.class)` dedicato; ricade sul
catch-all generico `@ExceptionHandler(Exception.class)` (riga 142) → `500`.

**Riprodotto**: `GET /api/v1/reports/owner` senza i parametri obbligatori
`startDate`/`endDate` → **`500 Internal Server Error`** invece di `400 Bad Request` (log
billing-service: `Required request parameter 'startDate'... is not present`). Stesso
pattern del bug #6 del round 1 (eccezioni di validazione mappate a 500 anziché al codice
corretto), qui a livello della libreria condivisa quindi potenzialmente presente su
qualunque endpoint `@RequestParam` obbligatorio di qualunque servizio.

**Fix suggerito (non applicato)**: aggiungere un handler dedicato per
`MissingServletRequestParameterException` (e simili eccezioni di binding Spring) che
ritorni `400`, nella `AbstractProblemDetailAdvice` condivisa.

---

## Verificato corretto (nessun bug, per completezza)

| Scenario | Esito |
|---|---|
| Numerazione fattura sotto concorrenza reale (6 `POST /invoices/stay` paralleli) | ✅ `2026/0025`..`2026/0030`, nessun buco/duplicato (lock pessimistico tenuto) |
| Doppio pagamento concorrente (5 richieste parallele da 100€ su fattura da 100€) | ✅ 1 sola `201`, 3× `400 INVOICE_ALREADY_PAID`, 1× `409 CONCURRENT_MODIFICATION`; DB conferma un solo pagamento registrato |
| Export batch con periodo invertito (`from > to`) | ✅ `400 EXPORT_PERIOD_INVALID` |
| Export batch con periodo senza fatture eleggibili | ✅ `200`, ZIP con solo `index.csv` header, nessun crash |
| Export FatturaPA forzato su documento `RICEVUTA` | ✅ Bloccato, `409 SDI_ONLY_VALID_FOR_FATTURA` |
| Export FatturaPA su fattura a importo 0 | ⚠️ Riesce (`200`), XML schema-valido con riga fittizia "Soggiorno" a 0,00€ — non blocca ma non è un vero bug (nessun crash/corruzione), solo un varco di validazione minore |
| Ricerca ospiti/fatture con query SQLi-style, `%`, accentate, stringa lunghissima | ✅ Nessun crash, query parametrizzate correttamente; stringa >URI-limite → `414` gestito a livello HTTP |
| IVA disaggregata: `ROOM_NIGHT` (10%) + `FB_ORDER` (10%) + `EXTRA` (22%) sullo stesso soggiorno | ✅ Raggruppamento corretto per aliquota in 2 righe `DatiRiepilogo` nell'XML, importi riconciliati esattamente; nota: `FB_ORDER` è tassato al 10% come `ROOM_NIGHT` (non 22%) per scelta del codice (`InvoiceServiceImpl.vatRateFor`) — coerente con la somministrazione di alimenti/bevande in hotel, ma vale la pena chiarirlo esplicitamente dove il gap E12 di `ROADMAP.md` lo presenta come "10% camere / 22% F&B" |
| Generazione PDF per fattura multi-aliquota | ✅ `200`, PDF valido, nessun crash |
| Gate ADMIN/OWNER su `/api/v1/reports/owner` da RECEPTIONIST | ✅ `403` (gateway `FULLY_RESTRICTED_PREFIXES` funziona correttamente qui) |

---

## Priorità consigliate (nessun fix applicato qui)

1. **#1 (guardia legale GDPR completamente bypassata)** — il più grave in assoluto tra i
   due round: distrugge irreversibilmente PII di ospiti reali senza rispettare obblighi
   di legge, root cause tripla (path Feign errato + metodo `final` sotto CGLIB + logica
   fail-open), tocca guest-service, frontdesk-service e billing-service insieme
2. **#2 (Partita IVA fittizia in export FatturaPA)** — genera documenti fiscalmente non
   validi che vengono trattati e bloccati come se fossero corretti
3. **#3 (blocco post-export incompleto — pagamenti/SDI status)** — misura precisa del
   vicolo cieco nota di credito già noto (T-BILL-06), ma qui si dimostra che il problema
   è più ampio: la fattura non è nemmeno davvero "congelata" come dichiarato
4. **#5 (RECEPTIONIST senza restrizioni su operazioni fiscali)** — stesso pattern del
   bug #7 del round 1 (`GuestController`), qui su superficie ancora più sensibile
5. **#4 (batch export blocca silenziosamente)** — basso rischio di esposizione reale
   (non cablato in UI oggi), ma comportamento pericoloso se mai esposto o scriptato
6. **#6 (RECEPTIONIST può cambiare retention settings)** — stesso pattern di RBAC
   mancante, impatto minore grazie al floor TULPS server-side
7. **#7 (CSV Owner Analytics — i18n/encoding)** — impatto pratico immediato per l'utente
   finale italiano (hotel owner), bassa complessità di fix
8. **#8 (overlap grafico dashboard)** — cosmetico ma visibile, stessa causa radice di
   dati di test già nota dal round 1
9. **#9 (500 invece di 400 su parametri mancanti)** — cosmetico/osservabilità, bassa
   urgenza, sistemico
