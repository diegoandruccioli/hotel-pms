# dependencies.md — Scansione CVE dipendenze

Sola analisi, nessun codice toccato. Due fonti combinate, non ridondanti:

1. `trivy fs` locale (Docker, `aquasec/trivy:latest`, DB `ghcr.io/aquasecurity/trivy-db:2`,
   scan del 2026-08-13 ~21:41 UTC) contro il repository intero (`build/`, `node_modules/`,
   `.git/`, `dist/` esclusi).
2. I job Trivy **già configurati in CI** (`.github/workflows/ci.yml`, job `trivy` e
   `trivy-thirdparty`) contro le immagini Docker buildate — dall'ultima run verde su
   `main`, commit `c872cad` (run id `31735606064`, 2026-08-13 19:23 UTC).

## Nota metodologica — perché due fonti

`trivy fs` ha **supporto nativo per `pom.xml` (Maven), non per Gradle senza lockfile**.
Questo repo è Gradle Kotlin DSL puro, nessun `gradle.lockfile` committato — la scan
filesystem locale ha infatti enumerato **un solo target**: `frontend/package-lock.json`
(npm), **0 vulnerabilità trovate a quella severità**. Zero dipendenze Java sono state
enumerate da `trivy fs` per questo motivo strutturale, non perché siano pulite.

I job CI scansionano invece le **immagini Docker già buildate** (jar risolti reali dentro
`app.jar`), che è l'unico modo efficace di ottenere CVE Java in questo stack — e sono già
attivi e verdi nella pipeline esistente, quindi riusati qui invece di duplicare lo sforzo.

---

## Findings

### HIGH — `httpcore5` CVE-2026-54399, presente identico in 7/8 servizi backend

| Servizio | Pacchetto | Versione installata | Versione fix |
|---|---|---|---|
| api-gateway | `org.apache.httpcomponents.core5:httpcore5` | 5.3.6 | 5.4.3 / 5.5-beta2 |
| auth-service | idem | 5.3.6 | idem |
| guest-service | idem | 5.3.6 | idem |
| frontdesk-service | idem | 5.3.6 | idem |
| billing-service | idem | 5.3.6 | idem |
| fb-service | idem | 5.3.6 | idem |
| config-service | idem | 5.3.6 | idem |

**Sfruttabilità reale**: nessuna dichiarazione diretta di `httpcore5` in alcun
`build.gradle.kts` (grep sul repo, zero match) — è **transitivo**, quasi certamente via
Spring Cloud OpenFeign (client HTTP di default) o Spring Boot's Apache HttpClient5
auto-config, presente identico su tutti e 7 i servizi che condividono lo stesso BOM
Spring Boot 3.5.16/Spring Cloud 2025.0.0. Non risolvibile con un override isolato per
servizio (comparirebbe drift come già successo in passato con Netty/Spring Cloud Gateway,
vedi `THREAT_MODEL.md` DEP-CVE-01) — la fix corretta è un bump del BOM o un
`dependencyManagement` override centralizzato una volta chiarito il CVE esatto (CVE
recentissimo, 2026, verificarne il dettaglio — non ancora triagato in `THREAT_MODEL.md`
né in `.trivyignore`). **Non ancora tracciato come DEP-CVE-XX — candidato per il prossimo
numero libero**.

**notification-service non è mai scansionato**: il job matrix in `.github/workflows/ci.yml`
(righe ~120-149) elenca `config-service, api-gateway, auth-service, guest-service,
frontdesk-service, billing-service, fb-service, frontend` — **manca notification-service**,
nonostante abbia un proprio `Dockerfile` e sia deployato in `docker-compose.yml`. Gap di
copertura CI, non una vulnerabilità di per sé, ma significa che se `httpcore5` (o
qualunque altro CVE) fosse presente anche lì, nessuno se ne accorgerebbe automaticamente.

### Frontend — immagine pulita, 1 HIGH npm dev-only già noto

- Immagine `nginx-unprivileged:alpine` (post-bump di oggi, GAP-14): **0 vulnerabilità** sui
  pacchetti OS Alpine 3.24 nella scan CI.
- `npm install` durante il build Docker riporta 1 vulnerabilità HIGH — è
  `brace-expansion` (GHSA-mh99-v99m-4gvg/GHSA-rgw5-rvv9-x895), transitivo via
  `@typescript-eslint/typescript-estree` (dev-only, mai nel bundle prod), **già
  identificato e triagato come fuori scope** in una sessione precedente
  (`backup/SUMMARY.md`, 2026-08-11 21:20) — non ri-aperto qui.
- `trivy fs` conferma indipendentemente: 0 vulnerabilità su `frontend/package-lock.json`
  a livello CRITICAL/HIGH/MEDIUM (la scan Trivy usa un DB diverso da npm audit — nessuna
  contraddizione, solo copertura CVE leggermente diversa tra le due fonti).

### Immagini di terze parti (informational, warn-only per design — vedi `ci.yml`)

Job `trivy-thirdparty` è dichiaratamente non-bloccante (`exit-code: "0"`, il progetto non
controlla il ciclo di release upstream). Risultati dell'ultima run:

| Immagine | Totale | Critical | High |
|---|---|---|---|
| `postgres:15-alpine` | 9 | 1 | 8 |
| `redis:8.8.1-alpine` | 6+5+8 (3 layer distinti) | 1 | 18 |
| `grafana/grafana:13.1.1` | — | — | — (bumpata di recente, DEP-CVE-07) |

Tutti i CVE campionati su `postgres:15-alpine` sono nel Go stdlib bundlato
(`crypto/tls`, `net/url`, `crypto/x509` — DoS/parsing, es. `CVE-2025-68121` CRITICAL)
— stesso pattern già discusso e accettato in `THREAT_MODEL.md` per Grafana (DEP-CVE-07):
un'immagine di terze parti offre come unica leva la versione, non un fix puntuale.
**Non ri-aperto come nuovo finding** — coerente con la postura già documentata dal
progetto, ma il conteggio CRITICAL=1 su postgres non risultava esplicitamente enumerato
prima: verificare se `.trivyignore` lo copre già o se manca una riga.

---

## Tabella riepilogo

| Finding | Componente | Severità | Fix disponibile | Sfruttabile qui? |
|---|---|---|---|---|
| CVE-2026-54399 | httpcore5 5.3.6 (7 servizi backend) | HIGH | 5.4.3 / 5.5-beta2 | Da valutare — transitivo, non ancora triagato |
| Gap copertura CI | notification-service assente dal matrix Trivy | — (gap processo) | aggiungere al matrix | N/A |
| GHSA-mh99/rgw5 | brace-expansion (npm, dev-only) | HIGH | disponibile | No — già triagato, dev-only |
| CVE-2025-68121 + altri | Go stdlib in postgres:15-alpine | CRITICAL+HIGH | richiede nuova immagine upstream | Accettato, stesso pattern DEP-CVE-07 |
| vari | redis:8.8.1-alpine (18 HIGH totali su 3 layer) | HIGH | richiede nuova immagine upstream | Non ancora triagato singolarmente |
