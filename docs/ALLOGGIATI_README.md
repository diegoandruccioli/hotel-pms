# Portale Alloggiati Web — Guida Operativa

Questa guida descrive come configurare, usare e diagnosticare l'integrazione del PMS con il Portale Alloggiati Web della Polizia di Stato.

---

## 1. Panoramica

L'integrazione gestisce tre operazioni:
1. **Generazione del file .txt** (tracciato 168 caratteri per record, formato PS)
2. **Download manuale** del file per caricamento su portale
3. **Invio automatico via SOAP** al portale PS dopo ogni check-in (opzionale)

---

## 2. Credenziali — come le inserisce l'hotel (percorso consigliato)

**L'hotel inserisce le proprie credenziali direttamente dall'applicazione,
senza bisogno di accesso al server o a file di configurazione.**

1. Accedere come ADMIN o OWNER.
2. Andare su **Profilo Hotel** (menu utente → Profilo Hotel, o
   `/profile/hotel`).
3. Compilare i tre campi nella sezione Alloggiati: **Username**, **Password**,
   **Chiave Web Service** (WsKey) — vedi §2.2 per come ottenere la WsKey dal
   portale (Username e Password sono le stesse dell'account PS esistente).
4. Salvare.

Cosa succede dopo il salvataggio:
- Username, password e WsKey vengono **cifrati** prima di essere salvati nel
  database (AES-256-GCM) — mai in chiaro.
- Dopo il salvataggio, i campi password/WsKey **appaiono sempre vuoti** alla
  successiva apertura della pagina: è comportamento voluto (write-only), non
  un errore. Il sistema non li rimostra mai, nemmeno all'ADMIN che li ha
  inseriti. Per cambiarli, basta compilare di nuovo il campo — lasciarlo
  vuoto significa "non modificare" quello già salvato.
- Da quel momento l'hotel usa le **proprie** credenziali per ogni invio,
  indipendentemente dalle altre installazioni.

**Non serve mai comunicare queste credenziali a chi gestisce il software** —
l'hotel le inserisce da solo, direttamente sul portale, e nessun'altra
persona le vede mai.

### 2.1 Variabili d'ambiente (`.env`) — solo fallback, non per l'uso quotidiano

Le variabili sotto sono un **fallback globale a livello di installazione**,
usato solo se l'hotel non ha ancora compilato i campi in §2 sopra — pensate
per chi gestisce il server (accesso a `.env`), non per l'hotel stesso.
Se l'hotel ha già inserito le proprie credenziali da UI, queste variabili
non vengono più usate per quell'hotel.

> Queste variabili DEVONO essere impostate via `.env` in produzione se usate.
> Non inserirle mai nel codice o nei file YAML sotto controllo di versione.

| Variabile | Descrizione | Obbligatoria |
|---|---|---|
| `ALLOGGIATI_USERNAME` | Username del portale PS (es. `HOTELALFA01`) | Sì |
| `ALLOGGIATI_PASSWORD` | Password del portale PS | Sì |
| `ALLOGGIATI_WS_KEY` | Chiave Web Service (vedi §2.2) | Sì |
| `ALLOGGIATI_SERVICE_URL` | Endpoint SOAP (default: `/service/Service.asmx`) | No (default OK) |
| `ALLOGGIATI_WS_NAMESPACE` | Namespace SOAP (default già configurato) | No |
| `ALLOGGIATI_DRY_RUN` | `true` = chiama `Test` invece di `Send` (default: `true`) | No |

### 2.2 Come ottenere la Web Service Key

1. Accedere al portale: https://alloggiatiweb.poliziadistato.it
2. Cliccare sull'icona account in alto a destra
3. Selezionare **"Chiave Web Service"**
4. Cliccare **"Genera nuova chiave"**
5. Copiarla nel campo **Chiave Web Service** di Profilo Hotel (§2 — percorso
   consigliato per l'hotel), oppure in `ALLOGGIATI_WS_KEY` nel file `.env`
   (solo per chi usa il fallback §2.1, gestito da chi amministra il server).

La chiave può essere rigenerata se compromessa; la vecchia chiave viene invalidata immediatamente.

### 2.3 Esempio di file `.env` (fallback §2.1 — non committare questo file)

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

I log del `frontdesk-service` contengono prefisso `[STAY]`. Messaggi rilevanti:

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

Le tabelle ufficiali (comuni, stati, documenti) vengono scaricate automaticamente dal portale PS al primo avvio del `frontdesk-service`. Per forzare un aggiornamento:

```bash
# 1. Svuotare le tabelle nel DB
docker exec -it postgres psql -U postgres -d hotel_stay -c \
  "TRUNCATE alloggiati_stati, alloggiati_comuni, alloggiati_tipdoc;"

# 2. Riavviare lo frontdesk-service
docker restart frontdesk-service
# Il DataLoader rileva le tabelle vuote e ri-scarica i CSV dal portale PS
```

---

## 8. Test dell'integrazione sul portale reale

`ALLOGGIATI_DRY_RUN` è un'impostazione di sistema (chi gestisce
l'installazione, non l'hotel): di default resta `true` finché non si
decide esplicitamente di passare a invii reali. Con `DRY_RUN=true` il
sistema chiama comunque il portale PS reale (serve comunque che l'hotel
abbia già inserito le proprie credenziali in §2), ma con l'operazione
`Test` — valida i dati senza registrarli definitivamente. È il modo sicuro
per l'hotel di verificare da solo, con le proprie credenziali reali, che
tutto funzioni prima che chiunque decida di passare a `Send`.

**Cosa può verificare l'hotel da solo, senza bisogno di accesso al server:**

1. Inserire le proprie credenziali PS (§2).
2. Effettuare un check-in reale (o il primo check-in con il sistema).
3. Aprire **Soggiorni** e controllare la colonna "PS Portal": icona verde =
   invio riuscito, icona rossa = fallito (con possibilità di reinvio manuale).
4. Solo se si vuole la conferma definitiva sul portale PS: accedere a
   `alloggiatiweb.poliziadistato.it` — in modalità `Test` la schedina viene
   validata ma **non** compare nell'Archivio (è la modalità `Send` che
   registra davvero, vedi sotto).

**Passaggio a invio reale (`Send`) — decisione di chi gestisce
l'installazione, non dell'hotel:**

5. Solo dopo aver visto badge verdi in modalità `Test` per un periodo
   ragionevole, impostare `ALLOGGIATI_DRY_RUN=false` (richiede accesso al
   server/`.env`) — da quel momento ogni invio è una comunicazione ufficiale
   ai sensi del TULPS art. 109, verificabile nell'Archivio del portale PS.
6. Se l'invio automatico non è desiderato: lasciare `ALLOGGIATI_DRY_RUN=true`
   indefinitamente e usare sempre il download manuale del file .txt.

---

## 9. Note tecniche

- **Protocollo**: SOAP 1.1 su HTTPS con TLS verificato (JVM truststore, nessun TrustAllCerts)
- **Autenticazione**: `GenerateToken(Utente, Password, WsKey)` → token di sessione, poi `Send(Utente, token, ElencoSchedine)`
- **Endpoint**: `https://alloggiatiweb.poliziadistato.it/service/Service.asmx`
- **WSDL**: `https://alloggiatiweb.poliziadistato.it/service/Service.asmx?WSDL`
- **Formato ElencoSchedine**: array di stringhe, ciascuna 168 caratteri esatti
- **Namespace SOAP**: configurabile via `ALLOGGIATI_WS_NAMESPACE` se il portale restituisce un errore di namespace
