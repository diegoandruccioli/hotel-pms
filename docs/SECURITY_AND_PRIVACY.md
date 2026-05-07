# Hotel PMS — Sicurezza e Privacy dei Dati

**Versione:** 1.0 — 2026-05-07  
**Riferimenti normativi:** GDPR (Regolamento UE 2016/679) · Art. 109 TULPS (D.M. 7 gennaio 2013)  
**Branch sicurezza:** `feature/secure-coding-hardening`

---

## 1. Panoramica del modello di sicurezza

Il sistema adotta un modello di difesa in profondità (defense-in-depth) su più livelli:

```
[Browser]          → HTTPS obbligatorio, cookie httpOnly, SameSite=Strict
[API Gateway]      → JWT validation, rate limiting, CORS, security headers
[Microservizi]     → HMAC X-Internal-Signature, @PreAuthorize, hotel_id isolation
[Database]         → un DB per servizio, soft delete, audit trail createdAt/updatedAt
[Infrastruttura]   → Docker network isolation, Spring Cloud Config secrets
```

---

## 2. Autenticazione — JWT in cookie httpOnly

### Meccanismo

Le credenziali (username + password bcrypt) vengono scambiate **una sola volta** al login. Il server emette due token:

| Token | Durata | Cookie |
|-------|--------|--------|
| Access token (JWT) | 15 minuti | `jwt`; HttpOnly; SameSite=Strict; Secure |
| Refresh token | 7 giorni | `refresh_token`; HttpOnly; SameSite=Strict; Secure |

Il JWT contiene: `sub` (username), `role`, `hotelId`, `tokenVersion`, `exp`.

### Perché cookie HttpOnly invece di localStorage

localStorage è accessibile a qualsiasi JavaScript nella pagina — un attacco XSS potrebbe estrarre il token e inviarlo a un server esterno. Un cookie `HttpOnly` non è accessibile via `document.cookie` da JavaScript: anche in caso di XSS riuscito, il token rimane protetto.

### Refresh automatico

Quando il JWT scade (401), l'interceptor Axios chiama `POST /api/v1/auth/refresh` con il cookie `refresh_token`. Il server verifica il refresh token (firma + versione), emette nuovi token e li imposta come cookie. La richiesta originale viene ritentata automaticamente. L'utente non vede interruzioni.

### Invalidazione selettiva tramite tokenVersion

Ogni `UserAccount` ha un campo `tokenVersion` (intero). Il JWT include la versione al momento dell'emissione. Alla validazione, il gateway confronta la versione nel JWT con quella nel database: se discordano, il token è revocato. Questo permette di invalidare tutti i token di un utente specifico (es. dopo cambio password, disattivazione account) senza invalidare globalmente tutti i token.

### mustChangePassword

Al momento della creazione di un account (da parte di un ADMIN), viene impostato il flag `mustChangePassword=true`. Al primo login, il JWT contiene questo flag: il frontend reindirizza alla pagina di cambio password e blocca qualsiasi altra navigazione finché non viene completata.

**Rischio mitigato:** accesso con credenziali temporanee condivise senza cambio password — scenario comune in hotel dove un ADMIN crea l'account di un receptionist con una password provvisoria.

---

## 3. Protezione CSRF

Il sistema usa cookie `SameSite=Strict`, che impedisce al browser di inviare i cookie di sessione in richieste cross-site. Una pagina malevola su un altro dominio non può innescare chiamate API che portino i cookie di autenticazione del PMS.

Regola aggiuntiva: `CORS` nel gateway permette richieste solo dall'origine del frontend (`http://localhost:5173` in sviluppo, dominio configurato in produzione).

**Rischio mitigato:** attacchi CSRF classici dove un sito malevolo tenta di eseguire azioni (es. checkout, cambio password) usando la sessione dell'utente autenticato.

---

## 4. Firma HMAC inter-service (X-Internal-Signature)

### Problema

I microservizi espongono endpoint REST. Se un attaccante che si trova nella stessa rete (es. accesso al cluster interno) può chiamare direttamente `billing-service:8085/api/v1/invoices/stay` senza passare per il gateway, bypasserebbe completamente l'autenticazione JWT.

### Soluzione

Il gateway calcola un header `X-Internal-Signature: HMAC-SHA256(method + path + hotelId, INTERNAL_HMAC_SECRET)` e lo inietta in ogni richiesta verso i servizi. Ogni microservizio verifica la firma con lo stesso segreto prima di processare la richiesta. La verifica usa un confronto a tempo costante (`MessageDigest.isEqual`) per prevenire timing attack.

Il segreto `INTERNAL_HMAC_SECRET` viene generato una volta con `setup-hmac-secret.sh/.ps1`, salvato nel file `.env` (mai committato), e caricato da Spring Cloud Config.

**Startup check:** Se `INTERNAL_HMAC_SECRET` ha un valore di default o lunghezza insufficiente, il servizio logga un ERROR bloccante all'avvio.

**Rischio mitigato:** chiamate dirette ai microservizi da attori interni alla rete; lateral movement dopo compromissione di un container.

---

## 5. Autorizzazione RBAC (Role-Based Access Control)

### Ruoli

| Ruolo | Descrizione |
|-------|-------------|
| `RECEPTIONIST` | Operazioni quotidiane: prenotazioni, check-in, checkout, billing, housekeeping |
| `OWNER` | Tutto di RECEPTIONIST + report finanziari, export CSV |
| `ADMIN` | Tutto di OWNER + gestione utenti, configurazione hotel |

### Enforcement

**Livello gateway:** Il gateway legge `X-Auth-Role` dal JWT e applica whitelist per path prefix:
- `/api/v1/users` → solo ADMIN
- `/api/v1/reports/owner` → OWNER, ADMIN
- `/api/v1/hotel-settings` → ADMIN

**Livello servizio:** Gli endpoint sensibili hanno `@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")` — secondo livello di difesa anche se il gateway venisse bypassato.

**Livello UI:** Il router React nasconde le route non autorizzate (es. `/admin/users`, `/owner-dashboard`) in base al ruolo del JWT. Questo è UI-only e non è un meccanismo di sicurezza — il backend è l'unica fonte di verità per l'autorizzazione.

**Rischio mitigato:** escalation di privilegi (un receptionist che accede a dati finanziari o crea/cancella utenti).

---

## 6. Multi-tenant isolation (hotel_id)

Il sistema gestisce più hotel sullo stesso deployment. Ogni entità rilevante ha un campo `hotel_id` (UUID). Ogni query filtra per `hotel_id` estratto dal JWT (`X-Auth-Hotel`), non da parametri utente.

Un receptionist dell'hotel A non può mai vedere dati dell'hotel B: il database non lo permette a livello di query, indipendentemente dall'URL chiamato.

Le migration Flyway garantiscono che `hotel_id` sia NOT NULL su tutte le tabelle rilevanti, con indici composti `(hotel_id, id)` per performance e correttezza.

**Rischio mitigato:** data leakage tra hotel in un deployment condiviso; IDOR (Insecure Direct Object Reference) tramite manipolazione degli ID negli URL.

---

## 7. Protezione dati ospiti (PII e GDPR)

### Dati trattati

| Dato | Classificazione | Servizio |
|------|----------------|---------|
| Nome, cognome | PII — Personale | guest-service |
| Email, telefono, città | PII — Contatto | guest-service |
| Data e luogo di nascita | PII — Sensibile | guest-service, stay-service |
| Tipo e numero documento | PII — Sensibile | stay-service (StayGuest) |
| Sesso, nazionalità | PII — Demografico | stay-service (StayGuest) |
| Dati di fatturazione | PII — Finanziario | billing-service |

### Misure implementate

**Soft delete:** La cancellazione di un ospite imposta `active=false` — il record fisico rimane nel database. Questo è necessario per la conservazione dei dati di fatturazione e dei report Alloggiati (obblighi legali TULPS e contabili). Tentare di cancellare un ospite con soggiorni attivi o fatture aperte ritorna HTTP 451 (Unavailable for Legal Reasons).

**Email uniqueness per hotel:** L'email è unica per coppia `(email, hotel_id)` — non globalmente. Un ospite può avere profili separati in hotel diversi (es. catena alberghiera). L'indice è parziale: `WHERE email IS NOT NULL` per permettere ospiti senza email.

**Separazione dei dati Alloggiati:** I dati PS (stato/comune nascita, documento) sono salvati su `StayGuest` (stay-service) separati dal profilo ospite in `Guest` (guest-service). Un receptionist non vede mai i dati PS storici — sono solo nel report Alloggiati e nel database stay-service.

### Diritti dell'interessato (GDPR Art. 17)

Il sistema implementa il diritto alla cancellazione come soft delete. Il dato rimane per obbligo legale (contratto, fatturazione, TULPS) ma viene marcato come inattivo. Per eliminazioni reali (es. dopo scadenza obbligo legale), è necessario un processo manuale a livello database, previa verifica con il DPO dell'hotel.

### Minimalità del dato

Il sistema raccoglie solo i dati necessari per:
1. L'operatività alberghiera (prenotazione, check-in, billing)
2. L'adempimento degli obblighi TULPS (art. 109 — comunicazione PS)
3. La fatturazione (dati fiscali hotel, non ospite)

Non vengono raccolti dati biometrici, dati sanitari, o profilazioni comportamentali.

---

## 8. Dati Alloggiati — Polizia di Stato (art. 109 TULPS)

### Obbligo legale

Le strutture ricettive devono comunicare telematicamente le generalità degli ospiti alla Questura competente entro 24 ore dal check-in (D.M. 7 gennaio 2013). La mancata comunicazione costituisce violazione dell'art. 109 TULPS, con sanzioni amministrative.

### Trasmissione sicura

- Il file `.txt` (tracciato 168 caratteri) viene generato in memoria e trasmesso via SOAP 1.1 su HTTPS al portale `alloggiatiweb.poliziadistato.it`
- Le credenziali PS (`ALLOGGIATI_USERNAME`, `ALLOGGIATI_PASSWORD`, `ALLOGGIATI_WS_KEY`) sono variabili d'ambiente — mai nel codice o nei YAML committati
- `ALLOGGIATI_DRY_RUN=true` (default) chiama l'operazione `Test` invece di `Send` — protegge da invii accidentali in ambiente di sviluppo/staging
- Il flag `alloggiatiSent` su ogni `Stay` traccia l'avvenuta comunicazione

---

## 9. Rate limiting

Il gateway implementa un rate limiter basato su Redis Token Bucket. Ogni `(IP, path)` ha un bucket con capacità e tasso di riempimento configurabili. Richieste eccessive ricevono HTTP 429 (Too Many Requests).

**Rischio mitigato:** brute force sulle credenziali di login, DoS sugli endpoint pubblici.

---

## 10. Osservabilità e Audit Trail

**X-Correlation-ID:** Ogni richiesta in ingresso al gateway riceve o genera un UUID `X-Correlation-ID`. Questo ID viene propagato via MDC a tutti i log di tutti i microservizi che elaborano la richiesta. Un errore distribuito (es. checkout → billing → inventory) è tracciabile con un unico ID nei log aggregati (Loki/Grafana).

**Audit trail database:** Tutte le entity hanno `createdAt` e `updatedAt` gestiti da `@EntityListeners(AuditingEntityListener.class)`. Il campo `active` (soft delete) preserva lo storico degli accessi.

**Distributed tracing:** Zipkin raccoglie i trace span di ogni richiesta inter-service. Prometheus raccoglie metriche di latenza, throughput e error rate. Grafana visualizza dashboard operative.

---

## 11. Gestione segreti e configurazione

| Segreto | Dove vive | Protezione |
|---------|-----------|-----------|
| JWT secret (`JWT_SECRET`) | `.env` → Spring Cloud Config | Non committato; startup check lunghezza |
| HMAC secret (`INTERNAL_HMAC_SECRET`) | `.env` → Spring Cloud Config | Generato da `setup-hmac-secret.sh` |
| DB password (`POSTGRES_PASSWORD`) | `.env` → `docker-compose.yml` env | Non committato |
| Credenziali PS Alloggiati | `.env` → Spring Cloud Config | Non committato; `.env.example` documenta i nomi |
| Credenziali default admin | README (solo per sviluppo) | `mustChangePassword=true` forza il cambio |

`.env` è in `.gitignore`. `.env.example` fornisce il template con tutti i nomi di variabile e commenti, senza valori reali.

Springdoc (Swagger UI) è disabilitato in produzione tramite `api-gateway-prod.yml` — gli endpoint `/swagger-ui.html` e `/v3/api-docs` non sono raggiungibili dall'esterno in produzione.

---

## 12. Sanzioni evitate e compliance

| Rischio | Norma | Misura |
|---------|-------|--------|
| XSS + furto token | OWASP A3 | Cookie HttpOnly |
| CSRF | OWASP A1 | SameSite=Strict + CORS |
| SQL injection | OWASP A3 | Spring Data JPA (prepared statements) |
| Data leakage tra hotel | GDPR Art. 5(1)(f) | hotel_id isolation + HMAC |
| Accesso non autorizzato a PII | GDPR Art. 32 | RBAC + JWT + HTTPS |
| Mancata comunicazione PS | TULPS Art. 109 | Auto-submit SOAP + audit flag |
| Furto credenziali admin | OWASP A7 | mustChangePassword + bcrypt |
| Brute force | OWASP A4 | Rate limiting Redis |
| Secret in repository | OWASP A2 | .gitignore + Spring Cloud Config |

**Sanzioni GDPR:** violazioni possono comportare sanzioni fino a €20 milioni o 4% del fatturato annuo globale (art. 83 GDPR). Le misure implementate mirano a dimostrare compliance con il principio di "adeguate misure tecniche e organizzative" (art. 32 GDPR).

**Sanzioni TULPS:** omessa o ritardata comunicazione PS è sanzionata amministrativamente con sospensione della licenza (art. 109 TULPS) e possibile denuncia penale per reiterazione.
