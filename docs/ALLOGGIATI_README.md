# Portale Alloggiati Web — Guida Operativa

Questa guida descrive come configurare, usare e diagnosticare l'integrazione del PMS con il Portale Alloggiati Web della Polizia di Stato.

---

## 1. Panoramica

L'integrazione gestisce tre operazioni:
1. **Generazione del file .txt** (tracciato 168 caratteri per record, formato PS)
2. **Download manuale** del file per caricamento su portale
3. **Invio automatico via SOAP** al portale PS dopo ogni check-in (opzionale)

---

## 2. Credenziali e variabili d'ambiente

> Queste variabili DEVONO essere impostate via `.env` in produzione.
> Non inserirle mai nel codice o nei file YAML sotto controllo di versione.

| Variabile | Descrizione | Obbligatoria |
|---|---|---|
| `ALLOGGIATI_USERNAME` | Username del portale PS (es. `HOTELALFA01`) | Sì |
| `ALLOGGIATI_PASSWORD` | Password del portale PS | Sì |
| `ALLOGGIATI_WS_KEY` | Chiave Web Service (vedi §2.1) | Sì |
| `ALLOGGIATI_SERVICE_URL` | Endpoint SOAP (default: `/service/Service.asmx`) | No (default OK) |
| `ALLOGGIATI_WS_NAMESPACE` | Namespace SOAP (default già configurato) | No |
| `ALLOGGIATI_DRY_RUN` | `true` = chiama `Test` invece di `Send` (default: `true`) | No |

### 2.1 Come ottenere la Web Service Key

1. Accedere al portale: https://alloggiatiweb.poliziadistato.it
2. Cliccare sull'icona account in alto a destra
3. Selezionare **"Chiave Web Service"**
4. Cliccare **"Genera nuova chiave"**
5. Copiare la chiave nel file `.env`: `ALLOGGIATI_WS_KEY=xxxx-xxxx-xxxx`

La chiave può essere rigenerata se compromessa; la vecchia chiave viene invalidata immediatamente.

### 2.2 Esempio di file `.env` (non committare questo file)

```
ALLOGGIATI_USERNAME=HOTELALFA01
ALLOGGIATI_PASSWORD=MySecurePassword123
ALLOGGIATI_WS_KEY=abcd-1234-efgh-5678
ALLOGGIATI_DRY_RUN=false
```

---

## 3. Modalità operative

### 3.1 Dry-run (sviluppo/test)

Con `ALLOGGIATI_DRY_RUN=true` (default), l'invio automatico chiama `Test` invece di `Send`: il portale PS valida i dati ma non li registra definitivamente. Usare sempre questa modalità fuori produzione.

### 3.2 Produzione

Impostare `ALLOGGIATI_DRY_RUN=false` solo sull'ambiente di produzione con credenziali reali.

---

## 4. Flusso operativo standard

### 4.1 Generazione e download manuale (raccomandato per il primo periodo)

1. Completare i check-in del giorno dalla pagina Check-in del frontend
2. Da **Soggiorni → Download Alloggiati**, selezionare la data e cliccare il pulsante
3. Viene scaricato `alloggiati-YYYY-MM-DD.txt`
4. Verificare il file (vedi §5)
5. Accedere al portale PS, sezione **"Invio File"**
6. Caricare il file e attendere la conferma

### 4.2 Invio automatico

Con `ALLOGGIATI_DRY_RUN=false`, ogni check-in completato con successo chiama automaticamente il portale. Lo stato di ogni invio è visibile nella colonna **"PS Portal"** nella tabella soggiorni (icona verde = inviato).

---

## 5. Verifica del file generato

Prima di caricare sul portale, verificare:

| Check | Regola |
|---|---|
| **Lunghezza record** | Ogni record è esattamente 168 caratteri |
| **Terminatori** | CR+LF (`\r\n`) tra i record; nessun CRLF finale sull'ultimo |
| **Encoding** | UTF-8 senza BOM |
| **Limite righe** | Massimo 1000 record per file |
| **Ordinamento** | All'interno di ogni soggiorno: CAPOFAMIGLIA (17) / CAPOGRUPPO (18) → FAMILIARE (19) / MEMBRO_GRUPPO (20) → OSPITE_SINGOLO (16) |

### 5.1 Verifica rapida da terminale

```bash
# Contare i record (deve corrispondere al numero di ospiti)
wc -l alloggiati-2026-04-15.txt

# Verificare lunghezza di ciascuna riga (deve essere 168)
awk '{ if (length($0) != 168) print NR": "length($0)" chars" }' alloggiati-2026-04-15.txt
```

---

## 6. Interpretazione dei log

I log del `stay-service` contengono prefisso `[STAY]`. Messaggi rilevanti:

| Pattern log | Significato | Azione |
|---|---|---|
| `ALLOGGIATI_SENT` | Invio riuscito | Nessuna |
| `ALLOGGIATI_SEND_FAILED` | Invio fallito (portale PS non raggiungibile) | Verificare connettività e credenziali; ripetere manualmente |
| `ALLOGGIATI_SUBMISSION_SUCCESS` | SOAP `Send`/`Test` accettato dal portale | Nessuna |
| `ALLOGGIATI_SUBMISSION_FAILED` | Portale ha risposto `esito=false` | Vedere `ErroreCod` e `ErroreDes` nel log per dettaglio |
| `ALLOGGIATI_TOKEN_OBTAINED` | Token di sessione ottenuto dal portale | Nessuna |
| `ALLOGGIATI_SOAP_ERROR` | Errore HTTP/TLS nel contattare il portale | Verificare URL, certificati, firewall |
| `[REPORT] Invalid citizenship code` | Codice stato non presente nella lookup table | Controllare i dati del check-in; tabelle forse da aggiornare |
| `[REPORT] placeOfBirth not found` | Codice luogo di nascita non trovato nelle lookup | Inserire manualmente il codice corretto al check-in |
| `[REPORT] Comune ... is expired` | Comune cessato usato per luogo di nascita | Accettabile (persone nate in comuni ora soppressi) |

### 6.1 Errori di validazione dominio (HTTP 422)

| Codice errore | Causa | Soluzione |
|---|---|---|
| `ALLOGGIATI_FAMILIARE_WITHOUT_CAPO` | FAMILIARE registrato senza CAPOFAMIGLIA nello stesso soggiorno | Aggiungere il capofamiglia al check-in |
| `ALLOGGIATI_MEMBRO_WITHOUT_CAPO` | MEMBRO_GRUPPO senza CAPOGRUPPO | Aggiungere il capogruppo |
| `ALLOGGIATI_MULTIPLE_CAPOFAMIGLIA` | Due CAPOFAMIGLIA nello stesso soggiorno | Correggere il tipo alloggiato |
| `ALLOGGIATI_INVALID_DATES` | Data check-out precedente alla data di arrivo | Correggere le date della prenotazione |
| `ALLOGGIATI_ROW_LIMIT_EXCEEDED` | Più di 1000 record in un giorno | Inviare manualmente in più tranche per sotto-intervalli di ore |

---

## 7. Aggiornamento delle lookup tables

Le tabelle ufficiali (comuni, stati, documenti) vengono scaricate automaticamente dal portale PS al primo avvio del `stay-service`. Per forzare un aggiornamento:

```bash
# 1. Svuotare le tabelle nel DB
docker exec -it postgres psql -U postgres -d hotel_stay -c \
  "TRUNCATE alloggiati_stati, alloggiati_comuni, alloggiati_tipdoc;"

# 2. Riavviare lo stay-service
docker restart stay-service
# Il DataLoader rileva le tabelle vuote e ri-scarica i CSV dal portale PS
```

---

## 8. Test dell'integrazione sul portale reale

Procedura raccomandata per il collaudo prima del go-live:

1. Impostare `ALLOGGIATI_DRY_RUN=false` su un ambiente staging con credenziali reali
2. Effettuare un check-in di test con dati validi
3. Verificare nei log che appaia `ALLOGGIATI_SUBMISSION_SUCCESS | operation=Send`
4. Accedere al portale PS e confermare che la schedina sia presente nella sezione "Archivio"
5. Verificare che la colonna "PS Portal" nel frontend mostri l'icona verde
6. Se l'invio automatico non è desiderato: lasciare `ALLOGGIATI_DRY_RUN=true` e usare sempre il download manuale

---

## 9. Note tecniche

- **Protocollo**: SOAP 1.1 su HTTPS con TLS verificato (JVM truststore, nessun TrustAllCerts)
- **Autenticazione**: `GenerateToken(Utente, Password, WsKey)` → token di sessione, poi `Send(Utente, token, ElencoSchedine)`
- **Endpoint**: `https://alloggiatiweb.poliziadistato.it/service/Service.asmx`
- **WSDL**: `https://alloggiatiweb.poliziadistato.it/service/Service.asmx?WSDL`
- **Formato ElencoSchedine**: array di stringhe, ciascuna 168 caratteri esatti
- **Namespace SOAP**: configurabile via `ALLOGGIATI_WS_NAMESPACE` se il portale restituisce un errore di namespace
