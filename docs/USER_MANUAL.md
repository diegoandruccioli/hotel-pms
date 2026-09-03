# Hotel PMS — Manuale Operativo

**Versione:** 1.4 — 2026-09-01 (avviso imposta di soggiorno non configurata al check-in; storno dell'imposta alla rimozione di un ospite; proroga non tassa più ospiti già partiti; protezione da salvataggio concorrente estesa a soggiorni/ospiti; chiarito comportamento imposta di soggiorno su fatture già emesse)
**Destinatari:** Receptionist, Owner, Admin
**Lingua sistema:** Italiano / Inglese (selezionabile)

---

## 1. Attori e Ruoli

| Ruolo | Accesso | Chi è |
|-------|---------|-------|
| **RECEPTIONIST** | Dashboard, Ospiti (anagrafica, senza export/eliminazione), Prenotazioni, Preventivi, Soggiorni, Billing, Ristorante (ordini, non gestione menu), Calendario, Tariffe (sola visualizzazione), Housekeeping, Camere | Personale di front desk |
| **OWNER** | Tutto di RECEPTIONIST + export/eliminazione Ospiti (GDPR), modifica Tariffe, gestione Menu F&B, Dashboard Proprietario, Gestione Utenti, Profilo Hotel, Impostazioni Sistema/Privacy/Imposta di Soggiorno | Proprietario dell'hotel |
| **ADMIN** | Stesso accesso di OWNER — nel routing attuale non esiste una pagina riservata al solo ADMIN | Amministratore di sistema |

> **Nota:** a differenza di quanto si potrebbe pensare dal nome, OWNER e ADMIN hanno oggi lo **stesso** accesso alle pagine (inclusa Gestione Utenti e Profilo Hotel). La distinzione tra i due ruoli è organizzativa, non tecnica.

> Al primo accesso, il sistema obbliga il cambio della password temporanea. Non è possibile operare finché non viene impostata una password personale.

---

## 2. Panoramica Schermate

| Percorso | Nome | Accesso | Descrizione |
|----------|------|---------|-------------|
| `/` | Dashboard | Tutti | Statistiche del giorno: ospiti attivi, prenotazioni, check-in/check-out attesi, camere disponibili |
| `/guests` | Ospiti | Tutti | Anagrafica ospiti: ricerca, creazione, modifica; eliminazione ed export GDPR visibili solo a OWNER/ADMIN |
| `/reservations` | Prenotazioni | Tutti | Lista prenotazioni con filtri; nuova prenotazione; modifica/cancellazione |
| `/quotations` | Preventivi | Tutti | Lista preventivi con stato (bozza/inviato/accettato/rifiutato/scaduto); nuovo preventivo |
| `/quotations/new` | Nuovo preventivo | Tutti | Form creazione preventivo: destinatario, una o più opzioni camere/prezzo, validità |
| `/quotations/:id` | Dettaglio preventivo | Tutti | Vista preventivo; invio email, conversione in prenotazione, anteprima/scarico PDF, duplicazione |
| `/quotations/:id/edit` | Modifica preventivo | Tutti | Modifica di un preventivo ancora in stato bozza |
| `/stays` | Soggiorni | Tutti | Lista check-in attivi; proroga check-out, gestione ospiti, check-out, report Alloggiati PS |
| `/stays/check-in/:id` | Check-in da prenotazione | Tutti | Form check-in per prenotazione esistente con campi PS |
| `/stays/walk-in` | Check-in walk-in | Tutti | Check-in diretto senza prenotazione |
| `/billing` | Fatturazione | Tutti | Fatture e pagamenti; registrazione pagamenti |
| `/restaurant` | Ristorante | Tutti | Ordini F&B; gestione voci menu visibile solo a OWNER/ADMIN |
| `/calendar` | Calendario | Tutti | Planning board e vista mensile prenotazioni |
| `/housekeeping` | Housekeeping | Tutti | Status pulizia camere; aggiornamento rapido |
| `/rooms` | Camere | Tutti | Inventario camere fisiche e tipologie; gestione status |
| `/rates` | Tariffe | Tutti (modifica: OWNER, ADMIN) | Calendario prezzi per tipo camera/giorno; applicazione di tariffe stagionali su un periodo |
| `/settings` | Impostazioni (hub) | Tutti | Punto d'accesso alle sotto-pagine impostazioni sotto |
| `/settings/profile` | Profilo utente | Tutti | Informazioni account personale |
| `/settings/password` | Cambio password | Tutti | Cambio password personale |
| `/settings/accessibility` | Accessibilità | Tutti | Opzioni di accessibilità dell'interfaccia |
| `/settings/appearance` | Aspetto | Tutti | Tema chiaro/scuro, lingua IT/EN |
| `/settings/system` | Sistema | OWNER, ADMIN | Impostazioni email e invio automatico Alloggiati |
| `/settings/city-tax` | Imposta di Soggiorno | OWNER, ADMIN | Applicabilità, categoria struttura, tariffe per notte, recupero soggiorni non tassati (backfill) |
| `/settings/privacy` | Privacy | OWNER, ADMIN | Anni di conservazione dati ospite (retention GDPR/TULPS) |
| `/owner-dashboard` | Report Proprietario | OWNER, ADMIN | Revenue, occupancy, ADR, RevPAR; export CSV |
| `/admin/users` | Gestione Utenti | OWNER, ADMIN | Crea, modifica, disattiva account receptionist/owner/admin |
| `/profile/hotel` | Profilo Hotel | OWNER, ADMIN | Nome, indirizzo, PIVA/CF, logo, toggle Alloggiati automatico, credenziali PS |

---

## 3. Procedure Operative

### 3.0 Configurazione iniziale del sistema (primo avvio)

Al primo avvio del sistema, seguire questa sequenza prima di iniziare le operazioni:

1. **Login** con le credenziali di default (`admin` / `password`)
2. **Cambio password obbligatorio** — il sistema reindirizza automaticamente a `/profile`.
   Impostare una password sicura (policy: ≥16 caratteri, 2 maiuscole, 2 cifre, 2 caratteri speciali).
3. **Profilo Hotel** → Menu → icona utente → **Profilo Hotel** (`/profile/hotel`)
   Inserire: nome struttura, indirizzo, P.IVA, Codice Fiscale. Senza questi dati le fatture PDF non saranno complete.
4. **Tipi camera** → Menu → **Camere** → sezione **Tipologie** → pulsante **Aggiungi tipo**
   Definire le tipologie disponibili (Singola, Doppia, Suite, ecc.) con tariffa e capacità.
5. **Camere** → Aggiungi le camere fisiche con numero di stanza e tipo associato.
6. **Menu F&B** → Menu → **Ristorante** → sezione **Gestione menu** (solo OWNER/ADMIN)
   Inserire le voci del bar/ristorante con nome, categoria, prezzo e disponibilità.
7. **Imposta di Soggiorno** → Menu → **Impostazioni** → **Sistema** → **Imposta di Soggiorno** (solo OWNER/ADMIN)
   Impostare applicabilità, categoria struttura e tariffe per notte (vedi §3.14). Senza questa configurazione il check-in non applica l'imposta.
8. **Utenti** → Menu → **Gestione Utenti** → **Aggiungi Utente** (solo OWNER/ADMIN)
   Creare gli account per il personale (receptionist, altri owner/admin). Comunicare username e password temporanea via canale sicuro. Al primo accesso l'utente dovrà impostare una nuova password.

Solo dopo questi passi il sistema è operativo per ricevere prenotazioni e gestire i soggiorni.

---

### 3.1 Login e Primo Accesso

1. Accedere all'URL del sistema (es. `http://localhost:5173`)
2. Inserire **Username** e **Password**
3. Se è il primo accesso o la password è temporanea, il sistema reindirizza alla pagina cambio password
4. Inserire la nuova password (minimo 8 caratteri) e confermarla
5. Dopo il cambio, si accede automaticamente alla Dashboard

---

### 3.2 Creare una Prenotazione

1. Menu → **Prenotazioni** → pulsante **Nuova Prenotazione**
2. **Step 1 — Ospite Principale:**
   - Cerca un ospite esistente per nome, cognome o email
   - Se l'ospite non esiste, clicca **Crea nuovo ospite** e compila il form (nome, cognome, email, telefono, città, GDPR consent)
3. **Step 2 — Dettagli Prenotazione:**
   - Seleziona date di check-in e check-out
   - Seleziona il numero di ospiti attesi
   - Seleziona una o più camere disponibili
4. Clicca **Conferma Prenotazione**
5. La prenotazione appare nella lista in stato **CONFIRMED**

**Edge case:** Se le date selezionate si sovrappongono con una prenotazione esistente per la stessa camera, il sistema mostra un errore 409 e impedisce la creazione.

**Nota — prenotazione già in check-in:** Se si riapre una prenotazione il cui soggiorno è già attivo (stato **CHECKED_IN**), il form mostra un banner: *"Questa prenotazione è già in check-in. Le modifiche qui non aggiornano il soggiorno in corso: usa il pannello soggiorni per proroghe, cambio ospiti o partenza anticipata."* con collegamento diretto alla pagina Soggiorni. I campi data/camere diventano **non modificabili** (sola lettura) in questo caso, non solo avvisati: il soggiorno già aperto non ha alcun collegamento "vivo" con la prenotazione di origine — la modifica di date o camere è bloccata anche a livello di sistema (non solo nell'interfaccia), quindi non ha effetto nemmeno chiamando direttamente il sistema — usare §3.5a/§3.5b/§3.5c per agire sul soggiorno (proroga, gestione ospiti, cambio camera).

---

### 3.2a Creare e Gestire un Preventivo

Un preventivo permette di proporre a un potenziale ospite una o più opzioni di soggiorno con prezzo, senza impegnare subito camere come una prenotazione.

**Creare un preventivo:**
1. Menu → **Preventivi** → pulsante **Nuovo preventivo**
2. **Destinatario**: cerca un ospite esistente per nome/email oppure inserisci un **Nuovo contatto** (nome, cognome, email)
3. **Dettagli soggiorno**: crea una o più **Opzioni** (fino a 5) tramite il pulsante **Aggiungi opzione**; per ciascuna opzione seleziona date di check-in/check-out, ospiti previsti e camere — il totale dell'opzione si aggiorna in tempo reale
4. Imposta **Valido fino al** (default: oggi + 7 giorni)
5. Clicca **Salva** — il preventivo appare in stato **DRAFT**

**Inviare, convertire, gestire un preventivo** (dalla pagina di dettaglio `/quotations/:id`):
- **Invia** (o **Reinvia**): invia il preventivo via email all'ospite; se l'invio fallisce, un banner rosso resta visibile finché non si reinvia
- **Converti**: se il preventivo ha una sola opzione, la converte direttamente in prenotazione; se ne ha più di una, apre una scelta dell'opzione da confermare — al termine si viene portati alla nuova prenotazione
- **Anteprima PDF / Scarica PDF**: apre/scarica il documento preventivo
- **Duplica**: crea una copia modificabile
- **Rifiuta**: richiede conferma, porta lo stato a **DECLINED**
- **Elimina**: richiede conferma, sempre disponibile

**Edge case:** Se non arriva risposta entro la data **Valido fino al**, il preventivo passa automaticamente allo stato **EXPIRED** e non è più convertibile — occorre crearne uno nuovo.

---

### 3.3 Check-in da Prenotazione

1. Menu → **Prenotazioni** → trova la prenotazione → pulsante **Check In**  
   *oppure*  
   Menu → **Soggiorni** → pulsante **Nuovo Check-in** → seleziona la prenotazione
2. Il form si apre con dati precompilati dall'ospite principale
3. Per ogni ospite aggiunto al soggiorno, compilare i campi Alloggiati PS:
   - **Tipo ospite**: Singolo / Capofamiglia / Familiare / Capogruppo / Membro Gruppo
   - **Sesso, Data di nascita, Stato di nascita**
   - **Comune di nascita** (solo se italiano; altrimenti solo lo Stato)
   - **Tipo documento, Numero documento, Stato/Comune rilascio**
   - Gli ospiti FAMILIARE e MEMBRO_GRUPPO non compilano i campi documento
4. Aggiungere ulteriori ospiti con il pulsante **Aggiungi ospite**
5. Clicca **Conferma Check-in**
6. Il sistema:
   - Crea il soggiorno e marca la camera come **OCCUPIED**
   - Apre una fattura con totale iniziale 0, con addebito **camera + notte** e, se configurata (§3.14), **imposta di soggiorno** per ogni notte
   - Se `alloggiatiAutoSend` è abilitato, invia i dati al portale PS via SOAP

**Avviso imposta di soggiorno non configurata:** se il comune/categoria/tariffa non risultano configurati per oggi, un banner *"Imposta di soggiorno non configurata"* appare **prima** della conferma (il check-in può comunque procedere) e un toast informativo appare **dopo**, se il check-in si conclude senza che l'imposta sia stata addebitata — vedi §3.14 per configurarla. Il banner controlla solo la data odierna: un buco di configurazione futuro (es. una tariffa non ancora inserita per il mese prossimo) non viene segnalato qui, solo dal riepilogo soggiorni non tassati (Dashboard) e dal backfill (§3.14d).

**Edge case:** Se il portale PS non risponde, il check-in viene comunque completato. Il badge **Inviato PS** non appare nella riga soggiorno e il report può essere inviato manualmente in seguito.

---

### 3.4 Check-in Walk-in (senza prenotazione)

1. Menu → **Soggiorni** → pulsante **Nuovo Check-in** → scegli **Walk-in**  
   *oppure*  
   Accedi direttamente a `/stays/walk-in`
2. Cerca o crea l'ospite principale
3. Seleziona la camera disponibile e la data di check-out prevista
4. Compila i campi Alloggiati PS (stessi del check-in normale)
5. Clicca **Conferma Check-in Walk-in**

Vale lo stesso avviso di imposta di soggiorno non configurata descritto in §3.3.

---

### 3.5 Check-out

1. Menu → **Soggiorni** → trova il soggiorno attivo → pulsante **Check-out**
2. Il sistema verifica che la fattura sia in stato **PAID**
3. Se la fattura non è saldata, il check-out è bloccato — registrare prima il pagamento (vedi §3.7)
4. Conferma il check-out
5. La camera passa in stato **DIRTY** (da pulire)

**Colonne Camera e Ospite nella lista Soggiorni:** La colonna "Camera" mostra il numero camera (es. "102") e la colonna "Ospite" mostra "Cognome Nome" dell'ospite principale al posto degli UUID troncati. Questo vale per i soggiorni creati dopo l'aggiornamento (G5); i soggiorni precedenti mostrano ancora l'ID troncato.

---

### 3.5a Prorogare un Soggiorno Aperto

1. Menu → **Soggiorni** → sul soggiorno in stato **CHECKED_IN**, pulsante **Proroga**
2. La finestra mostra il **Check-out attuale** e un campo **Nuova data di check-out** (precompilato a +1 giorno)
3. Clicca **Conferma**
4. Il sistema verifica che la camera sia disponibile per le notti aggiunte e registra gli addebiti supplementari (pernottamento + imposta di soggiorno, se applicabile) sulla fattura del soggiorno — l'imposta di soggiorno viene calcolata solo sugli ospiti ancora presenti: chi ha già registrato una partenza anticipata prima dell'inizio della proroga non viene tassato per le notti aggiunte

**Edge case:** Se la camera è già occupata da un'altra prenotazione nelle notti richieste, la proroga viene rifiutata e l'errore compare direttamente nella finestra — scegliere un'altra data o cambiare camera.

**Salvataggio concorrente:** se un altro utente ha già modificato lo stesso soggiorno (es. proroga effettuata da un'altra scheda rimasta aperta) tra l'apertura della finestra e la conferma, il sistema rifiuta il salvataggio invece di sovrascrivere in silenzio — ricaricare la pagina e riprovare.

---

### 3.5b Gestire gli Ospiti di un Soggiorno Aperto

1. Menu → **Soggiorni** → sul soggiorno **CHECKED_IN**, clicca il numero nella colonna **Ospiti**
2. Si apre l'elenco degli ospiti del soggiorno, ciascuno con badge di stato (Principale / Inviato / Da ritrasmettere / Partito il [data])

Azioni disponibili per ogni ospite:
- **Modifica** — corregge i dati anagrafici/documento già inseriti; se un altro utente ha già salvato una modifica sullo stesso ospite nel frattempo, il salvataggio viene rifiutato invece di sovrascrivere in silenzio — ricaricare e riprovare
- **Registra partenza** — segna una partenza anticipata di quel singolo ospite (data a scelta), senza rimuoverlo dallo storico del soggiorno
- **Rendi principale** — promuove un ospite non principale a intestatario del soggiorno
- **Rimuovi** — elimina l'ospite dal soggiorno; **non disponibile** se l'ospite è già stato trasmesso ad Alloggiati Web (usare "Registra partenza") o se è l'ospite principale (promuovere prima un altro ospite). Se per quell'ospite era già stato addebitato un supplemento di imposta di soggiorno (perché aggiunto a soggiorno già in corso), il sistema lo storna automaticamente dalla fattura, se questa è ancora aperta

Per aggiungere un nuovo ospite al soggiorno già aperto: pulsante **Aggiungi Ospite** in fondo alla finestra, compilare gli stessi campi Alloggiati PS del check-in e salvare. Se il soggiorno ha già una fattura aperta, il sistema addebita subito l'imposta di soggiorno per le notti restanti di quel nuovo ospite.

---

### 3.5c Cambiare Camera a un Soggiorno Aperto

1. Menu → **Soggiorni** → sul soggiorno **CHECKED_IN**, pulsante **Cambia camera**
2. La finestra mostra la camera attuale e un menu con le camere di destinazione **già filtrate**: solo camere **pulite**, **libere** per le notti restanti del soggiorno, e con **capienza sufficiente** per gli ospiti ancora presenti (chi ha già registrato una partenza anticipata non conta)
3. Seleziona la camera e clicca **Conferma**
4. Il sistema sposta il soggiorno da subito: la camera lasciata passa a **Da pulire**, quella nuova a **Occupata**

**Prezzo**: se la nuova camera ha la stessa tipologia di quella lasciata, il prezzo non cambia. Se la tipologia è diversa, il sistema ricalcola le notti restanti alla tariffa della nuova camera — le notti già trascorse restano addebitate al prezzo originale, mai ricalcolate — e questo richiede che la fattura del soggiorno sia ancora aperta.

**Edge case:** il sistema rifiuta lo spostamento (errore, nessuna modifica effettuata) se la camera scelta non è pulita, non ha capienza sufficiente, è già prenotata da un'altra prenotazione nelle notti restanti, o — solo quando la tipologia cambia — se la fattura del soggiorno non è più aperta.

---

### 3.6 Ordine F&B con addebito su camera

1. Menu → **Ristorante** → pulsante **Nuovo Ordine**
2. Inserire il **Stay ID** del soggiorno a cui addebitare
3. Selezionare gli articoli dal menu con le quantità
4. Clicca **Crea Ordine** — l'ordine è in stato **PENDING**
5. Nella lista ordini, clicca **Conferma** sull'ordine
6. Il sistema addebita automaticamente l'importo sulla fattura del soggiorno

---

### 3.7 Registrare un pagamento

1. Menu → **Fatturazione** → trova la fattura → pulsante **Registra Pagamento**
2. Inserire l'importo e selezionare il metodo di pagamento (Contanti, Carta, Bonifico, ecc.)
3. Clicca **Salva**
4. Quando l'importo pagato raggiunge il totale della fattura, lo stato passa automaticamente a **PAID**

---

### 3.7a Esportare la fattura elettronica (FatturaPA) per il commercialista

Il sistema **non invia** le fatture allo SDI (Sistema di Interscambio) — produce un
export XML corretto e validato, pronto da consegnare al commercialista o da importare
nel software di terze parti già in uso (TeamSystem, Zucchetti, Danea, ecc.).

**Export di una singola fattura:**
1. Menu → **Fatturazione** → apri la fattura → pulsante **Scarica FatturaPA**
2. Il sistema valida l'XML contro lo schema ufficiale dell'Agenzia delle Entrate prima
   di generarlo — se qualcosa non torna (es. indirizzo ospite incompleto), l'errore è
   segnalato subito invece di produrre un file che il commercialista scoprirebbe errato
3. **Attenzione**: dal momento in cui una fattura viene esportata, i suoi campi fiscali
   (importo, numero fattura, tipo documento) **non sono più modificabili** — un
   tentativo restituisce l'errore `INVOICE_LOCKED_AFTER_EXPORT`. Per correggere una
   fattura già esportata serve una nota di credito (funzionalità non ancora disponibile,
   vedi `docs/ROADMAP.md`)

**Export di un intero periodo (consegna al commercialista):**
1. Menu → **Fatturazione** → **Esporta periodo** → seleziona data inizio/fine
2. Il sistema produce uno ZIP con un XML per ogni fattura idonea del periodo, più un
   indice CSV riepilogativo. Le fatture con dati incompleti vengono escluse ed elencate
   nell'indice con l'errore specifico, senza bloccare l'export delle altre

---

### 3.7b Configurazione iniziale credenziali portale PS
(intervento tecnico obbligatorio — una tantum per installazione)

Prima di poter inviare i dati al portale della Polizia di Stato,
il tecnico IT che gestisce il sistema deve configurare le
credenziali nel file `.env` del server.

**Cosa comunicare al tecnico IT:**
1. Username dell'account struttura su `alloggiatiweb.poliziadistato.it`
2. Password dell'account
3. Chiave Web Service — generabile dal portale PS:
   accedere a `alloggiatiweb.poliziadistato.it` →
   icona account → "Chiave Web Service" → "Genera nuova chiave"
4. Comunicare al tecnico se si vuole partire in modalità TEST
   (nessun invio reale) o PRODUZIONE (invio con effetto legale)

**Cosa fa il tecnico (non richiede azione dell'admin):**
Il tecnico inserisce le credenziali nel file di configurazione
del server e riavvia il servizio (operazione di 5 minuti).

**Quando ripetere questa procedura:**
- La Chiave Web Service ha una scadenza — quando l'invio
  inizia a fallire sistematicamente, contattare il tecnico
  per rigenerare la chiave dal portale PS e aggiornarla.
- In caso di cambio password sul portale PS.

**Come verificare che la configurazione sia attiva:**
Dopo la configurazione, eseguire un check-in di test e
verificare il badge "PS Portal" nella lista Soggiorni:
- Badge verde = credenziali corrette, invio riuscito
- Badge rosso = credenziali errate o portale PS non raggiungibile
  → contattare il tecnico IT

**Nota sulla modalità TEST vs PRODUZIONE:**
Sono due livelli indipendenti:

| | Modalità TEST | Modalità PRODUZIONE |
|---|---|---|
| Chi la imposta | Tecnico IT (`.env`) | Tecnico IT (`.env`) |
| Cosa fa | Valida i dati, non registra | Invio reale alla Questura |
| Quando usarla | Durante il collaudo | Operatività normale |

Il toggle "Invio automatico" nel Profilo Hotel controlla **solo**
se l'invio avviene in automatico ad ogni check-in oppure
solo manualmente — non controlla TEST vs PRODUZIONE.

---

### 3.8 Generare e Inviare il Report Alloggiati PS

1. Menu → **Soggiorni** → sezione **Report Portale PS** in fondo alla pagina
2. Selezionare la data del rapporto
3. Clicca **Genera e Scarica** — scarica il file `.txt` in formato 168 caratteri per upload manuale sul portale
4. Per il formato JSON (debug): clicca **Scarica export JSON** (visibile solo a OWNER/ADMIN)
5. *(Solo OWNER/ADMIN)* Clicca **Invia a Questura** — appare una finestra di conferma; confermando, il sistema invia il report al portale PS via SOAP in tempo reale
   - Risposta di successo: toast verde "Lista inviata al portale PS con successo"
   - Risposta di errore portale (422): toast rosso con il messaggio ricevuto dal portale
   - Errore di rete: toast rosso generico — riprovare più tardi

**Invio automatico:** Se il toggle `alloggiatiAutoSend` è attivo nel Profilo Hotel, l'invio avviene automaticamente ad ogni check-in. Il badge **Inviato PS** appare nella colonna PS della lista soggiorni.

> **Importante — modalità DRY_RUN:** Se il sistema è configurato con `ALLOGGIATI_DRY_RUN=true`
> (impostazione predefinita in ambienti di sviluppo e staging), l'invio viene indirizzato
> all'endpoint di **test** del portale PS e **non costituisce adempimento** ai fini dell'art. 109 TULPS.
> Per l'invio effettivo ai fini di legge, verificare con l'amministratore di sistema che
> `ALLOGGIATI_DRY_RUN=false` e che le credenziali WsKey reali siano configurate nel file `.env`.

---

### 3.9 Housekeeping — Aggiornamento status camere

1. Menu → **Housekeeping** → lista camere con status corrente
2. Clicca il pulsante di aggiornamento accanto a una camera
3. Seleziona il nuovo status: **Pulita / Da Pulire / In Manutenzione**
4. Il cambio è istantaneo

---

### 3.9a Calendario Tariffe

1. Menu → **Tariffe** — griglia con i tipi camera in riga e i giorni del mese in colonna (frecce per cambiare mese)
2. Ogni cella mostra il prezzo risolto per quella camera/giorno; le celle coperte da una tariffa stagionale hanno un bordo/pallino colorato coerente con la legenda; senza stagione si vede il prezzo base

**Applicare un prezzo a un periodo** (solo OWNER/ADMIN):
1. Seleziona un intervallo di celle trascinando il mouse (o da tastiera: Invio/Spazio per iniziare, Maiusc+Invio/Spazio per estendere)
2. Clicca **Applica prezzo** sulla pillola di selezione (o dal pulsante in alto, per aprire la selezione manualmente)
3. Nella finestra: conferma/estendi i tipi camera, le date **Dal**/**Al**, imposta **Prezzo a notte** e un **Nome** opzionale (es. "Alta stagione")
4. Salva — crea o aggiorna la tariffa stagionale per quei tipi camera e periodo

**Edge case:** Se il periodo scelto si sovrappone a una tariffa già esistente per la stessa tipologia, il sistema rifiuta il salvataggio (409) con un messaggio dedicato.

Un utente RECEPTIONIST vede il calendario ma non ha il pulsante **Applica prezzo**: la pagina è in sola visualizzazione.

---

### 3.10 Creare un nuovo utente (solo OWNER/ADMIN)

1. Menu → **Gestione Utenti** → pulsante **Aggiungi Utente**
2. Compilare: username, email, password temporanea, ruolo (RECEPTIONIST / OWNER / ADMIN), hotel ID
3. Clicca **Salva**
4. Al primo accesso, il nuovo utente dovrà cambiare la password

Per disattivare un utente: pulsante **Disattiva** accanto all'utente. L'account viene soft-deleted (inattivo ma recuperabile).

---

### 3.11 Configurare il Profilo Hotel (solo OWNER/ADMIN)

1. Menu → icona utente → **Profilo Hotel** (o naviga a `/profile/hotel`)
2. Compilare: nome hotel, indirizzo, PIVA, Codice Fiscale
3. Per il logo: seleziona il file e carica
4. Per l'invio automatico Alloggiati: spuntare/rimuovere il toggle **Invio automatico al portale PS**
5. Clicca **Salva**

---

### 3.12 Reset password utente (solo OWNER/ADMIN)

Usare quando un utente ha dimenticato la password o per motivi di sicurezza (es. sospetta compromissione).

1. Menu → **Gestione Utenti** → trova l'utente nella lista
2. Clicca **Reset password** accanto all'utente
3. Inserire la nuova password temporanea nel campo **Nuova password** (minimo 16 caratteri, 2 maiuscole, 2 cifre, 2 caratteri speciali)
4. Confermare la password nel campo **Conferma password**
5. Clicca **Reset password**
6. Il sistema:
   - Sostituisce la password dell'utente con quella nuova
   - Attiva il flag `mustChangePassword` — al prossimo accesso l'utente sarà obbligato a scegliere una password personale
   - Invalida tutte le sessioni attive dell'utente (token esistenti non più validi)
7. Comunicare la nuova password temporanea all'utente via canale sicuro (telefono, messaggio cifrato)

**Nota:** Non è possibile resettare la propria password da questa pagina — usare il Profilo → Cambia Password.

---

### 3.13 Gestione Menu F&B (solo OWNER e ADMIN)

La pagina Ristorante include una sezione di gestione del menu
visibile solo agli utenti con ruolo OWNER o ADMIN.

**Aggiungere una voce menu:**
1. Vai su Ristorante → sezione "Gestione menu"
2. Clicca "Aggiungi voce"
3. Compila: nome (obbligatorio), categoria, prezzo (≥ 0),
   descrizione (opzionale), disponibile (toggle)
4. Salva — la voce appare immediatamente nella lista ordini

**Modificare una voce menu:**
1. Clicca l'icona matita sulla riga della voce
2. Modifica i campi desiderati e salva

**Eliminare una voce menu:**
1. Clicca l'icona cestino sulla riga della voce
2. Conferma nel dialog  
Nota: non è possibile eliminare una voce con ordini in corso
(stato PENDING). Chiudi o completa prima gli ordini attivi.

Il menu è specifico per hotel: ogni struttura gestisce
il proprio listino indipendentemente.

---

### 3.14 Configurare l'Imposta di Soggiorno (solo OWNER/ADMIN)

Menu → **Impostazioni** → **Sistema** → **Imposta di Soggiorno** (`/settings/city-tax`).

**a) Applicabilità dell'imposta**
Select con tre opzioni: **Da dichiarare** (default), **Applicabile**, **Non applicabile** (il comune non la prevede). Il salvataggio è automatico al cambio valore; se fallisce, il valore torna a quello precedente con un toast di errore.

**b) Categoria struttura**
1. Compila **Categoria** (es. "4 stelle", max 20 caratteri) e **Valida dal**
2. Clicca **Aggiungi categoria** — registrare una nuova categoria chiude automaticamente quella corrente
3. La tabella sotto mostra lo storico: Categoria / Valida dal / Valida fino al

**c) Tariffe imposta di soggiorno**
1. Compila **Categoria**, **Importo a notte** (obbligatorio, €), **Notti massime tassabili** (opzionale), **Esenzione sotto età** (opzionale, 0-120), **Valida dal**, **Nota** (opzionale, max 200 caratteri)
2. Clicca **Aggiungi tariffa** — una nuova tariffa per la stessa categoria chiude automaticamente quella corrente
3. La tabella mostra: Categoria / Importo a notte / Notti massime / Esenzione età / Valida dal / Valida fino al

**Edge case:** il sistema rifiuta (409) una tariffa che si sovrappone a una già esistente per la stessa categoria, o se il comune risulta non configurato, o se la data non è successiva a quella corrente — il messaggio d'errore indica la causa specifica.

**d) Recupero soggiorni non tassati (backfill)**
Serve a caricare retroattivamente l'imposta su soggiorni passati non ancora addebitati (es. dopo aver configurato le tariffe per la prima volta).
1. Clicca **Verifica soggiorni scoperti** — mostra una tabella con Check-in / Importo / Stato per ogni soggiorno interessato (nessun addebito viene ancora effettuato, è solo un'anteprima)
2. Le righe non addebitabili indicano il motivo: fattura già chiusa, periodo non ancora configurato, o addebito non riuscito
3. Se ci sono righe addebitabili, clicca **Conferma addebito** — addebita solo le fatture ancora aperte, lasciando intoccate quelle chiuse
4. Al termine appare un toast "Addebitati N soggiorni." e il pulsante di conferma scompare (l'operazione non è ripetibile sullo stesso risultato — rilanciare la verifica per un nuovo giro)

**Comportamento voluto — le fatture già emesse non cambiano:** una volta che l'imposta di soggiorno è stata calcolata per un soggiorno, resta quella anche se in seguito si modifica la tariffa. Non è un bug: è un requisito di controllo fiscale, lo stesso motivo per cui una fattura esportata non è più modificabile (§3.7a) — un importo già addebitato non deve cambiare sotto i piedi dell'operatore. Per applicare una nuova tariffa a soggiorni già in corso serve una nuova azione esplicita, non un ricalcolo automatico.

---

### 3.15 Configurare la Conservazione Dati Ospite / Privacy (solo OWNER/ADMIN)

Menu → **Impostazioni** → **Sistema** → **Privacy** (`/settings/privacy`).

1. Campo **Anni di conservazione**: quanti anni tenere i dati ospite prima che siano eleggibili per cancellazione
2. Sotto il campo, un riquadro informativo mostra il **minimo di legge TULPS** e, a titolo puramente informativo, il **minimo di legge fiscale** (fatture) — quest'ultimo non vincola il campo, che è gestito separatamente dal servizio di fatturazione
3. Clicca **Salva**

**Edge case:** se il valore inserito è inferiore al minimo TULPS, il salvataggio viene rifiutato lato client con il messaggio "Deve essere almeno N anni (obbligo TULPS)".

---

### 3.16 Esportare i Dati di un Ospite (GDPR) (solo OWNER/ADMIN)

1. Menu → **Ospiti** → sulla riga dell'ospite, azione **Esporta dati (GDPR)**
2. Si apre una finestra di conferma che ricorda: *"Il file scaricato conterrà i dati personali dell'ospite (anagrafica, documenti, soggiorni e fatture). È una comunicazione di dati personali: consegnalo solo alla persona a cui appartengono i dati o a chi ne ha titolo."*
3. Clicca **Scarica** — scarica un file JSON (`guest-export-{id}.json`) con tutti i dati dell'ospite

**Eliminare un ospite:** azione **Elimina** sulla stessa riga (solo OWNER/ADMIN) → conferma nel dialog. Se l'ospite ha soggiorni o fatture da conservare per obblighi di legge, il sistema risponde 451 (Legal Hold) e non elimina nulla — vedi §4.

---

## 4. Edge Case Frequenti

| Situazione | Comportamento | Azione consigliata |
|------------|--------------|-------------------|
| Ospite ha soggiorni attivi → si tenta cancellazione | Sistema risponde 451 (Legal Hold) | Non cancellare ospiti con soggiorni attivi o fatture aperte |
| Camera già occupata nelle date selezionate | Errore 409 Conflict | Scegliere date diverse o camera diversa |
| Portale PS irraggiungibile al check-in | Check-in completato, badge PS assente | Usare il pulsante **Invia a Questura** (Soggiorni → Report Portale PS) quando il portale torna disponibile |
| Pagamento parziale: il check-out è bloccato | Sistema rifiuta il checkout | Registrare il saldo rimanente prima di procedere |
| Password temporanea al primo login | Redirect obbligatorio al cambio password | Inserire e confermare la nuova password |
| Token JWT scaduto durante l'uso | L'app rinnova il token silenziosamente in background | Nessuna azione — l'utente non vede interruzioni |
| Lookup stati/comuni vuoto al check-in | Campi dropdown vuoti | Verificare connettività con il portale PS; le lookup vengono caricate al primo avvio |
| Preventivo non convertito entro "Valido fino al" | Passa automaticamente a EXPIRED, non più convertibile | Creare un nuovo preventivo |
| Tariffa stagionale sovrapposta a un periodo esistente | Errore 409 con messaggio dedicato | Modificare il periodo esistente invece di crearne uno nuovo, o scegliere date diverse |
| Proroga check-out su camera non disponibile nelle notti aggiunte | Proroga rifiutata, errore mostrato nella finestra | Scegliere un'altra data o cambiare camera |
| Rimozione di un ospite già trasmesso ad Alloggiati Web | Azione bloccata (tooltip esplicativo) | Usare **Registra partenza** per una partenza anticipata |
| Backfill imposta di soggiorno su fattura già chiusa | Soggiorno escluso dall'addebito, elencato con il motivo | Nessuna azione possibile da UI: la fattura chiusa non viene riaperta |
| Valore "Anni di conservazione" sotto il minimo TULPS | Salvataggio rifiutato lato client | Inserire un valore ≥ al minimo di legge mostrato in pagina |
| Due schede aperte sullo stesso soggiorno/ospite, una modifica dopo l'altra | La seconda modifica viene rifiutata (non sovrascrive in silenzio) | Ricaricare la pagina e ripetere la modifica |
| Rimozione di un ospite aggiunto a soggiorno già fatturato | L'eventuale supplemento di imposta di soggiorno già addebitato viene stornato dalla fattura, se ancora aperta | Nessuna azione — automatico |
| Proroga di un soggiorno con un ospite già partito in anticipo | L'ospite partito non viene tassato per le notti aggiunte | Nessuna azione — automatico |
| Cambio di una tariffa di imposta di soggiorno dopo che un ospite ha già fatto check-in | La sua fattura resta calcolata con la tariffa in vigore al check-in | Comportamento voluto (requisito fiscale) — non richiede intervento |
| Modifica di date/camere su una prenotazione con soggiorno già CHECKED_IN | Errore 409 (bloccato anche fuori dall'interfaccia) | Usare §3.5a/§3.5b sul soggiorno, non la prenotazione di origine |
| Cambio camera su camera non pulita, senza capienza sufficiente, o già prenotata nelle notti restanti | Spostamento rifiutato, errore mostrato nella finestra (§3.5c) | Scegliere un'altra camera tra quelle proposte |
| Cambio camera con fattura non più aperta, quando la nuova camera ha tipologia diversa | Spostamento rifiutato | Nessuna azione possibile da UI: serve una fattura aperta per ricalcolare l'addebito |
| Camera diventata "Da pulire" oggi (es. dopo un check-out) | Non compare tra le camere disponibili per il check-in **di oggi**, ma non blocca le nuove prenotazioni per date future | Rimettere la camera a "Pulita" da Housekeeping quando è pronta |
| Check-in su una camera in stato "In manutenzione" | Check-in rifiutato | Scegliere un'altra camera, o rimettere quella in manutenzione a "Pulita"/"Da pulire" da Housekeeping se il lavoro è concluso |

---

## 5. Glossario

| Termine | Significato |
|---------|-------------|
| **Stay / Soggiorno** | Il periodo di permanenza di un ospite in una camera specifica |
| **Walk-in** | Check-in senza prenotazione precedente |
| **Preventivo / Quotation** | Proposta di soggiorno con una o più opzioni di camere/prezzo, inviabile all'ospite e convertibile in prenotazione |
| **Alloggiati PS** | Report obbligatorio per legge (art. 109 TULPS) da inviare alla Polizia di Stato |
| **TULPS** | Testo Unico delle Leggi di Pubblica Sicurezza — fonte normativa che impone l'obbligo di comunicazione Alloggiati e un periodo minimo di conservazione dei relativi dati |
| **Imposta di Soggiorno** | Tassa locale a carico dell'ospite, calcolata per notte in base alla categoria della struttura e alle tariffe configurate |
| **Categoria struttura** | Classificazione dell'hotel (es. stelle) usata per determinare la tariffa di imposta di soggiorno applicabile |
| **Backfill** | Recupero retroattivo: applicazione di un addebito (es. imposta di soggiorno) a soggiorni passati non ancora regolarizzati |
| **HMAC** | Firma digitale interna tra i microservizi per garantire l'autenticità delle richieste |
| **DRY_RUN** | Modalità test del portale PS: invia i dati a un endpoint di test anziché quello reale |
| **Invoice / Fattura** | Documento che raccoglie tutti gli addebiti di un soggiorno (camere + F&B + extra + imposta di soggiorno) |
| **Soft delete** | Eliminazione logica: il dato viene marcato come inattivo ma non cancellato fisicamente |
| **Retention / Conservazione dati** | Periodo minimo per cui i dati di un ospite devono essere mantenuti prima di poter essere eleggibili per cancellazione (vincolato dal minimo TULPS) |
| **mustChangePassword** | Flag che obbliga il cambio password al prossimo login |
