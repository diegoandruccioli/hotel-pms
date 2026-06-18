# THREAT MODEL — Hotel PMS

**Metodologia**: STRIDE (Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege)  
**Branch di riferimento**: `feature/secure-coding-hardening`  
**Data ultima revisione**: 2026-04-15  
**Autore**: Diego Andruccioli

---

## 1. Scope & Assets da Proteggere

| Asset | Classificazione | Servizio |
|-------|----------------|---------|
| Credenziali utente (password hash) | CRITICO | auth-service |
| Token JWT (access + refresh) | CRITICO | auth-service, api-gateway |
| Segreti di configurazione (HMAC key, JWT secret) | CRITICO | config-service |
| Dati ospiti (PII: nome, documento, nazionalità) | ALTO | guest-service |
| Dati di prenotazione | ALTO | reservation-service |
| Report Alloggiati (dati PS) | ALTO | stay-service |
| Dati di fatturazione | ALTO | billing-service |
| Ordini F&B collegati a camera | MEDIO | fb-service |
| Disponibilità camere | MEDIO | inventory-service |

---

## 2. Architettura & Data Flow

```
[Browser/Client]
      │  HTTPS (cookie httpOnly)
      ▼
[API Gateway :8080]  ← JWT validation, rate limiting (Redis), CORS, headers HTTP
      │  X-Auth-User / X-Auth-Role / X-Internal-Signature
      ├──► [auth-service :8087]        ← login, register, refresh token
      ├──► [guest-service :8083]       ← CRUD ospiti
      ├──► [inventory-service :8081]   ← disponibilità camere
      ├──► [reservation-service :8082] ← prenotazioni
      ├──► [stay-service :8084]        ← check-in/out, alloggiati
      ├──► [billing-service :8085]     ← fatture, pagamenti
      └──► [fb-service :8086]          ← POS F&B
             │
             └── (direct, no gateway) ← [config-service :8888]
                                          [PostgreSQL x9]
                                          [Redis :6379]
                                          [Zipkin :9411]
```

---

## 3. Trust Boundaries

| Boundary | Descrizione | Rischio se violata |
|----------|-------------|-------------------|
| Internet → API Gateway | Ingresso pubblico non autenticato | Accesso non autorizzato a tutti i servizi |
| API Gateway → Services | Header interni iniettati dal gateway | Bypass autenticazione se servizi esposti direttamente |
| Services → Config Service | Lettura segreti applicativi | Esposizione JWT secret, HMAC key |
| Service → Service (Feign) | Comunicazione inter-servizio | Falsificazione identità, data tampering |
| Application → Database | Query ORM | SQL Injection se non parametrizzato |

---

## 4. Threat Enumeration per Servizio (STRIDE)

**Legenda stato**: 🔴 APERTO | 🟡 DA ANALIZZARE | 🔄 IN CORSO | ✅ RISOLTO

### 4.1 auth-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-AUTH-01 | Information Disclosure | User Enumeration: messaggi di errore distinti per "utente non trovato" vs "password errata" | ALTO | ALTA | ✅ RISOLTO |
| T-AUTH-02 | Denial of Service | Brute Force: nessun rate limiting sul login a livello di servizio | CRITICO | ALTA | ✅ RISOLTO |
| T-AUTH-03 | Spoofing | Password debole: algoritmo di hashing da verificare (bcrypt vs Argon2) + policy complessità insufficiente | CRITICO | MEDIA | ✅ RISOLTO |
| T-AUTH-04 | Elevation of Privilege | Refresh Token: assenza di meccanismo di revoca (token rotation) | ALTO | MEDIA | ✅ RISOLTO |
| T-AUTH-05 | Repudiation | Assenza di audit log per eventi di autenticazione (login, logout, failed attempts) | MEDIO | ALTA | ✅ RISOLTO |

### 4.2 api-gateway

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-GW-01 | Tampering | Header X-Auth-User/Role iniettabili dal client se non rimossi in ingresso | CRITICO | MEDIA | ✅ RISOLTO |
| T-GW-07 | Tampering | HMAC copre solo username:role — X-Auth-Hotel modificabile senza invalidare la firma (header-tampering sul tenant context) | ALTO | BASSA | ✅ RISOLTO |
| T-GW-02 | Denial of Service | Rate limiting insufficiente o bypassabile per certi endpoint | ALTO | MEDIA | ✅ RISOLTO |
| T-GW-03 | Information Disclosure | Assenza header sicurezza HTTP (CSP, HSTS, X-Frame-Options, X-Content-Type-Options) | MEDIO | ALTA | ✅ RISOLTO |
| T-GW-04 | Spoofing | CORS misconfiguration: origini non ristrette | ALTO | MEDIA | ✅ RISOLTO |
| T-GW-05 | Tampering | CSRF: mancanza protezione esplicita per operazioni mutanti via cookie | ALTO | MEDIA | ✅ RISOLTO |
| T-GW-06 | Elevation of Privilege | X-Auth-Hotel non propagato: UserAccount senza hotel_id, JWT senza claim hotelId, AuthenticationFilter non inietta X-Auth-Hotel → isolamento multi-tenant IDOR non funzionante end-to-end | CRITICO | ALTA | ✅ RISOLTO |
| T-GW-08 | Spoofing | HMAC interno (X-Internal-Signature) firma solo username:role:hotelId, senza timestamp/nonce: un set di header catturato (es. accesso alla rete Docker interna) resta una credenziale valida indefinitamente — nessuna protezione anti-replay | ALTO | BASSA | ✅ RISOLTO |

### 4.3 guest-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-GST-01 | Elevation of Privilege | IDOR: un RECEPTIONIST potrebbe accedere a ospiti di altri hotel via UUID prevedibile | CRITICO | MEDIA | ✅ RISOLTO |
| T-GST-02 | Injection | Input validation: campi testo libero (nome, note) non sanitizzati | ALTO | MEDIA | ✅ RISOLTO |
| T-GST-03 | Information Disclosure | Esposizione PII senza controllo hotel_id (multi-tenant data leak) | CRITICO | ALTA | ✅ RISOLTO |
| T-GST-04 | Tampering | Mass Assignment: DTO potrebbe accettare campi non previsti tramite JSON | MEDIO | MEDIA | ✅ RISOLTO |
| T-GST-05 | Information Disclosure | GDPR Data Retention: PII ospite (nome, data nascita, documento, numero documento) conservata a tempo indeterminato senza retention policy; soft-delete non soddisfa diritto all'oblio (Art. 17 GDPR); nessun campo consenso su entità Guest | ALTO | MEDIA | ✅ RISOLTO |

### 4.4 reservation-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-RES-01 | Tampering | Double Booking (Overbooking): assenza di lock ottimistico sulle date | CRITICO | ALTA | ✅ RISOLTO |
| T-RES-02 | Elevation of Privilege | IDOR: accesso a prenotazioni di altri hotel senza verifica ownership | CRITICO | MEDIA | ✅ RISOLTO |
| T-RES-03 | Injection | Filtri di ricerca date non validati server-side | MEDIO | BASSA | ✅ RISOLTO |

### 4.5 stay-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-STAY-01 | Elevation of Privilege | Check-in senza verifica stato prenotazione (es. CANCELLED) | ALTO | MEDIA | ✅ RISOLTO |
| T-STAY-02 | Repudiation | Assenza di audit trail per operazioni check-in/check-out | ALTO | ALTA | ✅ RISOLTO |
| T-STAY-03 | Information Disclosure | Report Alloggiati: trasmissione dati PS senza verifica firma/TLS | CRITICO | BASSA | ✅ RISOLTO |
| T-STAY-04 | Elevation of Privilege | getAllStays/getStayById/checkOut/getStaysByReservationId senza scoping hotelId: un RECEPTIONIST poteva leggere o modificare (check-out) stay di qualsiasi hotel via lista paginata, UUID indovinato/enumerato, o reservationId cross-hotel | CRITICO | ALTA | ✅ RISOLTO |

### 4.6 billing-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-BILL-01 | Elevation of Privilege | IDOR: accesso a fatture di altri ospiti/hotel | CRITICO | MEDIA | ✅ RISOLTO |
| T-BILL-02 | Tampering | Importi non validati server-side (negative amounts, overflow) | ALTO | BASSA | ✅ RISOLTO |
| T-BILL-03 | Repudiation | Assenza di log immutabile delle transazioni di pagamento | ALTO | ALTA | ✅ RISOLTO |

### 4.7 fb-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-FB-01 | Elevation of Privilege | IDOR: ordini F&B non legati a hotel_id, accessibili cross-hotel | ALTO | MEDIA | ✅ RISOLTO |
| T-FB-02 | Tampering | Prezzi degli ordini non ricalcolati server-side (client-side price tampering) | CRITICO | ALTA | ✅ RISOLTO |

### 4.8 config-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-CFG-01 | Information Disclosure | Endpoint `/actuator` esposti senza autenticazione | CRITICO | ALTA | ✅ RISOLTO |
| T-CFG-02 | Information Disclosure | Segreti in chiaro nei file di configurazione (JWT secret, HMAC key, DB password) | CRITICO | ALTA | ✅ RISOLTO |
| T-CFG-03 | Spoofing | Nessuna autenticazione tra config-service e microservizi consumer | ALTO | MEDIA | ✅ RISOLTO |

### 4.9 Frontend (React)

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-FE-01 | Tampering | XSS: output di dati utente non encoded in rendering dinamico | ALTO | MEDIA | ✅ RISOLTO |
| T-FE-02 | Information Disclosure | Token JWT eventualmente esposto in localStorage o state non protetto | CRITICO | BASSA | ✅ RISOLTO |
| T-FE-03 | Elevation of Privilege | Broken Access Control frontend: route admin accessibili nascondendo solo elementi UI | MEDIO | ALTA | ✅ RISOLTO |
| T-FE-04 | Tampering | Assenza Content-Security-Policy: possibilità di inject di script esterni | ALTO | MEDIA | ✅ RISOLTO |

---

## 5. Risk Matrix

```
IMPATTO
  │
  │  CRITICO  │ T-AUTH-02 │ T-GW-01  │ T-GST-01  │ T-RES-01  │ T-CFG-01  │
  │  ALTO     │ T-AUTH-01 │ T-GW-03  │ T-GST-02  │ T-STAY-01 │ T-BILL-01 │
  │  MEDIO    │ T-AUTH-05 │ T-GW-05  │ T-GST-04  │           │           │
  │  ALTO     │           │          │ T-GST-05  │           │           │
  │  BASSO    │           │          │           │           │           │
  │           ├───────────┴──────────┴───────────┴───────────┴───────────
  │                BASSA        MEDIA          ALTA
  │                               PROBABILITÀ
```

**Priorità interventi**: CRITICO+ALTA > CRITICO+MEDIA > ALTO+ALTA > ...

---

## 6. Mitigations — Tabella Tracciamento

Questa tabella viene aggiornata ad ogni commit di hardening sul branch `feature/secure-coding-hardening`.

| Threat ID | Intervento | Servizio/File | Commit | OWASP | Stato |
|-----------|------------|---------------|--------|-------|-------|
| T-AUTH-01 | Messaggi errore login uniformi (no user enumeration) | auth-service/AuthServiceImpl.java | baseline (main) | A07 | ✅ |
| T-AUTH-02 | Account lockout dopo 5 tentativi falliti, 15 min | auth-service/AuthServiceImpl.java | a90e462 | A07 | ✅ |
| T-AUTH-03 | BCryptPasswordEncoder strength 12 (vs default 10); lazy rehash on login via passwordEncoder.upgradeEncoding(): se l'hash stored ha cost < 12, viene ricalcolato con la password in chiaro al momento del login riuscito e salvato in DB | auth-service/SecurityConfig.java, AuthServiceImpl.java | 4e1f1e9 | A02 | ✅ |
| T-AUTH-04 | Refresh token rotation con blacklist Redis: access token TTL 15 min; refresh token TTL 7 giorni con jti UUID; POST /refresh valida jti, lo blacklista in Redis (rt:blacklist:<jti> con TTL residuo), emette nuovo token pair (rotation); logout blacklista il refresh token; frontend Axios interceptor ritenta la richiesta originale dopo refresh silenzioso | auth-service/JwtService.java, RefreshTokenService.java, RefreshTokenServiceImpl.java, AuthServiceImpl.java, AuthController.java, config/auth-service.yml, frontend/api.ts | 5ce3010 | A07 | ✅ |
| T-AUTH-04 residuo | Cambio password non revocava le sessioni attive: i refresh token emessi prima del cambio password restavano validi per 7 giorni. Fix: (1) Flyway V4 aggiunge token_version INT DEFAULT 0 a user_account; (2) claim tv embedded in ogni JWT via JwtService; (3) POST /api/v1/auth/change-password verifica current password (second factor), incrementa tokenVersion in DB, sovrascrive Redis key user:tv:<username> con nuovo valore; (4) AuthServiceImpl.refresh() confronta tv del JWT con Redis: se storedTv>=0 e jwtTv!=storedTv → 401. Login/register rinfrescano la Redis key. Migrazione graceful: storedTv=-1 (key assente) salta il check. 6 nuovi test. | auth-service/V4__add_token_version.sql, UserAccount.java, JwtService.java, RefreshTokenService.java, RefreshTokenServiceImpl.java, AuthServiceImpl.java, AuthController.java, ChangePasswordRequest.java | b473265 | A07 | ✅ |
| T-AUTH-05 | Structured SLF4J audit log (@Slf4j) in AuthServiceImpl e AuthController: REGISTER_SUCCESS, LOGIN_SUCCESS, LOGIN_FAILED (USER_NOT_FOUND / BAD_PASSWORD + counter), ACCOUNT_LOCKED (con expiry), LOGIN_BLOCKED, LOGOUT (username da JWT cookie) | auth-service/AuthServiceImpl.java, AuthController.java | ad441e9 | A09 | ✅ |
| T-GW-01 | Verifica HMAC-SHA256 su X-Internal-Signature (InternalAuthFilter) | api-gateway + tutti i servizi | baseline (main) | A01 | ✅ |
| T-GW-07 | Messaggio HMAC esteso da "username:role" a "username:role:hotelId": AuthenticationFilter.computeHmac aggiornato; tutti e 6 gli InternalAuthFilter aggiornati in isSignatureValid e computeHmac; nuovo test shouldProduceDifferentSignatureForDifferentHotelId verifica che la firma cambia al variare del tenant | api-gateway/AuthenticationFilter.java, guest/billing/reservation/fb/stay/inventory-service/InternalAuthFilter.java, AuthenticationFilterTest.java | 5bdc594 | A01 | ✅ |
| T-GW-08 | Messaggio HMAC esteso da "username:role:hotelId" a "username:role:hotelId:timestamp:nonce": AuthenticationFilter genera timestamp (epoch millis) + nonce (UUID) random ad ogni richiesta, iniettati come X-Auth-Timestamp/X-Auth-Nonce. Tutti e 5 gli InternalAuthFilter (auth/billing/fb/frontdesk/guest) validano: (1) finestra di tolleranza 60s sul timestamp, (2) nonce non già reclamato tramite nuovo NonceStore backed by Redis (SETNX via StringRedisTemplate.opsForValue().setIfAbsent, TTL 120s). Redis esteso a backend-network (oltre a gateway-network) così tutti i validator lo raggiungono; auth-service riusa la dipendenza/config Redis già presente per i refresh token, gli altri 4 servizi l'hanno aggiunta su namespace separato (internal-auth:nonce:*). SpotBugs EI_EXPOSE_REP2 su RedisNonceStore sopresso (bean Spring singleton, stesso pattern già usato per AlloggiatiWebSenderServiceImpl). Nuovi test: 4 casi anti-replay in InternalAuthFilterTest (fb-service) — timestamp stale, timestamp futuro, replay esatto rifiutato, due nonce diversi sullo stesso payload entrambi accettati; AuthenticationFilterTest verifica generazione header e unicità nonce; StayControllerSecurityTest aggiornato al nuovo payload a 5 campi. **Fix 2026-06-18 (review post-merge)**: il rollout iniziale (commit 5dd8ed8) aggiornava solo l'AuthenticationFilter del gateway e i 5 InternalAuthFilter, ma non FeignHeaderConfig — l'interceptor che ogni servizio usa per firmare le proprie chiamate Feign in uscita verso altri servizi (frontdesk→billing/guest, fb→billing/frontdesk, guest→billing/frontdesk, billing→guest/frontdesk). Senza timestamp/nonce nel payload firmato, ogni chiamata interservizio sarebbe stata rifiutata con 401 dal validator ricevente — regressione funzionale, non solo di sicurezza. Trovato da una review di sicurezza mirata post-merge (subagent indipendente) prima del merge su main. Corretto in tutti e 4 i FeignHeaderConfig (frontdesk/fb/guest/billing); scoperto anche un bug pre-esistente e separato in billing-service, che non inviava mai X-Internal-Signature. Aggiunti FeignHeaderConfigTest per tutti e 4 i servizi (nessuno esisteva prima) | api-gateway/AuthenticationFilter.java, auth/billing/fb/frontdesk/guest-service/security/InternalAuthFilter.java + NonceStore.java + RedisNonceStore.java + SecurityConfig.java, billing/fb/frontdesk/guest-service/config/FeignHeaderConfig.java, docker-compose.yml, config-service/config/{billing,fb,frontdesk,guest}-service.yml | 5dd8ed8, b80a303 | A01 | ✅ |
| T-FE-02 | Token JWT in httpOnly cookie (Secure + SameSite=Strict) | auth-service/AuthController.java | baseline (main) | A02 | ✅ |
| T-GW-03 | SecurityHeadersFilter GlobalFilter: HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Permissions-Policy, CSP default-src 'none' su tutte le risposte del gateway | api-gateway/SecurityHeadersFilter.java | b901a9f | A05 | ✅ |
| T-GW-04 | allowedHeaders wildcard rimosso: whitelist esplicita [Content-Type, X-CSRF-Token]; maxAge: 3600; allowedOrigins già vincolato a ${GW_CORS_ALLOWED_ORIGINS:http://localhost:5173} — mai wildcard; in produzione nginx proxies /api/ same-origin, CORS non necessario | config-service/config/api-gateway.yml | 73e2788 | A05 | ✅ |
| T-GW-05 | Double Submit Cookie: CsrfFilter GlobalFilter nell'api-gateway valida che X-CSRF-Token header == csrf_token cookie su ogni richiesta mutante (POST/PUT/PATCH/DELETE); esclusi /auth/login e /auth/register (pre-auth); auth-service emette csrf_token cookie non-httpOnly (Secure, SameSite=Strict) su login/register/refresh e la cancella al logout; frontend Axios interceptor legge il cookie e inietta l'header; 10 test in CsrfFilterTest | api-gateway/CsrfFilter.java, auth-service/AuthController.java, frontend/api.ts | e146b17 | A01 | ✅ |
| T-GW-06 | Flyway V3 aggiunge hotel_id UUID NOT NULL a user_account; UserAccount entity + RegisterRequest + JwtService.generateToken/Refresh(username,role,hotelId) + CLAIM_HOTEL_ID constant; AuthenticationFilter estrae hotelId dal JWT e inietta X-Auth-Hotel (401 se claim assente); stay-service e inventory-service InternalAuthFilter allineati con HEADER_HOTEL + null-check + auth.setDetails(hotelId); AuthServiceImplTest + AuthenticationFilterTest aggiornati | auth-service/V3, UserAccount.java, JwtService.java, AuthServiceImpl.java; api-gateway/AuthenticationFilter.java; stay-service/InternalAuthFilter.java; inventory-service/InternalAuthFilter.java | b469e21 | A01 | ✅ |
| T-GST-01 | IDOR fix: resolveGuest(id, hotelId) → findByIdAndHotelId, 404 anche per guest di altri hotel (no enumeration) | guest-service/GuestServiceImpl.java, GuestRepository.java | b483eac | A01 | ✅ |
| T-GST-03 | hotel_id su tabella guests (Flyway V2) + tutte le query repository scoped per hotelId; X-Auth-Hotel letto da InternalAuthFilter e propagato via FeignHeaderConfig | guest-service/V2__add_hotel_id_to_guests.sql, GuestRepository.java, InternalAuthFilter.java, FeignHeaderConfig.java | b483eac | A01 | ✅ |
| T-GST-02 | @Pattern su firstName/lastName (NAME_PATTERN), phone (PHONE_PATTERN), address (TEXT_SAFE_PATTERN), city/country (LOCATION_PATTERN); @Past su dateOfBirth; @Pattern(DOCUMENT_NUMBER_PATTERN) + @Past/@FutureOrPresent su IdentityDocumentRequestDTO | guest-service/GuestRequest.java, IdentityDocumentRequestDTO.java, ValidationConstants.java | fb6fdff | A03 | ✅ |
| T-GST-04 | GuestMapper.toEntity() con @Mapping(ignore=true) espliciti su id, hotelId, identityDocuments, active, createdAt, updatedAt — protezione mass-assignment dichiarativa | guest-service/GuestMapper.java | fb6fdff | A04 | ✅ |
| T-GST-05 | Flyway V4 (gdpr_consent_date su guests, backfill da created_at) + V5 (guest_privacy_settings, min 5 anni TULPS validato con @Min). deleteGuest() sostituito con hard-anonymize + guardia legale duale: TULPS (frontdesk-service GET /stays/guest/{id}/last-date, @NonNull con circuit-breaker fail-closed) e fiscale (billing-service GET /invoices/guest/{id}/last-invoice-date); HTTP 451 + GdprLegalHoldException con unlocksAt e legalBasis. Guardia aggiuntiva su prenotazioni attive: frontdesk-service GET /reservations/guest/{id}/active. GuestPrivacySettingsController (GET/PUT /api/v1/guests/settings). GuestRetentionJobServiceImpl (@Scheduled 02:00, pre-filtro conservativo FISCAL_MIN=10y, fail-closed). FeignHeaderConfig fix HMAC (era mancante). 17 test boundary-value. **Fix 2026-06-18 (ADR-001 frontdesk consolidation):** trovati 2 bug di wiring mai funzionanti — path StayServiceClient errato (`last-stay-date` invece di `last-date`, sempre 404) ed endpoint `/reservations/guest/{id}/active` mai implementato (sempre 404) — la guardia falliva sempre "closed" (bloccava sempre la cancellazione) indipendentemente dallo stato reale. Corretti: path client allineato al controller reale; nuovo endpoint implementato (ReservationRepository.existsByGuestIdAndHotelIdAndStatusNotIn + ReservationServiceImpl.hasActiveReservations + ReservationController). | guest-service, frontdesk-service (ex stay-service + reservation-service), billing-service | 56109eb, 066f611 | A04 | ✅ |
| T-RES-01 | @Version optimistic lock + @Lock(PESSIMISTIC_WRITE) su overlap query + ConflictException HTTP 409 + Flyway V4 version column | reservation-service/Reservation.java, ReservationRepository.java, GlobalExceptionHandler.java, V4__add_version_to_reservations.sql | ca9bf92 | A04 | ✅ |
| T-RES-02 | hotel_id scope su tutte le query reservation (findByIdAndHotelId, findAllByHotelId); InternalAuthFilter propaga X-Auth-Hotel in auth.details; createReservation imposta hotelId dall'auth context | reservation-service/ReservationServiceImpl.java, ReservationRepository.java, InternalAuthFilter.java, FeignHeaderConfig.java | 3e93f49 | A01 | ✅ |
| T-STAY-01 | Set.of("CONFIRMED","PARTIALLY_CHECKED_IN") come allowlist; checkIn() lancia IllegalStateException("INVALID_RESERVATION_STATUS") se stato non ammesso; log WARN [STAY] CHECK_IN_FAILED reason=INVALID_RESERVATION_STATUS; 4 test: shouldRejectCheckInWhenReservationIsCancelled/IsCheckedOut/IsNoShow + shouldAllowCheckInWhenPartiallyCheckedIn | stay-service/StayServiceImpl.java | 250edd0 | A04 | ✅ |
| T-STAY-02 | Structured SLF4J audit log in StayServiceImpl: CHECK_IN_SUCCESS (stayId+reservationId+guestId+roomId), CHECK_IN_FAILED (reason=EXTERNAL_SERVICE_UNAVAILABLE), CHECK_OUT_SUCCESS (stayId+reservationId+roomId), CHECK_OUT_FAILED (reason=INVALID_STATUS e BILLING_NOT_PAID); prefisso [STAY], WARN per fallimenti, INFO per successi | stay-service/StayServiceImpl.java | f2cb417 | A09 | ✅ |
| T-STAY-04 | Gap preesistente (non causato dal consolidamento ADR-001), trovato da review di sicurezza post-merge su PR #17. StayRepository: nuovi findByIdAndHotelId, findByHotelId(hotelId, Pageable), findAllByReservationIdAndHotelId. getAllStays/getStayById/checkOut/getStaysByReservationId ora richiedono hotelId (da StayController.extractHotelId(), stesso meccanismo già usato da checkIn) e ritornano 404 (no enumeration, pattern T-GST-01) per stay/reservation di altri hotel. findAllByReservationId interno (usato solo dalla saga updateReservationGuests con reservationId già fidato) lasciato invariato. 2 nuovi test (getAllStays/getStaysByReservationId non avevano copertura) + test esistenti aggiornati | frontdesk-service/stays/{repository/StayRepository.java, service/StayService.java, service/impl/StayServiceImpl.java, controller/StayController.java} | 92b8b49 | A01 | ✅ |
| T-BILL-01 | hotel_id scope su tutte le query invoice/payment (findByIdAndHotelId, findByHotelId con Pageable, findFirstByReservationIdAndHotelIdOrderByIssueDateDesc); InternalAuthFilter legge X-Auth-Hotel e lo memorizza in auth.details; createInvoice imposta hotelId dall'auth context (ignora valore client); FeignHeaderConfig propaga X-Auth-Hotel | billing-service/InvoiceRepository.java, InvoiceServiceImpl.java, PaymentServiceImpl.java, InternalAuthFilter.java, FeignHeaderConfig.java | 4a44eea | A01 | ✅ |
| T-FB-02 | Catalogo MenuItem server-side (V2 Flyway); OrderItemRequest accetta menuItemId+quantity, no unitPrice; service risolve prezzi da DB con buildItemsFromCatalog(); totalAmount ricalcolato server-side | fb-service/MenuItem.java, MenuItemRepository.java, V2__add_menu_items.sql, OrderItemRequest.java, RestaurantOrderServiceImpl.java | d7af61c | A04 | ✅ |
| T-CFG-01 | Porta management separata (:8090, non pubblicata) + show-details: when-authorized su tutti i servizi | config-service/resources/config/*.yml, docker-compose.yml, prometheus.yml | b0cf898 | A05 | ✅ |
| T-CFG-02 | Rimosso fallback JWT_SECRET hardcoded da auth-service.yml; POSTGRES_PASSWORD e JWT_SECRET parametrizzati come variabili d'ambiente nel docker-compose; setup script aggiornato per generare entrambi i segreti. Rimosso anche il fallback `bXktMzIt...` da api-gateway/JwtUtil.java (@Value senza default → fail-fast senza JWT_SECRET); aggiunto jwt.secret: ${JWT_SECRET} ad api-gateway.yml | config-service/config/auth-service.yml, docker-compose.yml, setup-hmac-secret.sh/.ps1, api-gateway/util/JwtUtil.java, config-service/config/api-gateway.yml | 91d9484, 6dc6ea4 | A02 | ✅ |
| T-GW-02 | Rate limiting esteso a tutte le 7 route dell'api-gateway (guest, inventory, reservation, stay, billing, fb, auth): business routes con userKeyResolver (per-user bucket "user:<username>", burst 50, 20 req/s); auth route con remoteAddrKeyResolver proxy-aware (X-Forwarded-For first, fallback RemoteAddress, burst 10, 5 req/s); AuthenticationFilter dichiarato prima di RequestRateLimiter per esporre X-Auth-User al resolver | api-gateway/RateLimiterConfig.java, config-service/config/api-gateway.yml | 25c7b57 | A05 | ✅ |
| T-RES-03 | @ValidDateRange class-level Bean Validation constraint su ReservationRequest: checkOutDate.isAfter(checkInDate) obbligatorio (rifiuta zero-night stay e date invertite); DateRangeValidator implementa ConstraintValidator; guard difesa-in-profondità verifyDateRange() in ReservationServiceImpl.createReservation() e updateReservation() lancia BadRequestException("CHECKOUT_MUST_BE_AFTER_CHECKIN") prima di qualsiasi accesso a servizi esterni o DB; 6 nuovi test (3 service + 3 validator + null-safety) | reservation-service/ReservationRequest.java, ValidDateRange.java, DateRangeValidator.java, ReservationServiceImpl.java | 43be320 | A03 | ✅ |
| T-STAY-03 | AlloggiatiWebConfig: RestTemplate con JVM default SSL context (no TrustAllCerts, no hostname-verifier bypass); AlloggiatiWebSenderServiceImpl: credenziali lette da env vars (ALLOGGIATI_USERNAME, ALLOGGIATI_PASSWORD), mai hardcoded; Basic Auth header RFC 7617; 5 test con MockRestServiceServer (POST URL, Authorization header, Content-Type, 5xx → ExternalServiceException, empty report) | stay-service/AlloggiatiWebConfig.java, AlloggiatiWebSenderService.java, AlloggiatiWebSenderServiceImpl.java, config-service/stay-service.yml | f0be747 | A02 | ✅ |
| T-BILL-02 | @Digits(integer=10, fraction=2) su PaymentRequest.amount e InvoiceRequest.totalAmount; @Size(max=100)+@Pattern su transactionReference; PaymentServiceImpl.addPayment() normalizza l'importo con setScale(2, HALF_UP) prima di ogni aritmetica; payment.setAmount(paymentAmount) assicura valore normalizzato persistito; 2 nuovi test: shouldThrowWhenInvoiceAlreadyPaid + shouldNormalizeAmountToTwoDecimalPlaces | billing-service/PaymentRequest.java, InvoiceRequest.java, PaymentServiceImpl.java | d346fe8 | A04 | ✅ |
| T-FB-01 | hotel_id scope su restaurant_orders (Flyway V3); InternalAuthFilter legge X-Auth-Hotel e lo memorizza in auth.details; createOrder imposta hotelId dall'auth context; getOrdersByStayId usa findByStayIdAndHotelId; getAllOrders usa findAllByHotelId(pageable) — nessun ordine cross-hotel mai restituito; FeignHeaderConfig propaga X-Auth-Hotel; Flyway V3 fix anche CHECK constraint status enum | fb-service/RestaurantOrder.java, RestaurantOrderRepository.java, RestaurantOrderServiceImpl.java, InternalAuthFilter.java, FeignHeaderConfig.java, V3__add_hotel_id_to_restaurant_orders.sql | 18a3768 | A01 | ✅ |
| T-CFG-03 | HTTP Basic Auth su config-service (SecurityConfig + spring.security.user); credenziali CONFIG_SERVER_USERNAME (default configuser) e CONFIG_SERVER_PASSWORD iniettate via env var; tutti gli 8 microservizi consumer aggiungono spring.cloud.config.username/password in application.yml; CONFIG_SERVER_PASSWORD generato da setup-hmac-secret.sh/.ps1 e iniettato nel docker-compose; 2 test di integrazione (401 senza credenziali, 200 con credenziali valide) | config-service/SecurityConfig.java, application.yml, tutti i microservizi/application.yml, docker-compose.yml, setup-hmac-secret.sh/.ps1 | c934f6e | A07 | ✅ |
| T-FE-01 | Audit completo: nessun dangerouslySetInnerHTML trovato; React JSX escapa l'output per default. Enforcement statico: ESLint no-restricted-syntax vieta dangerouslySetInnerHTML, innerHTML e outerHTML diretti (errore di build). Test di regressione: Guests.test.tsx verifica che payload XSS (`<img src=x onerror=alert(1)>`) in dati API sia escaped come testo e non injettato come elemento DOM | frontend/eslint.config.js, frontend/src/pages/Guests.test.tsx | 3655906 | A03 | ✅ |
| T-FE-03 | ProtectedRoute con prop allowedRoles?: Role[]; se fornito, utenti con ruolo non in allowlist → redirect a /. App.tsx: /owner-dashboard annidato in <ProtectedRoute allowedRoles={['OWNER','ADMIN']}> dentro MainLayout. 4 nuovi test: ADMIN/OWNER ammessi, RECEPTIONIST → redirect /, user null → redirect /. 144/144 test verdi, lint zero warnings, tsc clean | frontend/src/components/ProtectedRoute.tsx, frontend/src/App.tsx, frontend/src/components/ProtectedRoute.test.tsx | ccfa431 | A01 | ✅ |
| T-FE-03b | Estensione pattern "role-gated service call": dashboardService.getDashboardStats(isOwnerOrAdmin) — la chiamata a /api/v1/reports/owner (@PreAuthorize OWNER\|ADMIN) era eseguita incondizionatamente per tutti i ruoli, restituendo 403 per RECEPTIONIST/MANAGER e rompendo l'intera dashboard. Fix: parametro isOwnerOrAdmin propagato da dashboardStore.fetchStats() a Dashboard.tsx; pendingRevenue=null se false, chiamata billing omessa. Previene chiamate API non autorizzate a livello di servizio frontend (complementare a ProtectedRoute che opera a livello di route). | frontend/src/services/dashboardService.ts, frontend/src/store/dashboardStore.ts, frontend/src/pages/Dashboard.tsx | 14a43bb | A01 | ✅ |
| T-FE-04 | CSP SPA-level via nginx.conf: script-src 'self', style-src 'self' 'unsafe-inline', connect-src 'self', frame-ancestors 'none'; nginx proxy /api/ → api-gateway per same-origin; api.ts baseURL relativo | frontend/nginx.conf, frontend/Dockerfile, frontend/src/services/api.ts | b901a9f | A05 | ✅ |
| GAP-2 | E2E attack-path coverage: 11 Playwright test in 3 suite. security-auth.spec.ts: T-AUTH-SEC-01 (401 unauthenticated→/login), T-AUTH-SEC-02 (401+refresh fail→performLogout→/login), T-AUTH-SEC-03 (429 brute-force→generic error, no account detail), T-AUTH-SEC-04 (403 CSRF→toast error, modal aperto). security-rbac.spec.ts: T-RBAC-SEC-01 (RECEPTIONIST→/owner-dashboard→redirect /), T-RBAC-SEC-02/03 (ADMIN/OWNER→accesso concesso), T-RBAC-SEC-04 (unauthenticated→/billing→/login). security-idor.spec.ts: T-IDOR-SEC-01 (guest edit cross-hotel 404→toast error), T-IDOR-SEC-02 (URL manipulation reservation 404→error state), T-IDOR-SEC-03 (guest list hotel-scoped). | frontend/e2e/security-auth.spec.ts, security-rbac.spec.ts, security-idor.spec.ts | f2cbc7d | A01, A07 | ✅ |
| GAP-3 | fb-service InternalAuthFilter struttura inconsistente: il presence-check usava due `if` separati (il primo controllava username/role/signature ma non hotelId; il secondo controllava hotelId con LOG.warn dedicato) invece del singolo `if` combinato usato dagli altri 5 servizi. Fix: unificato in un singolo `if` con quattro condizioni (||) identico al pattern canonico; aggiornato Javadoc (tre→quattro header obbligatori); rimosso LOG.warn superfluo per assenza header (il HMAC-mismatch log rimane). Aggiunti 8 test JUnit 5 in InternalAuthFilterTest: 4 presenza (username/role/hotelId/signature mancante→401), 2 HMAC (firma invalida→401, firma per hotelId diverso→401 tenant isolation), 1 valido (200 + SecurityContext popolato con name/details/authority), 1 actuator bypass (shouldNotFilter→200 senza headers). | fb-service/src/main/java/com/hotelpms/fb/security/InternalAuthFilter.java, fb-service/src/test/java/com/hotelpms/fb/security/InternalAuthFilterTest.java | 9c299b1 | A01 | ✅ |
| GAP-4 | Audit log non aggregati (SIEM assente): i log strutturati [AUTH], [STAY], [BILLING] venivano emessi solo su stdout senza possibilità di aggregazione, ricerca e alerting su anomalie. Fix: (1) Loki (grafana/loki:2.9.0) + Grafana (grafana/grafana:10.2.0) aggiunti al docker-compose con network observability-network, volumi persistenti loki_data/grafana_data e healthcheck; (2) loki-logback-appender:1.5.2 aggiunto come dipendenza runtime a auth-service, stay-service, billing-service; (3) logback-spring.xml creato per ciascuno dei tre servizi: ConsoleAppender sempre attivo + Loki4jAppender (HTTP push, label service/level, AsyncAppender neverBlock=true) attivo solo nel profilo Docker (auth-service/stay-service/billing-service) — in test e sviluppo locale solo console; (4) Grafana provisioning automatico: datasource Loki + dashboard "Security Audit Log" con 6 pannelli (LOGIN_FAILED/h, ACCOUNT_LOCKED/h, HMAC Failures/h, trend eventi/min, log raw [AUTH]/[STAY]/[BILLING], tabella eventi recenti). | auth-service/logback-spring.xml, stay-service/logback-spring.xml, billing-service/logback-spring.xml, docker-compose.yml, docker/loki/loki-config.yaml, docker/grafana/provisioning/ | fe99e2b | A09 | ✅ |

| DEP-CVE-01 | Bump Netty 4.1.130→4.1.132.Final (CVE-2026-33870 request smuggling + CVE-2026-33871 HTTP/2 DoS) e Spring Cloud Gateway 4.2.0→4.2.6 (CVE-2025-41235 header forwarding + CVE-2025-41253 EL injection): ext["netty.version"]="4.1.132.Final" + dependencyManagement override gateway-server:4.2.6 in api-gateway/build.gradle.kts | api-gateway/build.gradle.kts | 14d29b9 | A06 | ✅ |
| DEP-CVE-02 | Bump commons-fileupload 1.5→1.6.0 (CVE-2025-48976 DoS via part headers) + commons-io 2.11.0→2.14.0 (CVE-2024-47554 XmlStreamReader DoS) in tutti gli 8 microservizi (auth, billing, config, fb, guest, inventory, reservation, stay). Fix corretto via dependencyManagement { dependencies { dependency(...) } } — la tecnica ext["commons-fileupload.version"] non funzionava perché commons-fileupload NON è gestito dal Spring Boot 3.4.x BOM (rimosso assieme a CommonsMultipartResolver in Spring 6.1). CI SCA (OWASP Dependency Check) rimosso perché instabile (NVD API in manutenzione, run >1h), sostituito dalla copertura Trivy per A06. | */build.gradle.kts | e9b6eb6 | A06 | ✅ |
| DEP-CVE-03 | CVE-2026-22739 — Spring Cloud Config Server 4.2.0: path traversal via profile parameter, consente lettura di file arbitrari dal filesystem. Fix upstream richiede config-server 4.3.2 (Spring Cloud 2025.x) o 5.0.2, incompatibili con Spring Boot 3.4.x/Spring Cloud 2024.0.x. Rischio residuale mitigato da: (1) config-service non esposto all'esterno (non instradato dall'API gateway), (2) accesso limitato alla rete interna Docker, (3) nessun file sensibile presente nel classpath del container oltre ai config YAML. Soppresso in .trivyignore con giustificazione. Migrazione pianificata al prossimo aggiornamento Spring Boot 3.5.x. | .trivyignore, config-service/build.gradle.kts | e9b6eb6 | A06 | ✅ |
| DEP-CVE-04 | Upgrade Spring Boot 3.4.x → 3.5.x e Spring Cloud 2024.0.x → 2025.0.x: risolve CVE-2026-22739 (config-server path traversal, fix upstream finalmente disponibile), CVE Netty serie 4.1.13x (request smuggling / HTTP/2 DoS). CVE-2026-42577 (Netty native epoll) classificato accepted risk: Spring Boot non attiva il trasporto native epoll in configurazione JDK NIO standard — rischio pratico nullo. 108 alert Trivy su immagini di terze parti (Grafana, postgres, Redis) analizzati e triaggiati con motivazione tecnica documentata in .trivyignore. | */build.gradle.kts, .trivyignore, docker-compose.yml | dc49c79 | A06 | ✅ |
| DEP-CVE-05 | Upgrade react-router-dom + react-router 7.13.1 → 7.17.0: risolve 6 CVE — XSS via RSC redirect (#36), XSS via Location header (#37), RCE via turbo-stream (#38), open redirect (#39), DoS via __manifest (#40), DoS via single-fetch (#41). Upgrade esbuild 0.27.3 → 0.28.1 via npm overrides (dipendenza transitiva di Vite): risolve CVE-2025-42577 (arbitrary file read via dev server su Windows). Tutti e 7 i fix applicati tramite npm install senza breaking change; 344/344 test verdi, lint zero warnings. | frontend/package.json, frontend/package-lock.json | b1be3e8 | A06 | ✅ |

**Legenda stato**: ⏳ In attesa | 🔄 In corso | ✅ Completato

---

## 7. Mappatura OWASP Top 10 (2021)

| OWASP | Nome | Threats mappati |
|-------|------|----------------|
| A01 | Broken Access Control | T-GST-01, T-GST-03, T-RES-02, T-BILL-01, T-FB-01, T-FE-03, T-GW-05, T-GW-06, T-GW-07, T-GW-08, T-STAY-04, GAP-3 |
| A02 | Cryptographic Failures | T-AUTH-03, T-CFG-02, T-STAY-03 |
| A03 | Injection | T-GST-02, T-RES-03, T-FE-01 |
| A04 | Insecure Design | T-RES-01, T-STAY-01, T-FB-02, T-BILL-02, T-GST-04, T-GST-05 |
| A05 | Security Misconfiguration | T-GW-02, T-GW-03, T-GW-04, T-CFG-01, T-CFG-03, T-FE-04 |
| A06 | Vulnerable and Outdated Components | DEP-CVE-01, DEP-CVE-02, DEP-CVE-03, DEP-CVE-04, DEP-CVE-05 |
| A07 | Identification & Authentication Failures | T-AUTH-01, T-AUTH-02, T-AUTH-03, T-AUTH-04, T-AUTH-04-residuo, T-FE-02 |
| A09 | Security Logging & Monitoring Failures | T-AUTH-05, T-STAY-02, T-BILL-03, GAP-4 |
