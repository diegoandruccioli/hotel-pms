# dast.md — OWASP ZAP baseline scan (dinamico, stack live)

Sola analisi contro l'app realmente in esecuzione, nessun codice toccato durante la scan.

**Setup**: stack Docker già in piedi (`docker compose up -d`, profilo base, healthy da
prima di questa fase); container `frontend` (immagine `nginx-unprivileged` bumpata oggi,
GAP-14) avviato per l'occasione (`docker compose up -d --no-deps frontend`). ZAP
(`ghcr.io/zaproxy/zaproxy:stable`, `zap-baseline.py`, passive scan + spider, nessun
attacco attivo) lanciato due volte, in parallelo, contro:
- `http://host.docker.internal:80` — frontend nginx (superficie browser reale)
- `http://host.docker.internal:8080` — api-gateway (superficie API diretta)

Report completi: `security-audit/.zap-work/{frontend,gateway}-zap.{json,html}` (rimossi
dopo questo riassunto, dati non persi — questo file ne è la sintesi completa).

---

## Frontend (`:80`) — 6 alert reali + 1 informational

### MEDIUM — CSP: `style-src 'unsafe-inline'`

`Content-Security-Policy` (evidenza catturata dallo scan):
`style-src 'self' 'unsafe-inline'`. Verificato nel codice
(`frontend/nginx.conf`): è **deliberato**, commentato esplicitamente
("TailwindCSS injects utility classes at runtime; inline styles are required").
Non un errore di configurazione — un trade-off già preso in una sessione precedente
(T-FE-04). Impatto reale attenuato: `script-src` resta `'self'` stretto (nessun
`unsafe-inline`/`unsafe-eval`), quindi l'`unsafe-inline` su `style-src` da solo non abilita
XSS via `<script>` — resta un vettore residuo più stretto (CSS-based data exfiltration via
selettori attributo, tecnica nota ma che richiede comunque un altro punto di iniezione
HTML per posizionare il CSS malevolo, che l'audit XSS di questa sessione non ha trovato).
**Non un nuovo problema, ma una remediation concreta esiste** se si vuole chiuderlo:
CSP nonce-based per lo stile invece del wildcard `unsafe-inline` — richiede supporto Tailwind
per stili nonce'd o migrazione a CSS-in-JS con nonce, sforzo non banale, non fatto qui.

### LOW — 3× header di isolamento cross-origin mancanti

`Cross-Origin-Embedder-Policy`, `Cross-Origin-Opener-Policy`,
`Cross-Origin-Resource-Policy` — nessuno dei tre è impostato in `frontend/nginx.conf`
(verificato: grep su "Cross-Origin" nel file, zero match). Sono header più recenti degli
altri già presenti (CSP/X-Frame-Options/HSTS/ecc., verificati corretti nell'audit
misconfig di questa sessione) — genuinamente non ancora aggiunti, non un falso positivo.
Impatto pratico basso per questa app (nessun contenuto cross-origin embeddabile sensibile,
nessun uso di `SharedArrayBuffer`/API che richiedono COEP), ma economico da aggiungere:
```
add_header Cross-Origin-Opener-Policy "same-origin" always;
add_header Cross-Origin-Embedder-Policy "require-corp" always;
add_header Cross-Origin-Resource-Policy "same-origin" always;
```

### LOW — Version leak: header `Server: nginx/1.31.3`

Reale, verificato: `frontend/nginx.conf` non imposta `server_tokens off;`. Fix banale,
una riga nel `server {}` block. Riduce ricognizione automatica di versione per un
attaccante, non blocca nulla di per sé.

### Informational (nessuna azione)

"Modern Web Application" (rilevamento SPA, solo un suggerimento di usare lo spider
client-side ZAP invece di quello standard) e "Storable but Non-Cacheable Content"
(coerente con `Cache-Control: no-cache` su `index.html`, comportamento voluto — vedi
commento in `nginx.conf` sulla cache-busting strategy).

---

## Gateway (`:8080`) — nessun alert reale

Solo 1 informational ("Storable and Cacheable Content", su risposte 401 di endpoint
protetti — comportamento neutro, nessuna azione). Nessun endpoint applicativo è stato
raggiungibile senza autenticazione durante lo spider passivo (coerente con l'audit
access-control: ogni route business richiede il cookie `jwt`), quindi la superficie
scansionabile senza credenziali è minima — lo scan **non sostituisce** una verifica
autenticata mirata (fuori scope per un baseline passivo).

---

## Tabella riepilogo

| Alert | Target | Severità (ZAP) | Verificato reale? | Remediation |
|---|---|---|---|---|
| CSP `style-src unsafe-inline` | frontend | Medium | Sì, trade-off deliberato già documentato (T-FE-04) | CSP nonce-based, sforzo non banale |
| COEP header mancante | frontend | Low | Sì, genuinamente assente | 1 riga `add_header` |
| COOP header mancante | frontend | Low | Sì, genuinamente assente | 1 riga `add_header` |
| CORP header mancante | frontend | Low | Sì, genuinamente assente | 1 riga `add_header` |
| `Server` version leak | frontend | Low | Sì, genuinamente assente `server_tokens off` | 1 riga in `nginx.conf` |
| — | gateway | — | Nessun finding reale | — |

Nessun alert High/Critical su nessuno dei due target. Il baseline passivo conferma quanto
già emerso dagli audit statici di questa sessione (CSP/security header sostanzialmente
solidi, gap solo su header di isolamento cross-origin di seconda generazione e version
disclosure minore) — nessuna sorpresa rispetto a `misconfig.md`, solo conferma indipendente
dal vivo.
