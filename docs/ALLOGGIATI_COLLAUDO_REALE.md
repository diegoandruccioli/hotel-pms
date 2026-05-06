# Collaudo Portale Alloggiati Web — Procedura con Credenziali Reali

**Prerequisito:** Credenziali PS reali (`ALLOGGIATI_USERNAME`, `ALLOGGIATI_PASSWORD`, `ALLOGGIATI_WS_KEY`).  
**Modalità obbligatoria:** `ALLOGGIATI_DRY_RUN=true` per tutto il collaudo.

---

## 1. Checklist pre-collaudo

| Step | Azione | Verifica |
|---|---|---|
| 1.1 | Impostare le 3 credenziali nel `.env` | `docker-compose config \| grep ALLOGGIATI` mostra i valori reali (non placeholder) |
| 1.2 | Verificare che `ALLOGGIATI_DRY_RUN=true` | `docker-compose config \| grep DRY_RUN` → `true` |
| 1.3 | Avviare lo stack | `docker-compose up -d` — tutti i container `healthy` |
| 1.4 | Verificare il popolamento delle lookup tables | Log `stay-service`: `Loaded N stati/comuni/tipdoc from Portale Alloggiati` |
| 1.5 | Verificare assenza di placeholder | `docker logs stay-service 2>&1 \| grep -i "placeholder\|ci_placeholder"` → nessun output |
| 1.6 | Verificare HMAC check | `docker logs api-gateway 2>&1 \| grep HMAC_SECRET` → `HMAC_SECRET_OK \| length=N` |

---

## 2. Dati di test minimi per una schedina valida

Preparare un ospite con i seguenti campi (tutti codici ufficiali PS):

| Campo | Valore di esempio | Nota |
|---|---|---|
| `tipoAlloggiato` | `16` (OSPITE_SINGOLO) | Scegliere il tipo più semplice |
| `cognome` | `Rossi` | |
| `nome` | `Mario` | |
| `sesso` | `1` (Maschile) | |
| `dataNascita` | `20/05/1985` | |
| `comuneNascita` | `058091000` (Roma) | Codice 9 cifre dalla lookup table |
| `provinciaNascita` | `RM` | |
| `statoNascita` | `100000100` (Italia) | |
| `cittadinanza` | `100000100` (Italia) | |
| `tipoDocumento` | `PASOR` (Passaporto ordinario) | Codice dalla lookup table |
| `numeroDocumento` | `AA1234567` | Max 20 caratteri |
| `luogoRilascioDoc` | `058091000` (Roma) | Codice comune 9 cifre |

---

## 3. Procedura di invio in DRY_RUN

```bash
# 1. Completare il check-in dell'ospite di test nel frontend
# (assicurarsi che i campi Alloggiati siano compilati correttamente)

# 2. Attivare alloggiatiAutoSend per l'hotel (dal frontend: Settings → Alloggiati Auto-Send ON)
# Oppure chiamare direttamente l'endpoint manuale:
curl -s -b "jwt=<TOKEN>" -X POST \
  "http://localhost:8080/api/v1/stays/reports/alloggiati/submit?date=$(date +%Y-%m-%d)"
```

---

## 4. Log da monitorare (in ordine cronologico)

Tutti i log rilevanti del `stay-service` hanno prefisso `[STAY]`.

```bash
docker logs stay-service --follow 2>&1 | grep -E "ALLOGGIATI|SOAP|TOKEN|SUBMISSION"
```

### Sequenza attesa di successo

```
[STAY] ALLOGGIATI_SUBMISSION_START | date=2026-05-XX | dryRun=true
[STAY] ALLOGGIATI_SUBMISSION_RECORDS | date=2026-05-XX | count=1
[STAY] ALLOGGIATI_TOKEN_OBTAINED
[STAY] ALLOGGIATI_SUBMISSION_SUCCESS | date=2026-05-XX | operation=Test | rows=1
[STAY] ALLOGGIATI_SENT | stayId=... | date=2026-05-XX
```

### Sequenza di fallimento (e causa)

| Pattern log | Causa probabile | Azione |
|---|---|---|
| `ALLOGGIATI_SOAP_ERROR \| action=AlloggiatiService/GenerateToken` | Credenziali errate o WsKey scaduta | Verificare username/password/wsKey nel portale PS |
| `ALLOGGIATI_SOAP_ERROR` + `Connection refused` | Portale PS non raggiungibile | Verificare connettività internet dal container |
| `ALLOGGIATI_SUBMISSION_FAILED \| code=... \| desc=...` | Portale ha accettato la chiamata ma rifiutato il contenuto | Vedere §5 — Errori portale |
| `GenerateToken returned empty token` | Risposta SOAP inattesa dal portale | Verificare namespace e SOAPAction (vedere §6) |
| `ALLOGGIATI_SUBMISSION_SKIPPED \| reason=EMPTY_REPORT` | Nessun check-in nella data specificata | Verificare che il check-in sia avvenuto nella data corretta |

---

## 5. Interpretazione errori portale (ErroreCod)

| ErroreCod | Significato | Fix |
|---|---|---|
| `E001` | Formato record non valido (lunghezza, caratteri) | Eseguire `awk '{ if (length($0) != 168) print NR": "length($0)" chars" }' alloggiati-DATE.txt` — tutte le righe devono essere esattamente 168 |
| `E002` | Codice stato/comune/tipdoc non riconosciuto | Verificare che i codici inseriti nel check-in corrispondano ai valori delle lookup tables; aggiornare le tabelle se necessario |
| `E003` | WsKey non valida o scaduta | Rigenerare la WsKey dal portale PS (account → Chiave Web Service → Genera nuova chiave) |
| `E004` | Utente non abilitato al Web Service | Contattare la Questura di competenza per abilitare il Web Service |
| `Namespace fault` | Namespace SOAP non corrispondente | Aggiornare `ALLOGGIATI_WS_NAMESPACE` con il valore estratto dal WSDL (vedere §6) |

---

## 6. Validazione del payload SOAP

### 6.1 Controllare il namespace effettivo

Se il portale risponde con un errore di namespace, fare una richiesta diretta al WSDL e confrontare il `targetNamespace`:

```bash
curl -s "https://alloggiatiweb.poliziadistato.it/service/Service.asmx?WSDL" \
  | grep -o 'targetNamespace="[^"]*"' | head -3
```

Il valore estratto deve corrispondere all'env `ALLOGGIATI_WS_NAMESPACE`.

### 6.2 Validare il tracciato scaricato

```bash
# Scaricare il file e verificarlo prima dell'invio automatico
curl -s -b "jwt=<TOKEN>" \
  "http://localhost:8080/api/v1/stays/reports/alloggiati?date=$(date +%Y-%m-%d)" \
  -o /tmp/alloggiati-test.txt

# Lunghezza di ogni riga (deve essere 168)
awk '{ if (length($0) != 168) print "ERRORE riga "NR": "length($0); else n++ } END { print n" righe valide" }' /tmp/alloggiati-test.txt

# Verifica encoding (deve essere UTF-8)
file /tmp/alloggiati-test.txt

# Verifica terminatore (CRLF tra righe, assente sull'ultima)
hexdump -C /tmp/alloggiati-test.txt | tail -3
```

### 6.3 SOAPAction atteso

Il codice invia:
```
SOAPAction: "AlloggiatiService/GenerateToken"
SOAPAction: "AlloggiatiService/Test"       (dry-run)
SOAPAction: "AlloggiatiService/Send"       (production)
```

Se il portale rifiuta queste azioni, leggere i valori corretti dal WSDL:

```bash
curl -s "https://alloggiatiweb.poliziadistato.it/service/Service.asmx?WSDL" \
  | grep -o 'soapAction="[^"]*"'
```

Aggiornare le costanti `SOAP_ACTION_*` in `AlloggiatiWebSenderServiceImpl.java` se i valori differiscono.

---

## 7. Criteri di accettazione

Il collaudo è superato se **tutte** le seguenti condizioni sono verificate:

- [ ] Log `ALLOGGIATI_TOKEN_OBTAINED` presente → autenticazione in due passi funzionante
- [ ] Log `ALLOGGIATI_SUBMISSION_SUCCESS | operation=Test` presente → portale ha validato il payload
- [ ] Badge "PS Portal" verde sulla riga soggiorno nel frontend
- [ ] Download del file `.txt` produce record da 168 caratteri con CRLF corretto
- [ ] Nessun `ALLOGGIATI_SOAP_ERROR` nei log
- [ ] Lookup tables popolate: `docker exec postgres psql -U postgres -d hotel_stay -c "SELECT COUNT(*) FROM alloggiati_comuni;"` → > 7000

---

## 8. Passaggio a produzione (dopo collaudo superato)

Solo dopo che tutti i criteri di accettazione sono verificati:

```bash
# Nel file .env di produzione
ALLOGGIATI_DRY_RUN=false

# Riavviare lo stay-service
docker restart stay-service

# Verificare il primo invio reale
docker logs stay-service --follow 2>&1 | grep "ALLOGGIATI_SUBMISSION_SUCCESS"
# Atteso: operation=Send (non più operation=Test)

# Verificare sul portale PS
# Navigare in: Portale PS → Archivio → confermare presenza della schedina
```

**Rischio residuo:** il namespace SOAP (`ALLOGGIATI_WS_NAMESPACE`) e i SOAPAction (`AlloggiatiService/GenerateToken`) sono stati inferiti dal WSDL senza una chiamata reale con credenziali. Se il portale risponde con un fault SOAP, aggiornare queste costanti seguendo la procedura §6.1 e §6.3.
