# Security Report — hotel-pms

Consolidamento di 13 controlli di sicurezza (1 ricognizione + 10 audit paralleli read-only
+ 1 scansione dipendenze Trivy + 1 DAST OWASP ZAP contro lo stack live), eseguiti in
questa sessione. Nessun file di codice sorgente è stato modificato per produrre questo
report — solo analisi. File sorgente completi in `security-audit/*.md`.

**Metodologia**: 10 subagent paralleli (`security-auditor`), ciascuno partito dalla mappa
condivisa `00-recon.md` ma istruito a **verificare ogni claim contro il codice reale**,
non a fidarsi ciecamente della ricognizione. Due finding CRITICAL sono stati trovati
**indipendentemente** da due agent diversi (access-control e auth-jwt) sulla stessa
riga di codice — cross-validazione reale, non duplicazione.

---

## Tabella riepilogativa

| Sev. | # | Finding | File:Riga | Categoria |
|---|---|---|---|---|
| 🔴 CRITICAL | 1 | `POST /api/v1/auth/register` pubblico accetta `role`+`hotelId` dal client → escalation privilegi non autenticata ad ADMIN su qualsiasi hotel | `RegisterRequest.java:20-27`, `AuthServiceImpl.java:59-83` | access-control, auth-jwt |
| 🔴 CRITICAL | 2 | `StayRequest.status` client-controllato bypassa il guard `BILLING_NOT_PAID` al check-out e blocca la camera `OCCUPIED` per sempre | `StayRequest.java:37`, `StayServiceImpl.java:78-128` | business-logic |
| 🟠 HIGH | 3 | Rate-limiter gateway si fida del primo `X-Forwarded-For` (attaccante-controllato) — bypassabile ruotando l'header | `RateLimiterConfig.java:51-64` | auth-jwt |
| 🟠 HIGH | 4 | Lockout account keyed solo su username, nessun bind IP/device — DoS non autenticato contro l'account `admin` noto | `AuthServiceImpl.java` (lockout logic) | auth-jwt |
| 🟠 HIGH | 5 | Doppia prenotazione: TOCTOU reale, nessun vincolo di esclusione DB su date/camera (a differenza di `rate_seasons`) | `ReservationServiceImpl.java:76-99,625-647` | business-logic |
| 🟠 HIGH | 6 | Conversione preventivo→prenotazione: race concorrente, nessun `@Version`/lock, doppia prenotazione dallo stesso preventivo | `QuotationServiceImpl.java:280-315` | business-logic |
| 🟠 HIGH | 7 | CVE-2026-54399 in `httpcore5` 5.3.6, presente identico su 7/8 servizi backend, transitivo, non ancora triagato | tutti i `build.gradle.kts` (transitivo) | dependencies |
| 🟡 MEDIUM | 8 | Status HTTP lockout (429 vs 401) reintroduce user enumeration, regressione parziale di T-AUTH-01 | `AuthServiceImpl.java` | auth-jwt |
| 🟡 MEDIUM | 9 | Timing side-channel (DB-miss vs verifica Argon2id) abilita user enumeration al login | `AuthServiceImpl.java:99-124` | auth-jwt |
| 🟡 MEDIUM | 10 | `QuotationRepository` assente da `TenantIsolationArchTest` — nessuna guardia di regressione (oggi scoping corretto) | `TenantIsolationArchTest.java:52` | access-control |
| 🟡 MEDIUM | 11 | Rate-calendar bulk-apply: `UPDATE` di split/trim su stagioni preesistenti senza lock — lost-update race tra admin concorrenti | `RateCalendarServiceImpl.java:174-206` | business-logic |
| 🟡 MEDIUM | 12 | Seed admin (`admin`/`password`) inserito incondizionatamente da Flyway `V1__init_schema.sql` con `must_change_password=FALSE`, nessun warning di avvio | `V1__init_schema.sql:64-89` | misconfig |
| 🟡 MEDIUM | 13 | Redis senza `requirepass`/ACL ovunque nello stack — backing store di nonce anti-replay e blacklist refresh-token | `docker-compose.yml:99-104` | misconfig |
| 🟡 MEDIUM | 14 | `GuestController.removeIdentityDocument` senza `@PreAuthorize` — qualunque RECEPTIONIST elimina documenti identità (ownership verificata, gap di ruolo) | `GuestController.java:155` | access-control |
| 🟡 MEDIUM | 15 | CSP `style-src 'unsafe-inline'` confermato dal vivo (trade-off Tailwind già documentato, T-FE-04) | `frontend/nginx.conf` | dast |
| 🔵 LOW | 16 | `FatturaPaXsdValidator` — `SchemaFactory`/`Validator` senza `ACCESS_EXTERNAL_DTD/SCHEMA` (non sfruttabile oggi, nessun endpoint di upload XML) | `FatturaPaXsdValidator.java:53,73` | deserialization-xxe |
| 🔵 LOW | 17 | `FeignException`/`ExternalServiceException` handler espone `ex.getMessage()` (URL Docker interni) nel body 502 | `AbstractProblemDetailAdvice.java:138-145` | misconfig |
| 🔵 LOW | 18 | Nessun limite superiore su liste/quantità (righe ordine F&B, camere prenotazione/preventivo) — leva DoS minore | vari DTO request | business-logic |
| 🔵 LOW | 19 | Password Postgres hardcoded in 4 `application-*.yml` locali (mai usate realmente, override Docker con precedenza) | `application-{auth,billing,fb,guest}-service.yml` | secrets |
| 🔵 LOW | 20 | 3 header di isolamento cross-origin mancanti (COOP/COEP/CORP) | `frontend/nginx.conf` | dast |
| 🔵 LOW | 21 | Header `Server: nginx/1.31.3` — version disclosure, manca `server_tokens off` | `frontend/nginx.conf` | dast |
| 🔵 LOW | 22 | `notification-service` assente dal matrix Trivy CI — gap di copertura scanning, non una CVE | `.github/workflows/ci.yml:~120-149` | dependencies |
| 🔵 LOW | 23 | `GW_CORS_ALLOWED_ORIGINS` hardcoded letterale in `docker-compose.yml`, non `${VAR}`-sostituibile come tutto il resto | `docker-compose.yml:339` | misconfig |
| ⚪ INFO | — | Access token senza revoca server-side (fino a 15 min di validità residua dopo logout/cambio password); `JwtService.isTokenValid()` codice morto/tautologico; nessun TLS in tutto lo stack; `logoUrl` hotel senza validazione schema (hardening preventivo, non sfruttabile); `.gitignore` senza pattern `*.p12/*.jks/*.pfx`; CVE Go-stdlib su immagini terze parti (postgres/redis/grafana), stessa postura già accettata per DEP-CVE-07 | vari | tutti |

**Confermato pulito** (nessun finding sfruttabile, verificato attivamente non solo assunto):
XSS (frontend+Thymeleaf, zero `th:utext`/`dangerouslySetInnerHTML`), CSRF (doppia difesa
SameSite=Strict + double-submit token, GAP-9 già triagged), SSRF (nessun URL outbound
influenzato da input utente), file upload (non esiste alcun endpoint di upload — solo
metadata documento), deserializzazione Java/YAML (zero `ObjectInputStream`/`Yaml.load`
su input non fidato), XXE su parsing SOAP reale (`AlloggiatiWebSenderServiceImpl`,
`disallow-doctype-decl` confermato sufficiente), segreti (nessuno mai committato, `.env`
mai tracciato, history git pulita), doppio pagamento/numerazione fattura/lock housekeeping
camera (tutti confermati ancora corretti).

---

## Dettaglio findings — Critical

### 1. Escalation privilegi non autenticata via self-registration

**File**: `auth-service/src/main/java/com/hotelpms/auth/dto/RegisterRequest.java:20-27`,
`auth-service/src/main/java/com/hotelpms/auth/service/AuthServiceImpl.java:57-83`,
`auth-service/src/main/java/com/hotelpms/auth/mapper/UserAccountMapper.java:23-24`

`RegisterRequest` accetta `role` (enum `ADMIN`/`OWNER`/`RECEPTIONIST`/`GUEST`) e
`hotelId` (qualsiasi UUID) direttamente dal body JSON. `AuthServiceImpl.register()`
mappa la request all'entità senza alcuna restrizione server-side su questi due campi.
`POST /api/v1/auth/register` è pubblico — la route `/api/v1/auth/**` non porta
`AuthenticationFilter` nel gateway (solo rate-limit per-IP, a sua volta bypassabile,
vedi finding #3).

**Exploit**: `POST /api/v1/auth/register` con
`{"username":"x","password":"Aa1!Aa1!Aa1!Aa1!","email":"x@x.com","role":"ADMIN","hotelId":"<uuid di qualsiasi hotel esistente>"}`
restituisce cookie httpOnly validi come ADMIN di quel tenant — accesso completo a export
PII ospiti, generazione FatturaPA, mutazione fatture, invio Alloggiati, e capacità di
disattivare l'admin reale dell'hotel via `UserManagementController`.

**Remediation**: `register()` deve ignorare `role`/`hotelId` dal client — assegnare
sempre `role=RECEPTIONIST` (o il minimo privilegio) e derivare `hotelId` da un contesto
verificato (es. un invito firmato/token di onboarding con hotelId embedded, o rimuovere
del tutto la self-registration pubblica e spostarla dietro `UserManagementController`
con `@PreAuthorize` ADMIN/OWNER esistente).

### 2. `Stay.status` client-controllato bypassa guard fatturazione, blocca camera

**File**: `frontdesk-service/src/main/java/com/hotelpms/frontdesk/stays/dto/StayRequest.java:37`,
`frontdesk-service/src/main/java/com/hotelpms/frontdesk/stays/service/impl/StayServiceImpl.java:78-128`

`StayRequest.status` è un campo client-controllato che arriva senza filtro
nell'entità `Stay` persistita. `checkIn()` forza `CHECKED_IN` solo se lo status
sottomesso è `null`/`EXPECTED` — qualunque utente con ruolo operativo può fare
`POST /api/v1/stays` con `"status":"CHECKED_OUT"` e creare una stay già "nata"
checked-out, saltando completamente il guard `BILLING_NOT_PAID` di `checkOut()`
(righe 133-170). Peggio: la camera viene comunque impostata `OCCUPIED` dal passo
incondizionato della saga, ma non transiziona mai a `DIRTY` (accade solo dentro
`checkOut()`, mai invocato) — `RoomServiceImpl` rifiuta esplicitamente di liberare
`OCCUPIED` per altra via, quindi la camera resta bloccata permanentemente.

**Exploit**: un RECEPTIONIST malintenzionato (o un bug client) checka-in una stay con
`status: CHECKED_OUT` — nessun addebito richiesto, camera inutilizzabile finché non
interviene un DBA.

**Remediation**: `checkIn()` deve ignorare/rifiutare qualunque `status` diverso da
`EXPECTED`/`null` in ingresso, sempre forzare `CHECKED_IN` internamente — `status` non
deve mai essere un campo scrivibile da `StayRequest` in creazione.

---

## Dettaglio findings — High

### 3. Rate-limiter bypassabile via `X-Forwarded-For`

`api-gateway/src/main/java/com/hotelpms/gateway/config/RateLimiterConfig.java:51-64` usa
il **primo** valore di `X-Forwarded-For`, attaccante-controllato quando il gateway è
raggiungibile direttamente (`docker-compose.prod.yml` espone `:8080`) o quando nginx
*aggiunge* all'header invece di sovrascriverlo. Rate-limit su login/register bypassabile
ruotando l'header ad ogni richiesta. **Remediation**: usare solo l'ultimo hop fidato
(IP sorgente reale della connessione TCP a nginx, non l'header client-forgeable) o
configurare `proxy_set_header X-Forwarded-For $remote_addr;` (sovrascrivi, non append) e
fidarsi solo della catena nota.

### 4. Lockout account come primitiva DoS

`AuthServiceImpl` blocca l'account dopo N tentativi falliti keyed solo su `username` —
nessun bind a IP/device. Un attaccante non autenticato può tenere bloccato
permanentemente l'account `admin` (credenziale di default nota) ripetendo il fallimento
ogni 15 minuti a costo quasi zero. **Remediation**: combinare il lockout con un secondo
fattore (IP, CAPTCHA dopo soglia) invece di bloccare solo sull'identità dichiarata.

### 5. Doppia prenotazione — TOCTOU reale

`ReservationServiceImpl.java:76-99,625-647` — il lock `PESSIMISTIC_WRITE` sulla query di
overlap blocca solo le righe **restituite**; per una camera/range senza prenotazioni
preesistenti, due insert concorrenti vedono entrambi risultato vuoto e passano entrambi.
Nessun `EXCLUDE USING gist` su `reservations`/`reservation_line_items`, a differenza di
`rate_seasons` che ce l'ha. **Remediation**: stesso pattern già usato per `rate_seasons` —
vincolo di esclusione a livello DB su (room, date range).

### 6. Conversione preventivo concorrente — doppia prenotazione

`QuotationServiceImpl.convertToReservation():280-315` — `Quotation` non ha `@Version`,
`QuotationRepository.findByIdAndHotelId` non prende lock. Due chiamate `convert`
concorrenti sullo stesso preventivo passano entrambe il controllo di stato e creano
entrambe una prenotazione. **Remediation**: `@Version` su `Quotation` o
`PESSIMISTIC_WRITE` sulla lettura pre-conversione.

### 7. CVE-2026-54399 — httpcore5, 7/8 servizi backend

Vedi `security-audit/dependencies.md` per dettaglio completo. Transitivo (nessuna
dichiarazione diretta), identico su tutti i servizi Spring Boot 3.5.16/Spring Cloud
2025.0.0. **Remediation**: bump centralizzato del BOM o override `dependencyManagement`,
non un fix per-servizio isolato (rischio di drift, vedi precedente DEP-CVE-01).

---

## Dettaglio findings — Medium

Vedi tabella riepilogativa sopra per finding #8-15 — dettaglio completo con file:riga,
scenario ed esatta remediation nei rispettivi `security-audit/{auth-jwt,access-control,
business-logic,misconfig,dast}.md`.

---

## Dettaglio findings — Low / Informational

Vedi tabella riepilogativa sopra per finding #16-23 e la riga INFO — dettaglio completo
nei rispettivi file sorgente. Nessuno di questi è sfruttabile oggi in modo diretto; sono
tutti hardening/difesa-in-profondità o gap di processo (copertura CI, pattern
`.gitignore`).

---

## Priorità di intervento consigliata

1. **Finding #1 e #2** (CRITICAL) — bloccanti, sfruttabili senza autenticazione o con il
   minimo ruolo operativo, impatto diretto su isolamento multi-tenant e integrità dati.
2. **Finding #3, #4** (HIGH, auth) — stesso dominio del fix #1, ha senso affrontarli
   nello stesso giro di hardening su `auth-service`/`api-gateway`.
3. **Finding #5, #6** (HIGH, business-logic) — richiedono migration DB (vincoli di
   esclusione), pianificare come le già esistenti su `rate_seasons`.
4. **Finding #7** (HIGH, dipendenze) — bump libreria, basso rischio di regressione,
   quick win.
5. Il resto (Medium/Low) può seguire nell'ordine della tabella, nessuno è bloccante per
   l'uso attuale del sistema.

Tutti i fix di codice per i finding sopra, quando implementati, vanno sul branch
`feature/secure-coding-hardening` per regola di progetto — questo report resta la
base di riferimento, da aggiornare in `THREAT_MODEL.md` con nuove voci T-*/GAP-* man
mano che ciascun finding viene chiuso.
