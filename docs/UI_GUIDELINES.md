# UI_GUIDELINES.md — regole di interazione e contenuto per ogni schermata

**Per chi è:** chiunque (persona o agente) crei una schermata nuova o ne modifichi una esistente nel frontend.
**Cosa copre:** *come si comporta* una schermata e *cosa dice*: priorità, stato, navigazione, errori, testi.
**Cosa non copre:** *come appare* (token, contrasto, focus, touch target, componenti m3): quello è in
[`frontend/DESIGN.md`](../frontend/DESIGN.md) e qui non si ripete. In caso di conflitto vale `DESIGN.md`
per l'aspetto e questo file per il comportamento.

Le regole derivano dall'ergonomia cognitiva e dall'usabilità web (euristiche di Nielsen, principi di
Norman, leggi di Miller e di Hick, principi della Gestalt, microcopy). Ogni regola indica il principio,
il modo di applicarla *con i componenti che esistono già* e una domanda di verifica.

---

## 1. Contesto d'uso del PMS (da tenere a mente)

| Chi | Dove | Cosa implica |
|---|---|---|
| Front desk | PC, turni lunghi, spesso sotto pressione (check-in con la coda) | Uso quotidiano: scorciatoie e densità vanno bene, ma la priorità deve restare chiara. Errori costosi (doppie prenotazioni, fatture) |
| Housekeeping | Tablet o telefono, in piedi, con i guanti, a volte stampa | Touch, nessuna informazione solo su hover, fogli stampati senza dati personali |
| Direzione / amministrazione | PC, uso saltuario | Chi usa poco dimentica: serve riconoscere, non ricordare |
| Turno di notte | Schermo scuro, stanchezza | Tema scuro/alto contrasto di prima classe, testi brevi |

Conseguenza: il PMS è uno strumento di lavoro *denso per scelta*. La disciplina che lo tiene leggibile è la
**coerenza e la gerarchia**, non la scarsità di elementi (vedi anche `DESIGN.md`, "Dashboard / layout composition rules").

---

## 2. Le 12 regole

Ogni regola ha: **Regola** · **Perché** · **Come** · **Verifica**.

### R1 — Un solo punto focale per schermata
- **Regola.** Una sola azione primaria (pulsante pieno) per schermata o per sezione. Le altre degradano in peso: outlined, poi testuale/link.
- **Perché.** Visual competition: se tutto ha lo stesso peso, l'occhio non sa dove andare e il lavoro di decidere ricade sull'utente.
- **Come.** `M3Button` pieno per l'azione principale in `PageHeader actions`; `variant="outlined"` per le secondarie; azioni rare o distruttive non devono mai essere il pulsante pieno. Un solo chip o colore saturo "di richiamo" per sezione.
- **Verifica.** Strizzando gli occhi, qual è la prima cosa che si nota? Deve essere una sola e deve essere quella giusta.

### R2 — Gerarchia a tre livelli, con de-enfasi
- **Regola.** Ogni schermata ha tre livelli: dato/azione chiave (grande, contrasto pieno), contesto (medio), servizio (piccolo, attenuato). L'enfasi è una risorsa limitata: per darla a qualcosa va tolta ad altro.
- **Perché.** La gerarchia nasce da posizione, contrasto e dimensione usate insieme. In una lista l'utente cerca il *valore*, non l'etichetta.
- **Come.** Pattern KPI già standard (etichetta piccola, valore grande, delta piccolo). Nelle liste e nelle schede: numero/nome in grassetto, attributi in `on-surface-variant`. Non evidenziare un elemento solo perché "è importante per noi".
- **Verifica.** Se si cancellasse una enfasi, la schermata sarebbe più chiara? Se sì, toglila.

### R3 — Lo stato è sempre visibile, il feedback è immediato
- **Regola.** Ogni azione ha una risposta visibile: in corso, riuscita, non riuscita. Filtri, data e ordinamento attivi si leggono a colpo d'occhio e si rimuovono con un clic.
- **Perché.** Euristica 1 (visibilità dello stato). Senza feedback l'utente clicca due volte e crea duplicati; anche ~100 ms senza risposta disorientano. Meglio nessun feedback che uno ambiguo.
- **Come.**
  - Caricamento: `M3LoadingState`; azioni in corso: pulsante disabilitato con etichetta di avanzamento ("Salvataggio…"), mai doppio invio.
  - Esito: `Toast` per conferme secondarie; `Alert`/errore nel punto dell'azione per ciò che blocca.
  - Filtri attivi: sempre mostrati come stato (`M3SegmentedRow` o `M3FilterChip`), con il conteggio quando serve, mai nascosti in un menu.
  - Un cambio di contesto non spiegato (scroll, filtro tolto da noi) si segnala: evidenziazione breve dell'elemento o toast discreto.
  - Animazioni solo di `transform` e `opacity` (le altre proprietà forzano il repaint) e sempre disattivate con `prefers-reduced-motion`.
- **Verifica.** Dopo ogni clic, l'utente capisce *cosa è successo* e *in quale stato è il sistema* senza dover indovinare?

### R4 — Lo stato sta nell'URL, e "indietro" funziona
- **Regola.** Filtri, ricerca, data, tab, ordinamento e pagina vivono nella query string: l'URL basta da solo a riaprire la schermata nello stesso stato. Tornare indietro da un dettaglio riporta alla stessa lista.
- **Perché.** Ogni meccanismo del browser (indietro, preferiti, aggiorna, nuova tab) è un contratto implicito. L'aggiorna deve ripristinare lo stato "pulito" senza costringere a ripetere la navigazione.
- **Come.**
  - `useSearchParams`; per i raffinamenti (filtro, data, ordinamento) `setSearchParams(..., { replace: true })` così "indietro" non si riempie di voci; per navigare verso un altro luogo (dettaglio) una navigazione vera.
  - Parametro non valido: si ignora in silenzio e si usa il default (è un'approssimazione dell'intenzione, non una colpa). Non mostrare errori per un URL manomesso.
  - Date: mai `new Date()` per il "oggi" del business (vedi la data business del server).
  - **Mai dati personali nell'URL** (nomi, documenti, email): solo id e valori di stato.
  - Un link che porta a una vista filtrata (es. dalla Dashboard) deve usare questi stessi parametri.
- **Verifica.** Copia l'URL, aprilo in una nuova tab: stessa schermata? Premi F5: nessuno stato utile perso, nessuna bozza fantasma?

### R5 — Riconoscimento, non ricordo
- **Regola.** Tutto ciò che serve per decidere o compilare è visibile; l'utente non deve ricordare nulla.
- **Perché.** Euristica 6 e memoria a breve termine (Miller): l'informazione sullo schermo non occupa memoria. Vale di più per chi usa il PMS di rado.
- **Come.**
  - Etichetta sempre visibile sopra il campo (`M3TextField`, `M3Select`, `M3Textarea`). Il placeholder non sostituisce l'etichetta: è, al massimo, un *esempio* di valore.
  - Formato atteso e regole (password, date, codici) mostrati *prima* di digitare (`PasswordRequirementsChecklist` è il modello). Accettare qualsiasi formato ragionevole e normalizzare lato sistema (spazi nei numeri di telefono) invece di punire.
  - Campi obbligatori marcati; "(facoltativo)" solo accanto a quelli che non lo sono.
  - Le opzioni di un insieme piccolo (≤ 5) stanno in vista (segmented, chip), non dentro un menu a tendina.
- **Verifica.** Un collega che apre la pagina dopo un mese capisce cosa fare senza istruzioni né memoria?

### R6 — Poche scelte per volta, scorciatoie per chi ha pratica
- **Regola.** Limitare le decisioni simultanee; spezzare i processi lunghi; offrire scorciatoie agli esperti.
- **Perché.** Legge di Hick: il tempo di decisione cresce con il numero di scelte. Euristica 7: principianti e utenti quotidiani hanno bisogni diversi.
- **Come.** Form lunghi a passi (`M3Stepper`) o in sezioni con titolo; azioni di riga: da due in su in `M3RowActionsMenu`, una sola come azione diretta; ricerca e liste lunghe con paginazione/virtualizzazione; per l'uso quotidiano: `CommandPalette` (Ctrl+K), ordine di tabulazione logico, azioni più frequenti raggiungibili da tastiera. Nota: "7±2" non è un tetto di voci di menu, riguarda cosa l'utente deve *ricordare*.
- **Verifica.** Quante decisioni diverse chiede questa schermata nello stesso momento? Si possono raggruppare, rimandare o spezzare?

### R7 — Raggruppare con lo spazio e con lo stile
- **Regola.** Elementi correlati vicini, gruppi diversi distanti. Azioni simili hanno aspetto simile; azioni diverse, aspetto diverso.
- **Perché.** Gestalt: prossimità e similarità sono i modi più potenti e meno costosi di comunicare relazioni. Spaziature tutte uguali cancellano la gerarchia.
- **Come.** Scala di spaziature dei token (small tra campi correlati, medium tra gruppi, large prima dell'azione finale; vedi `DESIGN.md`); bordi e divisori solo quando la prossimità non basta; stesso componente per la stessa funzione in ogni pagina.
- **Verifica.** Coprendo i bordi, si capisce comunque quali elementi vanno insieme?

### R8 — Errori e stati vuoti: dicono cosa, perché e cosa fare
- **Regola.** Ogni messaggio all'utente (errore, avviso, vuoto) contiene tre cose: cosa è successo, perché, cosa può fare adesso.
- **Perché.** Euristica 9. Un messaggio senza via d'uscita è un vicolo cieco (la 404 senza link). La fiducia dell'utente è un serbatoio: ogni messaggio ostile la svuota.
- **Come.**
  - Testi nuovi: niente "errore", "fallito", "non valido", "invalid", "failed"; niente codici, sigle o termini tecnici; niente imperativi secchi o punti esclamativi ("Questo campo è obbligatorio!"). Preferire un tono colloquiale e utile: *"Ci serve il numero di telefono per avvisare l'ospite."*
  - Errori di campo *accanto al campo*; il riepilogo in cima può esistere ma non è mai l'unico segnale.
  - Il codice interno o l'id di correlazione vanno nei log, non nel messaggio.
  - Stato vuoto = `M3EmptyState` con `description` e `action`: *"Nessuna camera in questo stato. Mostra tutte le camere."* Con filtro attivo il vuoto propone di rimuovere il filtro.
  - Errore di caricamento = `M3ErrorState` con "Riprova".
  - Colori: giallo/avviso per ciò che non è un guasto; rosso solo per ciò che lo è (e mai da solo, vedi R11).
  - Servizi sensibili: un messaggio che rivela se un'email è registrata è un compromesso con la sicurezza; in caso di dubbio, neutro.
- **Verifica.** Se il messaggio comparisse da solo su uno schermo, l'utente saprebbe cosa fare?

### R9 — Prevenire l'errore, rendere reversibile
- **Regola.** Meglio impedire lo sbaglio che segnalarlo; meglio poter annullare che chiedere conferma.
- **Perché.** Euristiche 3 e 5. La conferma "Sei sicuro?" funziona male: compare quando l'utente ha già deciso e viene confermata senza leggere.
- **Come.**
  - Azioni reversibili: eseguirle e offrire l'annullamento. *(Oggi `Toast` non ha un'azione "Annulla": se serve, estendere il componente, non aggirarlo.)*
  - Azioni irreversibili: `M3ConfirmDialog` che **nomina l'oggetto** ("Eliminare la camera 204?") con pulsanti dal verbo preciso ("Elimina camera", non "OK"/"Sì").
  - Vincoli: disabilitare l'azione non ancora possibile *e dire perché* (testo vicino o tooltip accessibile); validare i valori plausibili (importi, date, intervalli) prima dell'invio.
  - Tenere lontane le azioni facilmente confondibili ("Elimina" non accanto a "Modifica").
  - Modifiche non salvate: avvisare prima di perderle. *(Oggi non c'è un guard standard: in un form lungo, aggiungerlo.)*
- **Verifica.** Qual è l'errore più probabile qui? È già impedito, o almeno rimediabile in un clic?

### R10 — Consistenza, convenzioni e orientamento
- **Regola.** Stessa cosa, stesso aspetto, stessa parola, stessa posizione. L'utente deve sapere sempre dove si trova e come tornare.
- **Perché.** Euristica 4 e legge dell'esperienza passata: l'utente porta con sé le abitudini di altre app. Innovare *contro* le convenzioni ha un costo; l'originalità va spesa sul contenuto, non sulla posizione del menu.
- **Come.**
  - Una pagina elenco segue sempre la ricetta di `DESIGN.md` (`PageHeader` → `ListToolbar` → tabella → `M3Pagination` con `summary`).
  - Titolo sempre da `PageHeader` (un solo `h1`), breadcrumb dalla route (`config/navigation.ts`), voce di menu attiva evidenziata.
  - Un drill-down offre sempre un modo per tornare (`onBack`) e conserva lo stato della lista (R4).
  - Stesso verbo per la stessa azione in tutta l'app (non "Salva" qui e "Conferma" altrove); termini del dominio alberghiero (H2): "Camera", "Soggiorno", "Pulizie", non nomi interni di entità.
  - Icone sempre accompagnate da un'etichetta, accessibile o visibile.
- **Verifica.** Un'azione uguale a un'altra pagina si comporta e si chiama allo stesso modo?

### R11 — Touch, responsive, colore non da solo
- **Regola.** Si progetta partendo dallo schermo piccolo; nessuna informazione dipende dall'hover o dal solo colore.
- **Perché.** Il touch non ha hover; i daltonici non distinguono rosso da verde; il mobile first costringe a decidere cosa conta davvero.
- **Come.**
  - Layout a 390 px, poi a 712 e oltre: nessuno scroll orizzontale; i gruppi di controlli vanno a capo.
  - Ogni dettaglio mostrato su hover ha un equivalente a focus/click/tap; i tooltip non portano informazione essenziale.
  - Stato = icona *e* testo oltre al colore (`M3StatusChip`, vedi `DESIGN.md`, "Accessibility").
  - Non disabilitare mai lo zoom (`user-scalable=no`).
- **Verifica.** Funziona con una mano, senza mouse e in bianco e nero?

### R12 — Dati personali, stampa e URL (privacy by design)
- **Regola.** Una schermata o un foglio stampabile mostra solo i dati personali strettamente necessari al compito.
- **Perché.** Minimizzazione dei dati (GDPR): ciò che si stampa o si copia in un link esce dal perimetro dell'app.
- **Come.** Fogli stampabili (worksheet, ecc.): nessun nome ospite; URL, titoli di pagina e log senza dati anagrafici; in un'anteprima mostrare il minimo (numero camera, non l'ospite) a meno che il compito non lo richieda.
- **Verifica.** Se questo schermo o questo foglio finisse in mano a un estraneo, cosa vedrebbe?

---

## 3. Come applicarle: nuova schermata o modifica

### Schermata nuova
1. **Prima del codice.** Chi la usa, con quale dispositivo, quanto spesso? (sezione 1). Quali sono le due o tre azioni che contano? Disegnare a 390 px prima di allargare.
2. **Progetto.** Riusare i componenti `m3/` e le ricette di `DESIGN.md` prima di inventare. Rispondere alle **5 domande di chiusura**:
   1. Qual è il punto focale? (R1)
   2. Quante scelte ci sono nello stesso momento? (R6)
   3. Cosa deve ricordare l'utente? (R5)
   4. Gli errori compaiono dove l'utente guarda, in linguaggio umano? (R8)
   5. Le spaziature raggruppano ciò che è collegato? (R7)
3. **Implementazione.** URL con lo stato (R4), stati di caricamento/vuoto/errore con testo (R3, R8), testi in `en` **e** `it` (vedi [`I18N.md`](I18N.md)), test con `vitest-axe`.
4. **Verifica a occhio.** Aprire la pagina nel browser, con dati realistici, a 390 e 1280 px e con tema scuro. Navigare *solo con la tastiera*.

### Schermata esistente (modifica)
- Si applicano le regole che la modifica **tocca**; non si rifà l'intera pagina nella stessa PR.
- Regola della **non regressione**: la modifica non deve peggiorare nessuna regola, anche se la pagina era già non conforme. Se si passa da una regola violata, conviene sistemarla se costa poco; altrimenti annotarla nella PR.
- Una deviazione voluta si scrive nella descrizione della PR con la motivazione ("perché qui R9 non si applica").

### Valutazione euristica (per le schermate più importanti)
Prima di modificare una pagina importante, guardarla dal vivo e compilare una mini tabella; ripetere a lavoro finito. Va nella descrizione della PR.

| # | Problema osservato | Euristica (H1-H10) | Regola | Gravità 0-4 |
|---|---|---|---|---|
| 1 | … | H6 | R5 | 3 |

Gravità (Nielsen): 0 non è un problema · 1 estetico · 2 minore · 3 maggiore · 4 catastrofe.
Le 10 euristiche: H1 stato del sistema · H2 sistema e mondo reale · H3 controllo e libertà · H4 consistenza e standard · H5 prevenzione dell'errore · H6 riconoscimento · H7 flessibilità ed efficienza · H8 design minimalista · H9 aiuto con gli errori · H10 documentazione.

**Limite da conoscere.** La valutazione fatta da chi conosce il dominio è una buona *scrematura*, ma non sostituisce l'osservazione di un utente reale: l'esperto non ha l'ingenuità di chi usa l'app per la prima volta. Per le schermate critiche (check-in, fatturazione, pulizie) far provare la schermata a una persona del reparto, senza spiegarle nulla, e guardare dove esita (think aloud). Si osserva, non si intervista: le persone dicono bisogni diversi da quelli che hanno.

---

## 4. Checklist per la pull request

Copiata nel template della PR (`.github/pull_request_template.md`). Per le PR che toccano il frontend, spuntare o motivare ogni riga.

- [ ] R1 Una sola azione primaria; le altre pesano meno
- [ ] R3 Caricamento, esito ed errore visibili; nessun doppio invio
- [ ] R4 Filtri/data/tab nell'URL; indietro e aggiorna funzionano; nessun dato personale nell'URL
- [ ] R5 Etichette visibili; placeholder solo come esempio; formati mostrati prima
- [ ] R8 Errori e vuoti: cosa/perché/cosa fare; niente "errore/fallito", niente codici
- [ ] R9 Azioni distruttive: annullabili o confermate nominando l'oggetto
- [ ] R10 Componenti e verbi coerenti con le altre pagine; breadcrumb e ritorno ok
- [ ] R11 Provata a 390 px, da tastiera, senza hover; stato mai solo a colori
- [ ] R12 Nessun dato personale non necessario in schermata, stampa, URL
- [ ] Testi in `en` e `it`; test con asserzione axe
- [ ] Tabella euristica (prima/dopo) nella PR, se la schermata è importante
- [ ] Le righe non spuntate hanno una motivazione scritta

---

## 5. Mappa euristiche → regole

| Euristica | Regole |
|---|---|
| H1 Stato del sistema | R3, R4 |
| H2 Sistema e mondo reale | R8, R10 |
| H3 Controllo e libertà | R4, R9, R10 |
| H4 Consistenza e standard | R7, R10 |
| H5 Prevenzione dell'errore | R5, R9 |
| H6 Riconoscimento | R5, R6 |
| H7 Flessibilità ed efficienza | R6 |
| H8 Design minimalista | R1, R2, R6 |
| H9 Aiuto con gli errori | R8 |
| H10 Documentazione | R5, R8 |

---

## 6. Lacune note (da colmare quando servono)

Il codice attuale non fa ancora tutto ciò che queste regole chiedono. Non sono motivo per ignorare la regola, ma per sapere cosa manca:

- `Toast` non offre l'azione "Annulla" (R9).
- Non c'è un guard standard per le modifiche non salvate (R9).
- Molti testi esistenti usano ancora "errore/fallito" (R8): sono congelati nella baseline (vedi sotto) e si correggono quando si tocca quella schermata, non in blocco.
- L'adesione a R4 varia tra pagine: Stays e CalendarPlanning usano già `useSearchParams`, altre no.
- Il controllo è in gran parte manuale (checklist PR). Fa eccezione il lessico dei testi (R8): `frontend/src/locales/uiText.test.ts` blocca nei file `locales/en|it` parole come "error/failed/invalid" ("errore/fallito/non valido"), codici tipo `ERR-123` e punti esclamativi nei testi nuovi o modificati. I testi esistenti che non rispettano la regola sono congelati in `uiTextBaseline.json`, che può solo diminuire: correggi un testo, poi togli la sua riga. Un uso giusto di una di queste parole va in `allowed`, con il motivo. Il test non giudica se il messaggio è completo (cosa/perché/cosa fare): quello resta alla checklist e alla revisione.
