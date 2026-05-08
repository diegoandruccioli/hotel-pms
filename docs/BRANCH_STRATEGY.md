# Branch Strategy — Hotel PMS

**Data:** 2026-05-08  
**Stato:** Finale post-integrazione

---

## Topologia dei branch

```
pre-secure-coding (99e67c3) ──────────────────────────────── [congelato]
                   \
                    \── feature/secure-coding-hardening ──── [storico hardening]
                    \          (25 commit security)         /
                     \                                     /
feature/frontend-development (172 commit feature) ──[merge]
                                                           |
                                                         main (stato finale)
```

---

## Ruolo di ogni branch

| Branch | Scopo | Stato |
|---|---|---|
| `pre-secure-coding` | **Snapshot della baseline insicura** — punto di partenza "prima delle modifiche di sicurezza". Corrisponde a `main` prima dell'hardening. Usato dalla commissione esame come riferimento del codice originale. | Congelato — non modificare |
| `feature/secure-coding-hardening` | **Storico hardening** — contiene tutti i commit di sicurezza con messaggi descrittivi, SHA referenziati nel report LaTeX (`docs/security-report/report-secure-coding.tex`). Include: account lockout, BCrypt cost=12, token versioning, GDPR retention (T-GST-05), audit logging, HMAC fixes. | Congelato — non eliminare mai |
| `feature/frontend-development` | **Branch di sviluppo** — contiene lo sviluppo applicativo completo. Ora include anche i commit di sicurezza (via merge commit `420a89c`). | Può ricevere sviluppi futuri |
| `main` | **Stato finale integrato** — merge di entrambi i track in `ac7685e`. Build verde, tutti i test passano. | Branch di riferimento per produzione |

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

---

## Regole di governance

- `pre-secure-coding` e `feature/secure-coding-hardening` non vanno mai eliminati.
  Sono le evidenze storiche per l'esame di Secure Coding.
- Il report LaTeX (`docs/security-report/report-secure-coding.tex`) cita commit SHA
  del branch `feature/secure-coding-hardening`. Quei commit devono rimanere accessibili.
- Sviluppi futuri partono da `main` o da `feature/frontend-development`.
- Per ogni nuovo sprint di sicurezza: creare un branch `feature/security-sprint-N`
  con lo stesso pattern (commit atomici + aggiornamento THREAT_MODEL.md).
