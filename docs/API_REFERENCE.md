# API Reference — Hotel PMS

**Versione:** 0.1 — 2026-05-17  
**Base URL:** `http://localhost:8080` (sviluppo) / `https://pms.tuohotel.com` (produzione)  
**Autenticazione:** JWT in cookie httpOnly (vedere §1)

> **Nota:** questa è la documentazione degli endpoint interni usati dal frontend
> e dai tecnici per configurare il sistema. Una API pubblica per integrazioni ISV
> è in roadmap — vedere `docs/ROADMAP.md §E4`.
> La documentazione Swagger è accessibile in sviluppo su `http://localhost:8080/swagger-ui.html`.

---

## 1. Autenticazione

Tutti gli endpoint (eccetto `/api/v1/auth/login`) richiedono autenticazione.
Il JWT viene trasmesso automaticamente via cookie httpOnly — non è necessario
aggiungere header `Authorization` manualmente.

### Login

```
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "password"
}
```

**Risposta 200:**
```json
{
  "username": "admin",
  "role": "ADMIN",
  "hotelId": "uuid-hotel",
  "mustChangePassword": false
}
```
Il server imposta cookie httpOnly `jwt` (access token, 15 min) e
`refresh_token` (refresh token, 7 giorni). Il browser li invia automaticamente.

Se `mustChangePassword: true`: l'utente deve cambiare la password su `/profile`
prima di poter accedere ad altre funzionalità.

### Refresh token silenzioso

Il frontend esegue questo automaticamente quando il JWT scade (Axios interceptor).

```
POST /api/v1/auth/refresh
```

Usa il cookie `refresh_token`. Risposta: nuovi cookie JWT + refresh_token.

### Logout

```
POST /api/v1/auth/logout
```

Revoca il refresh token (Redis blacklist) e cancella i cookie.

---

## 2. Formato errori

Tutti gli endpoint restituiscono errori nel formato **RFC 7807**:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Room 101 is already occupied during 2026-05-01 / 2026-05-05"
}
```

| Status | Significato |
|---|---|
| 400 | Validazione fallita (campo mancante o formato errato) |
| 401 | JWT scaduto o assente |
| 403 | Ruolo insufficiente (RECEPTIONIST su endpoint ADMIN/OWNER) |
| 404 | Risorsa non trovata (o non appartiene all'hotel) |
| 409 | Conflitto (es. overbooking) |
| 422 | Business rule violata (es. checkout con fattura aperta) |
| 429 | Rate limit superato |
| 451 | Legal hold GDPR (ospite con soggiorni attivi) |
| 502 | Servizio esterno non raggiungibile (es. portale PS) |

---

## 3. Rate Limiting

Il gateway applica rate limiting per IP + path tramite Redis token bucket.
Superato il limite, la risposta è **HTTP 429** con header:

```
X-RateLimit-Remaining: 0
Retry-After: <secondi>
```

---

## 4. Endpoint principali

### 4.1 Gestione camere

```
GET    /api/v1/rooms                  Lista camere dell'hotel (tutti i ruoli)
POST   /api/v1/rooms                  Crea camera (ADMIN/OWNER)
GET    /api/v1/rooms/{id}             Dettaglio camera
PUT    /api/v1/rooms/{id}             Aggiorna camera (ADMIN/OWNER)
PATCH  /api/v1/rooms/{id}/status      Aggiorna status camera {AVAILABLE|OCCUPIED|DIRTY|MAINTENANCE}
GET    /api/v1/room-types             Lista tipologie camera
POST   /api/v1/room-types             Crea tipologia (ADMIN/OWNER)
```

### 4.2 Prenotazioni

```
GET    /api/v1/reservations           Lista prenotazioni (paginata, tutti i ruoli)
POST   /api/v1/reservations           Crea prenotazione
GET    /api/v1/reservations/{id}      Dettaglio prenotazione
PUT    /api/v1/reservations/{id}      Aggiorna prenotazione
PATCH  /api/v1/reservations/{id}/status  Aggiorna stato {CONFIRMED|CANCELLED|CHECKED_IN|CHECKED_OUT}
```

**Payload POST/PUT:**
```json
{
  "guestId": "uuid",
  "roomIds": ["uuid-camera"],
  "checkInDate": "2026-06-01",
  "checkOutDate": "2026-06-05",
  "expectedGuests": 2
}
```

### 4.3 Soggiorni (Check-in / Check-out)

```
GET    /api/v1/stays                  Lista soggiorni (paginata: ?page=0&size=20)
POST   /api/v1/stays/check-in         Check-in (da prenotazione o walk-in)
PATCH  /api/v1/stays/{id}/check-out   Check-out
```

**Payload check-in:**
```json
{
  "reservationId": "uuid-prenotazione",   // null per walk-in
  "roomId": "uuid-camera",
  "expectedCheckOutDate": "2026-06-05",   // obbligatorio solo per walk-in
  "guests": [
    {
      "guestId": "uuid-ospite",
      "travellerType": "OSPITE_SINGOLO",
      "gender": "M",
      "dateOfBirth": "1985-05-20",
      "placeOfBirth": "058091000",
      "citizenship": "100000100",
      "documentType": "PASOR",
      "documentNumber": "AA1234567",
      "documentPlaceOfIssue": "058091000"
    }
  ]
}
```

Valori `travellerType`: `OSPITE_SINGOLO` (16), `CAPOFAMIGLIA` (17),
`CAPOGRUPPO` (18), `FAMILIARE` (19), `MEMBRO_GRUPPO` (20).
Per `FAMILIARE` e `MEMBRO_GRUPPO`: omettere i campi documento.

### 4.4 Alloggiati PS

```
GET    /api/v1/stays/reports/alloggiati?date=YYYY-MM-DD
       → text/plain — file .txt 168-char per upload manuale sul portale PS

POST   /api/v1/stays/reports/alloggiati/submit?date=YYYY-MM-DD
       → invia al portale PS via SOAP (ADMIN/OWNER)
       → 200 OK se successo, 502 se portale non raggiungibile

GET    /api/v1/stays/reports/alloggiati/json?date=YYYY-MM-DD
       → application/json — export strutturato per debug (ADMIN/OWNER)
```

### 4.5 Fatture e pagamenti

```
GET    /api/v1/invoices               Lista fatture (paginata)
GET    /api/v1/invoices/{id}          Dettaglio fattura con charges e payments
GET    /api/v1/invoices/{id}/pdf      Scarica PDF fattura (application/pdf)
POST   /api/v1/invoices/{id}/payments Registra pagamento
```

**Payload pagamento:**
```json
{
  "amount": 150.00,
  "paymentMethod": "CREDIT_CARD",
  "transactionReference": "VISA-xxxx-1234"
}
```

Valori `paymentMethod`: `CASH`, `CREDIT_CARD`, `DEBIT_CARD`, `BANK_TRANSFER`, `CHECK`.

### 4.6 Ospiti

```
GET    /api/v1/guests                 Lista ospiti (con ricerca: ?q=rossi)
POST   /api/v1/guests                 Crea ospite
GET    /api/v1/guests/{id}            Dettaglio ospite
PUT    /api/v1/guests/{id}            Aggiorna ospite
DELETE /api/v1/guests/{id}            Soft delete (HTTP 451 se soggiorni attivi)
GET    /api/v1/guests/{id}/export     Export GDPR Art.20 (JSON)
```

### 4.7 Food & Beverage

```
GET    /api/v1/fb/menu-items          Lista menu per hotel (tutti i ruoli)
POST   /api/v1/fb/menu-items          Crea voce menu (ADMIN/OWNER)
PUT    /api/v1/fb/menu-items/{id}     Aggiorna voce menu (ADMIN/OWNER)
DELETE /api/v1/fb/menu-items/{id}     Elimina voce (HTTP 409 se ordini PENDING)
GET    /api/v1/fb/orders              Lista ordini
POST   /api/v1/fb/orders              Crea ordine
POST   /api/v1/fb/orders/{id}/confirm Conferma e addebita su conto camera
```

### 4.8 Configurazione hotel (ADMIN/OWNER)

```
GET    /api/v1/stays/settings         Legge impostazioni hotel
PUT    /api/v1/stays/settings         Aggiorna impostazioni hotel (ADMIN/OWNER)
```

**Payload PUT:**
```json
{
  "alloggiatiAutoSend": true,
  "hotelName": "Hotel Bella Vista",
  "address": "Via Roma 12, Firenze",
  "vatNumber": "12345678901",
  "fiscalCode": "BLLVST80A01H501X",
  "logoUrl": "https://..."
}
```

### 4.9 Lookup Alloggiati

```
GET    /api/v1/stays/lookup/stati            Lista stati (codici PS)
GET    /api/v1/stays/lookup/comuni?q=roma    Autocomplete comuni italiani
GET    /api/v1/stays/lookup/tipdoc           Lista tipi documento
```

### 4.10 Gestione utenti (ADMIN/OWNER)

```
GET    /api/v1/auth/users                           Lista utenti dell'hotel
POST   /api/v1/auth/users                           Crea utente
PATCH  /api/v1/auth/users/{id}/deactivate           Disattiva account
PATCH  /api/v1/auth/users/{id}/activate             Riattiva account
PATCH  /api/v1/auth/users/{id}/reset-password       Reset password (ADMIN/OWNER)
```

---

## 5. Paginazione

Gli endpoint che restituiscono liste supportano paginazione tramite query parameter:

```
GET /api/v1/stays?page=0&size=20
```

**Risposta paginata:**
```json
{
  "content": [...],
  "totalElements": 150,
  "totalPages": 8,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false
}
```

---

## 6. Header inter-service (solo uso interno)

Gli header seguenti vengono iniettati automaticamente dall'API Gateway.
Non devono essere inviati dal client — vengono ignorati se presenti nella richiesta.

| Header | Valore | Scopo |
|---|---|---|
| `X-Auth-User` | username dal JWT | Identità utente per i servizi |
| `X-Auth-Role` | ruolo dal JWT | RBAC nei servizi |
| `X-Auth-Hotel` | `hotelId` dal JWT | Multi-tenancy nei servizi |
| `X-Internal-Signature` | HMAC-SHA256 | Autenticità della richiesta |
| `X-Correlation-ID` | UUID | Tracciabilità distribuita nei log |
