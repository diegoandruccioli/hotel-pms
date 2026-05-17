# Deployment Guide — Hotel PMS

**Versione:** 1.0 — 2026-05-17  
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

# Docker Compose v2 (incluso in Docker Desktop o installare il plugin)
docker compose version   # deve mostrare v2.x

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

```bash
# Avvio completo in background
docker compose up -d

# Verifica che tutti i container siano healthy (attesa ~90 secondi)
watch docker compose ps

# Verifica log di startup
docker compose logs --tail=50 api-gateway
```

Al primo avvio:
- Flyway esegue le migration su tutti i 9 database
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
| 8081-8087 | Microservizi | **No — mai** |

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
docker exec postgres pg_dumpall -U postgres > "backup-pre-update-$(date +%Y%m%d).sql"

# 2. Pull del codice aggiornato
git pull origin main

# 3. Rebuild delle immagini Docker
docker compose build

# 4. Aggiornamento rolling (tempo di downtime < 30 secondi)
docker compose up -d

# 5. Verifica post-aggiornamento
docker compose ps
curl -s http://localhost:8080/actuator/health | grep '"status":"UP"'
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
| Spring Actuator | `http://localhost:<port>/actuator/health` | Health check singolo servizio |

> **Alert rule:** nessuna alert rule è configurata nell'installazione base.
> Configurare le alert rule Grafana prima del go-live in produzione
> — vedi `docs/ROADMAP.md §P4`.

### Health check rapido da cron

```bash
# Aggiungere a crontab per verifica ogni 5 minuti
*/5 * * * * curl -sf http://localhost:8080/actuator/health > /dev/null || \
  echo "API Gateway DOWN $(date)" >> /var/log/hotel-pms-health.log
```

---

## 9. Backup automatico (da configurare)

Un backup automatico schedulato non è incluso nell'installazione base
(vedi `docs/ROADMAP.md §P3`). Nel frattempo, eseguire manualmente:

```bash
# Aggiungere a crontab per backup giornaliero alle 3:00
0 3 * * * docker exec postgres pg_dumpall -U postgres | \
  gzip > /backup/hotel-pms/hotel-pms-$(date +\%Y\%m\%d).sql.gz && \
  find /backup/hotel-pms/ -name "*.sql.gz" -mtime +30 -delete
```

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
- [ ] `ALLOGGIATI_DRY_RUN=false` impostato per produzione → stay-service riavviato
- [ ] Primo check-in reale → log `SUBMISSION_SUCCESS | operation=Send` → schedina su portale PS verificata
