# Piano di Collaudo Reale — Integrazione Alloggiati Web (Polizia di Stato)

**Versione:** 2.0  
**Classificazione:** Operativo — uso interno pre-pilot  
**Autore:** Team Hotel PMS  
**Ultima revisione:** 2026-05-06  
**Validità:** fino al completamento del pilot con albergatori reali

> **Avvertenza:** Questo documento descrive procedure che interagiscono con il portale ufficiale della Polizia di Stato (`alloggiatiweb.poliziadistato.it`). Ogni invio in modalità `Send` (non `Test`) produce una comunicazione ufficiale ai sensi del TULPS. Non eseguire mai un invio reale con dati fittizi su credenziali di produzione.

---

## 1. Scopo del Collaudo

Il sistema Hotel PMS deve trasmettere le schedine di pubblica sicurezza al portale Alloggiati Web della Polizia di Stato entro le ore 24:00 del giorno di arrivo di ogni ospite, come previsto dall'art. 109 del T.U.L.P.S. (R.D. 18 giugno 1931, n. 773) e dalla circolare del Ministero dell'Interno n. 557/PAS/U/001596/10000/0/4 del 2004.

Questo collaudo verifica che il sistema:

1. Generi correttamente il tracciato a 168 caratteri nel formato prescritto dal portale PS.
2. Completi il protocollo SOAP a due passi (`GenerateToken` → `Send`/`Test`) con i certificati e le credenziali dell'hotel.
3. Non blocchi il flusso di check-in operativo in caso di indisponibilità del portale esterno.
4. Registri ogni tentativo con correlazione ID tracciabile, per audit e supporto.
5. Esponga al front desk lo stato di invio in modo univoco (badge verde/rosso).

Il collaudo è **obbligatorio** prima del pilot perché un errore silenzioso nell'invio espone l'albergatore a sanzioni amministrative (art. 17-bis T.U.L.P.S.) e a segnalazione da parte della Questura di competenza.

---

## 2. Ambito del Test

### In scope

| Area | Dettaglio |
|---|---|
| Flusso check-in con prenotazione | Check-in a partire da una prenotazione in stato CONFIRMED o PENDING |
| Flusso walk-in | Check-in senza prenotazione con data di checkout manuale |
| Generazione file Alloggiati | `GET /api/v1/stays/reports/alloggiati?date=YYYY-MM-DD` — testo 168-char |
| Invio SOAP dry-run | `POST .../submit?date=YYYY-MM-DD` con `ALLOGGIATI_DRY_RUN=true` — operazione `Test` |
| Invio SOAP reale | Come sopra con `ALLOGGIATI_DRY_RUN=false` — operazione `Send` |
| Validazione tracciato | Lunghezza righe, encoding, CRLF solo tra le righe (non in coda), max 1000 righe |
| Lookup tables | Codici stati, comuni, tipdoc — caricamento da CSV portale al primo avvio |
| Famiglie e gruppi | Ordinamento CAPOFAMIGLIA→FAMILIARE e CAPOGRUPPO→MEMBRO_GRUPPO, doc fields vuoti per componenti |
| Ospiti stranieri | Codice stato di nascita/rilascio documento ≠ Italia |
| Fallback e resilienza | Check-in completo anche se l'invio al portale fallisce |
| Correlazione log | Ogni operazione loggata con `X-Correlation-ID` tracciabile end-to-end |
| Badge alloggiatiSent | Frontend mostra lo stato di invio su ogni riga soggiorno |
| Toggle alloggiatiAutoSend | Il flag hotel-level attiva/disattiva l'invio automatico al check-in |

### Fuori scope

| Area | Motivo |
|---|---|
| Invio SCI (Commissariato di PS on-line) | Sistema diverso, non integrato in questa versione |
| Invio regionale alternativo (es. Sardegna) | Portale differente; da valutare in fase 2 |
| Notifica C.I.A. (Agenzia Entrate turismo) | Non richiesto per il pilot |
| Firma digitale dell'esportazione JSON | Non obbligatoria per comunicazione PS |
| Integrazione con PMS esterni | Non prevista nel piano pilot |

---

## 3. Prerequisiti

### 3.1 Ambiente tecnico

| Prerequisito | Verifica |
|---|---|
| Docker Engine ≥ 24 + Compose v2 attivi | `docker info` e `docker compose version` |
| Stack avviato e tutti i container healthy | `docker compose ps` — tutti `(healthy)` o `Up` |
| Rete internet dal container `stay-service` raggiungibile | `docker exec stay-service curl -s -o /dev/null -w "%{http_code}" https://alloggiatiweb.poliziadistato.it` → `200` o `302` |
| PostgreSQL `hotel_stay` DB attivo e migrazioni V1-V8 applicate | `docker exec postgres psql -U postgres -d hotel_stay -c "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"` → `8` |
| Lookup tables popolate | `docker exec postgres psql -U postgres -d hotel_stay -c "SELECT COUNT(*) FROM alloggiati_comuni;"` → ≥ 7800 |

### 3.2 Variabili di configurazione

Tutte le variabili seguenti devono essere valorizzate nel file `.env` nella root del progetto prima di avviare lo stack.

| Variabile | Valore richiesto | Verifica |
|---|---|---|
| `ALLOGGIATI_USERNAME` | Account PS reale (non placeholder) | `docker compose config \| grep ALLOGGIATI_USERNAME` — non deve contenere `placeholder` o essere vuoto |
| `ALLOGGIATI_PASSWORD` | Password account PS | Come sopra |
| `ALLOGGIATI_WS_KEY` | Chiave Web Service generata dal portale PS | Come sopra — stringa alfanumerica ≥ 20 caratteri |
| `ALLOGGIATI_DRY_RUN` | `true` per dry-run, `false` per invio reale | `docker compose config \| grep DRY_RUN` |
| `ALLOGGIATI_WS_NAMESPACE` | Namespace SOAP dal WSDL | Deve corrispondere a `targetNamespace` estratto dal WSDL (vedere §6.1) |
| `HMAC_SECRET` | Segreto HMAC ≥ 32 caratteri, non placeholder | `docker logs api-gateway 2>&1 \| grep HMAC_SECRET_OK` |

### 3.3 Dati di test minimi per una schedina valida

Preparare nel sistema almeno **tre ospiti di test** con i seguenti profili, usando codici ufficiali dalle lookup tables:

**Ospite A — Italiano singolo (caso base)**

| Campo | Valore | Codice usato |
|---|---|---|
| tipoAlloggiato | OSPITE_SINGOLO | `16` |
| cognome / nome | Rossi / Mario | |
| sesso | Maschile | `1` |
| dataNascita | 1985-05-20 | |
| comuneNascita | Roma | `058091000` |
| statoNascita | Italia | `100000100` |
| cittadinanza | Italia | `100000100` |
| tipoDocumento | Passaporto ordinario | `PASOR` |
| numeroDocumento | AA1234567 | max 20 char |
| luogoRilascioDoc | Roma | `058091000` |

**Ospite B — Straniero (caso extracomunitario)**

| Campo | Valore | Codice |
|---|---|---|
| tipoAlloggiato | OSPITE_SINGOLO | `16` |
| cognome / nome | Smith / John | |
| statoNascita | Stati Uniti | codice da lookup `alloggiati_stati` |
| cittadinanza | USA | stesso codice |
| tipoDocumento | Passaporto ordinario | `PASOR` |
| luogoRilascioDoc | USA | codice `statoNascita` (non codice comune) |

**Ospite C — Nucleo familiare (2 persone)**

- Ospite C1: `tipoAlloggiato = CAPOFAMIGLIA` (17) — tutti i campi documento compilati
- Ospite C2: `tipoAlloggiato = FAMILIARE` (19) — campi documento devono essere vuoti nel file

### 3.4 Account e ruoli

| Ruolo | Perché | Azione richiesta |
|---|---|---|
| ADMIN | Accesso alla pagina Hotel Profile, toggle alloggiatiAutoSend, invio manuale | Credenziali `admin / password` (o account dedicato) |
| RECEPTIONIST | Esecuzione check-in | Account separato raccomandato per test di segregazione |

### 3.5 Credenziali Polizia di Stato

- Account struttura ricettiva attivo sul portale `alloggiatiweb.poliziadistato.it`
- Chiave Web Service generata (sezione "Web Service" → "Genera chiave")
- Nota: la WsKey ha scadenza; verificare la data di scadenza prima del collaudo

### 3.6 Backup e sicurezza

- Eseguire `docker exec postgres pg_dump -U postgres hotel_stay > backup-pre-collaudo.sql` prima di ogni sessione di collaudo reale
- Avere accesso SSH al server di produzione per interventi di emergenza
- Il collaudo deve essere pianificato in orari non operativi se si usa il database di produzione

---

## 4. Matrice dei Test

> **Legenda severità:** BLOCCANTE = pilot non può partire; CRITICO = pilot può partire solo con piano di mitigazione documentato; ALTO = deve essere risolto entro il primo giorno operativo; MEDIO = da risolvere nella settimana del pilot.

| ID | Obiettivo | Precondizioni | Passi | Expected Result | Severità se fallisce | Stato |
|---|---|---|---|---|---|---|
| **TC-01** | Check-in da prenotazione — flusso completo | Prenotazione in stato CONFIRMED; camera disponibile; ospite A creato | 1. Login come RECEPTIONIST. 2. Aprire la prenotazione. 3. Eseguire check-in compilando tutti i campi Alloggiati. 4. Confermare. | HTTP 200; stayId restituito; stato soggiorno = `CHECKED_IN`; camera → OCCUPIED | BLOCCANTE | ⬜ |
| **TC-02** | Check-in walk-in — senza prenotazione | Camera disponibile; ospite A creato | 1. Login RECEPTIONIST. 2. Aprire Walk-in Check-in. 3. Selezionare ospite, camera, data checkout. 4. Compilare campi Alloggiati. 5. Confermare. | HTTP 200; stayId restituito; stato = `CHECKED_IN`; `reservationId` assente nel payload | BLOCCANTE | ⬜ |
| **TC-03** | Generazione file .txt Alloggiati | TC-01 o TC-02 completato; almeno 1 soggiorno nella data odierna | `GET /api/v1/stays/reports/alloggiati?date=$(date +%Y-%m-%d)` con token admin | Risposta 200; Content-Type `text/plain`; file scaricato con ≥ 1 riga | BLOCCANTE | ⬜ |
| **TC-04** | Validazione lunghezza righe (168 char) | TC-03 completato; file in `/tmp/alloggiati-test.txt` | `awk '{ if (length($0) != 168) print "ERRORE riga "NR": "length($0)" char"; else n++ } END { print n" righe valide" }' /tmp/alloggiati-test.txt` | Output: solo righe valide; nessuna riga con lunghezza ≠ 168 | BLOCCANTE | ⬜ |
| **TC-05** | Validazione CRLF — nessun CRLF finale | TC-03 completato | `hexdump -C /tmp/alloggiati-test.txt \| tail -4` | Ultima riga finisce con l'ultimo carattere del record, non con `0d 0a`; righe intermedie terminano con `0d 0a` | CRITICO | ⬜ |
| **TC-06** | Invio SOAP dry-run (operazione Test) | `ALLOGGIATI_DRY_RUN=true`; credenziali PS reali nel `.env`; TC-03 completato | `curl -s -b "jwt=<TOKEN>" -X POST "http://localhost:8080/api/v1/stays/reports/alloggiati/submit?date=$(date +%Y-%m-%d)"` | HTTP 200; log `ALLOGGIATI_SUBMISSION_SUCCESS \| operation=Test`; nessun `ALLOGGIATI_SOAP_ERROR` | BLOCCANTE | ⬜ |
| **TC-07** | Invio SOAP reale (operazione Send) | TC-06 superato; `ALLOGGIATI_DRY_RUN=false`; accesso al portale PS per verifica | Come TC-06 con `DRY_RUN=false` | HTTP 200; log `ALLOGGIATI_SUBMISSION_SUCCESS \| operation=Send`; sul portale PS → Archivio → schedina presente | BLOCCANTE | ⬜ |
| **TC-08** | Resilienza — fallimento rete durante invio | Stack avviato; simulare disconnessione di rete del container: `docker network disconnect hotel-pms_default stay-service` | Eseguire check-in completo → poi tentare invio manuale | Check-in completato con HTTP 200 prima della disconnessione; invio fallisce con log `ALLOGGIATI_SOAP_ERROR \| Connection refused`; lo stato del soggiorno rimane CHECKED_IN (non rollback); badge frontend rosso | BLOCCANTE | ⬜ |
| **TC-09** | Resilienza — risposta di errore dal portale PS | `ALLOGGIATI_DRY_RUN=true`; credenziali intenzionalmente errate (es. password sbagliata) | Tentare invio: `POST .../submit?date=...` | HTTP 500 o 422 restituito dall'endpoint; log `ALLOGGIATI_SOAP_ERROR \| action=AlloggiatiService/GenerateToken`; sistema non crasha; retry successivo non bloccato | CRITICO | ⬜ |
| **TC-10** | Check-in non bloccato da errore invio esterno | `ALLOGGIATI_DRY_RUN=true`; portale PS simulato irraggiungibile (blocco rete) | Eseguire check-in completo con `alloggiatiAutoSend=true` attivo | Check-in restituisce HTTP 200; soggiorno salvato nel DB; log mostra tentativo invio fallito con errore non-blocking; badge frontend rosso (non inviato); receptionist può procedere | BLOCCANTE | ⬜ |
| **TC-11** | Correlazione log — X-Correlation-ID end-to-end | Stack con correlation ID attivo | Eseguire check-in con header `X-Correlation-ID: test-collaudo-001`; poi tentare invio | Log di `api-gateway`, `stay-service` contengono `correlationId=test-collaudo-001` su ogni riga rilevante; log del `GenerateToken` e `Send` SOAP riportano lo stesso ID | ALTO | ⬜ |
| **TC-12** | Coerenza dati — frontend vs DB vs file esportato | TC-01 completato con ospite A | 1. Leggere dati ospite dal frontend (pagina Stays → dettaglio). 2. `SELECT * FROM stay_guests WHERE stay_id = '<id>'`. 3. Scaricare file Alloggiati e leggere riga corrispondente. | I 12 campi chiave (nome, cognome, sesso, dataNascita, comuneNascita, cittadinanza, tipoDoc, nDoc, luogoDoc, tipoAlloggiato, arrivo, permanenza) sono identici in tutte e tre le fonti | BLOCCANTE | ⬜ |
| **TC-13** | Nucleo familiare — ordinamento e campi doc | Ospiti C1 (CAPOFAMIGLIA) e C2 (FAMILIARE) in uno stesso soggiorno | Check-in con due ospiti C1+C2; scaricare file | Nel file: C1 precede C2 (ordinamento per `numericCode` ASC); C2 ha campi documento completamente blank (posizioni 45-97 = spazi) | CRITICO | ⬜ |
| **TC-14** | Ospite straniero — stato nascita e rilascio doc | Ospite B con stato nascita USA | Check-in ospite B; scaricare file | Campo statoNascita (pos. 58-66) contiene codice stato USA valido dalla lookup; luogoRilascioDoc (pos. 77-85) contiene lo stesso codice stato (non un codice comune); provinciaNascita = spazi | CRITICO | ⬜ |
| **TC-15** | Lookup tables — popolamento e aggiornamento | Stack appena avviato (primo avvio) | Verificare log di startup; poi `SELECT COUNT(*) FROM alloggiati_comuni; SELECT COUNT(*) FROM alloggiati_stati; SELECT COUNT(*) FROM alloggiati_tipdoc;` | Log `Loaded N stati/comuni/tipdoc from Portale Alloggiati`; comuni ≥ 7800; stati ≥ 200; tipdoc ≥ 15; cache Caffeine attiva (log `Cache alloggiati.*` al primo accesso) | BLOCCANTE | ⬜ |
| **TC-16** | Toggle alloggiatiAutoSend — comportamento on/off | Hotel profile configurato | 1. Disattivare toggle → eseguire check-in → verificare che non ci sia tentativo invio automatico. 2. Attivare toggle → eseguire check-in → verificare invio automatico. | Con toggle OFF: nessun log `ALLOGGIATI_SUBMISSION_START` al check-in. Con toggle ON: log `ALLOGGIATI_SUBMISSION_START` appare entro 2s dal check-in | ALTO | ⬜ |
| **TC-17** | Badge alloggiatiSent — aggiornamento UI | TC-06 completato con successo | Aprire pagina Stays nel frontend; osservare la riga del soggiorno testato | Badge verde "PS Portal" visibile sulla riga; tooltip o label indica data/ora invio; dopo fallimento (TC-09) badge rosso | ALTO | ⬜ |
| **TC-18** | Limite 1000 righe — blocco superamento | 1001 soggiorni fittizi nella data (preparare via script SQL) | Tentare `GET /api/v1/stays/reports/alloggiati?date=YYYY-MM-DD` | HTTP 422; body contiene errore `ALLOGGIATI_ROW_LIMIT_EXCEEDED`; nessun file generato parzialmente | MEDIO | ⬜ |

---

## 5. Criteri di Accettazione

### 5.1 Definizione di "test passato"

Un test è **PASSATO** se e solo se:
- L'expected result è verificato senza deviazioni.
- Nessun log di errore imprevisto è generato durante l'esecuzione del test.
- Il sistema non richiede intervento manuale per raggiungere l'expected result.

### 5.2 Definizione di "test fallito"

Un test è **FALLITO** se almeno una delle seguenti condizioni è vera:
- HTTP status code ≠ da quello atteso.
- Un campo obbligatorio nel file .txt ha lunghezza errata o valore non conforme al tracciato PS.
- Il portale PS risponde con un `ErroreCod` non vuoto e non `0000`.
- Un log `ERROR` o `ALLOGGIATI_SOAP_ERROR` è prodotto durante il test.
- Il check-in è bloccato da un errore di invio esterno (TC-10).

### 5.3 Definizione di "test superato con warning"

Un test è **SUPERATO CON WARNING** se:
- L'expected result è raggiunto MA si osserva almeno un log `WARN` correlato all'operazione.
- Il portale risponde con codice `0000` (successo) MA il tempo di risposta supera 10 secondi.
- Il file .txt è corretto MA la lookup tables contiene ≥ 1 codice scaduto (dataFineVal nel passato) usato nel test.

Un test superato con warning **conta come passato** per il Go/No-Go ma richiede un'azione correttiva documentata entro 48 ore.

### 5.4 Soglie quantitative

| Metrica | Soglia minima | Soglia target |
|---|---|---|
| Righe file 168 char valide | 100% | 100% |
| Latenza `GenerateToken` SOAP | < 15 s | < 5 s |
| Latenza `Send`/`Test` SOAP | < 30 s | < 10 s |
| Lookup tables comuni | ≥ 7800 | ≥ 8000 |
| Lookup tables stati | ≥ 200 | ≥ 250 |
| Log con correlationId su ogni operazione Alloggiati | 100% | 100% |
| Badge alloggiatiSent aggiornato entro | 30 s dal check-in | 5 s |

### 5.5 Condizione per "collaudo complessivamente approvato"

Il collaudo è **APPROVATO** se:
- Tutti i test con severità **BLOCCANTE** sono PASSATI (non è ammessa nessuna eccezione).
- Tutti i test con severità **CRITICO** sono PASSATI o SUPERATI CON WARNING con azione correttiva documentata.
- I test con severità ALTO e MEDIO: massimo 1 fallito, con piano di risoluzione scritto.

---

## 6. Procedura di Collaudo Reale

### Fase 0 — Preparazione ambiente (T-2 giorni)

**0.1** Generare un backup completo del database:
```bash
docker exec postgres pg_dump -U postgres hotel_stay > "backup-pre-collaudo-$(date +%Y%m%d).sql"
```

**0.2** Verificare che le lookup tables siano aggiornate scaricando i CSV attuali dal portale PS e confrontando i conteggi con quelli in DB:
```bash
docker exec postgres psql -U postgres -d hotel_stay -c \
  "SELECT 'comuni' AS tab, COUNT(*) FROM alloggiati_comuni
   UNION ALL SELECT 'stati', COUNT(*) FROM alloggiati_stati
   UNION ALL SELECT 'tipdoc', COUNT(*) FROM alloggiati_tipdoc;"
```

**0.3** Verificare namespace SOAP dal WSDL e confrontare con `ALLOGGIATI_WS_NAMESPACE` nel `.env`:
```bash
curl -s "https://alloggiatiweb.poliziadistato.it/service/Service.asmx?WSDL" \
  | grep -o 'targetNamespace="[^"]*"' | head -3
```

**0.4** Verificare che i SOAPAction nel WSDL corrispondano alle costanti nel codice:
```bash
curl -s "https://alloggiatiweb.poliziadistato.it/service/Service.asmx?WSDL" \
  | grep -o 'soapAction="[^"]*"'
# Atteso: AlloggiatiService/GenerateToken, AlloggiatiService/Test, AlloggiatiService/Send
```

Se i valori differiscono, aggiornare le costanti `SOAP_ACTION_*` in `AlloggiatiWebSenderServiceImpl.java` prima di procedere.

---

### Fase 1 — Avvio stack e verifica prerequisiti (T-0, mattina del collaudo)

**1.1** Impostare le credenziali reali nel `.env`:
```bash
# Verificare che tutti e tre i valori siano presenti e non placeholder
grep -E "ALLOGGIATI_(USERNAME|PASSWORD|WS_KEY|DRY_RUN)" .env
```

**1.2** Impostare `ALLOGGIATI_DRY_RUN=true` (obbligatorio per la fase dry-run):
```bash
grep "ALLOGGIATI_DRY_RUN" .env
# Deve restituire: ALLOGGIATI_DRY_RUN=true
```

**1.3** Avviare lo stack:
```bash
docker compose up -d
docker compose ps
# Attendere che tutti i container siano "healthy"
```

**1.4** Verificare HMAC check al gateway:
```bash
docker logs api-gateway 2>&1 | grep -E "HMAC_SECRET"
# Atteso: HMAC_SECRET_OK | length=N (dove N >= 32)
```

**1.5** Verificare caricamento lookup tables:
```bash
docker logs stay-service 2>&1 | grep -E "Loaded|lookup"
# Atteso: Loaded N stati/comuni/tipdoc from Portale Alloggiati
```

**1.6** Verificare assenza di placeholder nelle variabili Alloggiati:
```bash
docker logs stay-service 2>&1 | grep -i "placeholder\|ci_placeholder"
# Nessun output atteso
```

---

### Fase 2 — Creazione dati di test

**2.1** Login come ADMIN → creare gli ospiti A, B, C1, C2 se non esistono già.

**2.2** Login come RECEPTIONIST → creare una prenotazione per ospite A per la data odierna se non esiste.

**2.3** Verificare che la camera di test sia in stato AVAILABLE:
```bash
# Via API o frontend: verificare RoomStatus = AVAILABLE
```

**2.4** Verificare che il toggle `alloggiatiAutoSend` sia attivo per l'hotel:
- Frontend: Admin → Hotel Profile → Alloggiati Auto-Send → ON

---

### Fase 3 — Esecuzione dry-run (sequenza obbligatoria)

**3.1** Ottenere un token JWT ADMIN per le chiamate curl:
```bash
TOKEN=$(curl -s -c /tmp/cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}' | jq -r '.token // empty')
echo "Token: ${TOKEN:0:20}..."
```

**3.2** Eseguire il check-in dell'ospite A (da UI o API):
- Compilare tutti i campi Alloggiati obbligatori.
- Verificare HTTP 200 e stayId nella risposta.

**3.3** Scaricare e validare il file Alloggiati generato:
```bash
DATE=$(date +%Y-%m-%d)
curl -s -b /tmp/cookies.txt \
  "http://localhost:8080/api/v1/stays/reports/alloggiati?date=${DATE}" \
  -o /tmp/alloggiati-test.txt

# Conteggio righe
wc -l /tmp/alloggiati-test.txt

# Verifica lunghezza 168 char per ogni riga
awk '{ if (length($0) != 168) print "ERRORE riga "NR": "length($0)" char"; else n++ }
     END { print n" righe valide su "NR }' /tmp/alloggiati-test.txt

# Verifica encoding
file /tmp/alloggiati-test.txt

# Verifica CRLF (deve mostrare 0d 0a tra le righe, non in coda)
hexdump -C /tmp/alloggiati-test.txt | tail -5
```

**3.4** Inviare in modalità dry-run (operazione `Test` sul portale PS):
```bash
curl -s -b /tmp/cookies.txt -X POST \
  "http://localhost:8080/api/v1/stays/reports/alloggiati/submit?date=${DATE}" \
  | jq .
```

**3.5** Verificare i log dell'operazione:
```bash
docker logs stay-service --tail=50 2>&1 | grep -E "ALLOGGIATI|SOAP|TOKEN|SUBMISSION"
```

Sequenza attesa (nell'ordine):
```
ALLOGGIATI_SUBMISSION_START | date=YYYY-MM-DD | dryRun=true
ALLOGGIATI_SUBMISSION_RECORDS | date=YYYY-MM-DD | count=N
ALLOGGIATI_TOKEN_OBTAINED
ALLOGGIATI_SUBMISSION_SUCCESS | date=YYYY-MM-DD | operation=Test | rows=N
ALLOGGIATI_SENT | stayId=<uuid> | date=YYYY-MM-DD
```

**3.6** Verificare badge nel frontend:
- Aprire pagina Stays → verificare badge verde "PS Portal" sulla riga del soggiorno appena testato.

**3.7** Ripetere i passi 3.2-3.6 per:
- Ospite B (straniero) — verificare correttezza codici stato
- Ospiti C1+C2 (famiglia) — verificare ordinamento e campi doc vuoti per FAMILIARE

---

### Fase 4 — Invio reale (operazione Send)

> **ATTENZIONE:** Procedere solo dopo che Fase 3 è completata senza errori per tutti e tre i profili di ospite.

**4.1** Creare un backup del DB appena prima dell'invio reale:
```bash
docker exec postgres pg_dump -U postgres hotel_stay > "backup-pre-send-$(date +%Y%m%d-%H%M).sql"
```

**4.2** Cambiare `ALLOGGIATI_DRY_RUN=false` nel `.env` e riavviare solo lo stay-service:
```bash
docker compose up -d --no-deps stay-service
# Attendere che stay-service sia healthy
docker logs stay-service --tail=20
```

**4.3** Eseguire un nuovo check-in (ospite A fresco, non già inviato):
- Creare una nuova prenotazione o usare walk-in.
- Ospite diverso o data diversa rispetto alla fase 3 per evitare duplicati nel portale.

**4.4** Verificare il log con operazione `Send`:
```bash
docker logs stay-service --tail=50 2>&1 | grep -E "ALLOGGIATI|operation="
# Atteso: operation=Send (non più operation=Test)
```

**4.5** Verificare sul portale PS:
- Accedere a `https://alloggiatiweb.poliziadistato.it`
- Navigare in: Gestione Struttura → Archivio Schedine (o equivalente nella UI portale)
- Verificare presenza della schedina inviata con data, codice struttura e codice fiscale ospite corrispondenti

**4.6** Registrare il numero di conferma/protocollo restituito dal portale se presente.

---

### Fase 5 — Test di resilienza

**5.1** Test fallimento rete (TC-08):
```bash
# Isolare il container dalla rete
docker network disconnect hotel-pms_default stay-service

# Eseguire check-in da frontend — deve avere successo (200)
# Tentare invio manuale — deve fallire con errore non-blocking
curl -s -b /tmp/cookies.txt -X POST \
  "http://localhost:8080/api/v1/stays/reports/alloggiati/submit?date=${DATE}"

# Ripristinare la rete
docker network connect hotel-pms_default stay-service
```

**5.2** Test credenziali errate (TC-09):
```bash
# Modificare temporaneamente ALLOGGIATI_PASSWORD nel .env con valore errato
# Riavviare stay-service
docker compose up -d --no-deps stay-service

# Tentare invio
curl -s -b /tmp/cookies.txt -X POST \
  "http://localhost:8080/api/v1/stays/reports/alloggiati/submit?date=${DATE}"

# Verificare risposta di errore e log
docker logs stay-service --tail=30 2>&1 | grep ALLOGGIATI_SOAP_ERROR

# Ripristinare credenziali corrette e riavviare
```

**5.3** Test toggle off (TC-16):
```bash
# 1. Disattivare alloggiatiAutoSend via frontend (Admin → Hotel Profile → toggle OFF)
# 2. Eseguire check-in completo
# 3. Verificare che non appaia ALLOGGIATI_SUBMISSION_START nei log
docker logs stay-service --tail=50 2>&1 | grep ALLOGGIATI_SUBMISSION_START
# Nessun output atteso

# 4. Riattivare toggle
# 5. Eseguire check-in → verificare che ALLOGGIATI_SUBMISSION_START appaia
```

---

### Fase 6 — Chiusura collaudo

**6.1** Compilare la matrice dei test (§4) con gli stati finali.

**6.2** Ripristinare `ALLOGGIATI_DRY_RUN=true` nel `.env` per il pilot iniziale.

**6.3** Annotare il namespace SOAP, i SOAPAction e la versione WSDL usati.

**6.4** Comunicare l'esito alla Questura di competenza se richiesto dalla procedura locale.

**6.5** Aggiornare `backup/SUMMARY.md` con il risultato del collaudo.

---

## 7. Piano di Fallback e Rollback

### 7.1 Cosa fare immediatamente in caso di fallimento

| Scenario | Azione immediata | NON fare |
|---|---|---|
| `GenerateToken` fallisce con `Connection refused` | Verificare rete dal container: `docker exec stay-service curl -v https://alloggiatiweb.poliziadistato.it` | Non continuare a tentare invii in loop |
| `GenerateToken` fallisce con risposta SOAP `fault` | Estrarre il WSDL e confrontare namespace: vedere §6.1 | Non modificare codice in produzione senza test |
| `Send`/`Test` fallisce con `ErroreCod` non vuoto | Registrare codice errore; consultare tabella in §A.2; isolare il record problematico | Non continuare a inviare lo stesso file |
| Check-in bloccato da errore esterno | Fermare il collaudo; verificare TC-10; il check-in non deve mai essere bloccato | Non workaround manuali nel DB |
| Portale PS non risponde (timeout) | Attendere 30 minuti e ritentare; segnalare alla Questura se persiste | Non disabilitare il timeout nel codice |
| File .txt contiene righe ≠ 168 char | Fermare l'invio; analizzare quale campo è troncato o eccedente; correggere i dati ospite | Non inviare un file malformato al portale |

### 7.2 Isolamento del problema

**Passo 1 — Identificare la fase che ha fallito:**
```bash
# Cercare la sequenza di log per identificare dove si è fermato il processo
docker logs stay-service 2>&1 | grep -E "ALLOGGIATI_SUBMISSION_START|TOKEN_OBTAINED|SUBMISSION_SUCCESS|SOAP_ERROR|SUBMISSION_FAILED"
```

**Passo 2 — Verificare il payload SOAP:**
Se il log mostra che `GenerateToken` ha avuto successo ma `Send`/`Test` ha fallito, il problema è nel formato del file. Scaricare il file e analizzarlo riga per riga:
```bash
cat -A /tmp/alloggiati-test.txt | head -5
# ^M in coda a ogni riga = CRLF corretto
# Nessun ^M = solo LF (errore)
```

**Passo 3 — Verificare i codici lookup:**
```bash
docker exec postgres psql -U postgres -d hotel_stay -c "
  SELECT codice, descrizione FROM alloggiati_comuni WHERE codice = '058091000';
  SELECT codice, descrizione FROM alloggiati_stati WHERE codice = '100000100';
  SELECT codice, descrizione FROM alloggiati_tipdoc WHERE codice = 'PASOR';
"
```

### 7.3 Quando fermare il collaudo

Interrompere immediatamente il collaudo se:
- Qualsiasi test BLOCCANTE fallisce e non è identificata una causa chiara entro 30 minuti.
- Il portale PS risponde con errori di formato per tre record consecutivi (segnale che il tracciato è sistematicamente sbagliato).
- Il check-in viene bloccato da errori dell'invio esterno (violazione del requisito fondamentale di non-blocking).
- Il database mostra inconsistenze tra `stay_guests` e i dati nel file generato.

### 7.4 Quando è possibile ripetere il test

- Per test BLOCCANTE: solo dopo aver identificato la root cause e verificato il fix su ambiente di sviluppo.
- Per test CRITICO: dopo aver documentato il workaround e verificato che non impatti altri test.
- Per test di resilienza: il container può essere ricollegato alla rete in qualsiasi momento con `docker network connect`.
- Per test con credenziali errate: dopo aver ripristinato le credenziali corrette e riavviato il servizio.

### 7.5 Rollback del database

Se il collaudo ha prodotto dati sporchi nel DB che devono essere rimossi:
```bash
# SOLO se necessario e in ambiente di test
# Rimuovere soggiorni di test per data specifica (NON in produzione)
docker exec postgres psql -U postgres -d hotel_stay -c "
  DELETE FROM stays WHERE hotel_id = '<hotel_id_test>' AND created_at::date = '$(date +%Y-%m-%d)';
"
# Verificare backup disponibile prima di eseguire qualsiasi DELETE
```

---

## 8. Rischi Residui

### 8.1 Rischi tecnici residui post-collaudo

| Rischio | Probabilità | Impatto | Mitigazione in atto | Accettabile per pilot |
|---|---|---|---|---|
| Namespace SOAP cambia senza preavviso (PS aggiorna WSDL) | Bassa | Alto | `ALLOGGIATI_WS_NAMESPACE` configurabile via env; log chiaro del mismatch | Sì, con monitoring |
| WsKey scade durante il pilot | Media | Alto | Nessuna notifica automatica dal portale; monitorare la data di scadenza manualmente | Sì, con reminder calendarizzato |
| Portale PS irraggiungibile in orario di check-in | Bassa | Medio | Check-in non bloccante; invio manuale successivo disponibile; log e badge di stato | Sì |
| Lookup tables obsolete (comune soppresso) | Media | Medio | Warning automatico nei log per codici scaduti; tabelle aggiornabili con riavvio | Sì, con revisione mensile |
| Ospite con caratteri speciali (accenti, apostrofi) nel nome | Media | Alto | Non testato con tutti i caratteri del set CP1252 — verificare durante collaudo | Da verificare prima del pilot |
| Data di nascita prima del 1900 (raro ma possibile) | Molto bassa | Basso | Non testato specificamente | Sì |
| Più di 1000 check-in in un giorno (hotel molto grande) | Molto bassa per pilot | Critico | HTTP 422 con errore chiaro; invio manuale a blocchi non implementato | Da valutare per scale-up |

### 8.2 Rischi operativi per il pilot

| Rischio | Mitigazione |
|---|---|
| Il receptionist non compila tutti i campi Alloggiati al check-in | Validazione lato frontend blocca il submit se campo obbligatorio vuoto; tooltip di aiuto sul significato dei codici |
| Check-in con ospite già inviato in data precedente → duplicato nel portale | Il badge `alloggiatiSent` impedisce visivamente un secondo invio; il submit endpoint deve essere idempotente o bloccare re-invio |
| Credenziali PS scadono durante il pilot | Non c'è alert proattivo; l'albergatore deve essere formato su come rileggere il badge rosso e contattare supporto |

---

## 9. Go / No-Go Finale

### Condizioni per GO

Il pilot è autorizzato se, al termine del collaudo:

1. **Tutti** i test BLOCCANTE (TC-01, 02, 03, 04, 06, 07, 10, 12, 15) hanno stato PASSATO.
2. **Tutti** i test CRITICO (TC-05, 09, 13, 14) hanno stato PASSATO o SUPERATO CON WARNING con azione correttiva documentata.
3. La coerenza dati (TC-12) è verificata al 100%.
4. Il check-in non è mai stato bloccato da un errore esterno durante il collaudo.
5. Il portale PS ha risposto con `ErroreCod = 0000` sia in modalità Test che in modalità Send.
6. Il badge alloggiatiSent si aggiorna correttamente sia in caso di successo che di errore.

### Condizioni per NO-GO

Il pilot è bloccato se almeno una delle seguenti condizioni è vera:

1. Qualsiasi test BLOCCANTE ha stato FALLITO.
2. TC-07 (invio reale) non è stato completato con successo (portale non ha confermato la schedina).
3. Il check-in viene bloccato anche una sola volta da un errore dell'invio esterno.
4. Il file .txt contiene righe con lunghezza ≠ 168 caratteri per qualsiasi profilo di ospite.
5. Due o più test CRITICO hanno stato FALLITO.
6. Il portale PS segnala un `Namespace fault` che non è stato risolto prima della fine del collaudo.

### Condizioni per GO con riserva

Il pilot può partire con riserva se:

1. Tutti i test BLOCCANTE sono PASSATI.
2. Al massimo 1 test CRITICO è FALLITO, con:
   - Root cause identificata e documentata.
   - Workaround operativo scritto e comunicato al receptionist.
   - Data di risoluzione fissata (massimo 5 giorni lavorativi dall'inizio del pilot).
3. Il pilot si svolge con monitoraggio giornaliero dei log Alloggiati da parte del team tecnico.
4. Il flag `alloggiatiAutoSend=true` viene attivato solo dopo il primo invio manuale verificato.

---

## 10. Checklist Operativa Finale

Da usare il giorno del collaudo. Spuntare ogni voce solo dopo verifica effettiva.

### Pre-collaudo (T-2 ore)
- [ ] `.env` contiene credenziali PS reali (non placeholder)
- [ ] `ALLOGGIATI_DRY_RUN=true`
- [ ] Backup DB eseguito: `backup-pre-collaudo-YYYYMMDD.sql`
- [ ] Stack avviato: tutti i container `healthy`
- [ ] Log startup stay-service: `Loaded N stati/comuni/tipdoc`
- [ ] Log api-gateway: `HMAC_SECRET_OK`
- [ ] Namespace SOAP verificato vs WSDL: `ALLOGGIATI_WS_NAMESPACE` corretto
- [ ] SOAPAction verificati vs WSDL
- [ ] Ospiti A, B, C1, C2 creati nel sistema
- [ ] Toggle `alloggiatiAutoSend` attivo
- [ ] Token JWT admin ottenuto per chiamate curl

### Esecuzione dry-run
- [ ] TC-01 eseguito e PASSATO (check-in da prenotazione)
- [ ] TC-02 eseguito e PASSATO (walk-in)
- [ ] TC-03 eseguito: file .txt scaricato
- [ ] TC-04 eseguito: 0 righe ≠ 168 char
- [ ] TC-05 eseguito: nessun CRLF finale
- [ ] TC-06 eseguito: log `operation=Test | SUBMISSION_SUCCESS`
- [ ] TC-11 eseguito: correlationId tracciato nei log
- [ ] TC-12 eseguito: dati coerenti frontend/DB/file
- [ ] TC-13 eseguito: FAMILIARE con doc vuoti, ordinato dopo CAPOFAMIGLIA
- [ ] TC-14 eseguito: ospite straniero codici corretti
- [ ] TC-15 verificato: lookup tables popolate
- [ ] TC-16 verificato: toggle on/off funziona
- [ ] TC-17 verificato: badge verde dopo successo, rosso dopo errore

### Esecuzione reale (Send)
- [ ] Backup DB eseguito appena prima: `backup-pre-send-YYYYMMDD-HH.sql`
- [ ] `ALLOGGIATI_DRY_RUN=false` impostato; stay-service riavviato
- [ ] TC-07 eseguito: log `operation=Send | SUBMISSION_SUCCESS`
- [ ] Portale PS verificato: schedina presente in archivio
- [ ] Numero protocollo/conferma PS annotato: _______________

### Test di resilienza
- [ ] TC-08 eseguito: fallimento rete non blocca check-in
- [ ] TC-09 eseguito: credenziali errate → errore non-blocking
- [ ] Container rete ripristinata dopo TC-08
- [ ] Credenziali ripristinate dopo TC-09

### Chiusura
- [ ] `ALLOGGIATI_DRY_RUN=true` ripristinato per il pilot
- [ ] Matrice test §4 compilata con stati finali
- [ ] Decisione Go/No-Go documentata
- [ ] `backup/SUMMARY.md` aggiornato

---

## Appendice A — Riferimento rapido log e errori

### A.1 Sequenza log attesa — invio completo con successo

```
INFO  [correlationId=<uuid>] ALLOGGIATI_SUBMISSION_START | date=YYYY-MM-DD | dryRun=false
INFO  [correlationId=<uuid>] ALLOGGIATI_SUBMISSION_RECORDS | date=YYYY-MM-DD | count=N
INFO  [correlationId=<uuid>] ALLOGGIATI_TOKEN_OBTAINED
INFO  [correlationId=<uuid>] ALLOGGIATI_SUBMISSION_SUCCESS | date=YYYY-MM-DD | operation=Send | rows=N
INFO  [correlationId=<uuid>] ALLOGGIATI_SENT | stayId=<uuid> | date=YYYY-MM-DD
```

### A.2 Errori portale PS — interpretazione e fix

| ErroreCod | Significato | Fix immediato |
|---|---|---|
| `E001` | Formato record non valido (lunghezza o caratteri non CP1252) | Eseguire awk su /tmp/alloggiati-test.txt; individuare la riga errata; correggere il dato ospite |
| `E002` | Codice stato/comune/tipdoc non riconosciuto | Verificare che il codice sia presente nelle lookup tables con `SELECT * FROM alloggiati_comuni WHERE codice = '<codice>';` |
| `E003` | WsKey non valida o scaduta | Rigenerare dal portale PS: Account → Web Service → Genera nuova chiave; aggiornare `.env` e riavviare stay-service |
| `E004` | Struttura non abilitata al Web Service | Contattare la Questura di competenza per abilitazione specifica |
| `0000` | Successo | Nessuna azione |
| `Namespace fault` | Namespace SOAP nel codice non corrisponde al WSDL attuale | Aggiornare `ALLOGGIATI_WS_NAMESPACE`; verificare con §6.1 |
| `token vuoto / empty token` | Risposta `GenerateToken` inattesa | Abilitare log SOAP completo temporaneamente; verificare SOAPAction per GenerateToken |

### A.3 Errori comuni — pattern e diagnosi rapida

| Sintomo | Probabile causa | Tempo diagnosi stimato |
|---|---|---|
| Badge sempre rosso anche dopo check-in | Toggle `alloggiatiAutoSend` OFF o stay-service non raggiunge il portale | 5 min |
| File .txt scaricato vuoto (0 byte) | Nessun soggiorno nella data richiesta; verificare che il check-in sia stato fatto nella data passata come parametro | 2 min |
| `Connection refused` al portale | Firewall del server blocca HTTPS outbound verso IP PS | 15 min |
| Righe del file di 167 o 169 char | Un campo stringa ha un carattere in più/meno rispetto alle specifiche; usare `awk` per trovare la riga e il campo | 10 min |
| Lookup tables vuote al riavvio | Il download CSV dal portale fallisce (rete o URL cambiato); verificare log `AlloggiatiLookupDataLoader` | 5 min |
| `GenerateToken` lento (> 15 s) | Latenza normale del portale PS nelle ore di punta; non è un errore | 0 min |

### A.4 Note per il pilot con albergatori

1. **Formare il receptionist** sul badge Alloggiati (verde = inviato, rosso = errore) e su come contattare il supporto tecnico se il badge rimane rosso dopo il check-in.
2. **Monitorare i log** ogni sera con: `docker logs stay-service 2>&1 | grep -E "ALLOGGIATI_SOAP_ERROR|SUBMISSION_FAILED"` — qualsiasi riga qui è una schedina non inviata.
3. **La WsKey** ha scadenza: calendarizzare un reminder 30 giorni prima della scadenza per la rigenerazione.
4. **Invio manuale** disponibile sempre via endpoint `POST .../submit?date=YYYY-MM-DD` per recuperare eventuali mancati invii automatici.
5. **Non modificare** i codici stato/comune/tipdoc direttamente nel DB — usare sempre l'autocomplete del form check-in che attinge alle lookup tables validate.
6. **Ospiti minori**: verificare che il form check-in non blocchi la compilazione per ospiti con età < 14 anni (tipoDocumento può essere vuoto in alcuni casi specifici — verificare con la Questura locale).
