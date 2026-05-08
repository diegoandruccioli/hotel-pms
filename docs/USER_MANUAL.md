# Hotel PMS — Manuale Operativo

**Versione:** 1.0 — 2026-05-07  
**Destinatari:** Receptionist, Owner, Admin  
**Lingua sistema:** Italiano / Inglese (selezionabile)

---

## 1. Attori e Ruoli

| Ruolo | Accesso | Chi è |
|-------|---------|-------|
| **RECEPTIONIST** | Dashboard, Ospiti, Prenotazioni, Soggiorni, Billing, Ristorante, Calendario, Housekeeping, Camere | Personale di front desk |
| **OWNER** | Tutto di RECEPTIONIST + Dashboard Proprietario con report finanziari | Proprietario dell'hotel |
| **ADMIN** | Tutto di OWNER + Gestione Utenti + Configurazione Hotel | Amministratore di sistema |

> Al primo accesso, il sistema obbliga il cambio della password temporanea. Non è possibile operare finché non viene impostata una password personale.

---

## 2. Panoramica Schermate

| Percorso | Nome | Accesso | Descrizione |
|----------|------|---------|-------------|
| `/` | Dashboard | Tutti | Statistiche del giorno: ospiti attivi, prenotazioni, check-in/check-out attesi, camere disponibili |
| `/guests` | Ospiti | Tutti | Anagrafica ospiti: ricerca, creazione, modifica, cancellazione |
| `/reservations` | Prenotazioni | Tutti | Lista prenotazioni con filtri; nuova prenotazione; modifica/cancellazione |
| `/stays` | Soggiorni | Tutti | Lista check-in attivi; azioni check-out; report Alloggiati PS |
| `/stays/check-in/:id` | Check-in da prenotazione | Tutti | Form check-in per prenotazione esistente con campi PS |
| `/stays/walk-in` | Check-in walk-in | Tutti | Check-in diretto senza prenotazione |
| `/billing` | Fatturazione | Tutti | Fatture e pagamenti; registrazione pagamenti |
| `/restaurant` | Ristorante | Tutti | Ordini F&B; conferma ordine con addebito su conto camera |
| `/calendar` | Calendario | Tutti | Planning board e vista mensile prenotazioni |
| `/housekeeping` | Housekeeping | Tutti | Status pulizia camere; aggiornamento rapido |
| `/rooms` | Camere | Tutti | Inventario camere fisiche e tipologie; gestione status |
| `/profile` | Profilo | Tutti | Cambio password, informazioni account |
| `/owner-dashboard` | Report Proprietario | OWNER, ADMIN | Revenue, occupancy, ADR, RevPAR; export CSV |
| `/admin/users` | Gestione Utenti | ADMIN | Crea, modifica, disattiva account receptionist/owner |
| `/profile/hotel` | Profilo Hotel | ADMIN | Nome, indirizzo, PIVA/CF, logo, toggle Alloggiati automatico |

---

## 3. Procedure Operative

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
   - Apre una fattura con totale iniziale 0
   - Se `alloggiatiAutoSend` è abilitato, invia i dati al portale PS via SOAP

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

---

### 3.5 Check-out

1. Menu → **Soggiorni** → trova il soggiorno attivo → pulsante **Check-out**
2. Il sistema verifica che la fattura sia in stato **PAID**
3. Se la fattura non è saldata, il check-out è bloccato — registrare prima il pagamento (vedi §3.7)
4. Conferma il check-out
5. La camera passa in stato **DIRTY** (da pulire)

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

### 3.8 Generare il Report Alloggiati PS

1. Menu → **Soggiorni** → sezione **Report Portale PS** in fondo alla pagina
2. Selezionare la data del rapporto
3. Clicca **Genera e Scarica** — scarica il file `.txt` in formato 168 caratteri per upload manuale sul portale
4. Per il formato JSON (debug): clicca **Scarica export JSON**

**Invio automatico:** Se il toggle `alloggiatiAutoSend` è attivo nel Profilo Hotel, l'invio avviene automaticamente ad ogni check-in. Il badge **Inviato PS** appare nella colonna PS della lista soggiorni.

---

### 3.9 Housekeeping — Aggiornamento status camere

1. Menu → **Housekeeping** → lista camere con status corrente
2. Clicca il pulsante di aggiornamento accanto a una camera
3. Seleziona il nuovo status: **Pulita / Da Pulire / In Manutenzione**
4. Il cambio è istantaneo

---

### 3.10 Creare un nuovo utente (solo ADMIN)

1. Menu → **Gestione Utenti** → pulsante **Aggiungi Utente**
2. Compilare: username, email, password temporanea, ruolo (RECEPTIONIST / OWNER / ADMIN), hotel ID
3. Clicca **Salva**
4. Al primo accesso, il nuovo utente dovrà cambiare la password

Per disattivare un utente: pulsante **Disattiva** accanto all'utente. L'account viene soft-deleted (inattivo ma recuperabile).

---

### 3.11 Configurare il Profilo Hotel (solo ADMIN)

1. Menu → icona utente → **Profilo Hotel** (o naviga a `/profile/hotel`)
2. Compilare: nome hotel, indirizzo, PIVA, Codice Fiscale
3. Per il logo: seleziona il file e carica
4. Per l'invio automatico Alloggiati: spuntare/rimuovere il toggle **Invio automatico al portale PS**
5. Clicca **Salva**

---

## 4. Edge Case Frequenti

| Situazione | Comportamento | Azione consigliata |
|------------|--------------|-------------------|
| Ospite ha soggiorni attivi → si tenta cancellazione | Sistema risponde 451 (Legal Hold) | Non cancellare ospiti con soggiorni attivi o fatture aperte |
| Camera già occupata nelle date selezionate | Errore 409 Conflict | Scegliere date diverse o camera diversa |
| Portale PS irraggiungibile al check-in | Check-in completato, badge PS assente | Inviare il report manualmente quando il portale torna disponibile |
| Pagamento parziale: il check-out è bloccato | Sistema rifiuta il checkout | Registrare il saldo rimanente prima di procedere |
| Password temporanea al primo login | Redirect obbligatorio al cambio password | Inserire e confermare la nuova password |
| Token JWT scaduto durante l'uso | L'app rinnova il token silenziosamente in background | Nessuna azione — l'utente non vede interruzioni |
| Lookup stati/comuni vuoto al check-in | Campi dropdown vuoti | Verificare connettività con il portale PS; le lookup vengono caricate al primo avvio |

---

## 5. Glossario

| Termine | Significato |
|---------|-------------|
| **Stay / Soggiorno** | Il periodo di permanenza di un ospite in una camera specifica |
| **Walk-in** | Check-in senza prenotazione precedente |
| **Alloggiati PS** | Report obbligatorio per legge (art. 109 TULPS) da inviare alla Polizia di Stato |
| **HMAC** | Firma digitale interna tra i microservizi per garantire l'autenticità delle richieste |
| **DRY_RUN** | Modalità test del portale PS: invia i dati a un endpoint di test anziché quello reale |
| **Invoice / Fattura** | Documento che raccoglie tutti gli addebiti di un soggiorno (camere + F&B + extra) |
| **Soft delete** | Eliminazione logica: il dato viene marcato come inattivo ma non cancellato fisicamente |
| **mustChangePassword** | Flag che obbliga il cambio password al prossimo login |
