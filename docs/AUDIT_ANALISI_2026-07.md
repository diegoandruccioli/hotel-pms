> **STATO: STORICO — piano chiuso.** Sbloccato il 2026-07-27, eseguito integralmente nelle
> Fasi 1-8 tra il 2026-07-28 e il 2026-07-30 (vedi `backup/SUMMARY.md`, voci "piano di
> rifinitura"). Le marcature ✅/🟡 inline sotto riflettono lo stato al momento dello
> sblocco, non lo stato attuale — per lo stato reale di ciascun item fare riferimento a
> `backup/SUMMARY.md` e a `docs/ROADMAP.md`, non a questo documento.

# Analisi hotel-pms — verifica indipendente di 4 analisi esterne

## Context

L'utente ha ricevuto da fonti esterne (Gemini + una seconda analisi infrastrutturale) una serie di
affermazioni su: (1) cosa è estraibile come libreria riusabile, (2) stato del progetto e rispetto
delle regole UX/funzionali, (3) cosa contengono le dashboard dei PMS commerciali, (4) gap
infrastrutturali e frontend. Non si fida e ha chiesto di rifare tutte le analisi da zero.

Rifatte sul codice reale (3 agenti di esplorazione + verifica diretta dei finding più gravi) e con
ricerca web indipendente. **Le analisi esterne sbagliano in modo sistematico**: descrivono le
*intenzioni* dichiarate nei documenti di progetto (CLAUDE.md, ROADMAP, SUMMARY) invece di verificare
il codice, e in due casi citano versioni di dipendenze che il progetto ha già superato.

**Vincolo dichiarato dall'utente**: in questa fase non si aggiungono nuove funzionalità. L'obiettivo
è rendere pronto e perfetto all'uso ciò che è già stato sviluppato. Il piano d'azione in fondo è
organizzato di conseguenza, con una sezione esplicita di ciò che resta fuori scope.

---

## Q1 — Cosa è davvero estraibile

Elencati 6 candidati, di cui 2 sbagliati e 0 verificati. Il verdetto reale:

| Candidato | Analisi esterna | Verificato |
|---|---|---|
| `pdf-template-engine` | "già disaccoppiato" | **CORRETTO** — 158 righe, zero Spring, zero dominio, test PDF/UA reali. 1-2h di lavoro (rename package, `maven-publish`, togliere plugin QA, `api`→`implementation` su Thymeleaf) |
| `frontend/src/components/m3/` | "estrai in pacchetto NPM" | **PARZIALE** — zero dominio hotel (vero), ma 3 componenti fanno `useTranslation` con namespace hardcoded (`M3SegmentedRow.tsx:28` richiede un namespace `settings`), e i token Tailwind vivono in `tailwind.config.js` + `styles/m3-base.css` (154 occorrenze `--md-`). Serve spedire il preset, non la cartella. 1-2 giorni |
| `api-gateway/filter/` | "libreria Spring Boot Starter" | **SOLO 3 SU 4** — `AuthenticationFilter` (315 righe) **non è estraibile**: contiene la policy RBAC del PMS come costanti (`OPERATIONAL_ROLES`, `/api/v1/room-types`, `/api/v1/reports`, `hotelId` obbligatorio con 401). Gli altri 3 sì, con `@ConfigurationProperties` |
| `notification-service` | "notification gateway generico" | **FALSO** — endpoint `/reservation-confirmed`, `/checkin`, `/checkout`; DTO con `roomNumber`/`actualCheckIn`; subject in italiano hardcoded. Riusabili ~45 righe su 835. Rifarlo generico è progettazione nuova (~1 settimana), non estrazione |
| `auth-service` | "distribuiscilo come Identity Service" | **FALSO** — multi-tenant `hotelId`-centrico, con `mustChangePassword` nel claim JWT e RBAC PMS |
| `frontend/src/services/api.ts` | "SDK TypeScript" | **CORRETTO in sostanza** — la coda di refresh single-flight (`:37-52`, `:92-113`) è genuinamente generica; serve una factory `createApiClient({...})` per parametrizzare authStore/i18n/nomi cookie. 3-4h |

### Il finding vero, mancato dall'analisi esterna

Cercando file con nome identico tra moduli: **~2.700 righe duplicate**, in parte byte-identiche.

- **`InternalAuthFilter` — 6 copie.** `diff` normalizzato billing/fb/frontdesk/guest = **vuoto**
  (259 righe identiche di HMAC-SHA256, confronto costant-time, finestra anti-replay).
  `auth-service` (136 righe) e `notification-service` (176) **sono già divergenti**.
  Tre implementazioni diverse dello stesso controllo di sicurezza.
- **`NonceStore` + `RedisNonceStore` — 6 copie**, 5 identiche (320 righe).
- **`FeignHeaderConfig` — 4 copie**, differenze solo nei javadoc, tranne `guest-service` che ha un
  ramo `BatchJobContext` in più (drift).
- **`computeHmac` — 10 copie** della stessa funzione nel repo.
- **`SecurityConfig` — 7 copie**; `fb` vs `guest` differiscono per 3 righe.
  `config-service` **non ha affatto** `InternalAuthFilter` — da verificare a parte.
- **`TenantScopeExempt` + `TenantIsolationArchTest` — 5 copie** (~450 righe per una regola sola).

**Il valore non sta in ciò che puoi portare fuori, ma in ciò che è già stato copiato dentro.**
Una patch a `REPLAY_WINDOW_SECONDS` va oggi applicata a mano in 6 punti, su 3 varianti diverse.

---

## Q2 — Stato progetto e rispetto delle regole

Affermazione esterna: "regole di UX pienamente rispettate", "stato di maturità avanzata, pronto per
scenari d'uso reali". Verifica sul codice: **falso su entrambi i punti**.

### Il bug funzionale più grave (nessun documento lo registra)

**La fattura non contiene mai il prezzo della camera.**

- `billing-service/.../InvoiceServiceImpl.java:83` — `createInvoiceForStay` apre la fattura con
  `.totalAmount(BigDecimal.ZERO)` e non aggiunge alcun addebito.
- `ChargeType.ROOM_NIGHT` esiste (`domain/ChargeType.java:9`), ha un'aliquota IVA assegnata
  (`InvoiceServiceImpl.java:283`), ha un'etichetta PDF (`PdfInvoiceServiceImpl.java:202` → "Camera").
- **Nessun codice di produzione lo emette mai.** Grep su tutto il repo: le uniche occorrenze di
  `ROOM_NIGHT` fuori dagli enum/DTO sono in 3 file di test.
- L'unico chiamante reale di `addCharge` è `fb-service/.../RestaurantOrderServiceImpl.java:162`,
  con `type = FB_ORDER`.
- Il frontend non espone alcun metodo `addCharge` (`services/billingService.ts`).

**Conseguenza**: una fattura di hotel contiene solo i consumi del bar/ristorante.
`RoomType.basePrice` e `ReservationLineItem.price` non arrivano mai in fattura. Il KPI
`pendingRevenue` in dashboard e `total_revenue` in OwnerDashboard misurano solo l'F&B.
Questo è a monte di qualunque metrica di revenue.

### Regole CLAUDE.md violate

| Regola dichiarata | Realtà |
|---|---|
| Coverage ≥95% FE e BE | `frontend/vite.config.ts:26-31` → 90/80/88/92 (**branches 80%**). `build.gradle.kts:26-36` → jacoco `minimum = "0.40"` su INSTRUCTION, cioè **40%** |
| Zero testo hardcoded (i18n) | `ErrorBoundary.tsx:44,47,54` (non importa nemmeno `react-i18next`), `MenuFormModal.tsx:47,108,112` |
| Focus trap + Escape in ogni modale | 3 modali (`MenuFormModal.tsx:67`, `AdminUsers.tsx:63` e reset-password) usano `<dialog open>` invece di `showModal()` → **nessun trap nativo, nessun `aria-modal`**. 4 modali hanno il trap ma **non Escape** (`GuestFormModal.tsx`, `RoomFormModal.tsx`, `RoomTypeFormModal.tsx`, drawer `MainLayout.tsx`) |
| Virtual scrolling liste >50 | **Inesistente** — nessuna libreria in `package.json`, nessuna implementazione manuale. `AdminUsers.tsx:393`, `Housekeeping.tsx:223`, `RoomList.tsx:186` (scarta i metadati di paginazione a `:84`), `PlanningBoard.tsx:288` (griglia camere×giorni annidata) |
| Debounce ≥300ms sulle ricerche | 7 punti a 300ms esatti — **tranne** `WalkInCheckInForm.tsx:88-102`, che chiama `searchGuests()` dentro l'`onChange`: una request per battuta |
| Contrasto 7:1 testo normale | `--md-error #BA1A1A` = **6.29:1**, usato come testo small in 12+ file. `--md-tertiary #2E7D6A` = **4.80:1**. `CalendarPlanning.tsx:216` `text-xs + opacity-70` ≈ 4.8:1 |
| Touch target ≥40×40 | `Reservations.tsx:124` = **24×24**. `Toast.tsx:34` ≈ **16×16**. `CalendarPlanning.tsx:234` = **32×32**. `Housekeeping.tsx:111` ≈ **30px**. `InvoiceDetailModal.tsx:114` è l'unico file del progetto con `min-h-[40px]` |
| vitest-axe su ogni test componente | 51/52 (manca `ErrorBoundary.test.tsx`). Ma **7 sorgenti `.tsx` non hanno alcun test**, tra cui `MainLayout.tsx` (266 righe, contiene drawer e skip-link) e `StayGuestFieldSection.tsx` (**497 righe, il file più grande del progetto**) |
| React.lazy su tutte le pagine | 22/23 — `PlanningBoard.tsx` (344 righe) importato staticamente da `CalendarPlanning.tsx:12` |

Bug laterale trovato: `M3Button.tsx:37` applica `opacity-38` sullo stato disabled, ma
`tailwind.config.js` non estende la scala `opacity` e Tailwind non ha un valore `38` → **la classe
non genera CSS**. Lo stato disabled dei bottoni non è visivamente attenuato.

### Cosa invece è confermato solido

Skip-link su ogni route (via i due layout, nessuna route orfana); zero `dangerouslySetInnerHTML`
(vietato staticamente da `eslint.config.js:27-41`); zero `any` in produzione e zero `@ts-ignore`;
`jsxA11y.flatConfigs.strict`; zero grigi Tailwind di default (solo token M3) + tema high-contrast;
`M3Dialog` è l'implementazione modale corretta e 4 modali la riusano; paginazione server-side reale
su Guests/Reservations/Billing/Stays; prevenzione overbooking con `PESSIMISTIC_WRITE` + `@Version`.

### Il numero dei test, in prospettiva

1354 asserzioni totali (623 `@Test` backend + 659 `it(` frontend + 72 E2E), ma:
- **4 file su 66** nel backend toccano un DB reale (Testcontainers), e sono
  `@Testcontainers(disabledWithoutDocker=true)` → si skippano in silenzio senza Docker.
- **Tutti e 14 gli spec Playwright mockano il backend** con `page.route`.
  È esattamente per questo che BUG-0 (fatturazione 500 a ogni caricamento) è sopravvissuto in `main`
  mentre `billing.spec.ts` esisteva e passava verde.

Il numero è alto; la superficie realmente coperta end-to-end è molto più bassa.

### Stato roadmap reale

BUG-0…BUG-5 tutti chiusi (BUG-5 già fatto: commit `a82ce14`+`6bf60d9`, T-AUTH-06 in THREAT_MODEL).
THREAT_MODEL: **43 threat, 43 ✅, 0 aperti** — ma il modello non copre `notification-service`,
`pdf-template-engine`, né il flusso PDF/Feign che ha prodotto l'OOM di BUG-0b (DoS involontario su
servizio multi-tenant, nessun T-ID corrispondente).
Sprint 2: 6/13 aperti. Sprint 3: **15/17 aperti**. Sprint 4: 7/7 aperti, nessuno con stato.
`IMPLEMENTATION_PLAN.md` F7 (Grafana/Loki) è peggio di "parziale": solo 3 servizi su 8 hanno
l'appender Loki (`auth`, `billing`, `notification`); nessun promtail; Tempo assente; Zipkin ancora
in `docker-compose.yml:194`. F6 ("merge security branch → main, da iniziare") è **stale**: il merge
è già avvenuto (`9eccf59`), manca solo il tag `v1.0.0-secure`.
Privacy Policy / DPA / Cookie Policy: **obbligatorie per legge, nessuna prodotta**.
`DECISIONS.md §4.2` rimanda a `backup/ACCESSIBILITY_FIXES.md` — **il file non esiste**.

---

## Q3 — Cosa c'è nelle dashboard dei PMS commerciali

Ricerca web indipendente (luglio 2026). L'analisi esterna aveva la sostanza giusta ma zero fonti
verificabili, e un errore: presenta la nostra dashboard come già dotata di "KPI reali". Non li ha.

**I 5 blocchi ricorrenti** in Mews, Cloudbeds, Opera Cloud, Stayntouch, RoomRaccoon:

1. **Operatività del giorno** — arrivi/partenze con sotto-stato (attesi, completati, in ritardo,
   VIP), stay-over, azioni rapide (walk-in, nuova prenotazione), anteprima tape chart drag-and-drop.
2. **Housekeeping** — matrice stato camere (clean/dirty/inspected/OOO) con priorità "arrivo
   imminente", assegnazione task allo staff.
3. **Revenue/KPI** — Occupancy %, **ADR**, **RevPAR**, ALOS, booking pace vs anno precedente,
   breakdown incassi per metodo di pagamento.
4. **Distribuzione/channel** — stato sync OTA, quota vendite per canale, alert parità tariffaria.
5. **Task & guest** — to-do per turno, richieste speciali, check-in online completati.

**Trend confermati 2026**: dashboard a tile componibili con drag-and-drop (Opera Cloud: 30+ tile
preconfigurate, permessi *per ruolo sul singolo tile*, drill-down senza cambiare pagina); dashboard
role-based (reception vs owner vs governante su tablet); agentic AI che esegue task multi-step;
upselling AI context-aware. Mews ha raccolto $300M a gennaio 2026 per l'automazione.

**Mercato italiano** (Lodge Easy, Dylog, AmicHotel, OVVO): oltre al set internazionale, la dashboard
italiana espone sempre **Alloggiati Web, ISTAT/ROSS1000 e tassa di soggiorno** come blocchi di primo
livello.

### Confronto con la nostra dashboard

`Dashboard.tsx` (235 righe) mostra oggi: 4 card KPI (`guestsInHouse`, `todayArrivals`,
`todayDepartures`, `availableRooms`) + 1 card `pendingRevenue` per OWNER/ADMIN + banner fallimenti
Alloggiati + una room grid colorata per stato. **Zero grafici** — nessuna libreria di charting in
`package.json` (recharts/chart.js/d3/nivo tutte assenti); l'unica libreria visuale è
`react-big-calendar`.

`dashboardService.ts` (82 righe) calcola tutto **client-side**, senza endpoint backend dedicato:
5 chiamate a ogni mount, tra cui `getAllStays(0, 500)` (page size hardcoded) e
`getOwnerFinancialReport('2000-01-01','2099-12-31')` — scarica *tutte le fatture di sempre* per
sommare quelle `ISSUED`. Il `catch` è silenzioso: un errore backend è indistinguibile da
"zero pendenze" (`:60-70`).

Gap rispetto al mercato: **RevPAR, ADR e Occupancy sono già chiavi i18n in
`locales/{en,it}/common.json:35-37` — e sono orfane, mai usate in alcun `.tsx`.** Assenti anche
channel manager, rate plan, dynamic pricing, group booking, allotment, deposit/caparra,
cancellation policy, tassa di soggiorno, no-show operabile da UI (l'enum backend esiste, zero
occorrenze in `frontend/src`).

---

## Q4 — Verifica della seconda analisi (infrastruttura e frontend)

Cinque affermazioni, verificate una per una. **Due sono false su dati di fatto, una ha la premessa
sbagliata, due sono vere.**

### 4.1 «Grafana 11.5.0, isolare dietro VPN; aggiornare il gateway quando Spring Cloud supporterà le versioni corrette di Netty» — **FALSO su entrambi i punti**

- **Versione Grafana sbagliata**: `docker-compose.yml:164` → `image: grafana/grafana:12.4.6`.
  Non 11.5.0.
- **Netty è già stato forzato**, e proprio con la tecnica che l'analisi suggerisce di attendere:
  `api-gateway/build.gradle.kts` fa `set("netty.version", "4.1.136.Final")` con **override esplicito
  del BOM di Spring Boot**, e il commento sopra elenca le CVE coperte
  (`CVE-2026-56745/55833/55831/59901` e precedenti). Stesso trattamento per Tomcat
  (`set("tomcat.version", "10.1.55")`). Il consiglio di "aspettare Spring Cloud" descrive
  esattamente ciò che il progetto ha deciso di **non** fare, correttamente.
- **Esposizione Grafana**: già mitigata a livello di porte. `grafana` sta dietro
  `profiles: ["observability"]` e `docker-compose.prod.yml` gli applica `ports: !reset []` — in prod
  non è pubblicato sull'host. Resta vero in senso generale che l'accesso va mediato (reverse proxy
  autenticato o rete privata), ma non è "esposto" oggi.

**Verità residua utile**: nessuna, oltre al promemoria generico sull'accesso amministrativo.

### 4.2 «Manca pg_dump automatizzato via cron, esportazione crittografata su object storage, PITR» — **PREMESSA FALSA, CONCLUSIONE VERA**

- **Il backup automatico esiste**: `docker-compose.yml:60-96`, servizio `db-backup` con `pg_dumpall`
  + `gzip` + retention 14 giorni + `BACKUP_INTERVAL_SECONDS: 86400`, nessuna porta host, limiti di
  risorse. Quindi "manca pg_dump automatizzato" è falso.
- **Ma i tre limiti indicati sono reali e già noti** (ROADMAP P3 li registra come gap residuo del
  proprio item ✅):
  - scrive sul volume Docker `postgres_backups` **sullo stesso host del database** → un guasto
    dell'host perde dump e dati insieme;
  - **nessuna crittografia** del dump a riposo;
  - `pg_dumpall` è un dump **logico**: dà recovery al momento dell'ultimo dump (RPO fino a 24h),
    **non** PITR — quello richiede WAL archiving (`archive_command` / `pg_basebackup` /
    pgBackRest), che non è configurato.

**Verità residua utile**: alta. È l'unico punto delle cinque analisi che aggiunge lavoro sensato
sull'esistente. Rientra nel perimetro "rendere pronto all'uso": non aggiunge funzionalità utente,
rende affidabile ciò che c'è.

### 4.3 «Infrastruttura vincolata a singolo nodo Docker Compose; servire manifesti Kubernetes/Helm» — **VERO ma fuori scope**

Confermato: nessuna directory `k8s`/`helm`/`charts`, nessun `Chart.yaml`, nessun manifest nel repo.
È già tracciato come ROADMAP E5 / ADR-003 (🟡 Alta, aperto). Vero anche che i servizi sono stateless
per design, quindi mapparli è realistico.
**Ma è nuova infrastruttura, settimane di lavoro, e non rende "più pronto all'uso" ciò che esiste.**
Fuori scope in questa fase.

### 4.4 «Zustand deve separare le slice (reservationSlice, billingSlice) per evitare re-render massivi della dashboard frontdesk» — **PREMESSA SBAGLIATA**

Non esiste uno store monolitico da splittare. `frontend/src/store/` contiene **5 store già separati
e piccoli**: `authStore.ts` (39 righe), `dashboardStore.ts` (29), `settingsStore.ts` (75),
`themeStore.ts` (45), `toastStore.ts` (31).

`reservationSlice`/`billingSlice` non esistono **perché lo stato di prenotazioni e fatturazione non
è in Zustand affatto** — vive in `useState` locale dentro le pagine. Quindi lo scenario descritto
("aggiorni lo stato di una camera → si ri-renderizza tutta la dashboard") non può verificarsi:
`Dashboard.tsx:45` legge solo `useDashboardStore`, che contiene esclusivamente `stats`/`isLoading`/
`error`/`fetchStats`.

**Verità residua utile**: piccola e diversa da quella affermata. `Dashboard.tsx:45` destruttura
l'intero store (`useDashboardStore()`) invece di usare selettori — quindi il componente si
ri-renderizza a ogni cambio di *qualsiasi* campo dello store. È una micro-inefficienza da una riga,
non un problema architetturale. Il vero costo della dashboard è altrove: le 5 chiamate di rete a
ogni mount senza cache (vedi Q3).

### 4.5 «Manca il service worker per una PWA offline-first per governanti» — **VERO, e fuori scope, con un pezzo però dentro scope**

Confermato: `frontend/package.json` non contiene `vite-plugin-pwa` né `workbox`; `vite.config.ts`
non registra alcun service worker; `frontend/public/` contiene solo `vite.svg` — nessun
`manifest.webmanifest`, nessuna icona PWA. Già tracciato come ROADMAP C5 (🟡 Alta, aperto).
Il "70% dell'infrastruttura è pronta" è un'affermazione non verificabile e comunque ottimistica:
offline-first richiede anche una strategia di sync dei conflitti sullo stato camera, che non esiste.

**Il pezzo che invece rientra in "rendere pronto all'uso"**: `Housekeeping.tsx` esiste già ed è la
pagina che una governante userebbe da smartphone — ma oggi i suoi controlli sono sotto la soglia di
touch target (`Housekeeping.tsx:111` ≈ 30px contro i 40 richiesti) e la lista camere non è
paginata né virtualizzata (`:223`). Prima di parlare di PWA, quella pagina va resa usabile su
mobile con quello che già ha.

---

## Piano d'azione — rifinitura dell'esistente

Ordinato per rapporto valore/rischio. **Nessun item introduce funzionalità nuove**: ogni voce
completa, corregge o rende affidabile qualcosa che è già nel codice.

### Blocco 0 — bug funzionale bloccante
1. ✅ **FATTO (2026-07-27, commit `27fe70a`)** — ~~Addebito ROOM_NIGHT in fattura.~~ Risolto come
   parte della chiusura di `docs/LIVE_E2E_AUDIT_2026-07.md` §8.5. Causa radice reale trovata: non
   solo l'addebito camera mancava (`BillingClient` di frontdesk-service non aveva un metodo
   `addCharge`, ora aggiunto), ma la creazione stessa della fattura falliva per ogni stay walk-in
   (`StayInvoiceRequest.reservationId` era `@NotNull`, colonna DB `NOT NULL`, ma un walk-in ha
   legittimamente `reservationId=null` — Flyway V10 in billing-service rende la colonna nullable).
   `openInvoiceForStay` ora crea l'invoice e allega un charge `ROOM_NIGHT` (basePrice × notti),
   retry idempotente. Verificato dal vivo via Playwright contro Docker reale, non solo Mockito.

### Blocco 1 — debito di sicurezza (branch `feature/secure-coding-hardening`)
2. **Libreria `internal-auth-lib`**: `InternalAuthFilter` + `NonceStore` + `RedisNonceStore` +
   `FeignHeaderConfig` + `SecurityConfig` base. ~2.100 righe duplicate → ~450.
   Riconciliare prima le 3 varianti divergenti (auth / notification / gli altri 4), decidendo
   quale semantica è quella giusta. Verificare perché `config-service` non ha il filtro.
   Segue il flusso `security-followup` (THREAT_MODEL + LaTeX).
3. **Estendere THREAT_MODEL** a `notification-service` e al flusso PDF/Feign (l'OOM di BUG-0b è un
   DoS involontario su servizio multi-tenant senza alcun T-ID corrispondente).

### Blocco 2 — affidabilità dei dati (dal punto 4.2, l'unico valido dell'analisi esterna)
4. **Backup off-site + crittografato**: aggiungere al servizio `db-backup` esistente
   (`docker-compose.yml:60`) la cifratura del dump (`gpg --symmetric` o `age`) e l'upload verso
   object storage esterno (MinIO self-hosted o S3). Non serve un servizio nuovo — si estende lo
   script già presente.
5. **Decidere esplicitamente su PITR**: o si configura WAL archiving, o si documenta in
   `DECISIONS.md` che l'RPO accettato è 24h. Oggi il gap non è scritto da nessuna parte se non come
   nota a margine di ROADMAP P3.
6. **Provare un restore.** Un backup mai ripristinato non è un backup. Va fatto almeno una volta,
   end-to-end, e documentata la procedura.

### Blocco 3 — conformità alle regole già dichiarate
7. 🟡 **PARZIALE (verificato 2026-07-27)** — Escape handler sui 4 modali che hanno solo il trap;
   migrare i 3 `<dialog open>` a `M3Dialog`. **Fatto**: `GuestFormModal.tsx`, `RoomFormModal.tsx`,
   `RoomTypeFormModal.tsx` già migrati a `M3Dialog` (Escape + focus trap nativi, sessione BUG-9 /
   audit E2E). **Ancora da fare**: `MenuFormModal.tsx` e `AdminUsers.tsx` (2 dialoghi) usano ancora
   `<dialog open>` raw — nessun `aria-modal`/`showModal()`/focus-trap (`MenuFormModal` ha un handler
   Escape manuale ma non il trap); drawer di `MainLayout.tsx` ancora senza handler Escape.
8. i18n su `ErrorBoundary.tsx` e `MenuFormModal.tsx`.
9. Debounce su `WalkInCheckInForm.tsx:88`.
10. Touch target ≥40px: `Reservations.tsx:124`, `Toast.tsx:34`, `CalendarPlanning.tsx:234`,
    `AdminUsers.tsx:279/285`, **`Housekeeping.tsx:111`** (pagina da usare su smartphone),
    i link "vedi tutto" della dashboard.
11. Contrasto: scurire `--md-error` e `--md-tertiary` in `m3-base.css` fino a 7:1
    (già corretti in dark mode e high-contrast — replicare quei valori).
12. Fix `opacity-38` in `M3Button.tsx:37` (classe inesistente, disabled non attenuato).
13. Test mancanti sui 7 sorgenti `.tsx` scoperti, a partire da `MainLayout.tsx` (contiene skip-link
    e drawer, cioè due meccanismi di accessibilità non coperti) e `StayGuestFieldSection.tsx`.
14. Selettori Zustand in `Dashboard.tsx:45` (dal punto 4.4) — una riga.
15. **Allineare le soglie di coverage**: portarle al 95% dichiarato **oppure** correggere CLAUDE.md
    e `DECISIONS.md §4.1` alla soglia reale. Oggi i tre documenti si contraddicono.

### Blocco 4 — igiene della documentazione
16. `IMPLEMENTATION_PLAN.md` F6 è stale (merge già fatto in `9eccf59`) — chiudere o creare il tag
    `v1.0.0-secure`. F7 va riclassificato: 3 servizi su 8 con appender Loki non è "parziale", è
    "iniziato".
17. `DECISIONS.md §4.2` rimanda a `backup/ACCESSIBILITY_FIXES.md` che non esiste — creare o
    rimuovere il rimando.
18. Risolvere la contraddizione `DECISIONS.md §2.3` ("non integrare Stripe") vs ROADMAP E2
    ("Booking Engine + Stripe Checkout").
19. Privacy Policy / DPA / Cookie Policy — obbligatorie per legge, nessuna prodotta.
20. Completare la copertura E2E dichiarata incompleta in `LIVE_E2E_AUDIT_2026-07.md:79`
    (PDF/FatturaPA, checkout finale, walk-in, planning board, switch lingua) **contro backend
    reale**, non mockato.

### Esplicitamente FUORI SCOPE in questa fase
Sono tutte cose vere e legittime, ma aggiungono funzionalità invece di rifinire l'esistente:

- Kubernetes / Helm (punto 4.3, ROADMAP E5) — settimane, nessun beneficio sull'usabilità attuale.
- PWA / service worker offline-first (punto 4.5, ROADMAP C5) — richiede anche una strategia di sync
  dei conflitti che non esiste. Prima va resa usabile `Housekeeping.tsx` su mobile (blocco 3.10).
- KPI Occupancy/ADR/RevPAR (ROADMAP C4) — dipendono comunque dal blocco 0.
- Nuovi widget dashboard, grafici, tile componibili (Q3) — richiedono la prima libreria di charting.
- Channel manager (E1), booking engine (E2), tassa di soggiorno, rate plan, no-show da UI.
- Estrazione delle librerie riusabili di Q1 (tranne la deduplicazione di sicurezza del blocco 1,
  che è debito su codice esistente, non riuso).

---

## Verifica

- **Blocco 0**: test di integrazione Testcontainers su `createInvoiceForStay` + check-out completo;
  poi verifica in browser reale sullo stack Docker (check-in → consumo bar → check-out → PDF),
  controllando che il PDF mostri la riga "Camera" e che il totale includa il pernottamento.
- **Blocco 1**: `./gradlew clean build` (PMD/Checkstyle/SpotBugs zero warning) + i test esistenti
  degli `InternalAuthFilter` di ogni servizio devono passare invariati contro la libreria condivisa.
- **Blocco 2**: prova di restore end-to-end su un database vuoto, partendo da un dump cifrato
  scaricato dall'object storage. È l'unica verifica che conta.
- **Blocco 3**: `npm run lint` + `npm run test` + gli assert `vitest-axe`; navigazione TAB-only
  manuale sui modali toccati; contrasto misurato sui token modificati; `Housekeeping.tsx` provata
  su viewport mobile reale.
- **Blocco 4**: `npm run test:e2e` **contro backend reale**, non mockato — è la lacuna che ha
  lasciato passare BUG-0.

## Fonti (Q3)

- [Cloudbeds vs Mews — Hotel Tech Report 2026](https://hoteltechreport.com/compare/cloudbeds-myfrontdesk-vs-mews)
- [Opera Cloud — Home Dashboard (Oracle docs)](https://docs.oracle.com/en/industries/hospitality/opera-cloud/26.2/ocsuh/c_home_dashboard.htm)
- [Opera Cloud — Managing Dashboard Tiles](https://docs.oracle.com/en/industries/hospitality/opera-cloud/23.4/ocsuh/t_managing_dashboard_tiles.htm)
- [Mews — Occupancy dashboard in Business Intelligence](https://help.mews.com/s/article/The-Occupancy-dashboard-in-Mews-Business-Intelligence?language=en_US)
- [Mews launches Business Intelligence for hotels](https://www.prnewswire.com/news-releases/smarter-decisions-faster-mews-launches-business-intelligence-for-hotels-302743342.html)
- [Cloudbeds — Get to know your new Dashboard](https://myfrontdesk.cloudbeds.com/hc/en-us/articles/16873372577435-Get-to-know-your-new-Dashboard)
- [Cloudbeds — Housekeeping](https://myfrontdesk.cloudbeds.com/hc/en-us/articles/25695101078427-Housekeeping-Everything-you-need-to-know)
- [The future of hotel technology: 30 key trends for 2026](https://www.hospitalitynet.org/opinion/4131633/the-future-of-hotel-technology-30-key-trends-for-2026)
- [11 Best Hotel PMS Platforms 2026](https://stayfi.com/vrm-insider/2026/03/03/best-hotel-property-management-software/)
- [Portale Alloggiati, ISTAT e tassa di soggiorno](https://ospitalita40.it/marketing/portale-alloggiati-istat-e-tassa-di-soggiorno-perche-automatizzare-sembra-difficile/)
- [Software Gestionale Hotel Completo 2026 — Lodge Easy](https://lodgeasy.it/guides/software-gestionale-hotel)
