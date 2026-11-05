# Processo di rilascio — Hotel PMS

Come si porta `develop` su `main` e si pubblica una versione. Vale per il pilot mono-hotel:
host singolo, immagini costruite dal sorgente (nessun registry), deploy **manuale**.
Il modello di branch è in `docs/BRANCH_STRATEGY.md`; i comandi operativi di aggiornamento e
ripristino in `docs/OPERATIONS_RUNBOOK.md` (§5, §6, §8) e `docs/DEPLOYMENT_GUIDE.md` (§7).

```
develop --tag vX.Y.Z-rc.N--> (checklist rc) --PR--> main --tag vX.Y.Z--> deploy manuale
```

I tag li crea il maintainer a mano. I workflow `release-rc.yml` e `release.yml` (slice
successive) leggono il tag e producono report e GitHub Release, ma non fanno deploy.

## 1. Versioni e tag

| Tag | Dove | Significato |
|-----|------|-------------|
| `vX.Y.Z-rc.N` | commit di `develop` | candidato al rilascio, `N` parte da 1 |
| `vX.Y.Z` | commit di `main` dopo la promozione | rilascio |

- Semver: `Z` correzioni, `Y` funzioni nuove retrocompatibili, `X` rotture (API, schema DB
  non retrocompatibile, variabili `.env` obbligatorie nuove).
- Il numero del primo rilascio lo sceglie il maintainer. Esiste solo `v1.0.0-secure`, tag
  storico dell'esame di Secure Coding: non fa parte di questa sequenza.
- Un rc non si modifica: se serve una correzione si fa una PR verso `develop` e si taglia
  `rc.N+1`.

## 2. Checklist rc (prima del tag `-rc.N`)

Tutte le voci vanno verificate sul commit che riceverà il tag.

1. **CI verde su `develop`**: Backend, Frontend, Visual, E2E Docker Stack.
2. **E2E Live verde** (`e2e-live-nightly.yml`) sullo stesso commit: lanciarlo con
   `gh workflow run e2e-live-nightly.yml --ref develop` se l'ultimo run notturno è su un
   commit diverso. Durata circa 25-28 minuti.
3. **Gate Trivy**: nessuna vulnerabilità CRITICAL/HIGH con fix disponibile
   (`trivy.yaml`: `ignore-unfixed: true`). Le eccezioni accettate stanno solo in
   `.trivyignore`, con motivo e scadenza.
4. **Backup drill recente**: ultimo run di `backup-restore-drill.yml` verde, non più vecchio
   di 7 giorni (gira ogni lunedì). Se è rosso o vecchio, lanciarlo a mano prima del tag.
5. **Migration Flyway**: elencare le migration nuove rispetto all'ultimo rilascio
   (`git diff --stat <ultimo-tag>..HEAD -- '*/db/migration/*'`) e per ognuna annotare se è
   retrocompatibile col codice precedente (vedi §5).
6. **Variabili d'ambiente nuove**: confrontare `.env.example` con l'ultimo rilascio e
   annotare ogni variabile aggiunta o rinominata.
7. **CHANGELOG**: spostare le voci di `[Unreleased]` sotto la nuova versione.
8. **Dependabot di sicurezza**: nessun alert aperto sulle dipendenze di produzione (quelli
   solo dev-only si annotano nelle note di rilascio).

I controlli 1-4 sono informativi finché non sono automatizzati in `release-rc.yml`; dopo
alcuni rc senza falsi positivi si valuta se renderli bloccanti per la promozione.

## 3. Smoke test dello stack completo

Su una macchina pulita o con volumi dedicati (mai sui dati reali del pilot):

```bash
git checkout vX.Y.Z-rc.N
./gradlew clean build -x test
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
  --profile observability up -d --build
docker compose ps    # ogni servizio healthy o Up
docker exec api-gateway wget -qO- http://localhost:8090/actuator/health | grep '"status":"UP"'
```

Poi un giro manuale minimo: login, nuova prenotazione, check-in, fattura, check-out, e
verifica che Grafana/Alertmanager non abbiano alert aperti. L'E2E Live copre già il
percorso funzionale; lo smoke test serve a vedere la configurazione prod
(`docker-compose.prod.yml`), che l'E2E non usa.

## 4. Promozione e rilascio

1. Tag rc su `develop` e push del tag (azione del maintainer).
2. PR `develop` → `main`: CI completa e E2E Live obbligatori (già required su `main`),
   checklist §2 compilata nella descrizione della PR.
3. Merge. Poi tag `vX.Y.Z` sul commit di merge su `main` e push del tag.
4. Deploy manuale come da `docs/OPERATIONS_RUNBOOK.md` §8, **dopo** un backup:

```bash
docker exec hotel_postgres pg_dumpall -U postgres > "backup-pre-update-$(date +%Y%m%d).sql"
git fetch --tags && git checkout vX.Y.Z
./gradlew clean build -x test
docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile observability up -d --build
```

5. Verifica post-deploy: `docker compose ps`, health del gateway, nessun `ERROR` negli
   ultimi log, nessun alert Prometheus aperto.
6. Se `main` ha ricevuto commit non presenti in `develop` (hotfix), riportare `main` in
   `develop` con una PR.

## 5. Piano di rollback

Le immagini si costruiscono dal sorgente, quindi tornare indietro significa tornare al
tag precedente e ricostruire.

**Decidere in fretta**: se dopo il deploy health o smoke falliscono entro 30 minuti e non
c'è una correzione evidente, si torna indietro. Non si prova a riparare in produzione.

**Passo 1, codice**

```bash
git checkout vX.Y.Z-precedente
./gradlew clean build -x test
docker compose -f docker-compose.yml -f docker-compose.prod.yml --profile observability up -d --build
```

**Passo 2, database**: dipende dalle migration del rilascio (checklist §2 voce 5).

| Caso | Azione |
|------|--------|
| Nessuna migration nuova | Basta il passo 1. |
| Migration solo additive (colonna/tabella nuova, nullable, non letta dal codice vecchio) | Basta il passo 1: il codice vecchio le ignora. Non cancellare nulla. |
| Migration distruttiva o che cambia significato (DROP, rename, NOT NULL, tipo) | Ripristino dei dati: pgBackRest `restore` da repo locale o off-site (`docs/OPERATIONS_RUNBOOK.md` §5), oppure il dump `backup-pre-update-*.sql`. Si perdono i dati scritti dopo il deploy. |
| Migration fallita a metà | `docs/OPERATIONS_RUNBOOK.md` §6, poi passo 1. |

Flyway non ha migration di discesa. Regola per chi scrive migration: expand/contract. Un
rilascio aggiunge (expand), il rilascio successivo toglie (contract) quando il codice
vecchio non serve più. Una migration distruttiva nello stesso rilascio che cambia il
codice rende il rollback possibile solo con ripristino dati: va segnalata nella PR di
promozione.

**Passo 3, dopo il rollback**: tenere il tag `vX.Y.Z` (non si cancella né si sposta),
aprire una issue con la causa, correggere su `develop`, nuovo `rc.N+1`.

## 6. Hotfix

Da `main`, PR verso `main` con CI completa, tag `vX.Y.(Z+1)` dopo il merge, deploy come
§4 punto 4, poi `main` riportato in `develop`. Niente rc: la CI e l'E2E Live della PR
sono il cancello.
