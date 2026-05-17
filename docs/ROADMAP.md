# Roadmap — Hotel PMS

**Versione:** 1.0 — 2026-05-17  
**Branch:** `main`

Questo documento raccoglie le implementazioni future pianificate,
organizzate per priorità e orizzonte temporale. Lo stato attuale
del sistema è **Pilot-ready → Pre-production** su installazione
mono-hotel Docker Compose. La roadmap descrive il percorso verso
**Production-ready** e poi verso **Enterprise SaaS**.

---

## Stato attuale

| Dimensione | Livello | Note |
|---|---|---|
| Sicurezza | 8.5/10 | Argon2id, HMAC, RBAC doppio livello, GDPR |
| Affidabilità | 7.0/10 | Circuit breaker, saga checkIn — manca backup DB e @Version Invoice |
| Osservabilità | 7.5/10 | Zipkin + Prometheus + Loki — mancano alert rule |
| Scalabilità | 6.0/10 | Microservizi corretti — SimpleDiscovery statico, LIKE non indicizzato |
| Qualità codice | 8.0/10 | 324/324 test, PMD zero — Testcontainers mancanti |
| Operabilità | 5.5/10 | Docker Compose mono-host — no K8s, no backup, no restart policy |
| Conformità normativa | 9.0/10 | Alloggiati SOAP nativo, GDPR — no SDI/fattura B2B |
| UX e funzionalità | 7.0/10 | Flussi core completi — no mobile, no email, no channel manager |

**Punto di forza unico rispetto ai competitor:**
Implementazione Alloggiati PS nativa (SOAP, DRY_RUN, auto-send,
badge status) — tutti i PMS commerciali italiani usano plugin
di terze parti per questa funzionalità obbligatoria per legge (art. 109 TULPS).

---

## Sprint 1 — Da Pilot-ready a Production-ready
*Orizzonte: 4-6 settimane, 1 sviluppatore*

Prerequisiti bloccanti per il primo hotel reale in produzione.

| # | Implementazione | Priorità | Effort | Note |
|---|---|---|---|---|
| P1 | `@Version` su `Invoice` + migration Flyway | 🔴 Critica | 2h | Race condition con F&B + billing concorrenti — perdita silenziosa di addebiti |
| P2 | `restart: unless-stopped` in docker-compose | 🔴 Critica | 30min | Container crashato rimane down fino a intervento manuale |
| P3 | Backup PostgreSQL automatizzato (pg_dump cron) | 🔴 Critica | 4h | Data loss catastrofico su crash disco — tutti i dati hotel persi |
| P4 | Prometheus alert rules (error rate, latency, restarts) | 🟡 Alta | 1-2gg | Degradi invisibili senza notifica — SLA impossibile senza alert |
| P5 | Operations Runbook | 🟡 Alta | 1gg | Recovery ADMIN, rollback migration, restart ordinato — assenza = disastro in notturna |
| P6 | GIN index + `pg_trgm` su `GuestRepository` | 🟡 Alta | 4h + Flyway | `LIKE '%keyword%'` O(n) full-table scan — inutilizzabile con >50k ospiti |
| P7 | Testcontainers su stay-service e billing-service | 🟡 Media | 3-5gg | Migration Flyway non testate da codice Java — regressioni DB invisibili a Mockito |
| P8 | Credenziali Alloggiati PS configurabili da UI | 🟡 Media | 3-5gg | Attuale: richiede accesso `.env` + riavvio container per ogni hotel onboarding |
| P9 | Dependabot auto-PR per aggiornamenti dipendenze | 🟢 Bassa | 30min | Aggiornamenti dipendenze manuali — CVE latenti invisibili senza alert automatico |

---

## Sprint 2 — Quick wins commerciali
*Orizzonte: 2-3 mesi, team 2 persone*

Feature necessarie per la vendibilità del prodotto.

| # | Implementazione | Priorità | Effort | Impatto |
|---|---|---|---|---|
| C1 | Email/SMS conferme prenotazione (notification-service) | 🔴 Critica | 1-2 sett | Standard minimo assoluto — senza non vendibile |
| C2 | Numerazione fattura sequenziale certificata | 🔴 Critica | 4h + Flyway | Obbligatorio per fattura fiscalmente valida in Italia (D.P.R. 633/72) |
| C3 | IVA disaggregata nella fattura PDF | 🔴 Alta | 2-4h | Richiesto per nota spese B2B — PDF attuale non valido per clientela business |
| C4 | Report KPI avanzati (RevPAR, ADR, GOPPAR, Occupancy) | 🟡 Alta | 1-2 sett | Proprietario non può misurare performance senza KPI standard di settore |
| C5 | Mobile PWA (responsive ottimizzato) | 🟡 Alta | 2-4 sett | 70% già fatto — housekeeping e front desk usano telefono |
| C6 | Wizard onboarding primo avvio | 🟡 Media | 1 sett | Attuale: sequenza configurazione non guidata — rischio errori setup |
| C7 | Vista occupazione rapida nella dashboard | 🟡 Media | 2-3gg | Receptionist vede tabella, non ha widget visivo immediato per status camere |
| C8 | Multi-currency (campo `currency` in HotelSettings) | 🟢 Bassa | 3-5gg | Hotel in zona turistica internazionale o al confine |
| C9 | Grafana Tempo + OpenTelemetry (migrazione da Zipkin) | 🟢 Bassa | 3-5gg | Standard industria — Zipkin dichiarato deprecated in `backup/DECISIONS.md §7.2` |
| C10 | TailwindCSS 3 → 4 | 🟢 Bassa | 1-2gg | Breaking changes CSS — pianificare con tempo dedicato (`backup/DECISIONS.md §7.1`) |
| C11 | CONTRIBUTING.md — guida contribuzione e onboarding dev | 🟡 Media | 4h | Nessuna guida per handover a un team o nuovo sviluppatore |

---

## Sprint 3 — Feature enterprise core
*Orizzonte: 3-6 mesi, team 2-3 persone*

Feature che abilitano la competizione con PMS commerciali.

| # | Implementazione | Priorità | Effort | Dipendenze | Impatto |
|---|---|---|---|---|---|
| E1 | Channel Manager OTA (via intermediario SiteMinder/RateTiger) | 🔴 Critica | 1-2 mesi | Certificazione OTA 2-6 mesi | Sblocca 80% mercato — senza, non nella shortlist di valutazione |
| E2 | Booking Engine + pagamento online (Stripe Checkout) | 🔴 Alta | 1-2 mesi | C1 (notification-service) | Risparmio commissione OTA 15-25% per prenotazione |
| E3 | Fattura elettronica SDI/XML | 🔴 Alta | 1-2 mesi | Accreditamento AE | Obbligatorio per clientela business in Italia dal 2019 |
| E4 | API pubblica documentata + webhook system | 🟡 Alta | 2-3 sett | Nessuna | Ecosistema ISV — senza, nessun partner può integrarsi |
| E5 | Kubernetes migration | 🟡 Alta | 1-2 sett | Container già stateless e K8s-ready by design | Scaling orizzontale, rolling update, failover automatico |
| E6 | PostgreSQL HA (replica + failover automatico) | 🟡 Alta | 1-2 sett | E5 (K8s) o Patroni | Single node attuale = SPOF per i dati |
| E7 | Secrets → HashiCorp Vault o cloud KMS | 🟡 Alta | 2-3gg | E5 | Env var su disco non adeguato per server condivisi/prod |
| E8 | Online check-in guest self-service | 🟡 Media | 1-2 mesi | C1 (notification-service) | Dati Alloggiati pre-compilati — riduce tempo al check-in |
| E9 | Two-factor authentication (TOTP/FIDO2) | 🟡 Media | 1-2 sett | auth-service | GDPR Art. 32 — accesso ADMIN a PII senza 2FA è gap enterprise |
| E10 | Spring Cloud Contract (consumer-driven contract testing) | 🟢 Media | 1 sett | Nessuna | Rileva breaking change tra microservizi prima del deploy |
| E11 | Multi-hotel UI per catena alberghiera | 🟢 Media | M | `hotel_id` già pronto — serve solo selector UI | Clienti catena — architettura multi-tenant già predisposta |
| E12 | Configurazione IVA per tipologia (10% camere, 22% F&B) | 🟡 Alta | M | C2 (numerazione fattura) | Conformità fiscale italiana — aliquote diverse per categoria |
| E13 | Audit log immutabile append-only (GDPR Art. 30) | 🟡 Media | M | Nessuna | Registro trattamenti PII non modificabile — GDPR enterprise |
| E14 | Rate limiting per-utente granulare | 🟢 Media | S | Redis già presente | Credential stuffing da IP distribuiti non bloccato dall'attuale per-IP |
| E15 | SLA monitoring e status page | 🟢 Media | M | Prometheus già presente | Contratto SaaS richiede uptime dichiarato |
| E16 | Google Hotel Ads integration | 🟡 Media | 2-3 sett | E2 (Booking Engine) | Google Hotels mostra il "prezzo diretto" se il booking engine è accreditato |

---

## Sprint 4 — Revenue e differenziazione
*Orizzonte: 6-12 mesi, team 3-4 persone*

Feature di differenziazione competitiva ad alto impatto ROI.

| # | Implementazione | Effort | Impatto |
|---|---|---|---|
| R1 | Revenue Management con dynamic pricing | XL (4-6 mesi) | +10-20% RevPAR stimato |
| R2 | CRM ospiti avanzato (preference history, segmentazione, loyalty) | L (2-3 mesi) | Retention e upsell post-stay |
| R3 | Marketplace integrazioni (serrature smart, contabilità, CRM esterni) | L (post E4 API pubblica) | Ecosistema partner |
| R4 | Housekeeping mobile ottimizzata (task assignment, foto prova) | M (2-3 sett) | Personale pulizie usa telefono — gap operativo reale |
| R5 | Notifiche push/email operativi (camera pronta, check-in atteso) | M (1-2 sett) | Coordinamento front desk — housekeeping |
| R6 | Multi-region / HA cross-AZ | L | Uptime enterprise, disaster recovery geografico |
| R7 | AI/ML pricing engine avanzato | XL (6-9 mesi) | Ottimizzazione tariffe basata su dati storici, comp set, eventi — oltre le regole statiche di R1 |

---

## Confronto con PMS commerciali

| Funzionalità | hotel-pms | Mews / Opera Cloud | Gap |
|---|---|---|---|
| Gestione prenotazioni | ✅ Completa | ✅ + group, waitlist | Group bookings, waitlist |
| Channel Manager OTA | ❌ Assente | ✅ 400-1000+ canali | **Gap commerciale critico** |
| Revenue Management | ❌ Assente | ✅ Dynamic pricing | +10-20% RevPAR |
| Fatturazione | ⚠️ PDF base | ✅ SDI, multi-valuta, folio | IVA disaggregata, SDI, numerazione legale |
| CRM ospiti | ⚠️ Profilo base | ✅ Loyalty, marketing | Retention, upsell |
| Housekeeping | ⚠️ Desktop only | ✅ App mobile nativa | Mobile, task assignment |
| F&B / POS | ✅ Nativo integrato | ✅ + table mgmt, kitchen | Gestione tavoli, stampante cucina |
| Alloggiati PS | ✅ **Nativo SOAP** | ⚠️ Plugin terze parti | **hotel-pms vince** |
| Sicurezza auth | ✅ Argon2id + HMAC | ⚠️ Varia per vendor | **hotel-pms superiore** |
| Osservabilità | ✅ Stack completo | ⚠️ Varia per vendor | **hotel-pms superiore** |
| API pubblica | ❌ Assente | ✅ 400-1000+ partner | Ecosistema integrazioni |
| Mobile | ❌ PWA non implementata | ✅ App nativa iOS/Android | Gap operativo reale |
| Multi-property | ✅ Architettura pronta | ✅ Dashboard cross-hotel | Solo UI selector mancante |

---

## Timeline e investimento per Enterprise SaaS

| Fase | Durata | Team | Output |
|---|---|---|---|
| Production-ready | 4-6 sett | 1 dev | Backup, alert, @Version, runbook |
| Quick wins commerciali | 2-3 mesi | 2 persone | Email, mobile, KPI, fattura valida |
| Enterprise core | 3-6 mesi | 2-3 persone | Channel manager, booking engine, K8s |
| Enterprise SaaS | 6-12 mesi | 3-4 persone | API pubblica, revenue mgmt, HA |

**Investimento anno 1 stimato (team 4-5 FTE):** €265.000-330.000

**Punto di pareggio:** ~140 clienti a €199/mese (Professional tier)
— mercato di riferimento: 350.000 strutture ricettive registrate in Italia.

---

## Pricing commerciale SaaS

Modello di riferimento per il lancio commerciale.

| Piano | Target | Canone | Setup | Utenti | F&B POS | Channel Manager* |
|---|---|---|---|---|---|---|
| **Starter** | B&B, 1-15 camere | **€99/mese** | €500 | 3 | ❌ | ❌ |
| **Professional** | Hotel, 15-50 camere | **€199/mese** | €1.500 | 10 | ✅ | ✅ |
| **Enterprise** | Hotel/catena, 50+ | **€349/mese** | €3.000+ | Illimitati | ✅ | ✅ |

*\*Quando sviluppato (Sprint 3 E1)*

| Piano | Revenue/anno | Costo infra/anno | Margine |
|---|---|---|---|
| Starter | €1.188 | ~€600 | **49%** |
| Professional | €2.388 | ~€900 | **62%** |
| Enterprise | €4.188 | ~€1.200 | **71%** |

**Strategia lancio:** Primi 10 hotel pilota a €149/mese con setup al 50%.
Dopo 10 clienti, prezzo pieno €199/mese. I pilota mantengono il prezzo per 12 mesi.

**Costi extra:** personalizzazioni €80/ora · migrazione dati €500-1.500 ·
formazione €150/sessione · hotel aggiuntivo (catena) +€79/mese.

---

## Documentazione enterprise mancante

Documenti necessari per il livello enterprise che richiedono produzione
(alcuni necessitano revisione legale prima della pubblicazione).

| Documento | Priorità | Chi lo produce | Effort | Obbligatorio |
|---|---|---|---|---|
| Privacy Policy (GDPR Art. 13/14 — informativa agli interessati) | 🔴 Alta | Legal | 2-4h con template | **Sì — GDPR** |
| Data Processing Agreement (DPA GDPR Art. 28) | 🔴 Alta | Legal | 4-8h | **Sì — GDPR** |
| Cookie Policy (JWT httpOnly + CSRF + refresh token) | 🔴 Alta | Legal | 1-2h | **Sì — ePrivacy Directive** |
| Terms of Service | 🟡 Media | Legal + Product | 4h | No (ma atteso da clienti enterprise) |
| SLA document (uptime, tempi risposta supporto) | 🟡 Media | Product + DevOps | 2h | No (ma atteso da clienti enterprise) |
| Capacity Planning (n. hotel per server, dimensionamento) | 🟢 Bassa | DevOps | 4h | No |

*I documenti contrassegnati come obbligatori richiedono revisione legale prima della pubblicazione.*

---

*Documento aggiornato 2026-05-17. Fare riferimento a
[`docs/FINAL_AUDIT_ULTRA_SEVERE.md`](FINAL_AUDIT_ULTRA_SEVERE.md)
per i gap tecnici specifici e le evidenze da codice.*
