# Operations Runbook — Hotel PMS

**Versione:** 1.0 — 2026-05-17  
**Destinatari:** Tecnico IT responsabile dell'installazione  
**Prerequisiti:** accesso SSH al server, Docker installato, file `.env` configurato

---

## 1. Avvio e arresto del sistema

### Avvio completo

```bash
# Avvia tutti i container in background
docker compose up -d

# Verifica che tutti i servizi siano healthy
docker compose ps
# Atteso: ogni container mostra "(healthy)" o "Up"
```

Ordine di avvio gestito automaticamente dai `depends_on` + healthcheck.
Tempo stimato per avvio completo: **60-90 secondi**.

### Verifica salute del sistema

```bash
# Stato sintetico di tutti i container
docker compose ps

# Health check dell'API Gateway (primo punto di ingresso)
curl -s http://localhost:8080/actuator/health | python3 -m json.tool

# Verifica tutti i microservizi in parallelo
for port in 8081 8082 8083 8084 8085 8086 8087; do
  echo -n "Port $port: "
  curl -s http://localhost:$port/actuator/health | grep -o '"status":"[^"]*"'
done
```

### Arresto ordinato

```bash
# Arresta tutti i container (dati preservati nei volumi Docker)
docker compose down

# Arresta e RIMUOVE i volumi (DATA LOSS — usare solo per reset completo)
# docker compose down -v   # ← NON eseguire in produzione
```

---

## 2. Lettura dei log

### Log in tempo reale

```bash
# Tutti i servizi insieme
docker compose logs -f

# Singolo servizio
docker compose logs -f stay-service
docker compose logs -f api-gateway

# Ultimi N log + stream
docker compose logs --tail=100 -f auth-service
```

### Ricerca nei log

```bash
# Errori recenti su tutti i servizi
docker compose logs --tail=500 | grep -E "ERROR|WARN"

# Tracciare una richiesta tramite Correlation ID
docker compose logs | grep "correlationId=<UUID>"

# Log Alloggiati PS
docker compose logs stay-service | grep -E "ALLOGGIATI|SOAP_ERROR|SUBMISSION"
```

### Grafana + Loki (aggregazione log)

Apri **http://localhost:3000** con credenziali Grafana.
Naviga in **Explore → Loki** e usa query LogQL:
```logql
{container="hotel-pms-stay-service-1"} |= "ERROR"
{container=~"hotel-pms-.*"} |= "correlationId=abc123"
```

---

## 3. Procedura se un container non si avvia

### Diagnosi

```bash
# Vedere l'ultimo stato e gli errori di exit
docker compose ps -a

# Log completo del container problematico (inclusi errori di avvio)
docker compose logs --tail=200 <nome-servizio>

# Se il container crasha subito dopo l'avvio
docker compose up <nome-servizio>   # avvia in foreground per vedere l'output
```

### Cause comuni

| Sintomo nei log | Causa | Fix |
|---|---|---|
| `HMAC_SECRET_OK` assente o `HMAC_SECRET_PLACEHOLDER` | `.env` non configurato o HMAC troppo corto | Verificare `.env`, rigenerare HMAC con setup script |
| `Failed to connect to config-service` | Config service non ancora healthy | Attendere 30s e riprovare; verificare che `config-service` sia `Up` |
| `Connection refused` verso PostgreSQL | DB non ancora pronto | Attendere e verificare `docker compose ps postgres` |
| `Flyway: Found non-empty schema` con errore | Migration versione inconsistente | Vedi §6 Rollback migration |
| `ALLOGGIATI_USERNAME` non trovato | Variabile non impostata in `.env` | Aggiungere la variabile e riavviare il servizio |

### Riavvio singolo servizio (senza riavviare tutto)

```bash
docker compose restart stay-service
# oppure rebuild dell'immagine se il codice è cambiato:
docker compose up -d --no-deps --build stay-service
```

---

## 4. Recovery account ADMIN bloccato

Se **tutti** gli account ADMIN sono stati disattivati e nessun utente può fare login come admin:

```bash
# 1. Connettersi al container PostgreSQL
docker exec -it postgres psql -U postgres -d hotel_auth

# 2. Verificare gli account esistenti
SELECT id, username, email, active, role FROM user_account WHERE role = 'ADMIN';

# 3. Riattivare l'account (sostituire <UUID> con l'ID corretto)
UPDATE user_account
SET active = true, must_change_password = true
WHERE id = '<UUID-ADMIN>'
  AND role = 'ADMIN';

# 4. Verificare
SELECT id, username, active FROM user_account WHERE role = 'ADMIN';

# 5. Uscire
\q
```

Al prossimo login l'admin sarà costretto a cambiare la password (`mustChangePassword=true`).

---

## 5. Backup manuale del database

Eseguire prima di ogni aggiornamento del sistema o operazione rischiosa.

```bash
# Backup di tutti i database hotel in un singolo file
docker exec postgres pg_dumpall -U postgres > "backup-$(date +%Y%m%d-%H%M).sql"

# Oppure backup del singolo DB (es. hotel_stay)
docker exec postgres pg_dump -U postgres hotel_stay > "hotel_stay-$(date +%Y%m%d-%H%M).sql"

# Verifica che il file sia stato creato e non sia vuoto
ls -lh backup-*.sql
```

Conservare i backup in una posizione esterna al server (S3, NAS, ecc.).
Un backup automatizzato schedulato è in roadmap — vedi `docs/ROADMAP.md §P3`.

### Restore da backup

```bash
# ATTENZIONE: sovrascrive tutti i dati esistenti
docker exec -i postgres psql -U postgres < backup-YYYYMMDD-HHMM.sql
```

---

## 6. Rollback di una migration Flyway

Se una migration ha rotto il DB e il servizio non si avvia:

```bash
# 1. Identificare la migration problematica
docker compose logs <servizio> | grep -i "flyway\|migration\|V[0-9]"

# 2. Connettersi al DB del servizio (es. hotel_stay)
docker exec -it postgres psql -U postgres -d hotel_stay

# 3. Vedere lo stato delle migration
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;

# 4. Marcare la migration come fallita (rimuove il lock Flyway)
DELETE FROM flyway_schema_history WHERE version = '<versione-problematica>' AND success = false;

# 5. Fare il rollback manuale delle modifiche della migration (ALTER TABLE, DROP, ecc.)
# — dipende dal contenuto della migration specifica

# 6. Uscire e riavviare il servizio
\q
docker compose restart <servizio>
```

---

## 7. Aggiornamento credenziali Alloggiati PS

Quando la WsKey del portale Polizia di Stato scade o viene rigenerata:

```bash
# 1. Modificare il file .env sul server
nano .env
# Aggiornare la riga:
# ALLOGGIATI_WS_KEY=<nuova-chiave>
# (e ALLOGGIATI_PASSWORD se cambiata)

# 2. Riavviare SOLO lo stay-service (l'unico che usa queste variabili)
docker compose up -d --no-deps stay-service

# 3. Verificare nei log che il nuovo token venga ottenuto correttamente
docker compose logs --tail=50 stay-service | grep -E "ALLOGGIATI|TOKEN"

# 4. Fare un invio di test (dry-run)
# Ottenere prima un JWT admin:
TOKEN=$(curl -s -c /tmp/cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<password>"}')
# Poi testare l'invio (con DRY_RUN=true per sicurezza):
curl -s -b /tmp/cookies.txt -X POST \
  "http://localhost:8080/api/v1/stays/reports/alloggiati/submit?date=$(date +%Y-%m-%d)"
```

---

## 8. Aggiornamento del sistema a una nuova versione

```bash
# 1. Backup PRIMA dell'aggiornamento
docker exec postgres pg_dumpall -U postgres > "backup-pre-update-$(date +%Y%m%d).sql"

# 2. Pull dell'ultima versione del codice
git pull origin main

# 3. Rebuild delle immagini
docker compose build

# 4. Riavvio con le nuove immagini (rolling)
docker compose up -d

# 5. Verificare che tutti i servizi siano healthy
docker compose ps
docker compose logs --tail=50 | grep -E "ERROR|Started.*in"
```

Le migration Flyway vengono eseguite automaticamente al riavvio se ci sono versioni nuove.

---

## 9. Diagnostica rapida — checklist incidente

```bash
# Step 1: stato generale
docker compose ps

# Step 2: errori recenti
docker compose logs --tail=200 | grep -E "ERROR|FATAL|Exception"

# Step 3: salute API Gateway (punto di ingresso)
curl -s http://localhost:8080/actuator/health

# Step 4: connettività DB
docker exec postgres psql -U postgres -c "SELECT 1" 2>&1

# Step 5: connettività Redis
docker exec redis redis-cli ping

# Step 6: se container in CrashLoopBackOff
docker compose logs <nome-container> --tail=100
```

---

## 10. Monitoraggio giornaliero

Verificare ogni mattina prima dell'apertura:

```bash
# Alloggiati non inviati del giorno precedente
docker compose logs stay-service | grep -E "ALLOGGIATI_SOAP_ERROR|ALLOGGIATI_SEND_FAILED"
# Nessun output = tutto OK

# Errori 5xx nelle ultime 24 ore
docker compose logs | grep "ERROR" | tail -20

# Uso disco (i volumi Docker crescono nel tempo)
df -h
docker system df
```
