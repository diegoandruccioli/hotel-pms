# Ricognizione repo — hotel-pms

Documento di sola analisi, mappa condivisa per i controlli di sicurezza successivi in
questa cartella. Nessun file di codice toccato per produrlo.

---

## 1. Architettura e stack

**Backend**: Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.0, Gradle Kotlin DSL
(nessun `pom.xml` — il repo non usa Maven). PMD/Checkstyle/SpotBugs zero-warning via
`org.danilopianini.gradle-java-qa`.

**Frontend**: React 19.2, TypeScript strict, Vite 8.2.1 (bumpato da 7 in questa sessione),
Zustand 5, React Router DOM 7, TailwindCSS 3, Axios, react-i18next.

**Microservizi** (8 deployable + 3 librerie condivise):

| Servizio | Porta | DB | Instradato dal gateway |
|---|---|---|---|
| api-gateway | 8080 | — | — (è il gateway) |
| config-service | 8888 | — | no (Spring Cloud Config Server, Basic Auth) |
| auth-service | 8087 | hotel_auth | sì (`/api/v1/auth/**`) |
| guest-service | 8083 | hotel_guest | sì (`/api/v1/guests/**`) |
| frontdesk-service | 8081 | hotel_frontdesk | sì (rooms/room-types/reservations/stays/quotations/rate-calendar) |
| billing-service | 8085 | hotel_billing | sì (`/api/v1/invoices/**`, `/api/v1/reports/**`) |
| fb-service | 8086 | hotel_fb | sì (`/api/v1/fb/**`) |
| notification-service | 8088 | — (stateless) | **no** — raggiungibile solo dalla rete Docker interna, mai dal browser |

Librerie condivise (non deployable): `common-web-lib` (RFC 7807 error handling,
`AbstractProblemDetailAdvice`), `internal-auth-lib` (`InternalAuthFilter` HMAC+anti-replay,
`FeignAuthInterceptor`, `SecurityFilterChainFactory` — usata da billing/fb/frontdesk/guest/
notification-service), `pdf-template-engine` (Thymeleaf+openhtmltopdf, plain Java).

**Comunicazione**: browser → nginx (frontend, porta 80) → api-gateway (8080, JWT da
cookie httpOnly `jwt`) → microservizio via OpenFeign, ogni chiamata interna firmata HMAC-SHA256
(`X-Internal-Signature` + `X-Auth-Timestamp` + `X-Auth-Nonce`, validata da `InternalAuthFilter`
in ogni servizio, nonce store su Redis condiviso, finestra 60s). Il gateway inietta
`X-Auth-User`/`X-Auth-Role`/`X-Auth-Hotel` dopo aver validato il JWT — i servizi a valle si
fidano di questi header **solo** perché sono raggiungibili esclusivamente dalla rete Docker
interna (non pubblicati sull'host in `docker-compose.prod.yml`).

**Avvio locale**: `./setup-hmac-secret.ps1` (genera `INTERNAL_HMAC_SECRET`/`JWT_SECRET` in
`.env`, una tantum) poi `./start.ps1` (`docker compose up -d`, profilo base = 11 servizi
core; `--profile observability` aggiunge Loki/Grafana/Zipkin/Prometheus/Alertmanager).
5 DB Postgres (1 istanza, 5 database, container `hotel_postgres`), Redis per rate-limit
gateway + nonce store anti-replay + refresh-token blacklist auth-service.

---

## 2. Endpoint REST — punti di ingresso input utente

Vedi **tabella riepilogativa completa** in fondo (§7). Riassunto per categoria di input:

- **Body JSON**: quasi tutti i POST/PUT/PATCH (DTO record + Jakarta Bean Validation).
- **Path variable**: UUID quasi ovunque (`{id}`, `{guestId}`, `{stayId}`, `{invoiceId}`,
  `{userId}`, `{roomTypeId}`) — potenziale IDOR se manca lo scoping `hotel_id` lato query.
- **Query param**: filtri di ricerca/paginazione (`search`, `status`, `dateFrom`/`dateTo`,
  `sort`, `page`/`size`), `AlloggiatiLookupController` (`provincia`, `term`).
- **Header**: nessun endpoint pubblico legge header applicativi diretti — solo il gateway
  inietta `X-Auth-*`/`X-Internal-Signature` per il traffico interno.
- **Upload file**: **nessun endpoint di upload file trovato** nei controller REST — i
  "file" generati (PDF fattura/preventivo, XML FatturaPA, export CSV/ZIP Alloggiati) sono
  tutti **output**, mai input caricato dal client. Da confermare in `file-upload.md`
  (potrebbe restringersi a path-traversal su lettura/generazione, non su upload).

---

## 3. Autenticazione e autorizzazione

**Login**: `POST /api/v1/auth/login` (auth-service) → `AuthServiceImpl` verifica credenziali
(`DelegatingPasswordEncoder`: Argon2id di default — 19 MiB memoria, 2 iterazioni,
parallelismo 1 — con lazy-rehash da BCrypt legacy strength 12 al prossimo login riuscito),
emette access token (15 min) + refresh token (7 giorni) come cookie **httpOnly** (`jwt`,
`refreshToken`), `SameSite` configurato lato `AuthController`. JWT: **HS256**
(`SignatureAlgorithm.HS256`, `Keys.hmacShaKeyFor`), secret da `${JWT_SECRET}` env var.
Claims: `sub` (username), `role`, `hotelId`, `mustChangePassword`, `jti` (per il refresh,
supporta blacklist su Redis).

**Dove**: `auth-service/src/main/java/com/hotelpms/auth/{config/SecurityConfig.java,
service/JwtService.java, service/AuthServiceImpl.java, controller/AuthController.java}`.
`api-gateway/src/main/java/com/hotelpms/gateway/filter/AuthenticationFilter.java` è
l'**unico** punto che valida il JWT per il traffico browser (i microservizi a valle non
lo rivalidano, si fidano degli header iniettati dal gateway + della firma HMAC interna).

**RBAC — 3 livelli, non uno solo**:
1. **Gateway** (`AuthenticationFilter.java`): `OPERATIONAL_ROLES` = ADMIN/OWNER/RECEPTIONIST
   (chiunque altro → 401/403 prima di raggiungere qualunque servizio).
   `WRITE_RESTRICTED_PREFIXES` = `/api/v1/room-types`, `/api/v1/rate-calendar` (scritture
   solo ADMIN/OWNER, GET aperto a RECEPTIONIST). `FULLY_RESTRICTED_PREFIXES` =
   `/api/v1/reports` (ADMIN/OWNER anche in lettura). `USERS_PATH_PREFIX` =
   `/api/v1/auth/users` sempre ADMIN/OWNER. `mustChangePassword=true` blocca tutto tranne
   4 path allow-listed (change-password/me/logout/refresh).
2. **`@PreAuthorize`** per-controller (vedi tabella §7) — pattern deliberato del progetto:
   un endpoint SENZA `@PreAuthorize` non è necessariamente un buco, significa "ogni ruolo
   operativo può usarlo", si affida al gate generico del gateway (confermato in
   `backup/SUMMARY.md`, entry 2026-08-09 21:10 — verificato esplicitamente per
   `ReservationController`/`QuotationController`).
3. **`InternalAuthFilter`** (internal-auth-lib) su ogni servizio: firma HMAC+anti-replay
   per il traffico Feign service-to-service, indipendente dal JWT.

**Ruoli esistenti**: ADMIN, OWNER, RECEPTIONIST, MANAGER (quest'ultimo visto in
`RoomController`/`ReservationController` in alcuni `@PreAuthorize` — verificare in
`access-control.md` se è usato in modo coerente).

**Endpoint pubblici** (nessun cookie richiesto): `POST /api/v1/auth/register`,
`POST /api/v1/auth/login`, `POST /api/v1/auth/refresh` — tutta la route
`/api/v1/auth/**` non porta `AuthenticationFilter` nel gateway (solo rate-limit per-IP).
`/api/v1/auth/users/**` invece SÌ porta `AuthenticationFilter` (route dichiarata prima,
matching più specifico).

---

## 4. Flussi di dati sensibili

- **Pagamenti/fatturazione**: `billing-service` — `InvoiceController`/`PaymentController`,
  generazione XML FatturaPA (`FatturaPAServiceImpl`, XXE hardening applicato in questa
  sessione — GAP-11), guardie di immutabilità post-export (`InvoiceFiscalExport`,
  T-BILL-06), guardia legale GDPR/fiscale su cancellazione ospite (T-GST-06).
- **Dati personali ospiti (PII)**: `guest-service` — documento identità, nazionalità,
  indirizzo; `GuestPrivacySettingsController` gestisce retention policy GDPR; job
  notturno di anonimizzazione (`GuestRetentionJobServiceImpl`).
- **Report Alloggiati (dati per la Polizia di Stato)**: `frontdesk-service/stays` —
  `AlloggiatiWebSenderServiceImpl` invia SOAP verso il portale esterno (credenziali da
  env var, XXE hardening già presente — `disallow-doctype-decl`).
- **Query al DB**: esclusivamente via Spring Data JPA, `@Query` sempre parametrizzata
  (verificato a fondo in un audit injection precedente in questa stessa sessione — zero
  concatenazione di stringhe in SQL/JPQL in tutto il repo).
- **Comandi di sistema**: nessuno — zero `Runtime.exec`/`ProcessBuilder` in tutto il repo
  (stesso audit).
- **Richieste HTTP verso l'esterno**: `AlloggiatiWebSenderServiceImpl` (SOAP verso portale
  Polizia di Stato, URL fisso da config, non da input utente) — unico punto che esce dalla
  rete Docker verso Internet lato backend. Nessun endpoint "webhook"/fetch-URL-da-utente
  trovato — da confermare in `ssrf.md`.

---

## 5. Configurazione e segreti

- **Config centralizzata**: `config-service/src/main/resources/config/*.yml` (uno per
  servizio, Spring Cloud Config Server, Basic Auth tra config-service e i consumer —
  credenziali `CONFIG_SERVER_USERNAME`/`CONFIG_SERVER_PASSWORD` da env var).
- **Config locale per servizio**: `*/src/main/resources/application.yml` +
  `application-<service>.yml` (solo 4 servizi ne hanno uno: auth/billing/fb/guest —
  quello di fb-service aveva un residuo Actuator `include:"*"`, corretto in questa
  sessione, GAP-13).
- **Build**: `*/build.gradle.kts` per servizio + `settings.gradle.kts` root, BOM
  `spring-boot-dependencies:3.5.16` + `spring-cloud-dependencies:2025.0.0`.
- **Secret attesi da env var** (mai hardcoded, verificato più volte in sessioni precedenti):
  `JWT_SECRET`, `INTERNAL_HMAC_SECRET`, `POSTGRES_PASSWORD`, `CONFIG_SERVER_USERNAME/PASSWORD`,
  `PGBACKREST_CIPHER_PASS` (opzionale), `S3_*` (backup off-site, opzionale),
  `ALLOGGIATI_USERNAME/PASSWORD`. Generati/iniettati da `setup-hmac-secret.ps1`/`.sh` +
  `.env` (gitignored) + `docker-compose.yml`.
- **Credenziale di default nota e documentata**: `admin`/`password` (seed
  `auth-service/src/main/resources/data.sql`) — bcrypt hash statico, già flaggato da
  Semgrep come "secret" e già triagged come falso positivo (è il seed dev, non un leak).

---

## 6. Superficie di attacco frontend

- **Zero `dangerouslySetInnerHTML`** in tutto `frontend/src` (verificato, T-FE-01,
  enforcement statico via regola ESLint `no-restricted-syntax` che vieta anche
  `innerHTML`/`outerHTML` diretti — errore di build se qualcuno la reintroduce).
- **CSP**: dichiarata in `frontend/nginx.conf` (non in un header applicativo React) —
  `script-src 'self'`, `frame-ancestors` variabile per location (`'self'` su `/api/`,
  `'none'` altrove — GAP-10, fix per permettere il pattern iframe-nascosto usato per i
  download PDF).
- **Form principali che accettano input libero verso il backend**: `GuestFormModal`
  (nome, documento, indirizzo), `ReservationForm`/`WalkInCheckInForm`, `QuotationForm`,
  `PaymentModal`, `AdminUsers`/`CreateUserModal` (username/email), `RoomFormModal`/
  `RoomTypeFormModal`, `MenuFormModal`/`OrderFormModal` (F&B) — ognuno con Zod schema
  client-side + Jakarta Bean Validation server-side (difesa in profondità, non solo
  client).
- **Autenticazione lato client**: token **mai** in `localStorage` — solo cookie httpOnly,
  Axios interceptor (`services/api.ts`) gestisce refresh silenzioso su 401.
- **Superficie di sanitizzazione output**: React JSX escapa per default; nessun rendering
  raw HTML da dati API in nessuna pagina verificata negli audit precedenti — da
  riconfermare puntualmente in `xss.md`.

---

## 7. Tabella riepilogativa endpoint

Legenda accesso: **Gateway** = protetto solo dal gate generico del gateway (OPERATIONAL_ROLES,
nessun `@PreAuthorize` locale — pattern deliberato, non un gap di per sé).
**`@PreAuthorize`** = ruolo esplicito indicato. **Pubblico** = nessun cookie richiesto.
**HMAC-only** = raggiungibile solo dalla rete Docker interna, mai dal gateway/browser.

### auth-service (`/api/v1/auth`)

| Metodo | Path | Controller.metodo | Accesso | Input |
|---|---|---|---|---|
| POST | `/register` | AuthController.register | Pubblico | body (username,email,password) |
| POST | `/login` | AuthController.login | Pubblico | body (username,password) |
| POST | `/refresh` | AuthController.refresh | Pubblico (cookie refresh) | cookie |
| POST | `/logout` | AuthController.logout | Pubblico (cookie) | cookie |
| POST | `/change-password` | AuthController.changePassword | Gateway (autenticato) | body |
| GET | `/me` | AuthController.me | Gateway (autenticato) | cookie |
| GET | `/users` | UserManagementController.list | `@PreAuthorize` ADMIN/OWNER | query |
| POST | `/users` | UserManagementController.create | `@PreAuthorize` ADMIN/OWNER | body |
| PATCH | `/users/{userId}/deactivate` | UserManagementController | `@PreAuthorize` ADMIN/OWNER | path |
| PATCH | `/users/{userId}/activate` | UserManagementController | `@PreAuthorize` ADMIN/OWNER | path |
| PATCH | `/users/{userId}/reset-password` | UserManagementController | `@PreAuthorize` ADMIN/OWNER | path |

### guest-service (`/api/v1/guests`)

| Metodo | Path | Controller.metodo | Accesso | Input |
|---|---|---|---|---|
| POST | `` | GuestController.create | Gateway | body |
| GET | `/{id}` | GuestController.getById | Gateway | path (UUID) |
| GET | `` | GuestController.list | Gateway | query |
| PUT | `/{id}` | GuestController.update | Gateway | path+body |
| DELETE | `/{id}` | GuestController.delete | `@PreAuthorize` ADMIN/OWNER | path — guardia GDPR/fiscale aggiuntiva (T-GST-06) |
| GET | `/search` | GuestController.search | Gateway | query |
| POST | `/{id}/documents` | GuestController.addDocument | Gateway | path+body |
| DELETE | `/{id}/documents/{documentId}` | GuestController.removeDocument | Gateway — **nessun `@PreAuthorize`, nessuna guardia legale** (candidato T-GST-08, già segnalato in sessione precedente come fuori scope, mai fixato) | path |
| POST | `/batch` | GuestController.batch | Gateway | body (lista id) |
| GET | `/{id}/export` | GuestController.export | `@PreAuthorize` ADMIN/OWNER | path |
| GET | `/settings` | GuestPrivacySettingsController.get | `@PreAuthorize` classe ADMIN/OWNER | — |
| PUT | `/settings` | GuestPrivacySettingsController.update | `@PreAuthorize` classe ADMIN/OWNER | body |

### frontdesk-service — rooms (`/api/v1/rooms`, `/api/v1/room-types`)

| Metodo | Path | Controller.metodo | Accesso | Input |
|---|---|---|---|---|
| POST | `/api/v1/rooms` | RoomController.create | `@PreAuthorize` ADMIN/OWNER | body |
| GET | `/api/v1/rooms/{id}` | RoomController.getById | Gateway | path |
| GET | `/api/v1/rooms` | RoomController.list | Gateway | query |
| GET | `/api/v1/rooms/availability` | RoomController.availability | Gateway | query (date) |
| PUT | `/api/v1/rooms/{id}` | RoomController.update | `@PreAuthorize` ADMIN/OWNER | path+body |
| PATCH | `/api/v1/rooms/{id}/status` | RoomController.updateStatus | `@PreAuthorize` ADMIN/OWNER/RECEPTIONIST | path+body |
| DELETE | `/api/v1/rooms/{id}` | RoomController.delete | `@PreAuthorize` ADMIN/OWNER | path |
| POST | `/api/v1/room-types` | RoomTypeController.create | `@PreAuthorize` ADMIN/OWNER + gateway WRITE_RESTRICTED | body |
| GET | `/api/v1/room-types/{id}` | RoomTypeController.getById | Gateway | path |
| GET | `/api/v1/room-types` | RoomTypeController.list | Gateway | query |
| PUT | `/api/v1/room-types/{id}` | RoomTypeController.update | `@PreAuthorize` ADMIN/OWNER + gateway | path+body |
| DELETE | `/api/v1/room-types/{id}` | RoomTypeController.delete | `@PreAuthorize` ADMIN/OWNER + gateway | path |
| GET | `/api/v1/room-types/{roomTypeId}/rate-seasons` | RateSeasonController.list | Gateway | path+query |
| POST | `/api/v1/room-types/{roomTypeId}/rate-seasons` | RateSeasonController.create | `@PreAuthorize` ADMIN/OWNER | path+body |
| PUT | `/api/v1/room-types/{roomTypeId}/rate-seasons/{id}` | RateSeasonController.update | `@PreAuthorize` ADMIN/OWNER | path+body |
| DELETE | `/api/v1/room-types/{roomTypeId}/rate-seasons/{id}` | RateSeasonController.delete | `@PreAuthorize` ADMIN/OWNER | path |
| GET | `/api/v1/rate-calendar` | RateCalendarController.get | Gateway (GET aperto) | query |
| POST | `/api/v1/rate-calendar/bulk-apply` | RateCalendarController.bulkApply | `@PreAuthorize` ADMIN/OWNER + gateway WRITE_RESTRICTED (T-GW-09) | body |

### frontdesk-service — reservations/stays/quotations

| Metodo | Path | Controller.metodo | Accesso | Input |
|---|---|---|---|---|
| POST | `/api/v1/reservations` | ReservationController.create | Gateway | body |
| GET | `/api/v1/reservations/{id}` | ReservationController.getById | Gateway | path |
| GET | `/api/v1/reservations` | ReservationController.list | Gateway | query |
| GET | `/api/v1/reservations/search` | ReservationController.search | Gateway | query |
| PUT | `/api/v1/reservations/{id}` | ReservationController.update | Gateway | path+body |
| DELETE | `/api/v1/reservations/{id}` | ReservationController.delete | Gateway | path |
| PATCH | `/api/v1/reservations/{id}/status-and-guests` | ReservationController | Gateway | path+body |
| GET | `/api/v1/reservations/guest/{guestId}/active` | ReservationController | Gateway | path |
| POST | `/api/v1/reservations/{id}/confirmation-email/retry` | ReservationController | Gateway | path |
| POST | `/api/v1/stays` | StayController.checkIn | Gateway | body |
| PUT | `/api/v1/stays/{id}/check-out` | StayController.checkOut | Gateway | path |
| GET | `/api/v1/stays/{id}` | StayController.getById | Gateway | path |
| GET | `/api/v1/stays` | StayController.list | Gateway | query |
| GET | `/api/v1/stays/guest/{guestId}/latest` | StayController | Gateway | path |
| GET | `/api/v1/stays/reports/alloggiati` | StayController | `@PreAuthorize` ADMIN/OWNER | query |
| GET | `/api/v1/stays/reports/alloggiati/json` | StayController | `@PreAuthorize` ADMIN/OWNER | query |
| POST | `/api/v1/stays/reports/alloggiati/submit` | StayController | `@PreAuthorize` ADMIN/OWNER | body |
| GET | `/api/v1/stays/reports/alloggiati/failures/summary` | StayController | `@PreAuthorize` ADMIN/OWNER | query |
| GET | `/api/v1/stays/guest/{guestId}/last-date` | StayController | Gateway | path |
| GET | `/api/v1/stays/guest/{guestId}/history` | StayController | Gateway | path |
| POST | `/api/v1/stays/{id}/invoice/retry` | StayController | Gateway | path |
| POST | `/api/v1/stays/{id}/checkout-email/retry` | StayController | Gateway | path |
| GET | `/api/v1/stays/settings` | HotelSettingsController.get | Gateway | — |
| PUT | `/api/v1/stays/settings` | HotelSettingsController.update | `@PreAuthorize` ADMIN/OWNER | body |
| GET | `/api/v1/stays/lookup/stati` | AlloggiatiLookupController | Gateway | — |
| GET | `/api/v1/stays/lookup/comuni` | AlloggiatiLookupController | Gateway | query |
| GET | `/api/v1/stays/lookup/tipdoc` | AlloggiatiLookupController | Gateway | — |
| POST | `/api/v1/quotations` | QuotationController.create | Gateway | body |
| PUT | `/api/v1/quotations/{id}` | QuotationController.update | Gateway | path+body |
| POST | `/api/v1/quotations/{id}/duplicate` | QuotationController | Gateway | path |
| GET | `/api/v1/quotations` | QuotationController.list | Gateway | query |
| GET | `/api/v1/quotations/{id}` | QuotationController.getById | Gateway | path |
| GET | `/api/v1/quotations/{id}/pdf` | QuotationController.pdf | Gateway | path |
| POST | `/api/v1/quotations/{id}/send` | QuotationController | Gateway | path |
| POST | `/api/v1/quotations/{id}/convert` | QuotationController | Gateway | path+body |
| POST | `/api/v1/quotations/{id}/decline` | QuotationController | Gateway | path |
| DELETE | `/api/v1/quotations/{id}` | QuotationController.delete | Gateway | path |

### billing-service (`/api/v1/invoices`, `/api/v1/reports`)

| Metodo | Path | Controller.metodo | Accesso | Input |
|---|---|---|---|---|
| GET | `/{id}` | InvoiceController.getById | Gateway | path |
| GET | `` | InvoiceController.list | Gateway | query |
| GET | `/search` | InvoiceController.search | Gateway | query |
| GET | `/reservation/{reservationId}/latest` | InvoiceController | Gateway | path |
| POST | `/stay` | InvoiceController.createForStay | Gateway — **nessun `@PreAuthorize`** (chiamata interna via saga check-in) | body |
| POST | `/stay/{stayId}/charges` | InvoiceController.addCharge | Gateway — **nessun `@PreAuthorize`** | path+body |
| GET | `/guest/{guestId}/last-date` | InvoiceController | Gateway (chiamato anche via Feign interno) | path |
| GET | `/guest/{guestId}/history` | InvoiceController | Gateway | path |
| GET | `/{id}/pdf` | InvoiceController.pdf | Gateway | path |
| PATCH | `/{id}/document-type` | InvoiceController | `@PreAuthorize` ADMIN/OWNER | path+body |
| GET | `/{id}/fatturaPA` | InvoiceController.fatturaPA | `@PreAuthorize` ADMIN/OWNER | path |
| GET | `/{id}/fatturaPA/validate` | InvoiceController | `@PreAuthorize` ADMIN/OWNER | path |
| PATCH | `/{id}/sdi-status` | InvoiceController | `@PreAuthorize` ADMIN/OWNER | path+body |
| GET | `/export` | InvoiceController.exportBatch | `@PreAuthorize` ADMIN/OWNER | query (from,to,confirm) |
| POST | `/{invoiceId}/payments` | PaymentController.addPayment | Gateway — **nessun `@PreAuthorize`** | path+body |
| GET | `/reports/owner` | OwnerReportController | `@PreAuthorize` OWNER/ADMIN + gateway FULLY_RESTRICTED | query |

### fb-service (`/api/v1/fb`)

| Metodo | Path | Controller.metodo | Accesso | Input |
|---|---|---|---|---|
| POST | `/orders` | RestaurantOrderController.create | Gateway | body |
| GET | `/orders/stay/{stayId}` | RestaurantOrderController | Gateway | path |
| GET | `/orders` | RestaurantOrderController.list | Gateway | query |
| POST | `/orders/{id}/confirm` | RestaurantOrderController | Gateway — **nessun `@PreAuthorize`** (verifica stato interno: stay CHECKED_IN) | path |
| GET | `/menu-items` | MenuItemController.list | Gateway | query |
| POST | `/menu-items` | MenuItemController.create | `@PreAuthorize` ADMIN/OWNER | body |
| PUT | `/menu-items/{id}` | MenuItemController.update | `@PreAuthorize` ADMIN/OWNER | path+body |
| DELETE | `/menu-items/{id}` | MenuItemController.delete | `@PreAuthorize` ADMIN/OWNER | path |

### notification-service (`/internal/notifications`) — **HMAC-only, non instradato dal gateway**

| Metodo | Path | Controller.metodo | Accesso | Input |
|---|---|---|---|---|
| POST | `/reservation-confirmed` | NotificationController | HMAC-only (InternalAuthFilter) | body |
| POST | `/checkin` | NotificationController | HMAC-only | body |
| POST | `/checkout` | NotificationController | HMAC-only | body |
| POST | `/quotation` | NotificationController | HMAC-only | body |

---

## 8. Punti di partenza suggeriti per categoria

- **access-control.md**: partire dagli endpoint marcati "Gateway — nessun `@PreAuthorize`"
  sopra (specialmente billing-service `POST /stay`, `/stay/{stayId}/charges`,
  `PaymentController.addPayment`, fb-service `orders/{id}/confirm`,
  `GuestController.removeDocument`) — verificare se l'assenza è davvero il pattern
  deliberato (operational-role-only) o un vero gap IDOR/privilege escalation.
- **auth-jwt.md**: `JwtService.java` (HS256, no alg:none possibile con jjwt moderno ma
  verificare comunque), `AuthenticationFilter.parseJwtSafely`, refresh flow, Argon2id
  params, `AccountLockedException`/brute-force in `AuthServiceImpl`.
- **xss.md**: form React elencati in §6, verificare anche `dangerouslySetInnerHTML` in
  `pdf-template-engine` (Thymeleaf, contesto server-side ma genera HTML da dati utente
  per il PDF — vettore diverso, verificare escaping Thymeleaf `th:text` vs `th:utext`).
- **csrf.md**: `SecurityConfig` di ogni servizio + `InternalApiSecurityFilterChainFactory`
  (internal-auth-lib) — CSRF già disabilitato lì con motivazione documentata (GAP-9,
  falso positivo CodeQL già triagged, citare non ripetere). Verificare invece il gateway
  stesso e `AuthController` (cookie SameSite).
- **ssrf.md**: `AlloggiatiWebSenderServiceImpl` (URL fisso, non da input — probabile
  non-issue, verificare comunque), Feign client verso servizi interni (URL da config,
  non utente).
- **deserialization-xxe.md**: `FatturaPAServiceImpl` (XXE già hardenato, GAP-11 — verificare
  che l'hardening sia completo, non solo `disallow-doctype-decl`), `FatturaPaXsdValidator`,
  `AlloggiatiWebSenderServiceImpl` (parsing risposta SOAP).
- **misconfig.md**: Actuator per servizio (già verificato quasi tutti corretti,
  `prometheus,health,info` — solo fb-service aveva un residuo, già fixato GAP-13),
  CORS in `api-gateway.yml` (`allowedOrigins` da env var, verificare default),
  header sicurezza `frontend/nginx.conf` (già presenti, verificare completezza).
- **secrets.md**: `.env` (gitignored, verificare comunque non tracciato), `data.sql` (bcrypt
  seed admin, già triagged falso positivo), history git per secret storici mai rimossi.
- **file-upload.md**: nessun upload trovato in questa ricognizione — verificare a fondo
  comunque (potrebbe essercene uno non catturato dal grep `@*Mapping`), altrimenti
  concentrarsi su path-traversal nei download (PDF/XML/ZIP export).
- **business-logic.md**: race condition già note e già fixate in sessioni precedenti
  (numerazione fattura, doppio pagamento, `RoomService.updateHousekeepingStatus` +
  `SELECT ... FOR UPDATE`) — verificare se restano varchi non coperti, es. su
  `RateSeasonController`/`RateCalendarController` (bulk-apply) o `QuotationController`
  (conversione concorrente in prenotazione).
