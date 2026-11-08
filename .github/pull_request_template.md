## Cosa cambia e perché

<!-- Una o due frasi. Il "perché" conta più del "cosa". -->

## Come l'ho provato

<!-- Test automatici + prova a mano (browser, viewport, dati usati). -->

## Checklist UI

Solo per PR che toccano `frontend/` e cambiano una schermata. Regole complete:
[`docs/UI_GUIDELINES.md`](../docs/UI_GUIDELINES.md) (aspetto: [`frontend/DESIGN.md`](../frontend/DESIGN.md)).
Spunta ogni riga **oppure** scrivi accanto perché non si applica.

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
- [ ] Schermata importante: tabella euristica (prima/dopo) qui sotto

<!-- Tabella euristica, se serve:
| # | Problema | Euristica | Regola | Gravità 0-4 |
|---|---|---|---|---|
-->

## Note per chi rivede

<!-- Deviazioni volute dalle linee guida, rischi, cose da guardare. -->
