# THREAT MODEL — Hotel PMS

**Metodologia**: STRIDE (Spoofing, Tampering, Repudiation, Information Disclosure, Denial of Service, Elevation of Privilege)  
**Branch di riferimento**: `feature/secure-coding-hardening`  
**Data ultima revisione**: 2026-04-04  
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
| T-AUTH-03 | Spoofing | Password debole: algoritmo di hashing da verificare (bcrypt vs Argon2) | CRITICO | MEDIA | 🟡 DA ANALIZZARE |
| T-AUTH-04 | Elevation of Privilege | Refresh Token: assenza di meccanismo di revoca (token rotation) | ALTO | MEDIA | 🔴 APERTO |
| T-AUTH-05 | Repudiation | Assenza di audit log per eventi di autenticazione (login, logout, failed attempts) | MEDIO | ALTA | 🔴 APERTO |

### 4.2 api-gateway

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-GW-01 | Tampering | Header X-Auth-User/Role iniettabili dal client se non rimossi in ingresso | CRITICO | MEDIA | ✅ RISOLTO |
| T-GW-02 | Denial of Service | Rate limiting insufficiente o bypassabile per certi endpoint | ALTO | MEDIA | 🟡 DA ANALIZZARE |
| T-GW-03 | Information Disclosure | Assenza header sicurezza HTTP (CSP, HSTS, X-Frame-Options, X-Content-Type-Options) | MEDIO | ALTA | ✅ RISOLTO |
| T-GW-04 | Spoofing | CORS misconfiguration: origini non ristrette | ALTO | MEDIA | 🟡 DA ANALIZZARE |
| T-GW-05 | Tampering | CSRF: mancanza protezione esplicita per operazioni mutanti via cookie | ALTO | MEDIA | 🔴 APERTO |

### 4.3 guest-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-GST-01 | Elevation of Privilege | IDOR: un RECEPTIONIST potrebbe accedere a ospiti di altri hotel via UUID prevedibile | CRITICO | MEDIA | ✅ RISOLTO |
| T-GST-02 | Injection | Input validation: campi testo libero (nome, note) non sanitizzati | ALTO | MEDIA | 🟡 DA ANALIZZARE |
| T-GST-03 | Information Disclosure | Esposizione PII senza controllo hotel_id (multi-tenant data leak) | CRITICO | ALTA | ✅ RISOLTO |
| T-GST-04 | Tampering | Mass Assignment: DTO potrebbe accettare campi non previsti tramite JSON | MEDIO | MEDIA | 🟡 DA ANALIZZARE |

### 4.4 reservation-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-RES-01 | Tampering | Double Booking (Overbooking): assenza di lock ottimistico sulle date | CRITICO | ALTA | ✅ RISOLTO |
| T-RES-02 | Elevation of Privilege | IDOR: accesso a prenotazioni di altri hotel senza verifica ownership | CRITICO | MEDIA | 🔴 APERTO |
| T-RES-03 | Injection | Filtri di ricerca date non validati server-side | MEDIO | BASSA | 🟡 DA ANALIZZARE |

### 4.5 stay-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-STAY-01 | Elevation of Privilege | Check-in senza verifica stato prenotazione (es. CANCELLED) | ALTO | MEDIA | 🔴 APERTO |
| T-STAY-02 | Repudiation | Assenza di audit trail per operazioni check-in/check-out | ALTO | ALTA | 🔴 APERTO |
| T-STAY-03 | Information Disclosure | Report Alloggiati: trasmissione dati PS senza verifica firma/TLS | CRITICO | BASSA | 🟡 DA ANALIZZARE |

### 4.6 billing-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-BILL-01 | Elevation of Privilege | IDOR: accesso a fatture di altri ospiti/hotel | CRITICO | MEDIA | 🔴 APERTO |
| T-BILL-02 | Tampering | Importi non validati server-side (negative amounts, overflow) | ALTO | BASSA | 🟡 DA ANALIZZARE |
| T-BILL-03 | Repudiation | Assenza di log immutabile delle transazioni di pagamento | ALTO | ALTA | 🔴 APERTO |

### 4.7 fb-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-FB-01 | Elevation of Privilege | IDOR: ordini F&B non legati a hotel_id, accessibili cross-hotel | ALTO | MEDIA | 🔴 APERTO |
| T-FB-02 | Tampering | Prezzi degli ordini non ricalcolati server-side (client-side price tampering) | CRITICO | ALTA | 🔴 APERTO |

### 4.8 config-service

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-CFG-01 | Information Disclosure | Endpoint `/actuator` esposti senza autenticazione | CRITICO | ALTA | 🔴 APERTO |
| T-CFG-02 | Information Disclosure | Segreti in chiaro nei file di configurazione (JWT secret, HMAC key, DB password) | CRITICO | ALTA | 🟡 DA ANALIZZARE |
| T-CFG-03 | Spoofing | Nessuna autenticazione tra config-service e microservizi consumer | ALTO | MEDIA | 🟡 DA ANALIZZARE |

### 4.9 Frontend (React)

| ID | Categoria STRIDE | Threat | Impatto | Probabilità | Stato |
|----|-----------------|--------|---------|-------------|-------|
| T-FE-01 | Tampering | XSS: output di dati utente non encoded in rendering dinamico | ALTO | MEDIA | 🟡 DA ANALIZZARE |
| T-FE-02 | Information Disclosure | Token JWT eventualmente esposto in localStorage o state non protetto | CRITICO | BASSA | ✅ RISOLTO |
| T-FE-03 | Elevation of Privilege | Broken Access Control frontend: route admin accessibili nascondendo solo elementi UI | MEDIO | ALTA | 🔴 APERTO |
| T-FE-04 | Tampering | Assenza Content-Security-Policy: possibilità di inject di script esterni | ALTO | MEDIA | ✅ RISOLTO |

---

## 5. Risk Matrix

```
IMPATTO
  │
  │  CRITICO  │ T-AUTH-02 │ T-GW-01  │ T-GST-01  │ T-RES-01  │ T-CFG-01  │
  │  ALTO     │ T-AUTH-01 │ T-GW-03  │ T-GST-02  │ T-STAY-01 │ T-BILL-01 │
  │  MEDIO    │ T-AUTH-05 │ T-GW-05  │ T-GST-04  │           │           │
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
| T-AUTH-03 | Verifica/upgrade hashing password (bcrypt cost factor) | auth-service | — | A02 | ⏳ |
| T-AUTH-04 | Refresh token rotation + blacklist | auth-service | — | A07 | ⏳ |
| T-AUTH-05 | Audit log autenticazione | auth-service | — | A09 | ⏳ |
| T-GW-01 | Verifica HMAC-SHA256 su X-Internal-Signature (InternalAuthFilter) | api-gateway + tutti i servizi | baseline (main) | A01 | ✅ |
| T-FE-02 | Token JWT in httpOnly cookie (Secure + SameSite=Strict) | auth-service/AuthController.java | baseline (main) | A02 | ✅ |
| T-GW-03 | SecurityHeadersFilter GlobalFilter: HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Permissions-Policy, CSP default-src 'none' su tutte le risposte del gateway | api-gateway/SecurityHeadersFilter.java | b901a9f | A05 | ✅ |
| T-GW-04 | CORS origins whitelist restrittiva | api-gateway | — | A05 | ⏳ |
| T-GW-05 | CSRF token per operazioni mutanti | api-gateway/frontend | — | A01 | ⏳ |
| T-GST-01 | IDOR fix: resolveGuest(id, hotelId) → findByIdAndHotelId, 404 anche per guest di altri hotel (no enumeration) | guest-service/GuestServiceImpl.java, GuestRepository.java | b483eac | A01 | ✅ |
| T-GST-03 | hotel_id su tabella guests (Flyway V2) + tutte le query repository scoped per hotelId; X-Auth-Hotel letto da InternalAuthFilter e propagato via FeignHeaderConfig | guest-service/V2__add_hotel_id_to_guests.sql, GuestRepository.java, InternalAuthFilter.java, FeignHeaderConfig.java | b483eac | A01 | ✅ |
| T-RES-01 | @Version optimistic lock + @Lock(PESSIMISTIC_WRITE) su overlap query + ConflictException HTTP 409 + Flyway V4 version column | reservation-service/Reservation.java, ReservationRepository.java, GlobalExceptionHandler.java, V4__add_version_to_reservations.sql | ca9bf92 | A04 | ✅ |
| T-RES-02 | Verifica ownership hotel_id su prenotazioni | reservation-service | — | A01 | ⏳ |
| T-STAY-01 | Validazione stato prenotazione prima check-in | stay-service | — | A04 | ⏳ |
| T-STAY-02 | Audit trail check-in/check-out | stay-service | — | A09 | ⏳ |
| T-BILL-01 | Verifica ownership fatture | billing-service | — | A01 | ⏳ |
| T-FB-02 | Ricalcolo prezzi server-side | fb-service | — | A04 | ⏳ |
| T-CFG-01 | Porta management separata (:8090, non pubblicata) + show-details: when-authorized su tutti i servizi | config-service/resources/config/*.yml, docker-compose.yml, prometheus.yml | b0cf898 | A05 | ✅ |
| T-CFG-02 | Segreti in variabili d'ambiente (no plaintext) | config-service | — | A02 | ⏳ |
| T-FE-01 | Output encoding React (verifica dangerouslySetInnerHTML) | frontend | — | A03 | ⏳ |
| T-FE-03 | Route guard server-side (non solo UI hiding) | frontend/gateway | — | A01 | ⏳ |
| T-FE-04 | CSP SPA-level via nginx.conf: script-src 'self', style-src 'self' 'unsafe-inline', connect-src 'self', frame-ancestors 'none'; nginx proxy /api/ → api-gateway per same-origin; api.ts baseURL relativo | frontend/nginx.conf, frontend/Dockerfile, frontend/src/services/api.ts | b901a9f | A05 | ✅ |

**Legenda stato**: ⏳ In attesa | 🔄 In corso | ✅ Completato

---

## 7. Mappatura OWASP Top 10 (2021)

| OWASP | Nome | Threats mappati |
|-------|------|----------------|
| A01 | Broken Access Control | T-GST-01, T-GST-03, T-RES-02, T-BILL-01, T-FB-01, T-FE-03 |
| A02 | Cryptographic Failures | T-AUTH-03, T-CFG-02 |
| A03 | Injection | T-GST-02, T-RES-03, T-FE-01 |
| A04 | Insecure Design | T-RES-01, T-STAY-01, T-FB-02 |
| A05 | Security Misconfiguration | T-GW-03, T-GW-04, T-CFG-01, T-FE-04 |
| A07 | Identification & Authentication Failures | T-AUTH-01, T-AUTH-02, T-AUTH-04 |
| A09 | Security Logging & Monitoring Failures | T-AUTH-05, T-STAY-02, T-BILL-03 |
