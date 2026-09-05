# Brief per il commercialista — Corrispettivi telematici Horeca

**A cosa serve questo documento**: raccogliere in un unico posto le domande a cui
serve una risposta professionale prima di decidere se/come collegare questo
sistema ai corrispettivi telematici (invio giornaliero al portale "Fatture e
Corrispettivi" dell'Agenzia delle Entrate), obbligo in vigore dal 2026-01-01 per
l'intero settore Horeca (hotel + ristorazione). Ogni fatto tecnico qui sotto è
verificato sul codice sorgente al 2026-09-05, non dichiarato a intuito — vedi
`backup/DECISIONS.md` ADR-006 per la decisione presa nel frattempo (nessuna
integrazione nel pilota, solo un banner di avviso nell'interfaccia).

**Stato attuale**: nessuna integrazione con corrispettivi telematici o con un
registratore telematico. Il pilota resta in affiancamento al gestionale fiscale
primario dell'hotel, che rimane responsabile dell'adempimento.

---

## 1. Il pernottamento ricade nell'obbligo?

Il sistema emette per ogni soggiorno una fattura elettronica (FatturaPA, tramite
SDI) o, in alternativa, una ricevuta non fiscale interna (`DocumentType.RICEVUTA`
— vedi §3, disclaimer "documento non fiscale — copia di cortesia" già stampato
sul PDF). Non emette mai uno scontrino/documento commerciale.

**Domanda**: il solo pernottamento fatturato (senza vendita F&B diretta a un
cliente non alloggiato — vedi §2) ricade nell'obbligo di trasmissione
corrispettivi, oppure la fattura elettronica già emessa tramite SDI è di per sé
sufficiente e i corrispettivi telematici riguardano solo le vendite senza
fattura (tipicamente il bar/ristorante a clienti esterni)?

## 2. F&B a non alloggiati

`fb-service` impone `stayId NOT NULL` su ogni ordine: **non esiste, nel sistema,
un caso d'uso "vendita F&B diretta a un cliente non alloggiato"** — ogni
addebito F&B è sempre legato a un soggiorno attivo e finisce sul folio di quel
soggiorno, fatturato con lo stesso documento del pernottamento.

**Domanda**: se in futuro l'hotel volesse vendere anche a clienti esterni
(bar/ristorante aperto al pubblico, non solo agli alloggiati), a quel punto
scatterebbe l'obbligo indipendentemente da cosa succede oggi con i soli
alloggiati? È un fattore da tenere presente nello scoping futuro, non una
domanda per il pilota attuale.

## 3. La ricevuta interna del pilota è ammissibile in affiancamento?

Il sistema può emettere, invece di una fattura elettronica, una "ricevuta"
interna: un PDF con lo stesso disclaimer già presente
(`billing-service/.../templates/pdf/invoice-ricevuta.html`, "Documento non
fiscale — copia di cortesia. Non sostituisce lo scontrino o il documento
commerciale previsto dalla normativa vigente").

**Domanda**: usata solo come promemoria interno per l'operatore/il gestionale
primario (mai consegnata al cliente come unico documento), questa ricevuta crea
rischi di conformità, oppure è innocua proprio perché esplicitamente non
fiscale?

## 4. Numerazione fiscale condivisa tra fattura e ricevuta — sezionale o prefisso?

**Fatto verificato**: la numerazione (`InvoiceSequence`, un contatore per
hotel+anno) è **condivisa** tra `FATTURA` e `RICEVUTA` — ogni documento emesso,
di qualunque tipo, consuma un numero della stessa serie usata per le fatture
elettroniche reali. Emettere anche solo qualche ricevuta di prova durante il
pilota "brucia" comunque progressivi della serie fiscale che il gestionale
primario dell'hotel potrebbe voler usare in autonomia.

**Domanda**: per tenere il pilota davvero innocuo rispetto alla serie fiscale
reale, è necessario un sezionale separato (contatore distinto per tipo
documento) o basta un prefisso di serie non ambiguo (es. `PILOT/2026/0001`)
finché il pilota resta tale? Un prefisso di questo tipo renderebbe il documento
inammissibile come originale in qualche contesto?

## 5. Codice Natura per la tassa di soggiorno (già in `THREAT_MODEL.md`/E18)

La tassa di soggiorno (`ChargeType.CITY_TAX`) è oggi marcata fuori campo IVA con
`Natura` **N1** (art. 15 c.1 n.3 D.P.R. 633/1972, "anticipazioni fatte in nome e
per conto") — costante isolata in `InvoiceServiceImpl` per essere corretta in
un punto solo.

**Domanda**: N1 è il codice corretto, o è più appropriato **N2.2** (operazioni
non soggette, altri casi)? Rilevante anche per capire se la tassa di soggiorno
stessa entra nel perimetro dei corrispettivi telematici o ne resta fuori come
"anticipazione per conto del comune".

---

## Prossimi passi una volta ricevute le risposte

Vedi `backup/DECISIONS.md` ADR-006 e `docs/ROADMAP.md` E19 per lo stato
completo. In sintesi: se l'obbligo copre il pernottamento, serve (a) risolvere
il punto 4 sopra (sezionale o prefisso) prima di qualunque emissione reale nel
pilota, poi (b) valutare un collegamento POS↔registratore telematico via
provider (A-Cube è la prima scelta già verificata per E3bis, stesso account
gestirebbe anche i corrispettivi) — lavoro stimato solo dopo le risposte qui
sopra, non prima.
