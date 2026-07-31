# Brief per il consulente legale — Privacy Policy, DPA, Cookie Policy

**A cosa serve questo documento**: dare a chi scrive i tre testi legali obbligatori
(Privacy Policy, Data Processing Agreement, Cookie Policy) tutte le informazioni
tecniche verificate sul funzionamento reale del sistema, così che il lavoro legale
sia solo redazione del testo — non ricerca di che dati vengono trattati, dove
vanno, per quanto tempo. Ogni affermazione qui sotto è verificata sul codice
sorgente al 2026-07-31, non dichiarata a intuito.

**Cosa manca perché il progetto sia pronto**: solo il testo dei tre documenti.
Le route applicative (`/legal/privacy`, `/legal/cookies`) e i link (footer
login, hub Impostazioni) sono già scoperti/scoping-ready — cablarli è ~1-2h
una volta arrivato il testo (vedi §7). Nessun placeholder è esposto agli utenti
finché il testo reale non è pronto: un placeholder legale in produzione è un
rischio maggiore di nessun link (un utente potrebbe farvi legittimo affidamento).

---

## 1. Cos'è il prodotto

Hotel PMS è una piattaforma SaaS multi-tenant (un'istanza software, più hotel
clienti, dati isolati per hotel — mai condivisi tra hotel diversi, verificato
architetturalmente) per la gestione operativa alberghiera: prenotazioni,
check-in/check-out, fatturazione, F&B, comunicazione con l'ospite.

**Ruoli GDPR** (Art. 4, 24, 28):
- **Titolare del trattamento**: l'hotel cliente (raccoglie i dati dell'ospite
  per erogare il servizio di ospitalità ed eseguire gli obblighi di legge).
- **Responsabile del trattamento**: il fornitore della piattaforma (tratta i
  dati per conto dell'hotel, su istruzione, con le misure tecniche di §6).

Questa distinzione determina quale documento serve a chi: la **Privacy Policy**
è dell'hotel verso il proprio ospite (il fornitore piattaforma la prepara come
template che l'hotel personalizza e pubblica sotto il proprio nome); il **DPA**
è tra fornitore piattaforma e hotel cliente (disciplina il trattamento per
conto terzi); la **Cookie Policy** riguarda l'applicazione web stessa.

---

## 2. Dati personali trattati — per categoria di interessato

### 2.1 Ospite (guest)

| Dato | Raccolto quando | Base giuridica |
|---|---|---|
| Nome, cognome | Sempre (check-in) | Contratto (erogazione servizio alloggio) |
| Data di nascita, cittadinanza | Sempre (check-in) | **Obbligo di legge** — TULPS Art. 109, notifica Alloggiati Web al Questore entro 24h |
| Tipo e numero documento d'identità | Quasi sempre (esente solo per traveller type FAMILIARE/MEMBRO_GRUPPO — familiare/membro di un gruppo già identificato dal capofamiglia/capogruppo) | Obbligo di legge — TULPS |
| Luogo di nascita, comune di rilascio documento | Sempre | Obbligo di legge — TULPS |
| Email, telefono | Facoltativo (almeno uno richiesto se si vogliono email transazionali) | Contratto / consenso per le comunicazioni |
| Indirizzo, città, paese | Facoltativo | Contratto |
| Codice Fiscale, Partita IVA, ragione sociale, indirizzo strutturato (CAP/Comune/Provincia) | Solo se il soggiorno viene fatturato con fattura elettronica (FatturaPA) | Obbligo di legge — normativa fiscale (SDI) |

**Punto cruciale per chi scrive l'informativa**: i dati Alloggiati (riga 2-4
della tabella) **non si basano sul consenso** — sono raccolti per obbligo di
legge (TULPS) indipendentemente dalla volontà dell'ospite, e l'ospite non può
opporsi né richiederne la cancellazione prima della scadenza del periodo
TULPS (vedi §4). È l'errore più comune in questo tipo di informativa: scrivere
"previo consenso" per un dato che invece è raccolto per obbligo legale.

### 2.2 Utente staff (chi usa il gestionale — receptionist, admin, owner)

Username, email, ruolo. Password mai in chiaro (hashing Argon2id — vedi §6).
Nessun dato dell'ospite finale è raccolto per lo staff oltre a queste
credenziali operative.

---

## 3. Finalità del trattamento

1. **Adempimento TULPS** — notifica Alloggiati Web alla Polizia di Stato di
   ogni check-in entro 24h (obbligo di legge, Art. 109 TULPS).
2. **Erogazione del servizio alberghiero** — gestione prenotazione, soggiorno,
   camera, servizi F&B collegati.
3. **Fatturazione** — emissione fattura/ricevuta, invio SDI per fattura
   elettronica quando richiesto.
4. **Comunicazioni transazionali** — email di conferma prenotazione e
   riepilogo checkout (disattivabili per hotel, mai marketing/newsletter —
   il sistema non ha alcuna funzionalità di invio commerciale).

Non ci sono finalità di profilazione, marketing diretto, o cessione a terzi
per finalità commerciali proprie del fornitore piattaforma.

---

## 4. Conservazione e cancellazione (retention)

Implementato con una **doppia guardia legale** — l'ospite non può essere
cancellato/anonimizzato finché uno qualsiasi dei due vincoli è ancora attivo:

- **TULPS**: minimo 5 anni dall'ultimo soggiorno, configurabile più lungo per
  hotel (mai più corto — vincolo tecnico).
- **Fiscale**: 10 anni dall'ultima fattura (termine di accertamento fiscale
  italiano).

Trascorsi entrambi i periodi, un job notturno anonimizza (non cancella
fisicamente il record, per non rompere l'integrità referenziale di
fatture/soggiorni storici — sostituisce nome/cognome/documento con valori
anonimi) l'ospite automaticamente. L'hotel può anche anonimizzare
manualmente un ospite prima della scadenza naturale se richiesto
dall'interessato **solo se** nessuno dei due vincoli legali è più attivo —
altrimenti il sistema rifiuta l'operazione con errore esplicito (HTTP 451,
"Unavailable For Legal Reasons") indicando la data di sblocco.

Questo è il meccanismo tecnico che l'informativa deve descrivere quando parla
di "diritto alla cancellazione" (GDPR Art. 17): il diritto esiste ma è
**bilanciato da un obbligo di legge concorrente**, non assoluto.

---

## 5. Destinatari e sub-processor (per il DPA, Art. 28)

| Destinatario | Dati trasmessi | Finalità | Sede |
|---|---|---|---|
| **Portale Alloggiati Web** (Polizia di Stato — Ministero dell'Interno) | Dati anagrafici e documento di ogni ospite | Obbligo di legge TULPS | Italia (ente pubblico, non un sub-processor commerciale) |
| **Provider SMTP** per email transazionali | Nome ospite, email, dettagli prenotazione/checkout | Invio email di conferma/riepilogo | **Da definire** — oggi in sviluppo si usa un server SMTP locale (mailpit, mai usato in produzione); il consulente deve valutare il provider reale scelto per la produzione (es. SendGrid, Amazon SES, Mailgun — ciascuno ha una propria sede/DPA da agganciare) |
| **Backblaze B2** (storage backup off-site, cifrato) | Copia cifrata dell'intero database (tutti gli hotel, non solo uno — il backup è a livello di istanza, non per singolo hotel) | Continuità operativa / disaster recovery | Bucket configurato in region **eu-central-003** (infrastruttura Backblaze in UE) — **ma Backblaze Inc. è una società USA**: il consulente deve valutare se serve comunque una base giuridica per il trasferimento extra-UE (Clausole Contrattuali Standard) anche con dati fisicamente ospitati in UE, dato che l'entità legale è statunitense. I dati sono cifrati (age, chiave privata mai su un sistema Backblaze può leggere) prima dell'upload — un elemento a favore nella valutazione, ma non sostituisce l'analisi giuridica del trasferimento. |
| **Hosting applicativo** (dove girano i container in produzione) | Tutti i dati del sistema | Erogazione del servizio | **Da definire** — non ancora scelto un provider di produzione al momento di questo brief |

**Non ci sono altri destinatari.** Nessun servizio di analytics, advertising,
o tracciamento di terze parti è integrato nel prodotto (verificato: nessuno
script esterno, Content-Security-Policy che blocca risorse di terze parti
non elencate esplicitamente).

---

## 6. Misure di sicurezza tecniche (per il DPA Art. 32)

Elenco verificabile, non promesso a vuoto — ogni voce corrisponde a codice
realmente presente e testato:

- **Isolamento multi-tenant**: ogni record porta un `hotel_id`; ogni query è
  scoped a quell'hotel; una regola automatica (ArchUnit, gira ad ogni build)
  impedisce che una futura query dimentichi questo filtro.
- **Password**: hashing Argon2id (memory-hard, resistente ad attacchi GPU),
  mai testo in chiaro, mai reversibile.
- **Trasporto**: HTTPS end-to-end; comunicazione interna tra i servizi
  autenticata con firma HMAC-SHA256 e protezione anti-replay (timestamp +
  nonce, ogni richiesta è valida una sola volta).
- **Cookie di sessione**: `httpOnly` (mai leggibili da JavaScript, immuni a
  furto via XSS), `Secure`, `SameSite=Strict`.
- **Backup**: cifrato (age, chiave asimmetrica — il sistema che produce il
  backup non può mai decifrarlo da solo) prima di lasciare l'infrastruttura
  applicativa, copia off-site separata dal database di produzione.
- **Audit log strutturato**: eventi di autenticazione, check-in/check-out,
  operazioni di fatturazione registrati con timestamp e attore.
- **Rate limiting**: protezione da tentativi di forza bruta sul login.
- **Controllo accessi basato su ruolo (RBAC)**: RECEPTIONIST/MANAGER/ADMIN/
  OWNER con permessi distinti, verificati sia lato interfaccia sia lato server.

---

## 7. Cookie utilizzati (per la Cookie Policy)

Inventario esaustivo — verificato sul codice, non per ipotesi:

| Cookie | Tipo | Scopo | Durata |
|---|---|---|---|
| `jwt` | httpOnly, Secure, SameSite=Strict | Token di sessione (autenticazione) | 15 minuti |
| `refresh_token` | httpOnly, Secure, SameSite=Strict | Rinnovo automatico della sessione senza richiedere nuovo login | 7 giorni |
| `csrf_token` | Secure, SameSite=Strict, **non** httpOnly (richiesto dal meccanismo double-submit-cookie) | Protezione dalle richieste falsificate cross-site (CSRF) | Allineata al refresh token |

**Nessun cookie di profilazione, analytics, marketing o di terze parti.**
Sotto la Direttiva ePrivacy questi sono cookie "strettamente necessari"
(sicurezza di sessione) — non richiedono banner di consenso, ma vanno
comunque dichiarati con durata e finalità.

---

## 8. Deliverable attesi dal consulente

1. **Privacy Policy** — template che l'hotel cliente compila con i propri
   dati (ragione sociale, indirizzo, email DPO se nominato) e pubblica sotto
   il proprio nome. Contenuti minimi: quanto in §2-4, base giuridica
   distinta per i dati Alloggiati (obbligo di legge) rispetto al resto
   (contratto/consenso), diritti dell'interessato con la clausola sul
   bilanciamento TULPS/fiscale di §4.
2. **Data Processing Agreement** — tra il fornitore della piattaforma e
   ciascun hotel cliente. Contenuti minimi: ruoli (§1), sub-processor
   autorizzati (§5, incluso il giudizio sul trasferimento extra-UE
   Backblaze), misure tecniche (§6), modalità di notifica data breach,
   condizioni di restituzione/cancellazione dati a fine contratto.
3. **Cookie Policy** — inventario di §7, dichiarazione che nessun consenso è
   richiesto essendo cookie strettamente necessari, motivazione tecnica
   sintetica.

**Formato richiesto**: testo semplice (markdown o plain text va bene,
diventerà una pagina React statica) — non serve HTML/CSS, la formattazione
la gestisce l'applicazione. Se la Privacy Policy richiede campi
personalizzabili per hotel (ragione sociale, indirizzo), segnalarli
esplicitamente: verranno resi come placeholder da compilare per hotel
nell'hub Impostazioni, non hardcoded.

**Consegna**: file di testo (anche una email va bene) con i tre documenti;
il cablaggio tecnico (route `/legal/privacy`, `/legal/cookies`, link da
`AuthLayout.tsx` footer e da `Settings.tsx`) è già scoping-ready
(`docs/ROADMAP.md`, sezione "Documentazione enterprise mancante") — richiede
~1-2h una volta ricevuto il testo, non è un lavoro bloccante per il
consulente.

---

## 9. Domande aperte per il consulente (non risolvibili tecnicamente)

- Backblaze B2 (region UE, società USA): serve SCC/altra base giuridica per
  il trasferimento extra-UE? (§5)
- Chi nomina il DPO (Data Protection Officer), se richiesto per il volume di
  dati trattato da un hotel cliente di grandi dimensioni?
- Il DPA va firmato hotel-per-hotel, o basta un modello standard accettato
  in fase di sottoscrizione dell'abbonamento SaaS?
