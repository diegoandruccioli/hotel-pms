# C1 — notification-service: email conferme

## Decisione architetturale

### Opzione A — implementata ora (sincrona, Feign + Resilience4j)

Il servizio chiamante (reservation-service, stay-service) invoca `notification-service`
via **OpenFeign** con `@CircuitBreaker` Resilience4j.
Il fallback è silenzioso: log WARN, operazione business completata comunque.

**Perché ora:**
- Zero nuova infrastruttura (no broker, no Kafka, no RabbitMQ).
- Pattern identico a quello già in uso (BillingClient, GuestClient, FeignHeaderConfig + HMAC).
- Email non è mai critica quanto la prenotazione stessa.
- Adeguata per carico pilota (< 200 prenotazioni/giorno su 1–3 hotel).

### Opzione B — da implementare quando il carico cresce (asincrona, message broker)

Il servizio chiamante pubblica un evento su una coda (RabbitMQ o Kafka).
`notification-service` consuma in autonomia, con retry automatico e dead-letter queue.

**Trigger di migrazione obbligatori (uno dei seguenti è sufficiente):**
- Picchi di > 500 check-in/check-out/giorno su un singolo hotel.
- Latenza p99 del checkout > 2 s, e il profiling evidenzia la call a notification-service.
- Necessità di retry garantito (SLA contrattuale di delivery email ≥ 99 %).
- Espansione a notifiche push, SMS, webhook di terze parti (canali multipli per evento).
- Introduzione di booking-engine pubblico (E2): volumi non prevedibili, spike su offerte.

Fino ad allora Opzione A è corretta. Non anticipare la complessità.

---

## Piano di implementazione — Opzione A

### Prerequisiti (da verificare prima di iniziare)

1. `settings.gradle.kts` include il nuovo modulo `notification-service`.
2. Credenziali SMTP disponibili (Gmail SMTP o servizio dedicato) come variabili d'ambiente.
3. Non esiste già un bean `JavaMailSender` in nessun servizio.

---

### Step 1 — Scaffolding del modulo Gradle

**File:** `notification-service/build.gradle.kts`

Dipendenze necessarie:
- `spring-boot-starter-web` (espone endpoint REST per ricezione richieste)
- `spring-boot-starter-mail` (JavaMailSender)
- `spring-boot-starter-thymeleaf` (template engine per HTML email)
- `spring-cloud-starter-openfeign` (per chiamare config-service, se necessario)
- `spring-boot-starter-actuator`
- `resilience4j-spring-boot3` (per eventuale circuit breaker su risorse esterne future)
- `lombok`
- `spring-boot-starter-test` + `greenmail` (test SMTP)

**Porta:** 8088 (aggiornare `docker-compose.yml` e `vite.config.ts` se serve proxy).

**Nessun database** — il servizio è stateless: riceve DTO, renderizza template, spedisce.

**Config-service:** aggiungere `notification-service.yml` in `config-service/src/main/resources/config/`
con proprietà SMTP (risolte da env var a runtime):

```yaml
spring:
  mail:
    host: ${SMTP_HOST}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
      mail.smtp.starttls.required: true
      mail.mime.charset: UTF-8

notification:
  from-address: ${SMTP_FROM_ADDRESS}
  from-name: ${SMTP_FROM_NAME:Hotel PMS}
```

**Critico — sicurezza SMTP:**
- `SMTP_PASSWORD` NON va in `hotel_settings` (tabella DB accessibile via API).
- Deve stare solo in variabili d'ambiente del container / Docker secret.
- Nel `docker-compose.yml` aggiungere le env var sotto `notification-service`, mai committare
  valori reali.

---

### Step 2 — API contract del notification-service

Tre endpoint POST, tutti interni (non esposti oltre l'API Gateway):

```
POST /internal/notifications/reservation-confirmed
POST /internal/notifications/checkin
POST /internal/notifications/checkout
```

**DTO condiviso base (record Java):**

```java
// ReservationConfirmedNotificationRequest
record ReservationConfirmedNotificationRequest(
    String guestEmail,
    String guestName,
    String hotelName,
    String roomNumber,
    String roomTypeName,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int nights,
    BigDecimal totalEstimatedAmount,
    String currency,
    String reservationId,
    String locale          // "it" o "en" — per template i18n
) {}

// CheckinNotificationRequest
record CheckinNotificationRequest(
    String guestEmail,
    String guestName,
    String hotelName,
    String roomNumber,
    LocalDate expectedCheckOutDate,
    String locale
) {}

// CheckoutNotificationRequest — include dettaglio fattura
record CheckoutNotificationRequest(
    String guestEmail,
    String guestName,
    String hotelName,
    String roomNumber,
    LocalDateTime actualCheckIn,
    LocalDateTime actualCheckOut,
    List<InvoiceLineItem> lines,
    BigDecimal totalAmount,
    String currency,
    String paymentMethod,
    String locale
) {}

record InvoiceLineItem(
    String description,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {}
```

**Risposta:** `204 No Content` su successo. Errore SMTP → `500` con problem detail standard
(il chiamante gestisce via circuit breaker, non si aspetta recovery).

**Autenticazione:** header `X-Internal-Signature` (HMAC) obbligatorio — stesso pattern di
`FeignHeaderConfig` già in uso. Il controller valida la firma prima di processare.

**GDPR — log:** l'indirizzo email del guest NON deve apparire nei log applicativi in chiaro.
Mascherare: `a***@example.com`. Implementare utility `EmailMasker.mask(String email)`.

---

### Step 3 — Template Thymeleaf

Struttura file:

```
notification-service/src/main/resources/templates/email/
  reservation-confirmed-it.html
  reservation-confirmed-en.html
  checkin-it.html
  checkin-en.html
  checkout-it.html
  checkout-en.html
```

**Ogni template:**
- HTML responsive (table-based — i client email non supportano flexbox/grid).
- Header con nome hotel, footer con "non rispondere a questa email".
- Encoding dichiarato: `<meta charset="UTF-8">` nel `<head>`.
- Variabili Thymeleaf risolte dal DTO passato come model.

**Template checkout** deve includere:
- Tabella voci fattura (`lines`) con colonne: Descrizione / Qty / Prezzo unitario / Totale riga.
- Riga totale in grassetto.
- Metodo di pagamento.
- Nota: se `lines` è vuota (invoice non disponibile), mostrare solo totale — il chiamante
  non deve bloccare il checkout perché billing-service non ha risposto.

**Localizzazione:** il campo `locale` nel DTO determina quale template caricare.
Default `it` se locale sconosciuta o null.

---

### Step 4 — Implementazione NotificationService

```java
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.from-address}")
    private String fromAddress;

    @Value("${notification.from-name:Hotel PMS}")
    private String fromName;

    public void sendReservationConfirmed(ReservationConfirmedNotificationRequest req) {
        String templateName = "email/reservation-confirmed-" + sanitizeLocale(req.locale());
        Context ctx = new Context();
        ctx.setVariable("req", req);
        String html = templateEngine.process(templateName, ctx);
        sendHtmlEmail(req.guestEmail(), "Conferma prenotazione — " + req.hotelName(), html);
    }

    // sendCheckin, sendCheckout — stessa struttura

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(new InternetAddress(fromAddress, fromName));
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(message);
    }

    private String sanitizeLocale(String locale) {
        return ("en".equals(locale)) ? "en" : "it";
    }
}
```

**Critico:** `MimeMessageHelper` con `multipart=true` e encoding UTF-8 esplicito —
senza questo le lettere accentate italiane sono corrotte.

---

### Step 5 — Integrazione nei servizi chiamanti

#### 5a — reservation-service: conferma prenotazione

Creare `NotificationClient` (Feign) in `reservation-service`:

```java
@FeignClient(name = "notification-service", url = "${notification.service.url:http://localhost:8088}")
public interface NotificationClient {
    @PostMapping("/internal/notifications/reservation-confirmed")
    void sendReservationConfirmed(@RequestBody ReservationConfirmedNotificationRequest req);
}
```

In `ReservationServiceImpl.createReservation()`, dopo il save e prima del return:

```java
try {
    notificationClient.sendReservationConfirmed(buildConfirmationPayload(saved, guest, hotel));
} catch (Exception e) {
    log.warn("notification-service unreachable, email skipped for reservation {}", saved.getId());
}
```

**Alternativa preferita — `@CircuitBreaker` Resilience4j** (coerente col resto del progetto):

```java
@CircuitBreaker(name = "notification-service", fallbackMethod = "notificationFallback")
void sendReservationConfirmedNotification(ReservationResponse reservation) {
    notificationClient.sendReservationConfirmed(buildPayload(reservation));
}

void notificationFallback(ReservationResponse reservation, Exception ex) {
    log.warn("notification CB open — email skipped for reservation {}", reservation.id());
}
```

Config Resilience4j per `notification-service` (in `reservation-service.yml`):

```yaml
resilience4j.circuitbreaker.instances.notification-service:
  slidingWindowSize: 10
  failureRateThreshold: 50
  waitDurationInOpenState: 30s
  permittedNumberOfCallsInHalfOpenState: 3
```

#### 5b — stay-service (frontdesk-service): check-out con fattura

Il `StayServiceImpl.checkOut()` già chiama `billingClient` (F1).
Dopo la chiamata a `billingClient.closeInvoice()`:

1. Recuperare le righe fattura: `billingClient.getInvoiceLines(stay.getInvoiceId())`.
2. Costruire `CheckoutNotificationRequest` con `lines` popolate.
3. Chiamare `notificationClient.sendCheckout(...)` con stesso pattern `@CircuitBreaker`.

**Critico — ordine operazioni:**
```
1. checkOut() → aggiorna Stay.status = CHECKED_OUT
2. billingClient.closeInvoice(stay.invoiceId)     ← fattura finalizzata
3. billingClient.getInvoiceLines(stay.invoiceId)  ← righe per email
4. notificationClient.sendCheckout(...)           ← email con fattura
```
Se step 3 fallisce (billing down): inviare email ugualmente ma con `lines = emptyList()`.
Il guest riceve email col totale ma senza dettaglio — meglio di bloccare il checkout.

#### 5c — stay-service: check-in (opzionale, bassa priorità)

Semplice: dopo `checkIn()` salvato, `notificationClient.sendCheckin(...)`.
Nessun dato fattura richiesto — solo camera e data atteso check-out.

---

### Step 6 — docker-compose.yml

Aggiungere servizio `notification-service`:

```yaml
notification-service:
  build: ./notification-service
  ports:
    - "8088:8088"
  environment:
    - SPRING_PROFILES_ACTIVE=docker
    - CONFIG_SERVER_URI=http://config-service:8888
    - SMTP_HOST=${SMTP_HOST}
    - SMTP_PORT=${SMTP_PORT:-587}
    - SMTP_USERNAME=${SMTP_USERNAME}
    - SMTP_PASSWORD=${SMTP_PASSWORD}
    - SMTP_FROM_ADDRESS=${SMTP_FROM_ADDRESS}
    - INTERNAL_HMAC_SECRET=${INTERNAL_HMAC_SECRET}
  depends_on:
    - config-service
```

Aggiungere le variabili SMTP al `.env` locale (mai committare `.env` con valori reali).

---

### Step 7 — Test

**Strumento:** GreenMail (fake SMTP server per JUnit 5, nessun server esterno richiesto).

```xml
<dependency>
    <groupId>com.icegreen</groupId>
    <artifactId>greenmail-spring</artifactId>
    <version>2.1.x</version>
    <scope>test</scope>
</dependency>
```

**Test cases minimi (95% coverage obbligatorio):**

| Test | Verifica |
|------|----------|
| `sendReservationConfirmed_sendsHtmlEmail` | GreenMail riceve 1 email, subject corretto, to corretto |
| `sendReservationConfirmed_bodyContainsRoomAndDates` | HTML body contiene roomNumber, checkIn, checkOut |
| `sendCheckout_bodyContainsInvoiceLines` | tabella righe fattura presente nel body |
| `sendCheckout_emptyLines_sendsEmailWithoutLineTable` | email inviata anche con `lines=[]` |
| `sendCheckin_itLocale_usesItalianTemplate` | template `checkin-it.html` caricato |
| `sendCheckin_enLocale_usesEnglishTemplate` | template `checkin-en.html` caricato |
| `sendCheckin_unknownLocale_defaultsToItalian` | `locale=null` → template italiano |
| `emailMasker_masksLocalPart` | `a***@example.com` — no PII nei log |
| `controller_missingHmac_returns401` | header HMAC mancante → 401 |
| `controller_invalidHmac_returns401` | firma errata → 401 |

**Integration test con GreenMail:**

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "spring.mail.port=3025",
    "notification.from-address=test@hotel.com"
})
class NotificationControllerIntegrationTest {
    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);
    // ...
}
```

---

### Step 8 — Checklist critica pre-merge

- [ ] `SMTP_PASSWORD` non appare in nessun file committato (`.env`, `application.yml`, log).
- [ ] Email in log mascherata (`a***@domain.com`) — verificare con test.
- [ ] Fallback circuit breaker testato: se notification-service è down, `createReservation`
      e `checkOut` completano comunque (test con mock che lancia eccezione).
- [ ] Template HTML valido: testare render con Mailtrap o GreenMail e verificare
      visualizzazione su Gmail, Outlook (client email più comuni).
- [ ] Nessun `@Transactional` wrappa la call a `notificationClient` — l'email non deve
      fare parte della transazione business (se la mail fallisce, il rollback non deve
      annullare la prenotazione).
- [ ] Header `X-Internal-Signature` verificato in `NotificationController` prima di qualsiasi
      operazione — stesso pattern di `HmacRequestFilter` già in uso.
- [ ] Aggiornare `docs/API_REFERENCE.md` con i 3 endpoint interni.
- [ ] `./gradlew clean build` verde (PMD zero warnings, JaCoCo ≥ 95%).
- [ ] CI passa con il nuovo modulo incluso.

---

### Criticità e rischi aperti

| Rischio | Impatto | Mitigazione |
|---------|---------|-------------|
| Email guest non disponibile (campo nullable) | Email non spedita silenziosamente | Validare `guestEmail` nel DTO (`@Email @NotBlank`); se null, fallback log WARN |
| SMTP provider down al checkout | Nessuna email guest | Circuit breaker open → log; checkout non bloccato |
| Spike di check-out simultanei → SMTP throttling | Email ritardate o rifiutate da provider | Accettabile in Opzione A; trigger per Opzione B |
| Caratteri speciali nel nome hotel (es. `&`, `<`) | XSS nel body HTML | Thymeleaf escape automatico con `th:text` — non usare `th:utext` per dati esterni |
| Template non trovato (locale non supportata) | `TemplateInputException` a runtime | `sanitizeLocale()` garantisce solo `it`/`en`; aggiungere test per ogni locale |
| Credenziali SMTP ruotate → servizio giù | Nessuna email | SMTP config via env var → restart container sufficiente, zero redeploy codice |
