# 🏨 Hotel PMS — Report Completo | Parte 2

## 5. Feature Gap — Analisi Dettagliata

Queste sono le feature dove **tutti i competitor ti battono**. Per ognuna spiego cos'è, perché conta nel mondo reale, come funziona nei competitor, e quanto ti costerebbe implementarla.

---

### 5.1 🔴 Channel Manager (OTA Sync)

**Cos'è:** Un sistema che sincronizza in tempo reale disponibilità, tariffe e restrizioni del tuo hotel con le Online Travel Agencies (Booking.com, Expedia, Airbnb, Hotels.com, Agoda). Quando un ospite prenota su Booking.com, il channel manager aggiorna immediatamente la disponibilità su tutti gli altri canali e nel PMS.

**Perché è il gap #1:**
- Il 65-75% delle prenotazioni di un hotel indipendente arriva dalle OTA
- Senza channel manager, il receptionist deve aggiornare manualmente Booking.com, Expedia, il sito web. Se dimentica → **overbooking**, che costa all'hotel la camera + compensazione + recensione negativa
- È la prima feature che un albergatore chiede quando valuta un PMS. Senza di essa, il tuo PMS non è nemmeno considerato nella shortlist

**Come funziona nei competitor:**
- **Cloudbeds**: channel manager nativo, connesso a 400+ canali, sync ogni 30 secondi, rate parity automatica
- **Mews**: connesso a 600+ OTA, XML/API bidirezionale, gestisce anche restrizioni (min stay, closed to arrival)
- **Little Hotelier**: channel manager SiteMinder integrato (stessa azienda), 450+ canali

**Complessità tecnica:**
- Booking.com usa API XML v2 (proprietaria, richiede certificazione partner)
- Expedia usa EPS (Expedia Partner Solutions) REST API
- Airbnb usa API REST con OAuth2
- Ogni OTA ha un processo di certificazione che richiede 2-6 mesi
- Alternativa: integrarsi con un channel manager di terze parti (SiteMinder, RateTiger, ChannelRUSH) via API, riducendo lo sforzo a 1-2 mesi

**Effort:** 3-6 mesi (integrazione diretta OTA) oppure 1-2 mesi (integrazione con channel manager terzo)

**Impatto commerciale:** Senza questa feature, puoi vendere solo a strutture che non usano OTA (agriturismi rurali, affittacamere senza presenza online). Il mercato si riduce del 80%.

---

### 5.2 🔴 Booking Engine

**Cos'è:** Un widget embeddabile nel sito web dell'hotel che permette ai clienti di prenotare direttamente senza passare per le OTA. L'hotel risparmia il 15-25% di commissione su ogni prenotazione diretta.

**Perché conta:**
- Una prenotazione su Booking.com costa all'hotel il 15-18% di commissione
- Su Expedia il 18-25%
- Una prenotazione diretta dal sito web costa 0% (o il solo costo del payment gateway ~2%)
- Per un hotel con 30 camere e ADR €100, le prenotazioni dirette possono far risparmiare €15.000-30.000/anno
- Google Hotels mostra il "prezzo diretto" dell'hotel nel comparatore se hai un booking engine

**Come funziona nei competitor:**
- **Cloudbeds**: booking engine responsive, multilingua, multi-valuta, upsell (late check-out, colazione), integrato con Google Hotel Ads
- **Mews**: booking engine in 30+ lingue, pre-pagamento con Stripe/Adyen, personalizzabile con brand dell'hotel
- **Little Hotelier**: booking engine incluso nel piano base, drag-and-drop per il sito

**Cosa serve nel tuo PMS:**
1. Nuovo microservizio `booking-service` (o estensione di `reservation-service`) con endpoint pubblici (no JWT)
2. Widget JavaScript embeddabile (`<script>` tag per il sito dell'hotel)
3. Calendario disponibilità in tempo reale (query a `inventory-service`)
4. Form prenotazione: date, n° ospiti, selezione camera, dati ospite
5. Integrazione payment gateway (Stripe Checkout) per pre-pagamento
6. Email conferma prenotazione al guest
7. Admin panel per personalizzare colori/logo del widget

**Effort:** 1-2 mesi

---

### 5.3 🟡 Revenue Management / Dynamic Pricing

**Cos'è:** Un sistema che adegua automaticamente le tariffe delle camere in base a domanda, stagionalità, occupancy, eventi nella zona, e prezzi dei competitor. L'obiettivo è massimizzare il RevPAR (Revenue Per Available Room).

**Perché conta:**
- Un hotel che usa tariffe fisse lascia soldi sul tavolo durante alta stagione (potrebbe vendere a €150 invece di €100) e non riempie le camere in bassa stagione (potrebbe vendere a €60 invece di €100)
- I revenue manager stimano che il dynamic pricing aumenti il RevPAR del 10-20%
- Per un hotel con 40 camere, questo significa €20.000-60.000/anno di revenue aggiuntiva

**Come funziona nei competitor:**
- **IDeaS** e **Duetto** (leader di mercato), suggerimenti tariffari automatici
- **Cloudbeds**: "Pricing Intelligence Engine" integrato, confronto automatico con competitor (via API OTA)
- **RoomRaccoon**: yield management integrato con regole automatiche (occupancy > 80% → +15%)

**Cosa serve:**
1. Servizio di pricing con regole configurabili (occupancy-based, date-based, min/max)
2. Dashboard per il proprietario con suggerimenti tariffari
3. Report KPI: RevPAR, ADR, occupancy rate, pace report (prenotazioni in anticipo vs. anno precedente)
4. Opzionale: integrazione con API di scraping prezzi competitor (SiteMinder Insights, RateGain)

**Effort:** 2-3 mesi (regole base) + 2-3 mesi (AI/ML pricing avanzato)

---

### 5.4 🟡 Email/SMS Conferme Automatiche

**Cos'è:** Invio automatico di email/SMS al momento della prenotazione, del check-in, del check-out, e per promemoria pre-arrivo.

**Perché conta:**
- Il guest si aspetta una conferma istantanea. Senza email, non ha prova della prenotazione
- Le email pre-arrivo (48h prima) riducono i no-show del 10-15%
- Le email post-stay con link a TripAdvisor aumentano le recensioni positive
- È lo standard minimo assoluto — anche un B&B con 3 camere manda la conferma email

**Come funziona nei competitor:** Tutti i competitor inviano email automatiche con template personalizzabili (logo hotel, colori, testo custom), multilingua, con allegato PDF della prenotazione.

**Cosa serve:**
1. `notification-service` (nuovo microservizio) con coda messaggi (Spring AMQP / Redis pub/sub)
2. Template email HTML responsive (Thymeleaf o MJML)
3. Integrazione SMTP (SendGrid, Amazon SES, o Mailgun — costo ~€10/mese per 10.000 email)
4. Template prenotazione, check-in, check-out, promemoria, cambio password
5. Admin panel per personalizzare il template con logo/colori dell'hotel
6. Opzionale: SMS via Twilio/Vonage per conferme critiche

**Effort:** 1-2 settimane

---

### 5.5 🟡 Export PDF Fatture

**Cos'è:** Generazione di fatture PDF professionali con layout formale (intestazione hotel, dati fiscali, dettaglio addebiti, totale, metodo di pagamento).

**Perché conta:**
- Obbligo fiscale italiano: la fattura deve avere formato specifico
- Gli hotel stampano fatture per i clienti business che le scaricano per la nota spese
- Senza PDF, il receptionist stampa uno screenshot — non professionale

**Come funziona nei competitor:** Tutti generano PDF scaricabili con personalizzazione logo/template.

**Cosa serve:**
1. Libreria Java per PDF (JasperReports, iText, o Apache PDFBox)
2. Template fattura con: intestazione hotel (da HotelSettings), dati ospite, dettaglio charges, totali, metodo pagamento
3. Endpoint `GET /api/v1/invoices/{id}/pdf` → `Content-Type: application/pdf`
4. Pulsante "Scarica PDF" in `InvoiceDetailModal`

**Effort:** 1 settimana

---

### 5.6 🟡 Mobile App / PWA

**Cos'è:** Accesso al PMS da smartphone/tablet per il personale dell'hotel, ottimizzato per uso in mobilità (lobby, corridoi, aree comuni).

**Perché conta:**
- Il receptionist in un hotel moderno non sta solo dietro al bancone — accoglie gli ospiti in lobby con un tablet
- L'housekeeping aggiorna lo stato delle camere dal corridoio con il telefono
- Il proprietario controlla i KPI dal telefono mentre è fuori sede

**Come funziona nei competitor:**
- **Mews**: app mobile nativa (iOS/Android) per gestione camere, check-in mobile, housekeeping
- **Cloudbeds**: app mobile per gestione prenotazioni e revenue
- **Opera**: mobile companion app per housekeeping e front desk

**Cosa serve per il tuo PMS (approccio PWA, non app nativa):**
- Responsive mobile-first (vedi analisi sotto — 2-4 settimane)
- `manifest.json` per "Add to Home Screen"
- Service worker for offline básico (cache delle pagine principali)
- Non serve app nativa: una PWA costa 1/10 e copre il 90% dei casi d'uso hotel

**Effort:** 2-4 settimane (come analizzato — il 70% è già fatto)

---

### 5.7 🟡 Online Check-in Guest (Self-Service)

**Cos'è:** Un link inviato via email al guest 48h prima dell'arrivo che permette di compilare i dati personali, caricare il documento d'identità, e firmare il consenso privacy — tutto prima di arrivare in hotel.

**Perché conta:**
- Riduce il tempo di check-in alla reception da 5-10 min a 30 secondi
- Il receptionist non deve più digitare manualmente i dati Alloggiati
- Migliora la guest experience (nessuna coda alla reception dopo un volo)
- Post-COVID, è diventato lo standard atteso dai viaggiatori business

**Come funziona nei competitor:**
- **Mews**: online check-in completo con upload documento, firma digitale, pagamento, digital key
- **Cloudbeds**: pre-registration via email con compilazione dati e consensi

**Cosa serve:**
1. Pagina web pubblica (no login) con token univoco per prenotazione
2. Form: dati personali, tipo/numero documento, upload foto documento, firma consenso GDPR
3. I dati compilati popolano automaticamente `StayGuest` al check-in
4. Email trigger 48h prima del check-in (richiede `notification-service` — feature 5.4)

**Effort:** 1-2 mesi

---

### 5.8 🟡 Report KPI (RevPAR, ADR, Occupancy)

**Cos'è:** Dashboard con metriche standard dell'industria alberghiera per il proprietario.

**KPI standard:**
- **RevPAR** (Revenue Per Available Room) = Fatturato totale ÷ Camere disponibili × Giorni
- **ADR** (Average Daily Rate) = Fatturato camere ÷ Notti vendute
- **Occupancy Rate** = Notti vendute ÷ Notti disponibili × 100
- **GOPPAR** (Gross Operating Profit Per Available Room)
- **Pace Report**: prenotazioni accumulate vs. stesso periodo anno precedente

**Perché conta:** Il proprietario non può gestire ciò che non può misurare. Senza KPI, non sa se l'hotel va bene o male rispetto al mercato.

**Cosa serve:** Estensione di `billing-service` con aggregazioni SQL + nuovi endpoint + dashboard React con grafici (Recharts o Chart.js).

**Effort:** 1-2 settimane

---

### 5.9 🟠 Marketplace Integrazioni

**Cos'è:** Ecosistema di integrazioni pronte con software di terze parti.

| Tipo integrazione | Esempi |
|---|---|
| Contabilità | Zucchetti, Fatture in Cloud, TeamSystem |
| POS ristorante esterno | Tilby, Cassa in Cloud, Lightspeed |
| Smart locks | ASSA ABLOY, Salto, Nuki |
| CRM/Marketing | Mailchimp, HubSpot |
| Business Intelligence | Power BI, Tableau |
| Guest messaging | WhatsApp Business API |

**Numeri competitor:** Mews ha 1.000+ integrazioni, Cloudbeds 400+. Il tuo PMS ne ha 0.

**Perché NON è prioritario ora:** Le integrazioni si costruiscono dopo aver acquisito clienti. Ogni cliente chiede 1-2 integrazioni specifiche → le costruisci su richiesta e le aggiungi al marketplace.

**Effort:** Varia. API REST ben documentata + webhook system = 2-3 settimane per l'infrastruttura, poi 1-5 giorni per ogni integrazione specifica.

---

### 5.10 🟠 Multi-Currency

**Cos'è:** Supporto per più valute (EUR, USD, GBP, CHF) con conversione automatica.

**Perché conta:** Hotel in zone turistiche internazionali o hotel al confine (Svizzera, UK) devono fatturare in valute diverse. Il tuo Dashboard ha la currency hardcoded USD.

**Effort:** 3-5 giorni (campo `currency` su HotelSettings, formattazione Intl.NumberFormat dinamica, migration Flyway).

---

## 6. Mobile-First — Analisi Tecnica

### Verdetto: **2-4 settimane, NON una riscrittura**

Il 70% è già fatto. Ho verificato direttamente nel codice:

| Già implementato | Evidenza |
|---|---|
| Mobile drawer con FocusTrap | `MainLayout.tsx` L142-189 |
| Hamburger menu | `MainLayout.tsx` L228-234 |
| Navigation Rail → Drawer pattern M3 | `hidden md:flex` / `md:hidden` |
| Padding responsive | `px-4 sm:px-6 md:px-8` |
| Grid responsive | `grid-cols-1 sm:grid-cols-2 lg:grid-cols-4` |
| Design tokens M3 via CSS vars | `tailwind.config.js` — 50+ variabili |
| Touch target 40×40px | DECISIONS §4.2 |
| 15+ file con breakpoint `sm:` | Grep verificato |

### Lavoro rimanente

| Settimana | Lavoro | Dettaglio |
|---|---|---|
| **1** | Tabelle → card-list | 5 tabelle (Stays, Guests, Reservations, Billing, Restaurant) devono mostrare card stackate verticalmente sotto `sm:` invece dello scroll orizzontale |
| **2** | Form responsive + calendar | Form multi-colonna → `grid-cols-1 sm:grid-cols-2`. CalendarPlanning → vista agenda su mobile |
| **3** | Bottom nav + PWA | Bottom nav bar M3 con 5 voci + manifest.json + service worker |
| **4** | Polish + test | Safe area (notch iPhone), test su dispositivi reali, fix edge cases |

---

## 7. Pricing Commerciale

### 7.1 Modello SaaS Consigliato

| | **Starter** | **Professional** | **Enterprise** |
|---|---|---|---|
| Target | B&B, 1-15 camere | Hotel, 15-50 camere | Hotel/catena, 50+ |
| **Canone** | **€99/mese** | **€199/mese** | **€349/mese** |
| **Setup** | €500 | €1.500 | €3.000+ |
| Utenti | 3 | 10 | Illimitati |
| F&B POS | ❌ | ✅ | ✅ |
| Channel Manager* | ❌ | ✅ | ✅ |
| Booking Engine* | ❌ | ✅ | ✅ |
| Support | Email 48h | Email 24h + tel | Dedicato SLA 4h |
| Personalizzazioni | ❌ | 2h/mese incluse | 5h/mese incluse |

*\*Quando sviluppate*

### 7.2 Costi Extra

| Voce | Prezzo |
|---|---|
| Personalizzazioni extra | €80/ora |
| Migrazione dati | €500-1.500 |
| Formazione aggiuntiva | €150/sessione 2h |
| Hotel aggiuntivo (multi-property) | +€79/mese |
| Integrazione custom | €1.000-5.000 a progetto |

### 7.3 Margini

| Piano | Revenue/anno | Costo infra/anno | Margine |
|---|---|---|---|
| Starter | €1.188 | ~€600 | **49%** |
| Professional | €2.388 | ~€900 | **62%** |
| Enterprise | €4.188 | ~€1.200 | **71%** |

Punto di pareggio (1 sviluppatore full-time): **~25-30 clienti Professional**

### 7.4 Strategia di Lancio

> Lancia a **€149/mese** per i primi 10 clienti pilota con setup scontato al 50%. Dopo 10 clienti, alza a €199/mese. I primi 10 mantengono il prezzo pilota per 12 mesi.

---

## 8. Roadmap Post-Esame → Enterprise

```
FASE 1 — Quick Wins (2-4 settimane)
├── Email/SMS conferme (1-2 sett)
├── PDF fatture (1 sett)
├── Mobile responsive (2-4 sett, parallelizzabile)
├── Report KPI RevPAR/ADR (1-2 sett)
└── Multi-currency fix (3-5 giorni)

FASE 2 — Revenue Features (1-2 mesi)
├── Booking Engine + Stripe (1-2 mesi)
├── Online check-in guest (1-2 mesi)
└── Revenue management base (2-3 mesi)

FASE 3 — Distribution (3-6 mesi)
├── Channel Manager (integrazione terza parte: 1-2 mesi)
│   OPPURE
├── Channel Manager (integrazione diretta OTA: 3-6 mesi)
└── Google Hotel Ads integration

FASE 4 — Scale (post-lancio)
├── Integrazioni su richiesta cliente
├── App housekeeping mobile
├── AI pricing engine
└── Multi-property dashboard
```

### Dove Investi Prima

| Feature | Revenue impact | Effort | ROI |
|---|---|---|---|
| Email/SMS | Basso (igiene) | 1-2 sett | Medio — senza non vendi |
| PDF fatture | Basso (igiene) | 1 sett | Medio — senza non vendi |
| Booking Engine | Alto (€15-30k/anno/hotel risparmiati) | 1-2 mesi | **Altissimo** |
| Channel Manager (3rd party) | Critico (80% del mercato) | 1-2 mesi | **Critico** |
| Revenue Management | Alto (10-20% RevPAR) | 2-3 mesi | Alto |
| Mobile PWA | Medio (UX) | 2-4 sett | Medio |

---

## 9. Differenziatori Competitivi — Dove Batti Tutti

Non dimenticare i tuoi punti di forza unici:

| Differenziatore | Perché nessun competitor ce l'ha |
|---|---|
| **Threat model STRIDE pubblico** | Nessun vendor commerciale pubblica il proprio modello di minaccia |
| **Alloggiati PS SOAP nativo** | Tutti gli altri usano plugin di terze parti che si rompono |
| **Codice sorgente al cliente** | Zero vendor lock-in — l'hotel può uscire quando vuole |
| **F&B POS nativo** con addebito camera | Little Hotelier e RoomRaccoon non lo hanno |
| **Costo 50-70% inferiore** a Cloudbeds/Mews | Con channel manager incluso, €199 vs €300-600 |
| **Observability stack** (Zipkin + Prometheus + Grafana + Loki) | Nessun PMS di fascia media offre distributed tracing |

### Sweet Spot Commerciale

> **Hotel indipendente italiano 10-80 camere** che vuole: compliance Alloggiati senza plugin, controllo totale sul software, costi prevedibili, e nessun vendor lock-in.
