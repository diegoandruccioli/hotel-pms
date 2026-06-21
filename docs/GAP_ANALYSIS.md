# Gap Analysis — Hotel PMS
# Analisi completa dello stato del progetto

**Data audit:** 2026-05-06  
**Branch analizzato:** `feature/frontend-development`  
**Scopo:** Tracciare tutti i gap, bug e imperfezioni da correggere prima del pilot

> Aggiornare lo stato di ogni voce man mano che viene risolta.  
> Convenzione stato: ⬜ Da fare · 🔄 In corso · ✅ Risolto

---

## BLOCCO 1 — Gap funzionali (cose che non funzionano come sembrano)

### 🔴 B1-1 — WalkInCheckInForm manda `guests: []` hardcoded ⬜

**Severità:** CRITICA — bloccante per la compliance PS  
**File:** `frontend/src/pages/WalkInCheckInForm.tsx`, riga ~112  
**Problema:**  
Il form walk-in raccoglie camera, ospite e data checkout ma invia il payload con `guests: []` fisso. Ogni check-in walk-in crea uno stay **senza nessun dato ospite** (niente nome, cognome, documento, cittadinanza, tipoAlloggiato). Il file `.txt` Alloggiati per quel giorno non ha nessuna riga corrispondente a quell'ospite.  
Il `CheckInForm` (da prenotazione) raccoglie tutto correttamente. La WalkIn è una versione fast-path mai completata con i campi guest.  
**Fix richiesto:** Aggiungere gli stessi campi Alloggiati presenti in `CheckInForm` anche in `WalkInCheckInForm`, inclusi lookup stato/comune/tipdoc.  
**Impatto se non risolto:** Ospiti walk-in invisibili al portale PS. Illegale per un hotel reale.

---

### 🔴 B1-2 — HotelProfile non mostra il toggle `alloggiatiAutoSend` ⬜

**Severità:** ALTA  
**File:** `frontend/src/pages/HotelProfile.tsx`  
**Problema:**  
Il campo `alloggiatiAutoSend` è nello stato React (riga 49) e viene incluso nel payload al salvataggio, ma **non esiste nessun elemento UI** che lo esponga all'utente. Il toggle era in `SettingsModal` nella versione precedente. Ora che la funzione è migrata su HotelProfile, il rendering è stato dimenticato.  
**Fix richiesto:** Aggiungere un toggle/checkbox per `alloggiatiAutoSend` nel form, con label i18n. Aggiungere la chiave mancante in `admin.json` EN e IT.  
**Impatto se non risolto:** L'albergatore non può attivare/disattivare l'invio automatico al portale PS. Non modificabile senza chiamata API diretta.

---

### 🟡 B1-3 — Inventory-service: isolamento multi-tenant rotto per le camere ⬜

**Severità:** ALTA (architetturale)  
**File 1:** `inventory-service/.../domain/Room.java`  
**Problema 1:** `Room.hotelId` ha `@Column(name = "hotel_id")` **senza `nullable = false`**. Viola la regola obbligatoria di CLAUDE.md.  
**File 2:** `inventory-service/.../repository/RoomRepository.java`  
**Problema 2:** Manca il metodo `findByIdAndHotelId(UUID id, UUID hotelId)`. I metodi del service cercano le camere solo per UUID senza scope hotel. Un admin di un hotel potrebbe ottenere o modificare camere di un altro hotel.  
**Fix richiesto:**
1. Aggiungere `nullable = false` su `Room.hotelId`
2. Aggiungere `findByIdAndHotelId` in `RoomRepository`
3. Aggiornare `RoomServiceImpl` per usare il metodo con scope hotel in tutti i lookup per ID  
**Impatto se non risolto:** Bug di isolamento multi-tenant. Non critico per pilot a singolo hotel, ma sbagliato per design.

---

### 🟡 B1-4 — Email guest globalmente unica invece che per-hotel ⬜

**Severità:** MEDIA  
**File:** `guest-service/.../domain/Guest.java`  
**Problema:** La colonna `email` è `UNIQUE` a livello globale. In uno scenario multi-hotel, lo stesso ospite non può essere registrato in hotel diversi con la stessa email. Dovrebbe essere `UNIQUE(email, hotel_id)`.  
**Fix richiesto:** Flyway migration che rimuove il UNIQUE globale sull'email e aggiunge un UNIQUE constraint composito `(email, hotel_id)`. Aggiornare l'entità JPA di conseguenza.  
**Nota:** Richiede attenzione se ci sono dati esistenti con email duplicate tra hotel diversi.

---

### 🟢 B1-5 — 2 stringhe hardcoded in WalkInCheckInForm ⬜

**Severità:** BASSA (viola regola i18n)  
**File:** `frontend/src/pages/WalkInCheckInForm.tsx`  
- Riga ~141: `"Loading rooms…"` — non passa per i18n  
- Riga ~193: `"Cancel"` — non passa per i18n  
**Fix richiesto:** Sostituire con `t('loading_rooms')` e `t('cancel')` usando le chiavi già esistenti in `common.json` o aggiungendole a `stays.json`.

---

## BLOCCO 2 — Test coverage

### 🔴 B2-1 — guest-service: zero test sul branch corrente ⬜

**Severità:** ALTA (requisito 95% non rispettato)  
**Nota:** I test GDPR (GuestPrivacySettingsServiceImplTest, GuestServiceImplDeleteGuardTest, GuestRetentionJobServiceImplTest) esistono su `feature/secure-coding-hardening` ma **non su questo branch**.  
**Fix richiesto:** Creare o portare su questo branch:
- `GuestServiceImplTest` — CRUD, multi-tenant isolation, soft delete
- `GuestControllerTest` — tutti gli endpoint
- Test per GDPR guard (451) e batch fetch
- Copertura ≥ 95%

---

### 🔴 B2-2 — inventory-service: zero test ⬜

**Severità:** ALTA  
**Fix richiesto:**
- `RoomServiceImplTest` — CRUD, multi-tenant, status changes
- `RoomTypeServiceImplTest` — CRUD
- `RoomControllerTest` — tutti gli endpoint
- Copertura ≥ 95%

---

### 🔴 B2-3 — reservation-service: zero test ⬜

**Severità:** ALTA  
**Fix richiesto:**
- `ReservationServiceImplTest` — CRUD, overlap check, stato transitions
- `ReservationControllerTest` — tutti gli endpoint incluso `updateStatusAndGuests`
- Copertura ≥ 95%

---

### 🟡 B2-4 — auth-service: test parziali ⬜

**Severità:** MEDIA  
**Test esistenti:** AuthServiceImplTest, RefreshTokenServiceImplTest  
**Mancanti:**
- `UserManagementServiceImplTest` — creazione utente, attivazione, disattivazione, multi-tenant
- `AuthControllerTest` / `UserManagementControllerTest` — endpoint-level con MockMvc
- Test per `mustChangePassword` enforcement end-to-end

---

### 🟡 B2-5 — billing-service: copertura da verificare ⬜

**Severità:** MEDIA  
**Fix richiesto:** Verificare con `./gradlew :billing-service:test jacocoTestReport`. Se sotto 95%, aggiungere test per:
- `InvoiceServiceImplTest` — addCharge, totalAmount update
- `PaymentServiceImplTest` — processPayment, overpayment guard
- `PaymentControllerTest`

---

### 🟡 B2-6 — fb-service: copertura parziale ⬜

**Severità:** MEDIA  
**Test esistenti:** MenuItemServiceImplTest (2 test)  
**Mancanti:**
- `RestaurantOrderServiceImplTest` — createOrder, confirmOrder, billing integration
- `RestaurantOrderControllerTest`

---

### 🟡 B2-7 — Frontend: 12 componenti di pagina senza test ⬜

**Severità:** MEDIA-ALTA (requisito 95%)  

| File | Priorità |
|---|---|
| `AdminUsers.tsx` | Alta — flusso critico |
| `HotelProfile.tsx` | Alta |
| `CheckInForm.tsx` | Alta — flusso core |
| `WalkInCheckInForm.tsx` | Alta |
| `Billing/PaymentModal.tsx` | Media |
| `Billing/InvoiceDetailModal.tsx` | Media |
| `Reservations/GuestSearchAndCreate.tsx` | Media |
| `Rooms/RoomFormModal.tsx` | Media |
| `Rooms/RoomTypeFormModal.tsx` | Media |
| `PlanningBoard.tsx` | Media |
| `Rooms/RoomList.tsx` | Bassa |
| `Rooms/RoomTypeList.tsx` | Bassa |

**Fix richiesto:** File `.test.tsx` per ognuno, con `vitest-axe` su ciascuno, copertura ≥ 95%.

---

## BLOCCO 3 — Validazione input (backend)

### 🟡 B3-1 — 3 endpoint senza `@Valid` ⬜

**Severità:** MEDIA

| Endpoint | Servizio | File | Fix |
|---|---|---|---|
| `PATCH /api/v1/reservations/{id}/status-and-guests` | reservation-service | ReservationController | Aggiungere `@Valid` su `ReservationStatusUpdateRequest` |
| `POST /api/v1/guests/batch` | guest-service | GuestController | Aggiungere `@Valid @NotEmpty` su `List<UUID>` |
| `PATCH /api/v1/rooms/{id}/status` | inventory-service | RoomController | Wrappare `RoomStatus` in un DTO validato con `@NotNull` |

---

## BLOCCO 4 — Sicurezza e infrastruttura

### 🟢 B4-1 — Swagger esposto senza autenticazione ⬜

**Severità:** BASSA (rischio produzione)  
**File:** `config-service/src/main/resources/config/api-gateway.yml`  
**Problema:** I path `/swagger-ui.html` e `/*/v3/api-docs` passano per il gateway senza `AuthenticationFilter`. In produzione espongono la documentazione completa di tutti gli endpoint.  
**Fix richiesto:** In profilo produzione, aggiungere `AuthenticationFilter` alla route Swagger, oppure disabilitare Swagger con `springdoc.swagger-ui.enabled=false` nel profilo `prod`.

---

### 🟢 B4-2 — `PATCH /api/v1/rooms/{id}/status` senza `@PreAuthorize` esplicito ⬜

**Severità:** BASSA  
**File:** `inventory-service/.../controller/RoomController.java`  
**Problema:** L'endpoint di cambio stato camera non ha annotazione di ruolo esplicita. È raggiungibile da qualsiasi ruolo autenticato. Il RECEPTIONIST che marca la camera come pulita è probabilmente il comportamento voluto, ma va dichiarato esplicitamente.  
**Fix richiesto:** Aggiungere `@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'RECEPTIONIST')")` per documentare l'intento.

---

### 🟢 B4-3 — No barrel export per `src/components/m3/` ⬜

**Severità:** BASSA (quality-of-life)  
**File:** `frontend/src/components/m3/`  
**Problema:** Manca un `index.ts` con i re-export di tutti i componenti M3. Gli import sono tutti individuali.  
**Fix richiesto:** Creare `frontend/src/components/m3/index.ts` con re-export di tutti i componenti.

---

### 🟢 B4-4 — Virtual scrolling non implementato ⬜

**Severità:** BASSA (performance)  
**File:** `Stays.tsx`, `Guests.tsx`, `Reservations.tsx`  
**Problema:** Le tabelle usano `M3Table` standard senza virtual scrolling. Con dataset > 100 righe, le performance potrebbero degradare nel browser.  
**Fix richiesto:** Valutare l'aggiunta di `@tanstack/react-virtual` o simile per le pagine con liste potenzialmente lunghe. CLAUDE.md richiede virtual scrolling per liste > 50 elementi.

---

## Riepilogo priorità

| # | Gap | Severità | Sforzo stimato | Stato |
|---|---|---|---|---|
| B1-1 | WalkInCheckInForm: campi Alloggiati mancanti | 🔴 CRITICA | 1–2 giorni | ✅ |
| B1-2 | HotelProfile: toggle alloggiatiAutoSend mancante | 🔴 ALTA | 30 min | ✅ |
| B2-1 | guest-service: zero test | 🔴 ALTA | 1–2 giorni | ✅ |
| B2-2 | inventory-service: zero test | 🔴 ALTA | 1 giorno | ✅ |
| B2-3 | reservation-service: zero test | 🔴 ALTA | 1 giorno | ✅ |
| B1-3 | inventory-service: Room multi-tenant isolation | 🟡 ALTA | 1–2 ore | ✅ |
| B3-1 | 3 endpoint senza @Valid | 🟡 MEDIA | 30 min | ✅ |
| B2-4 | auth-service: test parziali | 🟡 MEDIA | 4 ore | ✅ |
| B2-5 | billing-service: coverage da verificare | 🟡 MEDIA | 2–4 ore | ✅ |
| B2-6 | fb-service: test parziali | 🟡 MEDIA | 2 ore | ✅ |
| B2-7 | Frontend: 12 componenti senza test | 🟡 MEDIA | 3–4 giorni | ✅ |
| B1-4 | Email guest: uniqueness per-hotel | 🟡 MEDIA | 1 ora | ✅ |
| B1-5 | 2 stringhe hardcoded WalkInCheckInForm | 🟢 BASSA | 5 min | ✅ |
| B4-1 | Swagger esposto | 🟢 BASSA | 15 min | ✅ |
| B4-2 | RoomController: @PreAuthorize esplicito | 🟢 BASSA | 5 min | ✅ |
| B4-3 | M3 barrel export mancante | 🟢 BASSA | 10 min | ✅ |
| B4-4 | Virtual scrolling tabelle | 🟢 BASSA | 2–4 ore | N/A — server-side pagination già presente |
| N1 | auth-service: AuthController senza test | 🟡 MEDIA | 2–3 ore | ✅ |
| N2 | stay-service: StayController senza test | 🟡 MEDIA | 2–3 ore | ✅ |
| N3 | stay-service: HotelSettingsController senza test | 🟢 BASSA | 30 min | ✅ |
| N4 | billing-service: OwnerReportController senza test | 🟢 BASSA | 15 min | ✅ |
| N5 | fb-service: MenuItemController senza test | 🟢 BASSA | 15 min | ✅ |

---

## Cosa funziona correttamente (verificato)

- ✅ Tutti i 17 route frontend registrati e role-protected
- ✅ i18n 100% consistente EN/IT (14 file JSON, zero chiavi mancanti o asimmetriche)
- ✅ Zero `any` types nei servizi e nei tipi TypeScript
- ✅ `React.lazy()` + `Suspense` su tutti i page-level component
- ✅ `useCallback` su tutti gli event handler (nessuna inline function come prop)
- ✅ Focus trap + Escape in tutti i modal (M3Dialog, GuestFormModal, RoomFormModal)
- ✅ Skip-to-main link presente nei layout
- ✅ Tutti i form con `<label htmlFor>` associata a ogni input
- ✅ JWT in httpOnly cookie, mai in localStorage
- ✅ Interceptor Axios: refresh token silenzioso + protezione race condition con queue
- ✅ CSRF double-submit cookie pattern implementato in `api.ts`
- ✅ InternalAuthFilter con HMAC su tutti e 7 i microservizi
- ✅ MDC correlationId propagato da tutti i filtri interni (tutti e 7 i servizi)
- ✅ CorrelationIdFilter a HIGHEST_PRECEDENCE nel gateway
- ✅ HMAC startup check con differenziazione prod/dev
- ✅ Route `auth-service-users` PRIMA di `auth-service` (first-match ordering corretto)
- ✅ JWT 15 min + refresh 7gg con rotation e Redis blacklist
- ✅ `mustChangePassword` enforced: impostato alla creazione, cancellato al cambio password
- ✅ Docker-compose: healthcheck, resource limits, secrets via env su tutti i 9 servizi
- ✅ `hotel_id` presente su tutte le tabelle rilevanti (eccetto Room.nullable — vedi B1-3)
- ✅ CircuitBreaker con fallback su tutti i Feign client
- ✅ GlobalExceptionHandler RFC 7807 in tutti i servizi
- ✅ Flyway V1-V8 in stay-service, nessun gap di versione
- ✅ File .txt Alloggiati: FAMILIARE/MEMBRO_GRUPPO con campi documento blank corretti
- ✅ `validateWalkInAndGetCheckOutDate` funziona senza `reservationId`
- ✅ `addCharge` aggiorna atomicamente `invoice.totalAmount`
- ✅ `processPayment` valida stato invoice e previene overpayment
- ✅ `@PreAuthorize` su tutti gli endpoint write di inventory-service
- ✅ `CheckInForm` con pre-fill da soggiorno precedente e tutti i campi Alloggiati completi
- ✅ Badge `alloggiatiSent` visibile in `Stays.tsx`
- ✅ `AdminUsers`: create, activate, deactivate flows completi
- ✅ Config service: nessun segreto hardcoded nei YAML versionati
- ✅ stay-service: copertura test eccellente (StayServiceImplTest, AlloggiatiReportServiceImplTest, AlloggiatiCsvParserTest, AlloggiatiLookupServiceImplTest, etc.)
