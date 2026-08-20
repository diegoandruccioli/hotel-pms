> **STATO: REPORT — nessuna implementazione in questo giro.** Audit richiesto dall'utente
> dopo la chiusura del lavoro FatturaPA (2026-08-03): verificare, per ogni obbligo
> normativo italiano/UE rilevante per un gestionale alberghiero, cosa il software
> effettivamente rispetta — non solo cosa dichiarano i documenti di progetto. Ambito
> concordato: solo analisi, nessun fix di gap in questa fase.

# Audit conformità normativa — hotel-pms (2026-08)

## Context

Verifica indipendente su 12 aree normative (§9bis aggiunta nell'aggiornamento del
2026-08-04), condotta con agenti di esplorazione sul codice reale (non sulle intenzioni
dichiarate in `backup/DECISIONS.md`/`docs/ROADMAP.md`) più ricerca web per la normativa
corrente (fonti citate in fondo a ogni sezione dove pertinente). Il verdetto per ciascuna
area è: ✅ implementato, 🟡 parziale, 🔴 assente (e non tracciato come gap noto in nessun
documento di progetto), ⚫ deciso esplicitamente di non implementare (con motivazione già
documentata altrove), ⚪ non applicabile allo scope attuale del prodotto. **Aggiornato
2026-08-04** con un secondo giro di verifica indipendente (agente separato, stessa
metodologia) — 4 delta integrati (vedi fondo "Priorità consigliate"), nessuna conclusione
precedente ribaltata.

**I tre gap più rilevanti trovati, perché nessuno dei tre risultava tracciato da nessuna
parte prima di questo audit** (non in `DECISIONS.md`, non in `ROADMAP.md`, non in
`THREAT_MODEL.md`):

1. **Imposta di soggiorno** — assente, mai pianificata (§4)
2. **Corrispettivi telematici Horeca** — obbligatori per legge dal 1° gennaio 2026,
   assenti e mai censiti come adempimento (§5)
3. **Antiriciclaggio (D.Lgs. 231/2007)** — mai citato nel repo, anche solo come
   valutazione già fatta e archiviata (§9)

Tutti e tre confluiscono come nuove voci in `docs/ROADMAP.md` nella fase successiva di
questo lavoro (audit documentazione), così restano tracciate invece di rimanere gap
orfani.

---

## 1. Alloggiati Web (comunicazione ospiti alla Questura — TULPS art. 109) — ✅ Implementato

Il pezzo più maturo del repo dal punto di vista normativo.

| Aspetto | Stato | Evidenza |
|---|---|---|
| Invio SOAP nativo al portale PS | ✅ | `frontdesk-service/.../stays/service/impl/AlloggiatiWebSenderServiceImpl.java` — `GenerateToken`+`Send`, `dry-run` che chiama `Test` invece di `Send` |
| Generazione tracciato 168 caratteri | ✅ | `AlloggiatiReportServiceImpl.java` |
| Tabelle ufficiali Comuni/Stati/TipiDocumento | ✅ | `AlloggiatiCsvParser.java`, `AlloggiatiLookupDataLoader.java` |
| Credenziali PS per-hotel cifrate | ✅ | `security/AlloggiatiCredentialEncryptor.java` (fallback su env var globali) |
| Endpoint protetti e hotel-scoped | ✅ | `StayController.java` — 3 endpoint (`GET .../alloggiati`, `.../alloggiati/json`, `POST .../alloggiati/submit`), tutti `@PreAuthorize` ADMIN/OWNER |
| Validazione tracciato | ✅ | `AlloggiatiValidationException`, `AlloggiatiRowLimitExceededException` (es. familiare senza capofamiglia) |

**Verifica puntuale fatta in questo audit — finestra di invio 6h vs 24h**: il Decreto
Sicurezza 2025 ha introdotto una finestra ridotta a **6 ore per soggiorni inferiori alle
24 ore** (invece delle 24h standard). Verificato sul codice: **non esiste alcuna logica
di enforcement/promemoria della scadenza**, né a 24h né a 6h — né uno `@Scheduled` job,
né un controllo che distingue la durata del soggiorno.

Quello che esiste realmente:
- Con `HotelSettings.alloggiatiAutoSend = true` (`StayServiceImpl.java:132,357-380`),
  l'invio avviene **sincrono al check-in** (`sendAlloggiatiIfEnabled`) — di fatto quasi
  istantaneo, quindi qualunque finestra legale (6h o 24h) è rispettata per costruzione,
  senza bisogno di una logica di scadenza dedicata.
- `alloggiatiAutoSend` è un `boolean` primitivo senza `@Builder.Default` → **default
  `false`** (manuale). In modalità manuale, l'operatore deve ricordarsi di generare e
  inviare il report per data (`/reports/alloggiati/submit?date=`) — **zero promemoria,
  zero alert, zero tracciamento di scadenza mancata** se lo dimentica o lo fa in ritardo.

**Gap reale**: non è la finestra 6h/24h in sé (irrilevante se auto-send è attivo), ma
l'assenza totale di un meccanismo di scadenza/allerta per gli hotel in modalità manuale
(probabilmente la maggioranza, essendo il default). Non tracciato in `ROADMAP.md`.

**Gap già noto e dichiarato** (`docs/ROADMAP.md` E13): il payload TXT/JSON è ricalcolato
on-demand dal DB live, nessuno snapshot immutabile di ciò che fu davvero trasmesso.

---

## 2. Fatturazione elettronica / SDI / FatturaPA — ✅ Export completo, ⚫ trasmissione diretta fuori scope

Appena chiuso in questa stessa sessione di lavoro (merge `4ed35b2`, 2026-08-03).

- **Export FatturaPA FPR12**: validato contro lo schema XSD ufficiale AE, immutabile post
  export (`invoice_fiscal_exports`: bytes+SHA-256+timestamp), blocco `409
  INVOICE_LOCKED_AFTER_EXPORT` su mutazione fattura dopo il primo export, batch ZIP+indice
  per il commercialista. `billing-service/.../service/FatturaPaXsdValidator.java`,
  `service/impl/FatturaPAServiceImpl.java`, `domain/InvoiceFiscalExport.java`.
- **Decisione esplicita** (`backup/DECISIONS.md §2.5`): nessuna trasmissione diretta a
  SDI — motivo, possedere la trasmissione implica possedere la conservazione sostitutiva
  (accreditamento, responsabilità legale 10 anni), sproporzionato pre-lancio. Interfaccia
  `SdiProvider` pensata ma non scritta (`docs/ROADMAP.md` E3bis).
- **Numerazione sequenziale** `YYYY/NNNN` con lock pessimistico ✅ (C2), **IVA
  disaggregata** ✅ (C3).
- 🔴 **Gap dichiarato**: flusso **nota di credito** per correggere fatture già esportate
  — non costruito, esplicitamente rimandato (`DECISIONS.md §2.5`, `THREAT_MODEL.md`
  T-BILL-06).
- 🟡 **E12 aperto**: configurazione aliquote IVA per tipologia (10% camere / 22% F&B) —
  `docs/ROADMAP.md:104`, non fatto.

---

## 3. GDPR (Reg. UE 2016/679) — 🟡 Parziale, più maturo di quanto sembri a prima vista

Il backend fa molto più di quanto emerga scorrendo la sola documentazione di alto
livello — ma restano gap concreti soprattutto lato UI e procedure organizzative.

| Sotto-obbligo | Stato | Evidenza |
|---|---|---|
| Consenso | 🟡 Solo campo data (`gdpr_consent_date`), nessuna raccolta esplicita | `guest-service/.../model/Guest.java:141-145`, backfill da `created_at` come proxy legalmente difendibile ma non un vero flusso di consenso; nessuna checkbox nel frontend |
| Art. 20 — portabilità | 🟡 Backend ✅, UI ❌ | `GuestController.java:174-185` → `GET /api/v1/guests/{id}/export`, `GuestServiceImpl.java:373`, aggrega stay+invoice. **`frontend/src/services/guestService.ts` non ha alcun metodo di export** — l'endpoint esiste ma non è raggiungibile da nessuna pagina |
| Art. 17 — oblio/anonimizzazione | ✅ Implementato | `GuestServiceImpl.java:176-218`, doppia guardia legale (TULPS 5 anni + fiscale 10 anni via Art. 2220 CC), blocco esplicito HTTP 451 con `unlocksAt`/`legalBasis` se i vincoli sono ancora attivi |
| Retention automatica | 🟡 Parziale — copre solo `Guest`, non la copia in `frontdesk-service` | `GuestRetentionJobServiceImpl` (job notturno `@Scheduled 0 0 2 * * *`) anonimizza `guest-service.Guest`. Ma `frontdesk-service/.../stays/domain/StayGuest.java:51-80` tiene una copia **completa e indipendente** della PII Alloggiati per ogni soggiorno (nome, nascita, cittadinanza, tipo+numero documento) — grep `anonym\|retention\|@Scheduled` su tutto `frontdesk-service/src/main/java` → **zero risultati**. Anonimizzare un `Guest` non tocca mai i suoi `StayGuest` storici: la PII sensibile resta in chiaro per sempre in un secondo DB |
| Art. 32 — cifratura a riposo della PII | 🔴 Assente | `documentNumber` in chiaro in due punti: `guest-service/.../model/IdentityDocument.java:59` e `frontdesk-service/.../StayGuest.java:75`. Grep `AttributeConverter` su tutto il repo → zero convertitori di cifratura per dati ospite. Per contrasto, le credenziali Alloggiati Web **sono** cifrate (`AlloggiatiCredentialEncryptor`, AES-256-GCM) — la stessa cura non è stata applicata al dato dell'interessato, che è la categoria più sensibile qui trattata |
| Art. 30 — registro trattamenti/audit log | 🟡 Log strutturati, non immutabili | Prefissi `[AUTH]`/`[STAY]`/`[BILLING]`/`[GDPR]`, aggregati su Loki con dashboard dedicata. Gap dichiarato: `docs/ROADMAP.md` E13 "audit log immutabile append-only" — non implementato |
| DPO | ❌ Domanda aperta, non risolvibile tecnicamente | `docs/legal/PRIVACY_DPA_COOKIE_BRIEF.md:207-208` la pone esplicitamente al consulente legale |
| Data breach (Art. 33/34) | ❌ Nessuna procedura | Citato solo come contenuto atteso nel DPA; nessun runbook in `docs/OPERATIONS_RUNBOOK.md` né in `SECURITY.md` |
| Privacy/Cookie Policy, ToS | ❌ Deliberatamente non pubblicate | `docs/legal/PRIVACY_DPA_COOKIE_BRIEF.md` già pronto e completo per il consulente (dati per categoria, basi giuridiche, sub-processor incluso Backblaze extra-UE, misure Art. 32, inventario cookie). Decisione esplicita: *"un placeholder legale in produzione è un rischio maggiore di nessun link"*. Route `/legal/privacy`, `/legal/cookies` non esistono ancora, scoping-ready (~1-2h di cablaggio una volta arrivato il testo) |

**Osservazione di sicurezza collaterale, non solo di conformità**: sia `GuestController.java`
sia `GuestPrivacySettingsController.java:25,49` **non hanno alcuna `@PreAuthorize`**, né a
livello classe né metodo — inclusi l'export Art. 20, la delete/anonimizzazione, e ora anche
la modifica della **retention policy dell'hotel** (`PUT /api/v1/guests/settings`, il periodo
minimo resta comunque 5 anni per il floor TULPS lato service, ma il controller non impedisce
a un ruolo qualsiasi di provarci). L'autorizzazione dipende interamente dalla whitelist per
path-prefix nell'api-gateway (`AuthenticationFilter.java:81-101` — `/api/v1/guests/**` non
compare in `WRITE_RESTRICTED_PREFIXES` né `FULLY_RESTRICTED_PREFIXES`,
`docs/SECURITY_AND_PRIVACY.md:96`). Funziona finché quella whitelist resta corretta, ma è un
singolo punto di fallimento invisibile al codice del servizio stesso — **da riverificare
end-to-end nella fase di test esplorativo** di questo stesso lavoro (accesso diretto alle API
con un ruolo non autorizzato).

---

## 4. Imposta di soggiorno (tassa di soggiorno comunale) — ✅ Implementato (2026-08-19)

**Normativa** (verificata via ricerca web, 2026-08-03): il gestore della struttura è
responsabile diretto della riscossione e del versamento (Cassazione n. 1527/2026, che ha
abolito il Modello 21 — il gestore risponde in prima persona, non come sostituto
d'imposta). Dichiarazione telematica annuale entro il 30 giugno all'Agenzia delle
Entrate; versamento mensile al Comune esclusivamente tramite portale telematico
comunale. Sanzione per dichiarazione omessa/infedele: 100-200% dell'imposta dovuta.
Modalità di calcolo (importo per notte/ospite, esenzioni minori/disabili, durata massima
di applicazione) variano **per regolamento comunale** — non esiste uno standard
nazionale unico.

**Verificato sul codice**: `billing-service/.../domain/ChargeType.java` ha solo
`ROOM_NIGHT`, `FB_ORDER`, `EXTRA`. Nessuna entità, campo, configurazione per-comune,
esenzione, o reportistica dedicata in tutto il repo.

**Stato della consapevolezza nel progetto**: citata **una sola volta**, come feature che
i competitor italiani espongono sempre come blocco di primo livello
(`docs/AUDIT_ANALISI_2026-07.md:159,179,343`) — mai promossa a voce di `docs/ROADMAP.md`
con un codice E/C/P, mai valutata come obbligo di legge proprio (a differenza di
FatturaPA o Alloggiati, che hanno entrambi una sezione dedicata nelle decisioni di
progetto).

**Perché è un gap rilevante e non solo commerciale**: a differenza di quasi tutti gli
altri item di questo report, l'imposta di soggiorno non è "una feature che manca" — è un
tributo che l'hotel cliente è legalmente tenuto a riscuotere e versare ogni mese. Un
gestionale che non la traccia affatto costringe l'hotel a calcolarla e dichiararla
completamente fuori dal sistema, con rischio di errore/dimenticanza che ricade
sull'hotel, non sul fornitore software — ma un prodotto che si presenta come
"gestionale alberghiero completo" ne risulta strutturalmente incompleto su un punto che
ogni hotel italiano deve gestire.

**Risoluzione** (2026-08-19, `docs/ROADMAP.md` E18): implementato su
`feature/imposta-di-soggiorno` (5 commit). `ChargeType.CITY_TAX` fuori campo IVA via
`Natura` FatturaPA N1 (art. 15 c.1 n.3 DPR 633/1972 — riscossione in nome e per conto del
comune), da confermare col commercialista prima del go-live. Nuovo package `citytax`
(frontdesk-service): `HotelCategoryHistory` (storico categoria per hotel, versionato —
una prenotazione passata si liquida con la categoria che l'hotel aveva allora, non quella
attuale), `CityTaxRate` (per hotel+comune+categoria, `EXCLUDE USING gist` contro
overlap, deliberatamente non condivisa fra hotel dello stesso comune per non introdurre
un modello di autorizzazione cross-tenant assente altrove nel repo), `CityTaxAssessment`
(record immutabile per-stay, FK + snapshot denormalizzato per ricostruibilità fiscale
anche se la regola referenziata cambiasse). Calcolo e posting automatico al check-in
insieme a `ROOM_NIGHT`, guardia di idempotenza per-charge indipendente
(`Stay.roomChargeId` / `CityTaxAssessment.billingChargeId` — un fallimento sulla sola
CITY_TAX non ripubblica la camera al retry). UI amministrazione tariffe/categoria su
`/settings/city-tax` (ADMIN/OWNER). ISTAT/ROSS1000 (§9ter/E17) resta esplicitamente
rinviato — nessun cliente pagante lo richiede oggi. **Prima di inserire tariffe reali**:
confermare con un commercialista il codice Natura corretto (N1 vs N2.2) e l'importo
esatto dalla delibera comunale ufficiale (non dedotto da fonti giornalistiche).

---

## 5. Corrispettivi telematici (scontrino elettronico, settore Horeca) — 🔴 Assente, non tracciata

**Normativa** (verificata via ricerca web, 2026-08-03): dal **1° gennaio 2026** lo
scontrino digitale è obbligatorio per l'intero settore Horeca (hotel, ristoranti, bar) —
il POS deve essere collegato al registratore telematico per memorizzare e trasmettere
giornalmente i corrispettivi all'Agenzia delle Entrate, tramite le funzionalità
telematiche del portale "Fatture e Corrispettivi" (non serve un collegamento hardware).
Sanzione per mancato collegamento: €1.000-4.000. Esenzioni limitate (tabaccai,
edicolanti, tassisti, distributori automatici) — nessuna delle quali si applica a un
hotel.

**Verificato sul codice**: nessuna implementazione, nessun registratore telematico,
nessun "documento commerciale" generato. Unica traccia nel prodotto: il template PDF
della ricevuta non fiscale ha un disclaimer difensivo
(`billing-service/.../templates/pdf/invoice-ricevuta.html:30`: *"copia di cortesia. Non
sostituisce lo scontrino o il documento commerciale"*) — riconosce il problema ma non lo
risolve.

**Mitigante architetturale parziale**: `fb-service` impone `stayId NOT NULL` su ogni
ordine (`RestaurantOrderRequest.java:19`, `RestaurantOrder.java:55-56`) — non esiste oggi
un caso d'uso "vendita diretta a cliente non alloggiato" (bar/ristorante aperto al
pubblico esterno). Questo riduce ma **non elimina** l'esposizione: il pernottamento
stesso, quando pagato in hotel con emissione di ricevuta non fiscale invece di fattura,
potrebbe comunque ricadere nell'obbligo di corrispettivo telematico a seconda
dell'inquadramento della prestazione alberghiera — punto che richiede una verifica con
un commercialista, non risolvibile solo leggendo il codice.

**Stato della consapevolezza nel progetto**: **zero**. Non censita in `DECISIONS.md`, non
in `ROADMAP.md` (l'unica menzione di "corrispettivi" è in E3bis, come feature del futuro
ipotetico provider A-Cube per la trasmissione SDI diretta — non come adempimento proprio
da coprire), non in `THREAT_MODEL.md`. A differenza dell'imposta di soggiorno (§4, almeno
menzionata una volta), questo obbligo non risultava sul radar del progetto prima di
questo audit.

---

## 6. Conservazione sostitutiva — ⚫ Deciso esplicitamente di non implementare

`backup/DECISIONS.md §2.5`: possedere la trasmissione diretta SDI implicherebbe
possedere anche la conservazione sostitutiva (accreditamento presso un conservatore,
responsabilità legale 10 anni) — impegno sproporzionato pre-lancio. Trigger di
riapertura dichiarato: solo quando un cliente pagante la richiede esplicitamente. Nessuna
azione richiesta, decisione coerente e già motivata.

---

## 7. PCI-DSS / gestione carte di pagamento — ⚫ Deciso esplicitamente di non implementare

`backup/DECISIONS.md §2.3`: nessuna integrazione con gateway di pagamento online
(Stripe/Nexi/PayPal). Solo tracciamento del metodo di pagamento usato alla chiusura del
conto (`PaymentMethod`: CASH/BANK_TRANSFER/CHECK/DEBIT_CARD/CREDIT_CARD). Nessun dato di
carta è mai persistito nel sistema. Decisione coerente, nessuna azione richiesta.

---

## 8. Accessibilità (WCAG 2.2 AA) — ✅ Implementato

Standard adottato: WCAG 2.2 AA con contrasto potenziato (7:1/4.5:1), focus ring come
elemento di design di prima classe, focus trap sui modali, `vitest-axe` su ogni test
componente. Extra: PDF/UA (ISO 14289) sui documenti fiscali — `usePdfUaAccessibility`
sempre attivo, verificato con PDFBox reale (`/MarkInfo`, `/StructTreeRoot`, `/Lang`).

**Discrepanza documentale trovata** (non un gap funzionale): `backup/DECISIONS.md §4.2`
rimanda a `backup/archive/ACCESSIBILITY_FIXES.md`, che riporta ancora *"Compliance
attuale: ~72% (audit del 2026-04-17)"*. È il dato di **partenza storico**
dell'intervento, non lo stato attuale (che è conforme WCAG 2.2 AA) — il rimando da
DECISIONS.md non lo chiarisce esplicitamente, rischio di lettura fuorviante. Corretto
nella fase di audit documentazione di questo stesso lavoro.

**Nota**: nessun riferimento esplicito ad AGID/Legge Stanca/EN 301 549 in tutto il repo —
lo standard WCAG 2.2 AA è stato adottato come scelta tecnica propria, non ancorato
esplicitamente alla normativa italiana sull'accessibilità digitale (che per un prodotto
SaaS B2B non pubblico non è comunque un obbligo diretto, a differenza della PA).

---

## 9. Antiriciclaggio (D.Lgs. 231/2007) — 🔴 Mai citato, rischio basso ma non documentato

**Normativa** (verificata via ricerca web, 2026-08-03 + 2026-08-04): il D.Lgs. 231/2007 impone, tra
gli obbligati, adeguata verifica della clientela, conservazione dati/documenti per 10
anni, segnalazione di operazioni sospette a UIF Banca d'Italia. Per le strutture
ricettive, l'identificazione dell'ospite è già imposta autonomamente dal TULPS
(Alloggiati Web) — normativa che **vieta esplicitamente** la conservazione di copie
(fisiche o digitali) dei documenti d'identità, permettendo solo la raccolta e
trasmissione dei dati identificativi. **Soglie concrete verificate**: limite ordinario
all'uso del contante €5.000 (2026); deroga specifica per le strutture ricettive fino a
**€15.000** per pagamenti da turisti stranieri non residenti (art. 3 D.L. 16/2012), a
condizione di acquisire copia del passaporto/documento + autocertificazione di non
residenza, versare l'importo in banca entro il primo giorno lavorativo successivo, e
presentare una **comunicazione telematica annuale all'Agenzia delle Entrate** (finestra
10-20 aprile) di tutte le operazioni in contanti pari o superiori alla soglia ordinaria.

**Verificato sul codice**: grep esaustivo su riciclaggio/antiriciclaggio/231-2007/soglia
contanti in tutto il repo (`.java`/`.ts`/`.tsx`/`.md`/`.tex`) → **zero risultati
pertinenti**. Nessun limite all'uso del contante (`PaymentMethod.CASH` accetta qualsiasi
importo, l'unica validazione in `PaymentServiceImpl.java:52-71` è
`PAYMENT_EXCEEDS_BALANCE`). I dati grezzi per un'eventuale estrazione esistono già
(`Payment.java:49-59`: `amount`, `paymentDate`, `paymentMethod`) ma **nessun report li
aggrega** (`OwnerReportController.java` copre solo KPI finanziari operativi, non
compliance) — e comunque non basterebbero da soli per la deroga turisti, perché la
cittadinanza dell'ospite (`StayGuest.citizenship`, `frontdesk-service`) non è collegata al
pagamento (`Payment`, `billing-service`): sono due servizi diversi senza join su questo
campo.

**Verifica da fare, non risolvibile solo dal codice**: se `guest-service` conserva copie
scansionate dei documenti d'identità caricati (`POST /api/v1/guests/{id}/documents`) oltre
ai soli dati testuali, ci sarebbe una tensione diretta con il divieto TULPS di cui sopra —
punto da approfondire specificamente (non incluso nell'inventario di questo audit, che si
è concentrato sulla presenza/assenza di logica antiriciclaggio, non sul contenuto esatto
dell'upload documenti).

**Valutazione di rischio**: basso nella sostanza (l'identificazione ospite è già coperta
da un obbligo equivalente e più stringente, TULPS/Alloggiati; nessuna soglia di legge sul
contante risulta specificamente applicabile a un hotel al di sotto degli importi tipici
di una permanenza), ma **zero documentazione** significa che nessuno ha mai fatto questa
valutazione esplicitamente — a differenza di conservazione sostitutiva e PCI-DSS, che
hanno entrambi una decisione scritta e motivata.

---

## 9bis. Allergeni F&B (Reg. UE 1169/2011) — 🔴 Assente, non tracciata

**Normativa**: il Regolamento UE 1169/2011 impone l'informazione obbligatoria sui 14
allergeni maggiori (glutine, crostacei, uova, pesce, arachidi, soia, latte, frutta a
guscio, sedano, senape, sesamo, solfiti, lupini, molluschi) per qualunque alimento non
preimballato somministrato al pubblico — quindi ogni voce di un menu bar/ristorante.
Obbligo del gestore, non solo raccomandazione.

**Verificato sul codice**: `fb-service/.../domain/MenuItem.java:41-79` ha solo `name`,
`price`, `category`, `description` (testo libero, non strutturato), `available`, `active`
— **nessun campo allergeni/ingredienti**, né come lista strutturata né come flag. Nessuna
colonna corrispondente nel Flyway di `fb-service`. Il campo `description` potrebbe in
teoria ospitare l'informazione come testo libero digitato dall'operatore, ma non è un
campo dedicato, non è validato, e non compare in nessuna verifica del frontend
(`Restaurant.tsx`, `OrderFormModal.tsx`) come informazione a sé.

**Stato della consapevolezza nel progetto**: zero — mai citato in `DECISIONS.md`,
`ROADMAP.md`, `THREAT_MODEL.md`. A differenza di HACCP (procedurale, fuori scope
software), questo è un obbligo informativo che un menu digitale deve strutturalmente
supportare.

---

## 10. ROSS1000 / rilevazione statistica turistica regionale (ISTAT/SISTAN) — ⚫ Deciso di rinviare

`docs/ROADMAP.md` E17: obbligo di legge (D.Lgs. 322/1989 art. 7, sanzioni fino a
€2.500/mese) ma copertura regionale parziale (~13/20 regioni via piattaforma GIES),
adempimento manuale via portale regionale oggi legale e sufficiente. Verdetto LLM
Council 2026-06-22: non implementare finché non c'è un cliente pagante in una regione
coperta che lo richiede esplicitamente. Decisione coerente, nessuna azione richiesta.

---

## 11. Altri riferimenti normativi trovati

| Riferimento | Stato | Dove |
|---|---|---|
| Art. 109 TULPS + D.M. 7 gennaio 2013 | ✅ Coperto (§1) | `docs/SECURITY_AND_PRIVACY.md`, `docs/DOCUMENTAZIONE_TECNICA_ALLOGGIATI_PS.md` |
| Codice Civile art. 2220 (conservazione 10 anni scritture contabili) | ✅ Implementato come guardia legale retention | `FISCAL_MIN_YEARS=10` |
| Direttiva ePrivacy (cookie) | Analisi fatta, policy non ancora pubblicata | `docs/legal/PRIVACY_DPA_COOKIE_BRIEF.md §7` — solo cookie "strettamente necessari" (jwt/refresh_token/csrf_token), nessun banner dovuto |
| Trasferimento extra-UE / SCC (Backblaze B2, region UE ma società USA) | ❌ Domanda aperta al consulente legale | `docs/legal/PRIVACY_DPA_COOKIE_BRIEF.md §5,§9` |
| Terms of Service | ❌ Non scritti | `docs/ROADMAP.md:202` — 🟡 Media priorità, atteso da clienti enterprise |
| Licenze OSS (ADR-002) | ✅ Processo attivo | verifica maintenance/CVE/licenza prima di ogni adozione libreria |
| Garante Privacy (notifica 72h) | Solo citato come impatto teorico | `docs/PILOT_READINESS_AUDIT.md:291` — vedi §3 (data breach, nessuna procedura reale) |
| European Accessibility Act (Dir. UE 2019/882, in vigore dal 28/06/2025) | ⚪ Fuori scope oggi, mai valutato per iscritto | Copre servizi e-commerce **B2C**; il PMS è B2B puro (nessuna vendita diretta all'ospite, `DECISIONS.md §2.3`), oltretutto sotto soglia microimpresa (&lt;10 dipendenti, &lt;€2M fatturato). **Trigger di riapertura**: se si costruisce il booking engine diretto (E2/E16) — a quel punto si applica EN 301 549/WCAG 2.1 AA, già ampiamente coperto dal WCAG 2.2 AA con contrasto 7:1 già adottato (§8) |
| NIS2 (Dir. UE 2022/2555) | ⚪ Fuori scope, mai valutato per iscritto | Settore ricettivo/gestionali PMS non è tra i servizi essenziali/importanti degli Allegati I/II; soglie dimensionali (≥50 dipendenti/€10M) non raggiunte. Da rivalutare solo se il prodotto diventasse fornitore di infrastruttura cloud gestita per terzi su scala |
| Codice del Consumo / Codice del Turismo (obblighi precontrattuali, diritto di recesso) | ⚪ Fuori scope oggi | Non applicabile senza vendita diretta al consumatore — diventa rilevante solo insieme a un futuro booking engine (stesso trigger di EAA sopra) |

---

## Priorità consigliate (nessuna implementazione decisa qui)

**I 3 gap non censiti da nessuna parte, in ordine di urgenza percepita**:

1. **Imposta di soggiorno** (§4) — tributo che ogni hotel italiano deve gestire
   mensilmente; oggi va calcolato e dichiarato completamente fuori dal sistema
2. **Corrispettivi telematici** (§5) — obbligo di legge già in vigore (dal 2026-01-01,
   non futuro); richiede prima un chiarimento con un commercialista su se/come si applica
   al pernottamento, poi eventuale implementazione
3. **Antiriciclaggio** (§9) — rischio sostanziale basso, ma manca anche solo una riga di
   valutazione scritta e archiviata (come già fatto per conservazione sostitutiva e
   PCI-DSS)

**Gap già pianificati, solo da ricordare in ordine di priorità propria**:
- Nota di credito FatturaPA (§2) — sostanziale, richiede design a parte
- Audit log immutabile Art. 30 (§3, E13)
- Privacy/Cookie Policy/ToS/DPO/procedura data breach (§3) — scoping-ready, in attesa di
  testo legale dal consulente
- Export GDPR Art. 20 — manca solo il cablaggio UI, il backend è già pronto (§3)

**Aggiornamento post-audit (2026-08-04, verifica indipendente)** — 4 delta nuovi, non
presenti alla prima stesura:
1. **Retention GDPR incompleta** (§3) — il job notturno anonimizza solo `Guest`, mai i
   `StayGuest` (copia PII Alloggiati per soggiorno) in `frontdesk-service`: PII sensibile
   (documento, cittadinanza) resta in chiaro per sempre lì anche dopo l'anonimizzazione
   dell'ospite. Severità alta — è il caso d'uso principale dell'Art. 17 mancato a metà.
2. **Cifratura a riposo assente per `documentNumber`** (§3) — due DB, zero
   `AttributeConverter`, incoerente con la cura già messa sulle credenziali Alloggiati.
3. **Allergeni F&B** (§9bis, nuova) — `MenuItem` senza alcun campo dedicato, obbligo
   Reg. UE 1169/2011 mai valutato.
4. **RBAC ospiti più ampio del previsto** (§3) — anche `GuestPrivacySettingsController`
   senza `@PreAuthorize`, non solo `GuestController`.

**Osservazione di sicurezza da portare in Fase 2 (test esplorativo)**: `GuestController`
senza `@PreAuthorize` (§3) — verificare end-to-end che la protezione via gateway regga
davvero su ogni chiamata, in particolare export dati e cancellazione/anonimizzazione.

---

## Fonti (ricerca web, 2026-08-03)

- [Quali obblighi per gli hotel? Istat, Alloggiati Web, fatture elettroniche](https://roomraccoon.it/blog/quali-obblighi-per-gli-hotel/)
- [Alloggiati web invio schedine: FAQ e obblighi 2026 - Chekin](https://chekin.com/it/blog/alloggiati-web-invio-schedine/)
- [Come si paga la tassa di soggiorno, cosa cambia per affitti brevi, B&B e hotel nel 2026](https://www.partitaiva.it/come-versare-imposta-soggiorno/)
- [Imposta di soggiorno 2026: dichiarazione annuale entro il 30 giugno - Commercialista Telematico](https://www.commercialistatelematico.com/articoli/2026/06/imposta-soggiorno-2026-dichiarazione-30-giugno.html)
- [POS e registratore telematico: obbligo di collegamento 2026 - A-Cube](https://www.acubeapi.com/blog/pos-e-registratore-telematico-obbligo-collegamento-2026)
- [Obbligo dello scontrino elettronico per hotel e ristoranti - Ericsoft](https://www.ericsoft.com/it/news/new/scontrini-elettronici-per-hotel-e-ristoranti)
- [Corrispettivi telematici e non obbligatorietà di emissione della fattura - TeamSystem](https://www.teamsystem.com/magazine/horeca/prestazioni-alberghiere-corrispettivo-o-fattura/)
- [Antiriciclaggio: guida 2026 - adempimenti D.Lgs. 231/2007](https://leggeinchiaro.it/antiriciclaggio-guida-completa/)
- [L'identificazione degli ospiti nelle strutture ricettive](https://www.studioallievi.com/identificazione-degli-ospiti-nelle-strutture-ricettive/)
- [GDPR Data Breach Notification: The 72-Hour Rule Explained](https://www.recordinglaw.com/world-laws/world-data-privacy-laws/eu-data-privacy-laws/gdpr-breach-notification-72-hour-rule/)

**Fonti aggiuntive (aggiornamento 2026-08-04)**:
- [Ipsoa — limiti uso contante e adempimenti antiriciclaggio 2026](https://www.ipsoa.it/guide/limiti-uso-contante-regole-adempimenti-antiriciclaggio)
- [Baker Tilly — pagamenti in contanti da turisti esteri, comunicazione AdE](https://www.bakertilly.it/en/insights/pagamenti-in-contanti-da-turisti-esteri-nuovo-limite-per-la-comunicazione-allagenzia-delle-entrate)
- [AccessibilityChecker — EAA compliance for B2B organisations](https://accessibilitychecker.com/news/eaa-compliance-for-b2b-organisations/)
- [Studio Legale Delli Ponti — European Accessibility Act dal 28 giugno 2025](https://www.studiolegaledelliponti.eu/european-accessibility-act-cosa-cambia-dal-28-giugno-2025-e-per-chi/)
