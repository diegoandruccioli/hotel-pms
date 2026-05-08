# Documentazione Tecnica — Integrazione Portale Alloggiati Web (Polizia di Stato)

**Servizio:** `stay-service` (porta 8084)  
**Normativa di riferimento:** Art. 109 TULPS (T.U. Leggi di Pubblica Sicurezza), D.M. 7 gennaio 2013  
**Versione documento:** 1.0 — 2026-05-05

---

## 1. Panoramica

L'art. 109 del TULPS impone alle strutture ricettive la comunicazione telematica alla Questura competente delle generalità di ogni ospite entro 24 ore dal check-in. Il Portale Alloggiati Web (`alloggiatiweb.poliziadistato.it`) è il sistema ufficiale della Polizia di Stato per la ricezione di queste comunicazioni.

L'integrazione implementata in `stay-service` supporta **due modalità operative** alternative e complementari:

| Modalità | Descrizione | Endpoint |
|---|---|---|
| **Export file** | Genera il file `.txt` in formato tracciato 168 caratteri per upload manuale sul portale PS | `GET /api/v1/stays/reports/alloggiati?date=YYYY-MM-DD` |
| **Invio SOAP automatico** | Trasmette i dati al portale PS via SOAP 1.1 su HTTPS al completamento del check-in | `POST /api/v1/stays/reports/alloggiati/submit?date=YYYY-MM-DD` |

La modalità SOAP automatica può essere abilitata per singolo hotel tramite il flag `alloggiatiAutoSend` in `HotelSettings`. Per sicurezza, l'invio automatico chiama l'operazione `Test` anziché `Send` finché `ALLOGGIATI_DRY_RUN=false` non viene impostato esplicitamente in produzione.

---

## 2. Specifiche del Tracciato File (Export `.txt`)

### 2.1 Caratteristiche generali del file

| Attributo | Valore |
|---|---|
| Encoding | UTF-8, **senza BOM** |
| Terminatore di riga | CR+LF (`\r\n`) **tra** i record; nessun terminatore dopo l'ultimo record |
| Lunghezza record | **168 caratteri** esatti per ogni riga |
| Record massimi per file | **1 000** (limite del portale PS) |
| Nome file consigliato | `alloggiati-YYYY-MM-DD.txt` |

Il rispetto del terminatore è critico: il portale PS rifiuta file con LF singolo o con CRLF finale dopo l'ultimo record.

### 2.2 Layout del record a 168 caratteri

| # | Campo | Offset | Lunghezza | Tipo | Note |
|---|---|---|---|---|---|
| 1 | `TipoAlloggiato` | 0 | 2 | Numerico | Vedi tabella §2.3 |
| 2 | `DataArrivo` | 2 | 10 | Data `dd/MM/yyyy` | |
| 3 | `Permanenza` | 12 | 2 | Numerico 0-padded | Min 1, Max 30 |
| 4 | `Cognome` | 14 | 50 | Stringa | Left-aligned, spazio-padded, troncato |
| 5 | `Nome` | 64 | 30 | Stringa | Left-aligned, spazio-padded, troncato |
| 6 | `Sesso` | 94 | 1 | Carattere | `1` = M, `2` = F; default `1` |
| 7 | `DataNascita` | 95 | 10 | Data `dd/MM/yyyy` | 10 spazi se non disponibile |
| 8 | `ComuneNascita` | 105 | 9 | Codice PS 9 cifre | Solo per nati in Italia; altrimenti 9 spazi |
| 9 | `ProvinciaNascita` | 114 | 2 | Sigla provincia | Solo per nati in Italia; altrimenti 2 spazi |
| 10 | `StatoNascita` | 116 | 9 | Codice PS 9 cifre | `100000100` per nati in Italia; codice stato estero per gli altri |
| 11 | `Cittadinanza` | 125 | 9 | Codice PS 9 cifre | Sempre obbligatorio |
| 12 | `TipoDocumento` | 134 | 5 | Codice PS | **Blank** per `FAMILIARE` e `MEMBRO_GRUPPO` |
| 13 | `NumeroDocumento` | 139 | 20 | Stringa | **Blank** per `FAMILIARE` e `MEMBRO_GRUPPO` |
| 14 | `LuogoRilascioDoc` | 159 | 9 | Codice PS 9 cifre | **Blank** per `FAMILIARE` e `MEMBRO_GRUPPO` |

### 2.3 Codici TipoAlloggiato

| Codice | Costante `TravellerType` | Descrizione |
|---|---|---|
| `16` | `OSPITE_SINGOLO` | Ospite singolo o non raggruppato |
| `17` | `CAPOFAMIGLIA` | Capofamiglia (richiede almeno un FAMILIARE nella stessa prenotazione) |
| `18` | `CAPOGRUPPO` | Capogruppo (richiede almeno un MEMBRO_GRUPPO nella stessa prenotazione) |
| `19` | `FAMILIARE` | Familiare del capofamiglia (doc fields blank) |
| `20` | `MEMBRO_GRUPPO` | Membro del gruppo (doc fields blank) |

**Ordinamento obbligatorio all'interno di ogni soggiorno:**  
CAPOFAMIGLIA/CAPOGRUPPO (17/18) → FAMILIARE/MEMBRO_GRUPPO (19/20) → OSPITE_SINGOLO (16)

### 2.4 Regole di padding e troncatura

```java
// Stringhe: left-aligned, blank-padded, troncate se overflow
private static String pad(String s, int len) {
    String safe = s == null ? "" : s;
    if (safe.length() >= len) return safe.substring(0, len);
    return String.format("%-" + len + "s", safe);
}

// Numeri: zero-padded a sinistra, clamped al massimo del campo
private static String padNum(int n, int len) {
    int clamped = Math.min(n, MAX_PERMANENZA); // MAX_PERMANENZA = 30
    return String.format("%0" + len + "d", clamped);
}
```

### 2.5 Risoluzione del luogo di nascita

La logica di disambiguazione italiano/estero si basa sulla lookup table dei comuni:

```
PlaceOfBirth (codice 9 char) da StayGuest
    │
    ├── trovato in alloggiati_comuni?
    │       Sì → nato in Italia
    │           comuneNascita = codice comune
    │           provinciaNascita = provincia dal record comune
    │           statoNascita = 100000100 (Italia)
    │
    └── non trovato → nato all'estero
            comuneNascita = "         " (9 spazi)
            provinciaNascita = "  " (2 spazi)
            statoNascita = codice (assunto essere uno stato estero)
```

### 2.6 Esempio di record conforme — Ospite italiano (OSPITE_SINGOLO)

```
16 15/04/2026 03 Rossi                                             Mario                         1 20/05/1985 058091000 RM 100000100 100000100 PASOR AA1234567           058091000
```

Verificabile con:
```bash
# Lunghezza deve essere esattamente 168
awk '{ print NR": "length($0)" chars" }' alloggiati-2026-04-15.txt

# Hexdump per verifica CRLF (0d0a tra record, assente dopo l'ultimo)
hexdump -C alloggiati-2026-04-15.txt | tail -5
```

### 2.7 Vincoli di validazione dominio

Prima di generare il file, `AlloggiatiReportServiceImpl` applica le seguenti validazioni bloccanti (HTTP 422 se violate):

| Errore | Condizione | Codice |
|---|---|---|
| `ALLOGGIATI_FAMILIARE_WITHOUT_CAPO` | `FAMILIARE` senza `CAPOFAMIGLIA` nello stesso soggiorno | 422 |
| `ALLOGGIATI_MEMBRO_WITHOUT_CAPO` | `MEMBRO_GRUPPO` senza `CAPOGRUPPO` nello stesso soggiorno | 422 |
| `ALLOGGIATI_MULTIPLE_CAPOFAMIGLIA` | Più di un `CAPOFAMIGLIA` nello stesso soggiorno | 422 |
| `ALLOGGIATI_MULTIPLE_CAPOGRUPPO` | Più di un `CAPOGRUPPO` nello stesso soggiorno | 422 |
| `ALLOGGIATI_INVALID_DATES` | `expectedCheckOutDate` antecedente alla data di arrivo | 422 |
| `ALLOGGIATI_ROW_LIMIT_EXCEEDED` | Più di 1 000 record per una singola data | 422 |

Le validazioni sui codici lookup (stato, comune, tipdoc sconosciuti) sono **soft**: generano un `WARN` nei log con prefisso `[REPORT]` ma non bloccano la generazione. Il portale PS rechterà l'errore all'upload.

---

## 3. Integrazione SOAP (Invio Automatico)

### 3.1 Flusso di autenticazione e invio

Il portale PS espone un servizio SOAP 1.1 a:  
`https://alloggiatiweb.poliziadistato.it/service/Service.asmx`

Il protocollo è a **due passi**:

```
stay-service                              Portale PS
     │                                         │
     │── POST GenerateToken ─────────────────>│
     │   (Utente, Password, WsKey)             │
     │<── token di sessione ──────────────────│
     │                                         │
     │── POST Send (o Test in dry-run) ───────>│
     │   (Utente, token, ElencoSchedine[])      │
     │<── EsitoOperazioneServizio ─────────────│
     │   {esito: true/false, ErroreCod, ErroreDes}
```

### 3.2 Struttura SOAP — GenerateToken

```xml
<?xml version="1.0" encoding="utf-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
  <soapenv:Body>
    <GenerateToken xmlns="http://alloggiatiweb.poliziadistato.it/PortaleAlloggiati/Service/">
      <Utente>HOTELALFA01</Utente>
      <Password>MySecurePassword123</Password>
      <WsKey>abcd-1234-efgh-5678</WsKey>
    </GenerateToken>
  </soapenv:Body>
</soapenv:Envelope>
```

Header HTTP: `Content-Type: text/xml;charset=UTF-8`, `SOAPAction: "AlloggiatiService/GenerateToken"`

### 3.3 Struttura SOAP — Send (o Test)

```xml
<?xml version="1.0" encoding="utf-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
  <soapenv:Body>
    <Send xmlns="http://alloggiatiweb.poliziadistato.it/PortaleAlloggiati/Service/">
      <Utente>HOTELALFA01</Utente>
      <token>SESSION-TOKEN-OBTAINED-ABOVE</token>
      <ElencoSchedine>
        <string xmlns="http://schemas.microsoft.com/2003/10/Serialization/Arrays">
          16 15/04/2026 03Rossi...
        </string>
        <string xmlns="http://schemas.microsoft.com/2003/10/Serialization/Arrays">
          17 15/04/2026 03Verdi...
        </string>
      </ElencoSchedine>
    </Send>
  </soapenv:Body>
</soapenv:Envelope>
```

Header: `SOAPAction: "AlloggiatiService/Send"` (oppure `"AlloggiatiService/Test"` in dry-run)

### 3.4 Sicurezza — TLS e credenziali

- **TLS:** `RestTemplate` configurato in `AlloggiatiWebConfig` **senza** `TrustManager` personalizzato. Utilizza il JVM truststore (`cacerts`): verifica catena del certificato, CN/SAN e revoca. Nessun `TrustAllCerts` o bypass hostname. *(Riferimento threat: T-STAY-03)*
- **Credenziali:** lette esclusivamente da variabili d'ambiente via `@Value`. Mai hardcoded.
- **WsKey:** chiave di servizio separata da username/password, ottenuta dal portale PS (account → "Chiave Web Service").
- **Token di sessione:** valido per la singola chiamata; non viene memorizzato tra le richieste.

### 3.5 Parsing della risposta e gestione errori

```
Risposta DOM parse (XXE protetto — disallow-doctype-decl = true)
    │
    ├── <token> (GenerateToken response)
    │       blank/assente → ExternalServiceException → HTTP 502
    │
    └── <esito> (Send/Test response)
            "true"  → ALLOGGIATI_SUBMISSION_SUCCESS (log INFO)
            "false" → legge <ErroreCod> e <ErroreDes>
                      → ExternalServiceException → HTTP 502
                      → log ERROR: ALLOGGIATI_SUBMISSION_FAILED
```

Errori di trasporto HTTP (5xx, timeout, TLS): `RestClientException` → `ExternalServiceException` → HTTP 502, log ERROR: `ALLOGGIATI_SOAP_ERROR`.

### 3.6 Dry-run e modalità test

| Variabile | Valore | Comportamento |
|---|---|---|
| `ALLOGGIATI_DRY_RUN=true` (default) | Chiama `Test` anziché `Send` | Il portale valida i dati ma non li registra definitivamente |
| `ALLOGGIATI_DRY_RUN=false` | Chiama `Send` | Registrazione definitiva delle schedine |

**Regola di sicurezza:** impostare `false` **esclusivamente** in produzione, dopo collaudo con `true`.

### 3.7 Auto-submit post check-in

```
StayServiceImpl.checkIn()
    └── sendAlloggiatiIfEnabled(stay)
            │
            ├── HotelSettings.alloggiatiAutoSend() == false → skip (log skip)
            │
            └── alloggiatiAutoSend == true
                    └── alloggiatiWebSenderService.submitReport(checkInDate)
                            ├── successo → stay.alloggiatiSent = true, save
                            │             log INFO: ALLOGGIATI_SENT
                            └── ExternalServiceException → log ERROR: ALLOGGIATI_SEND_FAILED
                                                           check-in NON viene bloccato
```

Il flag `alloggiatiSent` è visibile nella colonna **"PS Portal"** nell'interfaccia Soggiorni (verde = inviato, grigio = non inviato o invio fallito).

---

## 4. Architettura e Modello Dati

### 4.1 Componenti principali

```
frontend
  └── CheckInForm.tsx
        ├── StatoSelect           (lista stati da GET /api/v1/stays/lookup/stati)
        ├── ComuneAutocomplete    (typeahead da GET /api/v1/stays/lookup/comuni?q=)
        └── tipdoc select         (lista da GET /api/v1/stays/lookup/tipdoc)

api-gateway (:8080)
  └── stay-service (:8084)
        ├── AlloggiatiLookupController    → /api/v1/stays/lookup/{stati,comuni,tipdoc}
        ├── StayController                → /api/v1/stays/reports/alloggiati/*
        ├── AlloggiatiReportServiceImpl   ← StayRepository, AlloggiatiLookupService
        ├── AlloggiatiWebSenderServiceImpl ← AlloggiatiReportService, RestTemplate
        ├── AlloggiatiLookupServiceImpl   ← 3 repository JPA + Caffeine cache
        └── AlloggiatiLookupDataLoader    ← scarica CSV al primo avvio da portale PS
```

### 4.2 Lookup tables PostgreSQL

Popolate automaticamente al primo avvio di `stay-service` da `AlloggiatiLookupDataLoader` scaricando i CSV pubblici del portale PS.

| Tabella | Sorgente CSV | ~Righe | Primary Key | Campo chiave |
|---|---|---|---|---|
| `alloggiati_stati` | `Download.ashx?ID=1&N=STATI` | ~249 | `codice` (9 char) | `data_fine_val` (NULL = attivo) |
| `alloggiati_comuni` | `Download.ashx?ID=0&N=COMUNI` | ~7 500 | `codice` (9 char) | `provincia`, `data_fine_val` |
| `alloggiati_tipdoc` | `Download.ashx?ID=2&N=TIPDOC` | ~95 | `codice` (5 char) | — |

I lookup frequenti (`findByCodice`) sono cachati in-memory con **Caffeine** (TTL 24h; dimensioni: 10 000 comuni, 300 stati, 200 tipdoc). Per forzare un ricaricamento:

```bash
# Svuotare le tabelle e riavviare il servizio
docker exec -it postgres psql -U postgres -d hotel_stay -c \
  "TRUNCATE alloggiati_stati, alloggiati_comuni, alloggiati_tipdoc;"
docker restart stay-service
```

### 4.3 Entità StayGuest — campi Alloggiati

`StayGuest` è la **fonte primaria** per il tracciato: i campi PS vengono catturati al momento del check-in dal personale e non derivano dal profilo ospite nel `guest-service`.

```
StayGuest
  ├── travellerType      : TravellerType (enum 16–20)
  ├── citizenship        : String  — codice PS 9 cifre (stato di cittadinanza)
  ├── placeOfBirth       : String  — codice PS 9 cifre (comune italiano o stato estero)
  ├── dateOfBirth        : LocalDate
  ├── gender             : String  ("M" / "F")
  ├── documentType       : String  — codice tipdoc (es. "PASOR"); null per FAMILIARE/MEMBRO_GRUPPO
  ├── documentNumber     : String  — max 20 char; null per FAMILIARE/MEMBRO_GRUPPO
  └── documentPlaceOfIssue: String — codice PS 9 cifre; null per FAMILIARE/MEMBRO_GRUPPO
```

**Vincolo di nullability per FAMILIARE/MEMBRO_GRUPPO:** i campi documento (`documentType`, `documentNumber`, `documentPlaceOfIssue`) sono nullable nel DB e nel DTO. Il frontend imposta esplicitamente `undefined` per questi tipi prima di inviare il payload di check-in. Nel tracciato, le posizioni corrispondenti (offset 134–167) vengono riempite con spazi.

### 4.4 Dipendenze build rilevanti

```kotlin
// stay-service/build.gradle.kts
implementation("org.apache.commons:commons-csv:1.9.0")          // CSV parser con supporto quoted fields
implementation("org.springframework.boot:spring-boot-starter-cache")
implementation("com.github.ben-manes.caffeine:caffeine")        // in-memory cache
```

> **Nota versione:** `commons-csv:1.12.0` richiede `commons-io >= 2.15` ma il progetto impone `commons-io:2.14.0`. La versione `1.9.0` è compatibile e supporta `withFirstRecordAsHeader()`.

---

## 5. Operatività e Go-Live

### 5.1 Variabili d'ambiente

| Variabile | Obbligatoria | Default | Descrizione |
|---|---|---|---|
| `ALLOGGIATI_USERNAME` | **Sì** | — (startup fail se assente) | Username portale PS (es. `HOTELALFA01`) |
| `ALLOGGIATI_PASSWORD` | **Sì** | — | Password portale PS |
| `ALLOGGIATI_WS_KEY` | **Sì** | — | Web Service Key (account PS → "Chiave Web Service") |
| `ALLOGGIATI_SERVICE_URL` | No | `https://alloggiatiweb.poliziadistato.it/service/Service.asmx` | Endpoint SOAP |
| `ALLOGGIATI_WS_NAMESPACE` | No | `http://alloggiatiweb.poliziadistato.it/PortaleAlloggiati/Service/` | SOAP namespace |
| `ALLOGGIATI_DRY_RUN` | No | `true` | `false` solo in produzione |

Esempio `.env` (non committare questo file):
```
ALLOGGIATI_USERNAME=HOTELALFA01
ALLOGGIATI_PASSWORD=MySecurePassword123
ALLOGGIATI_WS_KEY=abcd-1234-efgh-5678
ALLOGGIATI_DRY_RUN=false
```

### 5.2 Procedura di verifica senza credenziali reali (Upload manuale)

Questa procedura consente di validare la correttezza del file generato **prima** di richiedere le credenziali PS, usando l'export manuale e il portale web.

**Step 1 — Generare il file**

```bash
# Via API (richiede JWT valido)
curl -s -b "jwt=<token>" \
  "http://localhost:8084/api/v1/stays/reports/alloggiati?date=2026-04-15" \
  -o alloggiati-2026-04-15.txt

# Oppure tramite frontend: Soggiorni → Download Alloggiati → seleziona data
```

**Step 2 — Verificare il formato**

```bash
# 1. Contare i record (deve corrispondere al numero di ospiti del giorno)
wc -l alloggiati-2026-04-15.txt

# 2. Verificare che ogni riga sia esattamente 168 caratteri
awk '{ if (length($0) != 168) print "ERRORE riga "NR": "length($0)" char" }' \
  alloggiati-2026-04-15.txt

# 3. Verificare il terminatore CRLF (0d 0a) tra righe, assente sull'ultima
hexdump -C alloggiati-2026-04-15.txt | grep -E "0d 0a|\\\\r"

# 4. Verificare encoding UTF-8 senza BOM
file alloggiati-2026-04-15.txt
# Atteso: "UTF-8 Unicode text, with CRLF line terminators"

# 5. Verificare i campi chiave della prima riga
head -c 168 alloggiati-2026-04-15.txt | cut -c1-2   # TipoAlloggiato
head -c 168 alloggiati-2026-04-15.txt | cut -c3-12  # DataArrivo
head -c 168 alloggiati-2026-04-15.txt | cut -c15-64 # Cognome
```

**Step 3 — Upload manuale sul portale PS**

1. Accedere a `https://alloggiatiweb.poliziadistato.it`
2. Autenticarsi con le credenziali dell'hotel
3. Navigare in **Invio File**
4. Selezionare il file `alloggiati-YYYY-MM-DD.txt`
5. Confermare l'upload e verificare il messaggio di esito
6. In caso di errore: leggere il codice errore restituito dal portale (es. `E001 — Formato non valido`)

### 5.3 Checklist Go-Live

Completare tutti i punti prima di impostare `ALLOGGIATI_DRY_RUN=false` in produzione:

- [ ] `ALLOGGIATI_USERNAME`, `ALLOGGIATI_PASSWORD`, `ALLOGGIATI_WS_KEY` impostati nel `.env` di produzione
- [ ] Avvio `stay-service` con `DRY_RUN=true` — verificare nei log che le lookup tables si popolino (`Loaded N stati/comuni/tipdoc from Portale Alloggiati`)
- [ ] Effettuare almeno un check-in con dati reali e codici PS corretti
- [ ] Verificare log `ALLOGGIATI_SUBMISSION_SUCCESS | operation=Test`
- [ ] Accedere al portale PS e confermare che la schedina appaia nell'area archivio (il `Test` è visibile nella sezione test del portale)
- [ ] Verificare che la colonna "PS Portal" nel frontend mostri il badge verde per il soggiorno testato
- [ ] Testare upload manuale del file `.txt` per validare il tracciato (procedura §5.2)
- [ ] Verificare che file con caratteri accentati (à, è, ü) vengano accettati dal portale (encoding UTF-8 vs ISO-8859-1)
- [ ] Impostare `ALLOGGIATI_DRY_RUN=false` — effettuare un check-in definitivo
- [ ] Verificare nei log `ALLOGGIATI_SUBMISSION_SUCCESS | operation=Send`
- [ ] Verificare la presenza della schedina nella sezione **Archivio** del portale PS

---

## 6. Troubleshooting

### 6.1 Errori di validazione dominio (HTTP 422)

| Codice nel log / risposta | Causa | Soluzione |
|---|---|---|
| `ALLOGGIATI_FAMILIARE_WITHOUT_CAPO` | Ospite con tipo `FAMILIARE` senza un `CAPOFAMIGLIA` nello stesso soggiorno | Assegnare il tipo corretto al capofamiglia nel form di check-in |
| `ALLOGGIATI_MEMBRO_WITHOUT_CAPO` | `MEMBRO_GRUPPO` senza `CAPOGRUPPO` | Come sopra per il capogruppo |
| `ALLOGGIATI_MULTIPLE_CAPOFAMIGLIA` | Due ospiti con tipo `CAPOFAMIGLIA` nello stesso soggiorno | Correggere il tipo alloggiato di uno dei due |
| `ALLOGGIATI_INVALID_DATES` | Data di check-out precedente alla data di arrivo | Correggere le date nella prenotazione |
| `ALLOGGIATI_ROW_LIMIT_EXCEEDED` | Più di 1 000 ospiti controllati in un singolo giorno | Inviare manualmente due file per sotto-intervalli di ore (mattina/pomeriggio) |

### 6.2 Errori di lookup nei log (soft — non bloccanti)

| Pattern log | Significato | Azione |
|---|---|---|
| `[REPORT] Unknown citizenship code 'XXX'` | Codice stato non presente in `alloggiati_stati` | Verificare il codice inserito; aggiornare le lookup tables se necessario |
| `[REPORT] placeOfBirth 'XXX' not found in comuni or stati` | Codice 9 cifre non trovato né in comuni né in stati | Inserire il codice corretto al check-in; può indicare tabella incompleta |
| `[REPORT] Comune 'Nome' (XXX) is expired (dataFineVal=...)` | Comune cessato usato come luogo di nascita | Accettabile per persone nate in comuni soppressi; il portale PS di solito lo accetta |

### 6.3 Errori SOAP (HTTP 502)

| Pattern log | Significato | Azione |
|---|---|---|
| `ALLOGGIATI_SOAP_ERROR` | Errore HTTP/TLS nella comunicazione con il portale | Verificare connettività, certificato TLS del portale, firewall |
| `ALLOGGIATI_SUBMISSION_FAILED \| code=... \| desc=...` | Il portale ha risposto con `esito=false` | Leggere `ErroreCod` e `ErroreDes` nel log; vedi §6.4 |
| `GenerateToken returned empty token` | Il portale non ha restituito un token valido | Verificare `ALLOGGIATI_USERNAME`, `ALLOGGIATI_PASSWORD`, `ALLOGGIATI_WS_KEY` |

### 6.4 Codici errore comuni del portale PS

| ErroreCod | Probabile causa | Azione |
|---|---|---|
| `E001` | Formato record non valido (lunghezza, caratteri) | Verificare il tracciato con lo script §5.2 |
| `E002` | Codice stato/comune non riconosciuto | Aggiornare le lookup tables |
| `E003` | WsKey non valida o scaduta | Rigenerare la WsKey dal portale PS |
| `E004` | Utente non abilitato al Web Service | Contattare la Questura competente |
| `NS_FAULT` | Namespace SOAP non corrispondente | Aggiornare `ALLOGGIATI_WS_NAMESPACE` con il valore corretto dal WSDL |

### 6.5 Aggiornamento delle lookup tables

Le tabelle vengono scaricate automaticamente solo se vuote. Per forzare un aggiornamento completo:

```bash
# 1. Svuotare le tabelle
docker exec -it postgres psql -U postgres -d hotel_stay -c \
  "TRUNCATE alloggiati_stati, alloggiati_comuni, alloggiati_tipdoc;"

# 2. Riavviare lo stay-service (il DataLoader rileva le tabelle vuote)
docker restart stay-service

# 3. Verificare nei log
docker logs stay-service --tail=30 | grep -E "alloggiati_stati|alloggiati_comuni|alloggiati_tipdoc|Loaded"
```

### 6.6 Verifica cache

Se i dati sembrano obsoleti nonostante le tabelle aggiornate, la cache Caffeine (TTL 24h) potrebbe essere stantia. Il reset più semplice è il riavvio del servizio:

```bash
docker restart stay-service
```

---

## Appendice — Primo Test Upload Manuale (Istruzioni Operative)

Questa procedura è destinata al personale tecnico per il collaudo iniziale prima del go-live.

**Prerequisiti:**
- Credenziali del portale PS (`HOTELALFA01` / password)
- Almeno un check-in completato nella data di test
- Accesso all'API del `stay-service` (via Postman o curl con JWT)

**Procedura:**

```bash
# PASSO 1 — Ottenere un JWT valido (credenziali default per sviluppo)
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@hotel.com","password":"password"}' \
  -c cookies.txt | jq -r '.token // empty')

# PASSO 2 — Scaricare il file per la data di interesse
curl -s -b cookies.txt \
  "http://localhost:8080/api/v1/stays/reports/alloggiati?date=$(date +%Y-%m-%d)" \
  -o "alloggiati-$(date +%Y-%m-%d).txt"

# PASSO 3 — Verificare il file generato
echo "=== Numero record ==="
wc -l "alloggiati-$(date +%Y-%m-%d).txt"

echo "=== Verifica lunghezza righe (devono essere tutte 168) ==="
awk '{ if (length($0) != 168) print "ERRORE riga "NR": "length($0); else ok++ } END { print ok" righe corrette" }' \
  "alloggiati-$(date +%Y-%m-%d).txt"

echo "=== Verifica assenza trailing CRLF ==="
tail -c 2 "alloggiati-$(date +%Y-%m-%d).txt" | xxd
# Atteso: se l'ultimo byte è 0a (LF) o 0d 0a (CRLF) → anomalia

echo "=== Prima riga — campi principali ==="
head -c 168 "alloggiati-$(date +%Y-%m-%d).txt" | awk '{
  printf "TipoAlloggiato: %s\n", substr($0,1,2)
  printf "DataArrivo:     %s\n", substr($0,3,10)
  printf "Permanenza:     %s\n", substr($0,13,2)
  printf "Cognome:        [%s]\n", substr($0,15,50)
  printf "Nome:           [%s]\n", substr($0,65,30)
  printf "StatoNascita:   %s\n",  substr($0,117,9)
  printf "Cittadinanza:   %s\n",  substr($0,126,9)
  printf "TipoDoc:        [%s]\n", substr($0,135,5)
}'

# PASSO 4 — Se la verifica è OK, caricare manualmente sul portale PS
# Accedere a: https://alloggiatiweb.poliziadistato.it
# Navigare in: Invio File → selezionare il file → confermare
```

**Esito atteso:** il portale PS mostra `"Operazione completata con successo"` e la schedina appare nell'archivio. Se il portale restituisce un errore, annotare il codice errore e consultare §6.4.
