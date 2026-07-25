# Branch Strategy — Hotel PMS

**Data:** 2026-07-25 (aggiornato — vedi `backup/PROJECT_ANALYSIS.md` §4 per l'analisi completa)  
**Stato:** Attivo — sviluppo continuo su `main`, non più "finale post-integrazione"

> Il progetto ha superato l'esame di Secure Coding il 2026-06-18. Da allora lo sviluppo è
> proseguito con obiettivo prodotto (SaaS commerciale), non più esame — questo file descrive la
> strategia di branch **attuale**, aggiornata rispetto alla topologia del 2026-05-08.

---

## Topologia dei branch (stato corrente)

```
pre-secure-coding (99e67c3, 2026-04-07) ──────────────────────── [congelato, baseline esame]
                   \
                    \── feature/secure-coding-hardening ───────── [congelato, storico hardening]
                    \          (27 commit security)              /
                     \                                          /
feature/frontend-development (257 commit indietro, 2026-05-08) ─[merge]
                                                                  |
                                                    main (2026-05-08, integrazione iniziale)
                                                                  |
                                    feature/frontdesk-consolidation ─[merge, 2026-06-20]
                                                                  |
                                          main (HEAD attuale — sviluppo continua qui direttamente)
```

Da `ac7685e` (merge iniziale, 2026-05-08) in poi, **tutto lo sviluppo regolare avviene con commit
diretti su `main`** — non su un branch `feature/*` dedicato. Le uniche eccezioni sono: lavoro di
sicurezza (branch `feature/secure-coding-hardening`, regola MANDATORY di CLAUDE.md) e la
ristrutturazione architetturale ADR-001 (branch dedicato per isolare i 823 file toccati dal
consolidamento prima del merge).

---

## Ruolo di ogni branch

| Branch | Scopo | Stato |
|---|---|---|
| `pre-secure-coding` | **Snapshot della baseline insicura** — punto di partenza "prima delle modifiche di sicurezza". Usato dalla commissione esame come riferimento del codice originale. ⚠️ Non più solo un diff di sicurezza: main ha da allora consolidato 3 servizi in `frontdesk-service` (ADR-001) e aggiunto `notification-service`+`pdf-template-engine` — un confronto diretto oggi attraversa anche quel refactor (823 file di differenza). | Congelato — non modificare |
| `feature/secure-coding-hardening` | **Storico hardening** — tutti i commit di sicurezza con SHA referenziati nel report LaTeX (`docs/security-report/report-secure-coding.tex`). Include: account lockout, BCrypt cost=12, token versioning, GDPR retention (T-GST-05), audit logging, HMAC fixes. | Congelato — non eliminare mai |
| `feature/frontdesk-consolidation` | **ADR-001** — consolida gli ex `inventory-service`/`reservation-service`/`stay-service` in `frontdesk-service` (bounded context reale: Room↔Reservation↔Stay condiviso su 3 DB senza FK). Merge il 2026-06-20. | Mergiato, congelato — conservabile come storico o eliminabile |
| `feature/frontend-development` | Branch di sviluppo usato fino all'integrazione iniziale (2026-05-08). **Non più una base valida**: è oggi ~257 commit dietro `main`, ripartire da lì significherebbe perdere tutto il lavoro successivo (hardening ADR-004, consolidamento frontdesk, notification-service, verticale fiscale, ecc.). | Mergiato, stale — **non usare come base per nuovi sviluppi** |
| `main` | **Branch di sviluppo attivo e di riferimento produzione.** Build verde, tutti i test passano. Riceve sia il lavoro regolare (commit diretti) sia i merge dei branch dedicati (sicurezza, consolidamenti architetturali). | Branch di riferimento — HEAD del progetto |

---

## Storia dell'integrazione

1. `pre-secure-coding` creato da `main` come snapshot — 2026-05-08
2. Correzioni al report LaTeX (MANAGER→OWNER, placeholder rimosso) — commit `06cfbd4`
3. Merge `feature/secure-coding-hardening` → `feature/frontend-development` — commit `420a89c`  
   Conflitti risolti: 8 file (billing-service + stay-service) — GDPR methods aggiunte, F&B methods conservate
4. Fix Checkstyle/PMD/test da T-GST-05 — commit `e8a7c13`
5. Merge `feature/frontend-development` → `main` — commit `ac7685e`  
   Conflitti risolti: 21 file frontend — presi da feature/frontend-development (stato completo)
6. Build verde su `main`: backend BUILD SUCCESSFUL, frontend 317/317, lint zero
7. **Esame di Secure Coding superato (30) — 2026-06-18.** Da qui in poi lo sviluppo prosegue con
   obiettivo prodotto, non più esame (vedi `backup/DECISIONS.md` §8 ADR post-esame).
8. Sviluppo regolare prosegue con commit diretti su `main` (niente più branch `feature/*` per il
   lavoro ordinario) — verticale fiscale, GDPR, notification-service, ricerca server-side, ecc.
9. `feature/frontdesk-consolidation` creato, sviluppato e mergiato in `main` — ADR-001, 2026-06-20.
   Riduce 9 servizi a 7 (poi 8 con l'aggiunta di `notification-service`), 3 database a 1.
10. Sicurezza continua a passare da `feature/secure-coding-hardening` con fast-forward merge in
    `main` ad ogni threat risolto (es. T-BILL-04/T-STAY-06 il 2026-07-18, T-ROOM-02 il 2026-07-18).

---

## Regole di governance

- `pre-secure-coding` e `feature/secure-coding-hardening` non vanno mai eliminati.
  Sono le evidenze storiche per l'esame di Secure Coding.
- Il report LaTeX (`docs/security-report/report-secure-coding.tex`) cita commit SHA
  del branch `feature/secure-coding-hardening`. Quei commit devono rimanere accessibili.
- **Sviluppi futuri partono da `main`.** `feature/frontend-development` non è più una base valida
  (257 commit indietro) — non ripartire da lì.
- Lavoro di sicurezza (auth/JWT/RBAC/CSRF/XSS/injection/secret management/rate limiting/audit
  log/IDOR) va **sempre** su `feature/secure-coding-hardening`, mai direttamente su `main`
  (regola MANDATORY, `CLAUDE.md`). Merge con fast-forward quando pronto.
- Ristrutturazioni architetturali maggiori (tipo il consolidamento ADR-001) vanno isolate su un
  branch `feature/<nome-adr>` dedicato finché non sono verificate end-to-end, poi mergiate — non
  vanno fatte con commit diretti su `main` data l'ampiezza del diff.
- Per ogni nuovo sprint di sicurezza: continuare su `feature/secure-coding-hardening` (non crearne
  uno nuovo per sprint — il branch esistente è lo storico completo referenziato dal report LaTeX).

---

## Branch automatici (Dependabot)

`.github/dependabot.yml` apre branch/PR automatici (`dependabot/gradle/...`, `dependabot/npm_and_yarn/...`,
`dependabot/docker/<servizio>/...`, `dependabot/github_actions/...`) — **non fanno parte della
topologia sopra**, non richiedono governance dedicata, e vengono chiusi/mergiati per PR review
ordinaria. Un'eccezione da tenere presente:

- **Java 21 → 25**: esistono 8 PR Dependabot separate (una per servizio) che bumpano *solo* il
  Dockerfile (`eclipse-temurin:21-jre-alpine` → `25-jre-alpine`), lasciando toolchain Gradle e CI
  fermi a 21 — più 1 PR che bump `gradle-wrapper` a 9.6.1. Stack già pronto per JDK 25 (Spring Boot
  3.5.14 "Java 25 ready", Gradle 9.3.1 supporta il daemon su 25 dalla 9.1), ma un bump *solo*
  dell'immagine runtime, servizio per servizio, senza mai passare da CI a JDK 25, è drift non
  validato in produzione. Decisione (LLM Council, 2026-07-25): **restare su Java 21 ovunque**
  (toolchain + CI + Docker, invariato), chiudere queste 9 PR, aggiungere una regola `ignore` per i
  major bump dell'immagine JDK in `dependabot.yml`. Un eventuale upgrade a 25 va fatto come
  migrazione atomica dedicata (toolchain di tutti gli 8 servizi + CI + tutti gli 8 Dockerfile +
  wrapper insieme, validata con build/test locale su JDK 25 prima del merge) quando c'è un trigger
  di prodotto reale, non perché Dependabot ha aperto una PR.
