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

### 1. 🔴 Fallimento silenzioso degli addebiti su rifiuto legittimo di billing-service (il più grave)

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

### 2. 🟠 Nessuna validazione dello stato del soggiorno negli ordini F&B

**Dove**: `fb-service/.../service/impl/RestaurantOrderServiceImpl.java` (creazione e
conferma ordine).

**Riprodotto**: creato e confermato con successo un ordine F&B su uno stay già
`CHECKED_OUT` (ospite non più in hotel). Nessun controllo sullo stato del soggiorno prima
di accettare l'ordine — si combina con il bug #1: l'ordine viene "confermato" ma
l'addebito fallisce silenziosamente, quindi l'esito pratico è un ordine fantasma che non
risulta né bloccato né fatturato.

---

### 3. 🟠 Crash 500 su camere con nome > 20 caratteri

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

### 4. 🟠 Nessun enforcement sulle transizioni di stato camera

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

---

### 5. 🟡 `documentType` modificabile su fattura già `PAID`

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

### 6. 🟢 Cancellazione ospite con prenotazioni attive: 500 invece di 409

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

### 7. `GuestController` senza alcun `@PreAuthorize`

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
