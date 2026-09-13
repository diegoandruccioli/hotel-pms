# Corrispettivi telematici — opzioni di mercato e perché non li implementiamo ora

**A cosa serve questo documento**: raccogliere come i gestionali alberghieri commerciali
implementano oggi i corrispettivi telematici, quali opzioni tecniche esistono realmente, e
perché hotel-pms ha deciso di non implementare nessuna delle due ora — solo su richiesta
esplicita di un cliente pagante, appoggiandosi a un fornitore terzo già certificato, mai
costruendo la soluzione in casa. Si affianca a
[`CORRISPETTIVI_TELEMATICI_COMMERCIALISTA_BRIEF.md`](CORRISPETTIVI_TELEMATICI_COMMERCIALISTA_BRIEF.md)
(le domande aperte per il commercialista) e a `backup/DECISIONS.md` ADR-006 (la decisione
sul pilota e sul prefisso di numerazione). Ricerca di mercato del 2026-09-13.

---

## Il vincolo normativo che determina le opzioni disponibili

Dal **7 marzo 2025** (Provvedimento Agenzia delle Entrate n. 111204), una soluzione
*software* che sostituisce il registratore telematico fisico deve essere **certificata da
un Ente Certificatore accreditato dall'Agenzia delle Entrate** — nessuna soluzione software
può essere commercializzata senza questa certificazione. L'elenco degli enti certificatori
è pubblico sul portale AdE.

**Conseguenza diretta**: un gestionale generico come hotel-pms non può memorizzare e
trasmettere corrispettivi in autonomia — violerebbe il principio di "adempimento
contestuale e unitario" della trasmissione telematica. L'unico percorso legittimo è
integrarsi **in tempo reale**, via API, con una soluzione software già certificata da
terzi. Fonte: analisi Bludata, che cita la Risposta a Interpello dell'Agenzia delle Entrate
n. 413 come riferimento per la verifica col commercialista prima di adottare qualunque
soluzione — la responsabilità di conformità resta sempre in capo a chi adotta la
soluzione, non solo al fornitore.

## Le due opzioni di mercato

### Modello A — Registratore Telematico fisico + stampante fiscale

Il modello "tradizionale", usato dalla maggior parte dei gestionali alberghieri italiani
storici (es. Zucchetti/Ericsoft, TeamSystem Hospitality, Bedzzle): il PMS invia l'importo a
una stampante fiscale/RT fisico collegato in rete locale in ogni struttura. Il PMS resta un
semplice client che comanda la cassa fiscale; la certificazione e la responsabilità RT sono
del produttore hardware, non del gestionale.

**Perché non è adatto a noi**: richiede hardware fisico presente in ogni hotel cliente, un
protocollo di comunicazione locale (seriale/rete, dipende dal modello di stampante), e non
si integra naturalmente con un'architettura SaaS multi-tenant come hotel-pms — ogni
installazione diventerebbe un progetto di integrazione hardware a sé.

### Modello B — Soluzione software certificata via API ("RT virtuale")

Il modello emergente 2025-2026: un provider terzo, già certificato da un Ente Certificatore
AdE, espone un'API REST; il gestionale chiama l'API in tempo reale per ogni corrispettivo,
senza alcun hardware fisico. Esempi verificati: **A-Cube** (`acubeapi.com`, già indicato in
`docs/ROADMAP.md` E3bis per la trasmissione SDI diretta e la conservazione sostitutiva —
lo stesso account coprirebbe anche i corrispettivi), **Effatta** (`effatta.it`, piattaforma
API-first equivalente con sandbox di test pubblica).

Flusso tecnico tipico (da Effatta, rappresentativo del modello): autenticazione →
registrazione una tantum dei dati del punto cassa sul portale AdE → emissione scontrino via
API → il provider trasmette immediatamente all'Agenzia (nessun dato memorizzato in locale
lato gestionale) → PDF generato dalla procedura ufficiale del provider.

**Perché è l'unico modello compatibile con hotel-pms, se mai servisse**: nessun hardware,
integrazione puramente API — lo stesso pattern già usato nel progetto per altri servizi
esterni fiscali/PA (credenziali per-hotel cifrate come `AlloggiatiCredentialEncryptor` in
frontdesk-service, `DocumentNumberEncryptor` in guest-service; Feign client con circuit
breaker come tutti gli altri client inter-servizio). Scala naturalmente su
un'architettura multi-tenant.

## Confronto

| | Modello A — RT fisico | Modello B — provider software certificato |
|---|---|---|
| Hardware richiesto | Sì, per ogni hotel | No |
| Certificazione nostra richiesta | No (del produttore hardware) | No (del provider) |
| Adatto a SaaS multi-tenant | ❌ No | ✅ Sì |
| Pattern di integrazione già noto nel progetto | No | ✅ Sì (stesso schema Alloggiati/A-Cube) |
| Consigliato per hotel-pms, se mai servisse | ❌ No | ✅ Sì |

## Perché non implementiamo nessuna delle due, ora

1. **Probabile esenzione**: hotel-pms fattura sempre elettronicamente il pernottamento
   (mai scontrino/documento commerciale) e ogni addebito F&B è sempre legato a un
   soggiorno attivo (`fb-service` impone `stayId NOT NULL` su ogni ordine — nessuna vendita
   diretta a un cliente non alloggiato esiste nel sistema). L'obbligo di corrispettivi
   telematici riguarda tecnicamente chi emette scontrino — un sistema che non lo emette mai
   potrebbe risultarne esonerato per l'intero perimetro attuale. Ipotesi ben sourciata, non
   ancora confermata da un commercialista (vedi il brief collegato).
2. **Nessuna certificazione da costruire in casa**: anche se l'esenzione non reggesse, la
   strada tecnica corretta (Modello B) è integrarsi con un provider già certificato, non
   ottenere una nostra certificazione — coerente con ADR-002 del progetto (non reinventare
   infrastruttura non-core quando esiste una libreria/servizio maturo).
3. **Nessun cliente reale lo richiede oggi**: stesso standard già applicato ad altre
   funzionalità rinviate nel progetto (E3bis — trasmissione diretta SDI, E17 — ROSS1000
   ISTAT) — costruire un'integrazione prima che serva davvero è costo di opportunità senza
   ritorno, specialmente pre-revenue.
4. **Effort reale contenuto quando servirà**: se un cliente pagante lo richiedesse, o il
   commercialista confermasse che l'obbligo si applica, il lavoro tecnico è limitato
   (integrazione API + encryptor per credenziali, pattern già rodato) — non è un motivo per
   anticiparlo, ma nemmeno un rischio se rinviato.

## Trigger di riapertura

- Un commercialista conferma che l'obbligo si applica al nostro caso d'uso (pernottamento
  sempre fatturato elettronicamente), **oppure**
- Un cliente pagante richiede esplicitamente la funzionalità (es. vuole vendere F&B anche a
  clienti non alloggiati, scenario in cui l'esenzione cadrebbe per quella sola quota).

In entrambi i casi: scegliere tra A-Cube ed Effatta confrontando condizioni commerciali
reali (nessuna delle due pubblica un listino, serve contatto diretto — stesso approccio già
usato per il primo contatto con i canale manager OTA), poi aprire un piano di
implementazione dedicato per il Feign client, l'encryptor delle credenziali, e i dati esatti
richiesti per transazione (da verificare sulla documentazione tecnica del provider scelto).

## Fonti

- Provvedimento Agenzia delle Entrate n. 111204 del 7 marzo 2025 (obbligo di certificazione
  delle soluzioni software) — portale AdE, sezione Enti Certificatori.
- Bludata, *"Il Registratore Telematico può essere sostituito dal gestionale?"* — modello di
  responsabilità, riferimento alla Risposta a Interpello AdE n. 413.
- Effatta (`effatta.it/scontrino-elettronico`) — architettura API rappresentativa del
  Modello B.
- A-Cube (`acubeapi.com/prodotti/api-scontrino-elettronico-smart`) — provider già scelto
  per E3bis in `docs/ROADMAP.md`.
- Bedzzle, Zucchetti/Ericsoft, TeamSystem Hospitality — esempi verificati del Modello A sul
  mercato PMS alberghiero italiano.
