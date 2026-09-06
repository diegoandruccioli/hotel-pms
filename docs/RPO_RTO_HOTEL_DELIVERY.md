# Backup e ripristino — cosa aspettarsi davvero

**Per:** il gestore dell'hotel (non un documento tecnico)
**Data:** 2026-09-06
**Perché questo documento**: prima di affidarsi al sistema per la gestione
quotidiana, è giusto sapere con chiarezza cosa succede se il PC si rompe, si
guasta o viene rubato — e cosa NON succede.

---

## Il fatto più importante

**Questo pilota gira su un solo PC.** Non c'è un secondo PC pronto a
sostituirlo in automatico. Se il PC si guasta, **il sistema si ferma** finché
non viene ripristinato su un PC nuovo o riparato. Durante quel tempo di
fermo, prenotazioni, check-in/check-out e fatturazione vanno gestiti a mano
(carta, foglio Excel, telefono) fino al ripristino.

Questo non è un difetto nascosto: è una scelta esplicita, coerente con
l'essere un pilota su un solo PC, non un'installazione con hardware
ridondato. Un'installazione con quel livello di affidabilità è un progetto
diverso, con un costo diverso.

## Cosa è protetto, e come

I dati (prenotazioni, ospiti, fatture, tutto) vengono salvati automaticamente
due volte al giorno, più continuamente in background (ogni movimento viene
scritto anche in un log tecnico che permette di recuperare i dati fino a
pochi minuti prima di un guasto, non solo fino all'ultimo salvataggio
programmato):

- **Una copia locale**, sullo stesso PC (protegge da errori software, non da
  furto/incendio/guasto hardware del PC stesso).
- **Una copia fuori sede**, su un servizio cloud indipendente (Backblaze B2)
  — protegge anche se il PC viene rubato, distrutto o il locale va a fuoco.

Entrambe le copie sono cifrate: solo chi possiede la password di cifratura
può leggerle. Questa password **non è recuperabile da nessuno se viene
persa** (nemmeno da chi ha sviluppato il sistema) — per questo va conservata
con cura in un posto sicuro, fisicamente separato dal PC dell'hotel (es. una
chiavetta USB in cassaforte, o un gestore di password su un altro
dispositivo). Se si perde quella password insieme al PC, la copia fuori sede
diventa dati illeggibili per chiunque.

## I due numeri che contano

**RPO (quanti dati si rischia di perdere in caso di guasto improvviso)**:
pochi minuti — non un giorno intero come con un backup notturno tradizionale.

**RTO (quanto tempo serve per ripartire su un PC nuovo, dati inclusi)**: il
solo ripristino dei dati, misurato oggi su un volume di dati da pilota, ha
richiesto **circa 7 secondi**. A questo va aggiunto il tempo per preparare un
PC nuovo (installare il software di base) e recuperare fisicamente la
password di cifratura dalla sua custodia — tempo che dipende da quanto
velocemente si riesce a procurarsi un PC sostitutivo e a raggiungere dove è
custodita la password, non dal software.

**Questi numeri vanno rimisurati quando il sistema sarà in uso reale**, con
mesi di dati accumulati invece dei pochi dati del pilota, e sull'hardware
reale dell'hotel invece che su una macchina di sviluppo. Il numero di oggi è
una prova che il meccanismo funziona, non una garanzia contrattuale sul
tempo esatto a regime.

## Cosa è già stato verificato per davvero

Non solo "il backup è configurato" — è stato controllato che il ripristino
funzioni realmente, più volte:

- Un ripristino di prova, isolato e senza toccare i dati veri, ha
  recuperato correttamente tutte le informazioni salvate.
- Un controllo automatico settimanale (che gira da solo, senza intervento
  umano) scarica l'ultima copia fuori sede e verifica che sia
  effettivamente utilizzabile — non solo che esista.

## Una precisazione sul backup: è dell'intero sistema, non del singolo hotel

Il backup descritto sopra salva **tutti i dati dell'installazione**, non un
singolo hotel selezionabile a parte. Per questo pilota — un solo hotel su
questa installazione — non fa alcuna differenza pratica: ripristinare
"tutto" o ripristinare "il tuo hotel" sono la stessa cosa. Diventerebbe
rilevante solo se in futuro più hotel condividessero la stessa
installazione, uno scenario non previsto per questo pilota.

## Cosa manca ancora, onestamente

- **Nessun avviso automatico oggi raggiunge una persona** in caso di guasto
  (es. un'email). Il sistema tecnico che lo permetterebbe esiste ma non è
  ancora collegato a un indirizzo email reale — è il prossimo passo prima
  del go-live.
- Il monitoraggio dello spazio disco disponibile sul PC non è ancora attivo:
  su un PC solo, il disco pieno è il guasto più probabile, e oggi non c'è un
  avviso dedicato per quello.

Questi due punti verranno chiusi prima di considerare il pilota pronto per
l'uso quotidiano senza supervisione tecnica ravvicinata.
