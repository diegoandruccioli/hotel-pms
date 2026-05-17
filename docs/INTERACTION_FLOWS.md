# Hotel PMS — Flussi di Interazione

**Versione:** 1.0 — 2026-05-07  
**Scope:** Flussi end-to-end tra browser, frontend React, API Gateway e microservizi

---

## Architettura generale

```
[Browser]
    │  HTTPS — cookie httpOnly (jwt, refresh_token)
    ▼
[API Gateway :8080]
    │  Valida JWT → inietta X-Auth-User, X-Auth-Role, X-Auth-Hotel
    │  Calcola X-Internal-Signature (HMAC-SHA256)
    │  Propaga X-Correlation-ID → MDC in ogni servizio
    │  Rate limiting via Redis Token Bucket
    ├──► auth-service       :8087
    ├──► guest-service      :8083
    ├──► inventory-service  :8081
    ├──► reservation-service :8082
    ├──► stay-service       :8084
    ├──► billing-service    :8085
    └──► fb-service         :8086
```

Tutte le chiamate dal frontend passano per l'API Gateway. I microservizi si chiamano tra loro via **OpenFeign** con **CircuitBreaker Resilience4j**.

---

## 1. Login

```
Browser → POST /api/v1/auth/login {username, password}
  → Gateway (no JWT check — route pubblica)
  → auth-service
      ├── verifica hash bcrypt della password
      ├── genera JWT (access 15min) e refresh token (7gg)
      └── risponde con Set-Cookie: jwt=<token>; HttpOnly; SameSite=Strict
                         Set-Cookie: refresh_token=<rt>; HttpOnly; SameSite=Strict

Browser riceve i cookie → salva automaticamente → invia con ogni richiesta successiva
```

**mustChangePassword:** Se il flag è attivo sull'account, il login ritorna `mustChangePassword: true` nel body. Il frontend reindirizza a `/profile` e blocca la navigazione fino al cambio.

---

## 2. Refresh Token silenzioso

```
Browser → richiesta qualsiasi → Gateway → risposta 401 (JWT scaduto)
  → Axios interceptor intercetta il 401
  → POST /api/v1/auth/refresh (cookie refresh_token)
  → auth-service
      ├── verifica refresh_token (versione + firma)
      ├── emette nuovo JWT + nuovo refresh_token (rotation)
      └── Set-Cookie: jwt=<nuovo>; Set-Cookie: refresh_token=<nuovo>
  → Axios ritenta la richiesta originale con il nuovo cookie
```

L'utente non vede interruzioni. Se anche il refresh fallisce (token scaduto o revocato), l'interceptor fa redirect a `/login`.

---

## 3. Prenotazione

```
Browser → POST /api/v1/reservations {guestId, roomIds[], checkInDate, checkOutDate, expectedGuests}
  → Gateway (valida JWT, inietta headers)
  → reservation-service
      ├── @PreAuthorize: ADMIN / OWNER / RECEPTIONIST
      ├── verifica disponibilità: GET /api/v1/rooms/{id} per ogni camera (→ inventory-service via Feign)
      │     CircuitBreaker: fallback con ConflictException se inventory non risponde
      ├── crea Reservation con status CONFIRMED
      └── risponde 201 con ReservationResponse

Modifica prenotazione:
  PUT /api/v1/reservations/{id}
    → stesso flusso, aggiorna date e camere

Cancellazione:
  PATCH /api/v1/reservations/{id}/status {status: CANCELLED}
    → reservation-service marca status CANCELLED
```

---

## 4. Check-in da prenotazione

```
Browser → POST /api/v1/stays/check-in
          {reservationId, roomId, guests: [{nome, cognome, sesso, dataNascita,
                                            statoDiNascita, luogoNascita, travellerType,
                                            tipoDocumento, numeroDocumento, ...}]}
  → Gateway
  → stay-service.checkIn()
      ├── verifica reservation (→ reservation-service via Feign)
      │     fallback: eccezione se non trovata
      ├── verifica guest (→ guest-service via Feign)
      ├── verifica room disponibile (→ inventory-service via Feign)
      ├── crea Stay con expectedCheckOutDate dalla reservation
      ├── salva StayGuest[] con i campi Alloggiati
      ├── PATCH /api/v1/rooms/{id}/status {OCCUPIED} (→ inventory-service via Feign)
      ├── PATCH /api/v1/reservations/{id}/status {CHECKED_IN} (→ reservation-service via Feign)
      ├── POST /api/v1/invoices/stay (→ billing-service via Feign)
      │     → crea Invoice con status=ISSUED, totalAmount=0, stayId
      │     fallback: log warning, check-in non bloccato
      └── se alloggiatiAutoSend=true:
            POST /api/v1/stays/reports/alloggiati/submit?date=oggi (interno)
              → genera file 168-char → SOAP GenerateToken → SOAP Send/Test
              → aggiorna Stay.alloggiatiSent=true
              fallback: log error, stay.alloggiatiSent=false, check-in completato
```

**Nota G5 — StayResponse arricchito:** `StayResponse` include i campi `guestDisplayName` (Cognome Nome dell'ospite principale) e `roomNumber` (numero camera), popolati al momento del check-in dai dati già disponibili via Feign. I soggiorni precedenti alla migration V9 hanno questi campi null; la UI mostra il fallback all'ID troncato.

---

## 5. Check-in Walk-in

```
Browser → POST /api/v1/stays/check-in
          {reservationId: null, roomId, guests: [...], expectedCheckOutDate}
  → stay-service.checkIn()
      ├── nessuna verifica reservation (null tollerato)
      ├── verifica room disponibile
      ├── crea Stay senza reservationId
      ├── [stesso flusso di addebiti e Alloggiati del check-in normale]
      └── risponde 201 StayResponse
```

---

## 6. Check-out

```
Browser → PATCH /api/v1/stays/{id}/check-out
  → stay-service.checkOut()
      ├── recupera Stay
      ├── GET /api/v1/invoices/stay/{stayId}/latest (→ billing-service via Feign)
      │     se invoice.status ≠ PAID → risponde 422 (checkout bloccato)
      ├── PATCH /api/v1/rooms/{roomId}/status {DIRTY} (→ inventory-service via Feign)
      ├── PATCH /api/v1/reservations/{reservationId}/status {CHECKED_OUT} (→ reservation-service via Feign)
      │     (solo se reservationId presente — walk-in: skip)
      └── aggiorna Stay.checkOutDate=oggi, status=CHECKED_OUT
```

---

## 7. Ordine F&B con addebito su camera

```
Browser → POST /api/v1/fb/orders
          {stayId, items: [{menuItemId, quantity}, ...]}
  → fb-service
      ├── crea RestaurantOrder con status=PENDING
      └── risponde 201 con OrderResponse

Browser → POST /api/v1/fb/orders/{id}/confirm
  → fb-service.confirmOrder()
      ├── calcola totalAmount
      ├── POST /api/v1/invoices/stay/{stayId}/charges (→ billing-service via Feign, con X-Internal-Signature HMAC)
      │     billing-service aggiunge InvoiceCharge, aggiorna invoice.totalAmount
      ├── aggiorna Order.status = BILLED_TO_ROOM
      └── risponde 200 OrderResponse
```

---

## 8. Registrare un pagamento

```
Browser → POST /api/v1/invoices/{invoiceId}/payments
          {amount, paymentMethod, transactionReference}
  → billing-service
      ├── aggiunge Payment alla fattura
      ├── ricalcola totalPaid
      ├── se totalPaid >= invoice.totalAmount → aggiorna invoice.status = PAID
      └── risponde 201 PaymentResponse
```

---

## 9. Invio Alloggiati manuale

```
Browser → GET /api/v1/stays/reports/alloggiati?date=YYYY-MM-DD
  → stay-service
      ├── recupera tutti gli StayGuest con arrivalDate=date e hotelId=X-Auth-Hotel
      ├── genera file .txt (168 char per record, CRLF, max 1000 righe)
      └── risponde con Content-Type: text/plain; Content-Disposition: attachment; filename=alloggiati-YYYY-MM-DD.txt

Browser → POST /api/v1/stays/reports/alloggiati/submit?date=YYYY-MM-DD
  → stay-service.AlloggiatiWebSenderService
      ├── POST SOAP GenerateToken (WsKey) → ottiene Token
      ├── POST SOAP Send (o Test se DRY_RUN=true) con file .txt
      └── risponde 200 / 422 con esito portale
```

---

## 10. Gestione Utenti (ADMIN)

```
GET /api/v1/users → auth-service → lista utenti del hotel
POST /api/v1/users → auth-service → crea utente con password temporanea + mustChangePassword=true
PATCH /api/v1/users/{id}/deactivate → auth-service → imposta active=false
PATCH /api/v1/users/{id}/activate → auth-service → imposta active=true
```

---

## 11. Profilo Hotel (ADMIN)

```
GET /api/v1/hotel-settings → stay-service → restituisce HotelSettings (nome, indirizzo, PIVA, CF, alloggiatiAutoSend, logoUrl)
PUT /api/v1/hotel-settings → stay-service → aggiorna HotelSettings
POST /api/v1/hotel-settings/logo → stay-service → carica logo, salva file, aggiorna logoUrl
```

---

## 12. Report Proprietario

```
GET /api/v1/reports/owner?startDate=...&endDate=...
  → billing-service
      ├── @PreAuthorize: OWNER / ADMIN
      ├── aggrega invoices nel periodo per hotelId
      └── risponde con {totalRevenue, totalInvoices, paidInvoices, collectionRate}
```

---

## Reset Password Utente

**Attori:** ADMIN, OWNER  
**Endpoint:** `PATCH /api/v1/auth/users/{userId}/reset-password`

```
Browser → PATCH /api/v1/auth/users/{userId}/reset-password {newPassword}
  → Gateway (valida JWT, verifica ruolo ADMIN/OWNER)
  → auth-service
      ├── @PreAuthorize: ADMIN / OWNER
      ├── verifica che userId appartenga allo stesso hotel (multi-tenant)
      ├── encode nuova password con Argon2id
      ├── imposta mustChangePassword = true
      ├── incrementa tokenVersion → invalida tutti i token attivi in Redis
      └── risponde 204 No Content

Al prossimo login: auth-service rileva mustChangePassword=true
  → risponde con flag nel body → frontend reindirizza a /profile
  → utente deve impostare una nuova password personale
```

---

## Gestione Menu F&B per Hotel

**Attori:** ADMIN, OWNER  
**Endpoint:** `POST/PUT/DELETE /api/v1/fb/menu-items`

```
Flusso creazione:
Browser → POST /api/v1/fb/menu-items {name, category, price, available}
  → Gateway (inietta X-Auth-Hotel con hotelId)
  → fb-service
      ├── @PreAuthorize: ADMIN / OWNER
      ├── associa la voce all'hotel (hotel_id = hotelId dal header)
      └── risponde 201 con MenuItemResponse

Flusso eliminazione:
Browser → DELETE /api/v1/fb/menu-items/{id}
  → fb-service
      ├── verifica che non esistano ordini in stato PENDING che referenziano la voce
      │     se sì: risponde 409 Conflict
      └── se nessun ordine PENDING: elimina e risponde 204

Flusso modifica:
Browser → PUT /api/v1/fb/menu-items/{id} {name, category, price, available, description}
  → fb-service → aggiorna voce (stesso hotel_id) → risponde 200 MenuItemResponse
```

---

## Gestione degli errori

| HTTP | Significato | Comportamento frontend |
|------|------------|----------------------|
| 400 | Validazione fallita | Toast errore con dettaglio campo |
| 401 | JWT scaduto | Interceptor prova refresh; se fallisce → redirect login |
| 403 | Ruolo insufficiente | Pagina "Access Denied" |
| 404 | Risorsa non trovata | Toast errore |
| 409 | Conflitto (es. overbooking) | Toast con messaggio specifico |
| 422 | Business rule violata (es. checkout con fattura aperta) | Toast con motivazione |
| 451 | Legal hold (ospite con soggiorni attivi) | Toast con spiegazione GDPR |
| 500 | Errore interno | Toast generico "Si è verificato un errore" |

Tutti gli errori seguono il formato RFC 7807 (`application/problem+json`):

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Room 101 is already occupied during 2026-05-01 / 2026-05-05"
}
```
