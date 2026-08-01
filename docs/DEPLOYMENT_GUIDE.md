# Deployment Guide — Hotel PMS

**Versione:** 1.1 — 2026-07-25 (aggiunto hardening porte prod, corretto backup/alert gia esistenti, riferimenti stay-service/container dopo audit Sprint 1)  
**Destinatari:** Tecnico IT che installa il sistema su un server di produzione

---

## 1. Requisiti del server

### Minimi (1 hotel, uso operativo normale)

| Risorsa | Minimo | Consigliato |
|---|---|---|
| CPU | 2 core | 4 core |
| RAM | 6 GB | 8 GB |
| Disco | 40 GB SSD | 100 GB SSD |
| OS | Ubuntu 22.04 LTS | Ubuntu 24.04 LTS |
| Rete | 100 Mbps | 1 Gbps |

### Stima crescita disco

- Database PostgreSQL: ~500 MB/anno per hotel di medie dimensioni
- Log Loki: ~2 GB/mese (configurare retention)
- Immagini Docker: ~5 GB fissi

### Prerequisiti software

```bash
# Docker Engine 24+
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# Docker Compose — richiesto >= 2.24 (il merge-tag `!reset` usato dal file
# di hardening produzione, §4, non funziona su versioni precedenti)
docker compose version   # deve mostrare v2.24 o superiore

# openssl (per generare i segreti)
openssl version
```

---

## 2. Installazione

### 2.1 Clonare il repository

```bash
git clone https://github.com/diegoandruccioli/hotel-pms.git
cd hotel-pms
```

### 2.2 Generare i segreti

```bash
# Linux/macOS
chmod +x setup-hmac-secret.sh && ./setup-hmac-secret.sh

# Lo script crea/aggiorna il file .env con INTERNAL_HMAC_SECRET
# Verificare che sia stato generato:
grep INTERNAL_HMAC_SECRET .env
```

### 2.3 Configurare le variabili d'ambiente

```bash
cp .env.example .env
nano .env   # o vim .env
```

**Variabili obbligatorie per la produzione:**

```bash
# Sicurezza — generare con: openssl rand -base64 48
INTERNAL_HMAC_SECRET=<stringa-random-base64-48-char-min>
JWT_SECRET=<stringa-random-base64-48-char-min>
POSTGRES_PASSWORD=<password-forte-db>
CONFIG_SERVER_PASSWORD=<password-config-server>

# Portale Alloggiati PS (ottenere dall'hotel)
ALLOGGIATI_USERNAME=<username-portale-ps>
ALLOGGIATI_PASSWORD=<password-portale-ps>
ALLOGGIATI_WS_KEY=<chiave-web-service-ps>

# IMPORTANTE: impostare false solo dopo collaudo dry-run
ALLOGGIATI_DRY_RUN=false

# CORS — dominio del frontend in produzione
GW_CORS_ALLOWED_ORIGINS=https://pms.tuohotel.com
```

---

## 3. Differenze sviluppo vs produzione

| Aspetto | Sviluppo | Produzione |
|---|---|---|
| `ALLOGGIATI_DRY_RUN` | `true` (sicuro) | `false` (invio reale PS) |
| Frontend URL | `http://localhost:5173` | `https://pms.tuohotel.com` |
| CORS origin | `http://localhost:5173` | dominio HTTPS del frontend |
| Swagger UI | Accessibile (dev) | Disabilitato (`api-gateway-prod.yml`) |
| HTTPS | Non richiesto in dev | **Obbligatorio** in produzione |
| Credenziali admin default | Cambio obbligatorio al primo login | **Mai usare `admin`/`password` in prod** |
| Log level | `DEBUG` (config dev) | `INFO` o `WARN` |

---

## 4. Avvio del sistema

**Importante — hardening delle porte in produzione.** `docker-compose.yml` da solo
espone TUTTE le porte sull'host (comodo in sviluppo, non sicuro in produzione).
Il file `docker-compose.prod.yml` è un override che azzera le porte esposte di
ogni servizio interno (DB, Redis, Prometheus/Grafana/Zipkin/Loki, tutti i
backend) lasciando pubblici solo `:80` (frontend) e `:8080` (API Gateway). Va
**sempre** combinato col flag `--profile observability` (altrimenti Alertmanager/
Grafana/Loki/Prometheus non partono affatto — sono opt-in). Il backup automatico
locale (WAL archiving + pgBackRest) non richiede più un profilo: è parte del
servizio `postgres` sempre attivo — vedi §9.

```bash
# Avvio produzione — hardening porte + osservabilità
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  --profile observability up -d

# Verifica che tutti i container siano healthy (attesa ~90 secondi)
watch docker compose ps

# Verifica log di startup
docker compose logs --tail=50 api-gateway
```

Un `docker compose up -d` semplice (senza `-f docker-compose.prod.yml` e senza
i `--profile`) avvia solo i 11 servizi core con **tutte le porte esposte
sull'host** e **senza** monitoring/backup — va bene per sviluppo/test, non per
un deploy di produzione reale.

Al primo avvio:
- Flyway esegue le migration su tutti i 5 database (frontdesk-service ne
  consolida 3 ex-separati — ADR-001, `docs/BRANCH_STRATEGY.md`)
- `AlloggiatiLookupDataLoader` scarica le tabelle di riferimento dal portale PS
  (richiede connettività internet verso `alloggiatiweb.poliziadistato.it`)
- L'account admin default viene creato con `mustChangePassword=true`

---

## 5. Configurazione HTTPS e reverse proxy (nginx)

In produzione, nginx davanti al gateway è **fortemente consigliato** per:
- Terminazione TLS/SSL (certificati Let's Encrypt)
- Compressione gzip
- Caching asset statici

### 5.1 Installare nginx e Certbot

```bash
sudo apt install nginx certbot python3-certbot-nginx -y
```

### 5.2 Configurazione nginx

```nginx
# /etc/nginx/sites-available/hotel-pms
server {
    listen 80;
    server_name pms.tuohotel.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl http2;
    server_name pms.tuohotel.com;

    ssl_certificate /etc/letsencrypt/live/pms.tuohotel.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/pms.tuohotel.com/privkey.pem;

    # API Gateway (microservizi)
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # Frontend React (Nginx container)
    location / {
        proxy_pass http://localhost:80;
        proxy_set_header Host $host;
    }
}
```

```bash
# Attivare il sito
sudo ln -s /etc/nginx/sites-available/hotel-pms /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# Ottenere certificato SSL gratuito (Let's Encrypt)
sudo certbot --nginx -d pms.tuohotel.com

# Il certificato si rinnova automaticamente ogni 90 giorni
# Verificare il rinnovo automatico:
sudo certbot renew --dry-run
```

---

## 6. Porte da esporre al pubblico

Con `docker-compose.prod.yml` (§4) questo è già enforced a livello Docker, non
solo firewall — i servizi elencati come "No" non hanno proprio una porta
pubblicata sull'host, indipendentemente dal firewall.

| Porta | Servizio | Esporre al pubblico |
|---|---|---|
| 80 | nginx HTTP | Sì (redirect a 443) |
| 443 | nginx HTTPS | Sì |
| 8080 | API Gateway | No — accessibile solo via nginx |
| 5432 | PostgreSQL | **No — mai** |
| 6379 | Redis | **No — mai** |
| 9090 | Prometheus | No — solo LAN interna |
| 3000 | Grafana | No — solo LAN interna (o VPN) |
| 9411 | Zipkin | No — solo LAN interna |
| 9093 | Alertmanager | No — solo LAN interna |
| 8081/8083/8085/8086/8087/8088 | Microservizi (frontdesk/guest/billing/fb/auth/notification) | **No — mai** |
| 8888 | Config Server | **No — mai** |
| 8090 | Management/Actuator (tutti i backend) | **No — mai pubblicata di default**, nemmeno in dev — vedi `docs/OPERATIONS_RUNBOOK.md §1` per come interrogarla da dentro la rete Docker |

### Configurazione firewall (ufw)

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp      # SSH
sudo ufw allow 80/tcp      # HTTP (redirect)
sudo ufw allow 443/tcp     # HTTPS
sudo ufw enable
sudo ufw status
```

---

## 7. Aggiornamento a una nuova versione

```bash
# 1. Backup del DB (obbligatorio prima di ogni aggiornamento)
docker exec hotel_postgres pg_dumpall -U postgres > "backup-pre-update-$(date +%Y%m%d).sql"

# 2. Pull del codice aggiornato
git pull origin main

# 3. Rebuild delle immagini Docker
docker compose build

# 4. Aggiornamento rolling (tempo di downtime < 30 secondi) — stessi flag di §4
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  --profile observability up -d

# 5. Verifica post-aggiornamento
docker compose ps
docker exec api-gateway wget -qO- http://localhost:8090/actuator/health | grep '"status":"UP"'
```

Le migration Flyway vengono eseguite automaticamente.
Se una migration fallisce, il servizio non si avvia — vedi `docs/OPERATIONS_RUNBOOK.md §6`.

---

## 8. Monitoraggio e alerting

### Stack disponibile

| Tool | URL | Scopo |
|---|---|---|
| Grafana | `http://localhost:3000` | Dashboard metriche e log |
| Prometheus | `http://localhost:9090` | Metriche raw |
| Zipkin | `http://localhost:9411` | Distributed tracing |
| Spring Actuator | interrogabile solo da dentro la rete Docker (porta 8090, mai pubblicata) — vedi `docs/OPERATIONS_RUNBOOK.md §1` | Health check singolo servizio |

> **Alert rule:** 6 alert Prometheus sono già configurate (`docker/prometheus/alert_rules.yml`):
> ServiceDown, HighErrorRate, HighLatencyP99, JvmHeapHigh, CircuitBreakerOpen,
> DbConnectionPoolNearExhaustion. Richiedono il profilo `observability` (§4).
> A queste si aggiunge `BackupCycleFailed`, inviato **direttamente** ad Alertmanager
> dal container `postgres` (non una regola Prometheus — push via API su fallimento
> di `archive-push`/`backup`/`check` di pgBackRest, §9).
> **Da fare prima del go-live**: il receiver Alertmanager di default è `null`
> (nessuna notifica esce, solo visibili su `http://localhost:9093`) — configurare
> un receiver reale (email/Slack/PagerDuty) in `docker/alertmanager/alertmanager.yml`
> se si vuole essere avvisati attivamente, non solo poter controllare la UI.

### Health check rapido da cron

```bash
# Aggiungere a crontab per verifica ogni 5 minuti (eseguito da dentro l'host
# Docker, non serve esporre la porta di management)
*/5 * * * * docker exec api-gateway wget -qO- http://localhost:8090/actuator/health > /dev/null || \
  echo "API Gateway DOWN $(date)" >> /var/log/hotel-pms-health.log
```

---

## 9. Backup automatico

**Sempre attivo**, nessun profilo da abilitare: il container `hotel_postgres`
(immagine custom, `docker/postgres/`) integra **pgBackRest** — WAL archiving
continuo (RPO in minuti, non più un dump giornaliero fisso) più backup
full/incrementali schedulati, sul volume dedicato `pgbackrest_repo`. Nessun
cron da configurare a mano. Il vecchio container `hotel_db_backup`
(`pg_dumpall` ogni 24h) non esiste più — vedi `backup/DECISIONS.md §3.5`.

```bash
# Stato del repository di backup (catena full/incr, WAL, ultimo successo)
docker exec hotel_postgres gosu postgres pgbackrest --stanza=hotel-pms info

# Verifica rapida (archive_command + repo raggiungibili)
docker exec hotel_postgres gosu postgres pgbackrest --stanza=hotel-pms check
```

Off-site (repo2, Backblaze B2) resta opt-in — impostare `S3_BUCKET` e le
altre variabili `S3_*` in `.env` (già configurate se il bucket `hotel-pms-backups`
è stato provisionato in una sessione precedente), più `PGBACKREST_CIPHER_PASS`
per cifrare entrambi i repository (`openssl rand -base64 48`, **da trattare
come `POSTGRES_PASSWORD`**: a differenza del vecchio `AGE_RECIPIENT`, questa è
una passphrase simmetrica, non solo una chiave pubblica).

Dettagli completi (restore, PITR, drill off-site) in
`docs/OPERATIONS_RUNBOOK.md §5`.

---

## 10. Checklist go-live

- [ ] `.env` configurato con credenziali reali (non placeholder)
- [ ] `INTERNAL_HMAC_SECRET` generato con setup script (≥32 char)
- [ ] `ALLOGGIATI_DRY_RUN=true` per il collaudo iniziale
- [ ] Stack avviato: tutti i container `(healthy)`
- [ ] Certificato SSL valido (nginx + Let's Encrypt)
- [ ] Firewall configurato — solo 80/443 esposti
- [ ] Login come admin → cambio password obbligatorio completato
- [ ] Profilo Hotel configurato (nome, indirizzo, P.IVA, CF)
- [ ] Tipi camera e camere create
- [ ] Credenziali Alloggiati PS testate con DRY_RUN=true → log `SUBMISSION_SUCCESS | operation=Test`
- [ ] Backup manuale eseguito e verificato
- [ ] Crontab backup giornaliero configurato
- [ ] `ALLOGGIATI_DRY_RUN=false` impostato per produzione → frontdesk-service riavviato
- [ ] Primo check-in reale → log `SUBMISSION_SUCCESS | operation=Send` → schedina su portale PS verificata
