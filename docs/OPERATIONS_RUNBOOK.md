# Operations Runbook — Hotel PMS

**Versione:** 1.1 — 2026-07-25 (corretti riferimenti stay-service/porte/container dopo audit Sprint 1)  
**Destinatari:** Tecnico IT responsabile dell'installazione  
**Prerequisiti:** accesso SSH al server, Docker installato, file `.env` configurato

---

## 1. Avvio e arresto del sistema

### Avvio completo

```bash
# Stack core (11 servizi) — SENZA osservabilità/backup
docker compose up -d

# Stack completo (raccomandato in produzione): aggiunge Loki/Grafana/Zipkin/
# Alertmanager/Prometheus (profilo "observability") e il backup automatico
# Postgres (profilo "backup") — entrambi i --profile sono opt-in, senza non partono
docker compose --profile observability --profile backup up -d

# Verifica che tutti i servizi siano healthy
docker compose ps
# Atteso: ogni container mostra "(healthy)" o "Up"
```

Ordine di avvio gestito automaticamente dai `depends_on` + healthcheck.
Tempo stimato per avvio completo: **60-90 secondi**.

### Verifica salute del sistema

```bash
# Stato sintetico di tutti i container (il modo più semplice: si basa
# sull'HEALTHCHECK Docker già configurato su ogni servizio)
docker compose ps

# Health check dettagliato di un servizio: l'actuator gira su una porta di
# management SEPARATA (8090), mai pubblicata sull'host — va interrogata da
# dentro la rete Docker, non da curl sull'host sulla porta applicativa
docker exec api-gateway wget -qO- http://localhost:8090/actuator/health

# Verifica tutti i microservizi in un colpo (nomi container, non porte host)
for svc in config-server api-gateway auth-service guest-service frontdesk-service \
           billing-service fb-service notification-service; do
  echo -n "$svc: "
  docker exec "$svc" wget -qO- http://localhost:8090/actuator/health 2>/dev/null \
    | grep -o '"status":"[^"]*"' || echo "UNREACHABLE"
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
docker compose logs -f frontdesk-service
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
docker compose logs frontdesk-service | grep -E "ALLOGGIATI|SOAP_ERROR|SUBMISSION"
```

### Grafana + Loki (aggregazione log)

Richiede il profilo `observability` (vedi §1). Apri **http://localhost:3000**
con credenziali Grafana. Naviga in **Explore → Loki** e usa query LogQL — il
label `container` corrisponde al `container_name` esplicito in
`docker-compose.yml` (es. `frontdesk-service`, `api-gateway`), non al nome
di default prefissato dal progetto Compose:
```logql
{container="frontdesk-service"} |= "ERROR"
{container=~".*-service"} |= "correlationId=abc123"
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
docker compose restart frontdesk-service
# oppure rebuild dell'immagine se il codice è cambiato:
docker compose up -d --no-deps --build frontdesk-service
```

---

## 4. Recovery account ADMIN bloccato

Se **tutti** gli account ADMIN sono stati disattivati e nessun utente può fare login come admin:

```bash
# 1. Connettersi al container PostgreSQL
docker exec -it hotel_postgres psql -U postgres -d hotel_auth

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

## 5. Backup del database

Il container `postgres` (immagine custom `docker/postgres/`, non più
`postgres:15-alpine` semplice) integra **pgBackRest**: WAL archiving continuo
(`archive_timeout=120s`) più backup full settimanali/incrementali giornalieri,
su due repository indipendenti — `repo1` locale (volume `pgbackrest_repo`) e
`repo2` off-site (Backblaze B2, opt-in via `S3_*` in `.env`). Sostituisce
interamente il vecchio container `hotel_db_backup` (`pg_dumpall` ogni 24h) —
vedi `backup/DECISIONS.md §3.5b` per il perché.

```bash
# Stato del repository: catena full/incr, range WAL, ultimo successo per repo
docker exec hotel_postgres gosu postgres pgbackrest --stanza=hotel-pms info

# Verifica rapida (archive_command + entrambi i repo raggiungibili)
docker exec hotel_postgres gosu postgres pgbackrest --stanza=hotel-pms check
```

**RPO/RTO**: RPO target di **pochi minuti** (non più 24h) — decisione
esplicita in `backup/DECISIONS.md §3.5b`, che sostituisce la 24h precedente.
**Cambio di modello di sicurezza da annotare**: la cifratura (`PGBACKREST_CIPHER_PASS`)
è simmetrica, non asimmetrica come il vecchio schema `age` — la passphrase
vive necessariamente sullo stesso host di produzione. Vedi `THREAT_MODEL.md` T-OPS-02.

### Backup manuale (prima di un aggiornamento o operazione rischiosa)

```bash
# pg_dumpall resta disponibile per uno snapshot rapido indipendente da pgBackRest
docker exec hotel_postgres pg_dumpall -U postgres > "backup-$(date +%Y%m%d-%H%M).sql"
```

### Restore dell'ultimo backup (sovrascrive i dati correnti)

```bash
# ATTENZIONE: ferma Postgres, sovrascrive PGDATA, poi lo riavvia
docker compose stop postgres
docker run --rm -v hotel-pms_postgres_data:/var/lib/postgresql/data \
  -v hotel-pms_pgbackrest_repo:/var/lib/pgbackrest hotel-pms-postgres \
  gosu postgres pgbackrest --stanza=hotel-pms --delta restore
docker compose start postgres
```

### Restore Point-In-Time (PITR) — a un istante preciso, non solo l'ultimo backup

Questo è il motivo per cui esiste pgBackRest al posto del vecchio dump
giornaliero: si può tornare a un secondo prima di un errore (es. una
migration sbagliata, un `DELETE` senza `WHERE`), non solo all'ultimo backup
schedulato.

```bash
# Restore in una directory separata — NON tocca lo stack live, permette di
# verificare prima di decidere se promuovere il restore a dati reali
docker exec -u postgres hotel_postgres sh -c "
  mkdir -p /tmp/pitr-restore &&
  pgbackrest --stanza=hotel-pms --pg1-path=/tmp/pitr-restore \
    --type=time --target='2026-08-01 12:00:00+00' --target-action=promote \
    --delta restore
"

# Avvia il restore su una porta alternativa per verificarlo prima di promuoverlo
docker exec -u postgres hotel_postgres sh -c "
  postgres -D /tmp/pitr-restore -p 5433 -c unix_socket_directories=/tmp &
"
docker exec -u postgres hotel_postgres psql -h /tmp -p 5433 -d hotel_auth \
  -c "SELECT count(*) FROM flyway_schema_history;"

# Solo dopo aver verificato che è il punto giusto: fermare l'istanza di prova,
# fermare postgres reale, copiare /tmp/pitr-restore su PGDATA, riavviare
```

### Restore end-to-end da copia off-site (disaster recovery)

Stessa procedura PITR sopra, ma con `--repo=2` (Backblaze B2) invece di
`repo1` (locale) — per il caso peggiore, host di produzione perso del tutto.
Il drill periodico di questo path è **automatizzato** in CI (sotto), non solo
manuale come nel vecchio schema.

```bash
pgbackrest --stanza=hotel-pms --repo=2 --pg1-path=/tmp/dr-restore \
  --target-action=promote --delta restore
```

### Drill automatico settimanale (CI)

`.github/workflows/backup-restore-drill.yml` — schedulato ogni lunedì
(più `workflow_dispatch` per un run manuale), scarica l'ultimo backup da
`repo2`, lo ripristina su un runner GitHub effimero, verifica
`flyway_schema_history` per le 5 database. La private
`PGBACKREST_CIPHER_PASS` vive come secret CI, mai nel repo — richiede secret
GitHub: `PGBACKREST_CIPHER_PASS`, `S3_ENDPOINT`, `S3_BUCKET`,
`S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY`, `S3_REGION` (stessi valori di `.env`).

**Stato**: ✅ **pgBackRest verificato dal vivo, 2026-08-01.** WAL archiving
confermato su `repo1` e `repo2` (`pg_switch_wal()` forzato, segmento
comparso su entrambi entro pochi secondi). Backup full+incrementale
confermati su entrambi i repository — **bug trovato e corretto nello stesso
giro**: la prima versione dello scheduler backuppava solo il repo di default
(`repo1`), perché `pgbackrest backup` opera su un repo per invocazione
(diversamente da `archive-push`, che scrive su tutti i repo configurati in
automatico); `docker/postgres/backup-scheduler.sh` ora itera esplicitamente
su ogni repo configurato. **Drill PITR reale eseguito**: tabella marker
creata, riga inserita, WAL switch forzato, `pgbackrest restore --type=time`
puntato a un istante precedente l'insert, ripristinato in una directory
scratch separata dallo stack live, avviato come istanza Postgres temporanea
— la riga marker è risultata assente, la tabella (creata prima del target)
presente, `flyway_schema_history` coerente. Log pgBackRest conferma
esplicitamente: *"recovery stopping before commit of transaction ..."* alla
transazione esatta dell'insert. Verifica visiva sul bucket B2 reale
(console `secure.backblaze.com`) richiede login con le credenziali
dell'utente — da fare manualmente, non eseguibile da un agente automatico.
Drill CI non ancora eseguito dal vivo — richiede prima la configurazione dei
secret GitHub elencati sopra.

---

## 6. Rollback di una migration Flyway

Se una migration ha rotto il DB e il servizio non si avvia:

```bash
# 1. Identificare la migration problematica
docker compose logs <servizio> | grep -i "flyway\|migration\|V[0-9]"

# 2. Connettersi al DB del servizio (es. hotel_frontdesk)
docker exec -it hotel_postgres psql -U postgres -d hotel_frontdesk

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

# 2. Riavviare SOLO il frontdesk-service (l'unico che usa queste variabili —
#    da ADR-001 consolida quello che era stay-service)
docker compose up -d --no-deps frontdesk-service

# 3. Verificare nei log che il nuovo token venga ottenuto correttamente
docker compose logs --tail=50 frontdesk-service | grep -E "ALLOGGIATI|TOKEN"

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
docker exec hotel_postgres pg_dumpall -U postgres > "backup-pre-update-$(date +%Y%m%d).sql"

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

# Step 3: salute API Gateway (punto di ingresso) — actuator è su una porta di
# management separata (8090), mai pubblicata sull'host: va interrogata da
# dentro il container, non da curl sull'host
docker exec api-gateway wget -qO- http://localhost:8090/actuator/health

# Step 4: connettività DB
docker exec hotel_postgres psql -U postgres -c "SELECT 1" 2>&1

# Step 5: connettività Redis
docker exec hotel_redis redis-cli ping

# Step 6: se container in CrashLoopBackOff
docker compose logs <nome-container> --tail=100
```

---

## 10. Monitoraggio giornaliero

Verificare ogni mattina prima dell'apertura:

```bash
# Alloggiati non inviati del giorno precedente
docker compose logs frontdesk-service | grep -E "ALLOGGIATI_SOAP_ERROR|ALLOGGIATI_SEND_FAILED"
# Nessun output = tutto OK

# Errori 5xx nelle ultime 24 ore
docker compose logs | grep "ERROR" | tail -20

# Uso disco (i volumi Docker crescono nel tempo)
df -h
docker system df
```
