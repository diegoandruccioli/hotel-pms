# SSRF Audit

Scope: all backend services (`api-gateway`, `auth-service`, `guest-service`,
`frontdesk-service`, `billing-service`, `fb-service`, `notification-service`,
`config-service`, `pdf-template-engine`, `common-web-lib`). Read-only review;
no files modified except this report.

## Methodology

1. Read `AlloggiatiWebSenderServiceImpl.java` and `AlloggiatiWebConfig.java` in full,
   plus the resolved `alloggiati.web.*` properties in
   `config-service/src/main/resources/config/frontdesk-service.yml`, and the
   per-hotel credential-resolution path (`HotelSettingsRepository` /
   `HotelSettings` entity) to confirm what a hotel can and cannot override.
2. Grepped the entire Java tree (`**/*.java`) for every outbound-HTTP
   construct: `new RestTemplate`, `RestTemplateBuilder`, `WebClient.`,
   `HttpClient.`, `.openConnection(`, `new URL(`, `new URI(`, OkHttp, and
   `@FeignClient`.
3. For every `@FeignClient` found, read the annotation's `url`/`name` binding
   and traced it back to its Spring property in `config-service` YAMLs (or its
   inline default) to confirm no request/DB-sourced value feeds the host.
4. Grepped all DTOs (`**/dto/**/*.java`) and the wider tree for
   `webhook|callbackUrl|redirectUrl|avatarUrl|logoUrl|imageUrl|externalUrl`-style
   fields, since these are the classic "user-suppliable URL" SSRF entry point.
5. For every hit on `logoUrl` (the only user-suppliable URL-shaped field found),
   traced it end-to-end: DTO → controller → persistence → every consumer
   (`billing-service` PDF generation, `notification-service` email templates)
   to determine whether any backend service ever dereferences it itself.
6. Checked `pdf-template-engine` (shared by `billing-service` for FatturaPA/
   invoice PDFs) for any network-resource resolution capability.
7. Checked gateway routing (`api-gateway`) for dynamic/user-influenced upstream
   URI construction.
8. As a secondary, related check (XXE can produce SSRF-like out-of-process
   fetches), scanned `DocumentBuilderFactory.newInstance()` call sites.

---

## Finding 1 — AlloggiatiWebSenderServiceImpl: NOT SSRF (confirmed)

**File**: `frontdesk-service/src/main/java/com/hotelpms/frontdesk/stays/service/impl/AlloggiatiWebSenderServiceImpl.java:110-131, 283-297`
**Severity**: N/A (no finding)

The SOAP endpoint URL is injected once at construction via
`@Value("${alloggiati.web.service-url}")` (line 115) and stored in the `final
String serviceUrl` field (line 89). It is used unmodified as the POST target
in `callSoap()` (line 291: `restOperations.postForEntity(serviceUrl, ...)`).

Resolved property, `config-service/src/main/resources/config/frontdesk-service.yml:52`:
```
service-url: ${ALLOGGIATI_SERVICE_URL:https://alloggiatiweb.poliziadistato.it/service/Service.asmx}
```
This is a Spring `@Value` placeholder resolved once at bean-construction time
from an environment variable with a hardcoded fallback — never from a request,
a database row, or any per-hotel setting.

Per-hotel customization (`resolveCredentials()`, lines 140-148, backed by
`HotelSettingsRepository`/`HotelSettings`) is scoped **only** to
`alloggiatiUsername` / `alloggiatiPassword` (decrypted) / `alloggiatiWsKey` —
i.e., SOAP *credentials*, not the SOAP *endpoint*. `HotelSettings` (`frontdesk-service/.../domain/HotelSettings.java`)
and `HotelSettingsRequest`/`HotelSettingsResponse` were inspected and contain
no `serviceUrl`/host/port field of any kind — a hotel administrator cannot
influence the outbound target for this integration by any path. Confirmed non-issue.

---

## Finding 2 — AlloggiatiLookupDataLoader: second internet-outbound call, also NOT SSRF, but recon's inventory was incomplete

**File**: `frontdesk-service/src/main/java/com/hotelpms/frontdesk/stays/config/AlloggiatiLookupDataLoader.java:29-138`
**Severity**: N/A (no finding), but flagged because `security-audit/00-recon.md` stated
AlloggiatiWebSenderServiceImpl was "the only outbound-to-internet call" — that is inaccurate.

This `ApplicationRunner` downloads three CSV reference datasets (states,
comuni, document types) from the Polizia di Stato portal at application
startup, using `java.net.http.HttpClient` directly (not the SOAP
`RestOperations` bean):

```java
private static final String BASE_URL =
        "https://alloggiatiweb.poliziadistato.it/portalealloggiati/ashx/Download.ashx";
private static final String STATI_URL = BASE_URL + "?ID=1&N=STATI";
private static final String COMUNI_URL = BASE_URL + "?ID=0&N=COMUNI";
private static final String TIPDOC_URL = BASE_URL + "?ID=2&N=TIPDOC";
```
All three URLs are `private static final` compile-time constants built purely
from other constants — no request, config, or DB input reaches `download(url)`
(lines 123-138). Not exploitable. Recommend updating `00-recon.md`'s SSRF
section to list this second call so the inventory is accurate for future
audits (documentation gap, not a vulnerability).

---

## Finding 3 — `logoUrl`: user-suppliable URL field exists, but is never dereferenced server-side today (informational / hardening recommendation)

**Severity**: INFO / LOW (hardening recommendation — not currently exploitable)

`logoUrl` is the only URL-shaped field found anywhere in the DTO layer across
all 7 services (grepped for `webhook|callback|redirect|avatar|logo|image|external`
× `Url`/`Uri` — this was the sole hit family). Trace:

1. **Write path** — `frontdesk-service/src/main/java/com/hotelpms/frontdesk/stays/dto/HotelSettingsRequest.java:54`:
   `String logoUrl` — **no validation annotation at all** (no `@URL`, `@Pattern`,
   `@Size`, scheme allowlist, or host allowlist), unlike neighboring fields in
   the same record (`cap` has `@Pattern`, `alloggiatiUsername` has `@Size`).
   Endpoint: `HotelSettingsController.updateSettings()`
   (`frontdesk-service/.../controller/HotelSettingsController.java:47-51`),
   gated `@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")` — so only a
   hotel-tenant admin/owner (not a guest, not an anonymous caller) can set it.
2. **Storage** — `HotelSettings.java:71-72`, `@Column(name = "logo_url", length = 500)`.
3. **Read/consumption paths traced**:
   - `frontdesk-service` → `NotificationClient` DTOs (`NotificationQuotationRequest`,
     `NotificationCheckoutRequest`, `NotificationReservationRequest`) → forwarded
     verbatim to `notification-service`.
   - `notification-service` templates (`checkout-{it,en}.html`,
     `reservation-confirmed-{it,en}.html`, `quotation-{it,en}.html`):
     `<img th:if="${req.logoUrl != null and !req.logoUrl.isEmpty()}" th:src="${req.logoUrl}" .../>`.
     `NotificationServiceImpl.sendHtmlEmail()` (lines 127-143) calls
     `helper.setText(html, true)` and `mailSender.send(message)` — **the
     backend never issues an HTTP request for this URL itself**; the string
     is only embedded as an `<img src>` attribute inside the outbound MIME
     email. Thymeleaf's `th:src` HTML-attribute-escapes the value, so this is
     not an HTML-injection/XSS vector either. The only fetch that occurs is by
     the **guest's own mail client**, when/if they open the email and their
     client auto-loads remote images — that is a client-side
     tracking-pixel/phishing consideration, not server-side SSRF against our
     infrastructure, and is out of this audit's scope.
   - `billing-service` → `HotelSettingsClient`/`HotelSettingsResponse.logoUrl`
     (`billing-service/.../client/dto/HotelSettingsResponse.java:24`) is
     fetched but **never read** by `PdfInvoiceServiceImpl.buildContext()`
     (`billing-service/.../service/impl/PdfInvoiceServiceImpl.java:89-124`) —
     only `hotelName`, `address`, `vatNumber`, `fiscalCode` are pulled from
     `hotel`. The PDF logo instead comes exclusively from a bundled classpath
     asset (`LOGO_CLASSPATH_RESOURCE = "static/pdf/logo.png"`, lines 51-56,
     137-147), base64-embedded as a `data:` URI — by explicit design, per the
     code comment: *"never admin-uploaded — uploads are an attack surface, a
     static asset shipped by the developer is not"*.
   - `pdf-template-engine/src/main/java/com/hotelpms/pdftemplate/PdfTemplateRenderer.java:6-14`
     documents this as a hard module invariant: *"this renderer never resolves
     file paths or network URLs, which keeps it free of filesystem/SSRF
     assumptions."* Confirmed no `openhtmltopdf` `UserAgentCallback`/network
     resource resolution is wired up anywhere that would fetch `logoUrl`
     server-side.

**Conclusion**: not currently exploitable as SSRF — no service in the current
codebase performs a server-side fetch of `logoUrl`. It is flagged as
low/informational because:
- The field is completely unvalidated (no scheme allowlist, e.g. could hold
  `file://`, `gopher://`, an internal hostname, or a link-local/metadata IP —
  none of that matters *today* only because nothing fetches it).
- The explicit design comment in `PdfInvoiceServiceImpl` shows the team is
  already aware that admin-suppliable image sources are attack surface and
  deliberately avoided it for the PDF path — the same reasoning has not yet
  been applied/documented for `logoUrl` itself (it's stored and threaded
  through 3 services' DTOs for a feature — email header branding — that
  never actually renders it server-side and only reaches client mail apps).
- **Recommendation (defense-in-depth, only if server-side fetching of this
  URL is ever added later, e.g. to inline the logo as a CID attachment or to
  proxy/cache it)**: enforce `https://` scheme only, resolve DNS and reject
  private/link-local/loopback/multicast ranges (RFC 1918, 169.254.0.0/16
  incl. `169.254.169.254`, `127.0.0.0/8`, `::1`, `fc00::/7`) and re-check the
  resolved IP immediately before connecting (to close the DNS-rebinding TOCTOU
  gap), or route any such fetch through an egress allowlist/proxy. Until such
  a feature exists, no code change is required.

---

## Finding 4 — Inter-service Feign clients: NOT SSRF (confirmed)

**Severity**: N/A (no finding)

Every `@FeignClient` in the codebase was enumerated and its `url`/`name`
traced to its source:

| Client | File | URL source |
|---|---|---|
| `HotelSettingsClient` | `billing-service/.../client/HotelSettingsClient.java:14` | `${application.config.frontdesk-service-url}` → `config-service/billing-service.yml:9` = `http://frontdesk-service:8081` (fixed) |
| `GuestClient` | `billing-service/.../client/GuestClient.java:25` | `${application.config.guest-service-url}` → `config-service/billing-service.yml:13` = `http://guest-service:8083` (fixed) |
| `StayClient` | `fb-service/.../client/StayClient.java:16` | `${application.config.frontdesk-service-url}` (fixed, config-service) |
| `BillingClient` | `fb-service/.../client/BillingClient.java:19` | `${application.config.billing-service-url}` (fixed, config-service) |
| `BillingClient` | `frontdesk-service/.../client/BillingClient.java:26` | `name` only, resolved via Eureka/service registry (no dynamic `url`) |
| `GuestClient` | `frontdesk-service/.../client/GuestClient.java:47` | `${APPLICATION_CONFIG_GUEST_SERVICE_URL:http://guest-service:8083}` — inline fixed default |
| `NotificationClient` | `frontdesk-service/.../client/NotificationClient.java:24` | `name` only (registry-resolved) |
| `AlloggiatiComuniClient`, `ReservationClient`, `StayServiceClient`, `BillingServiceClient` | `guest-service/.../client/*.java` | all `name`/config-property based, same pattern |

None of these interpolate any part of the host, port, or scheme from request
input, JWT claims, path/query params, or a database column. All targets are
either Docker Compose internal service DNS names or Spring Cloud service-registry
names, sourced exclusively from `config-service`'s YAMLs or hardcoded
fallbacks. `api-gateway` routing was also checked (`route(`, `uri(`, `lb://`,
`setUri(`, `forward(` grepped across `api-gateway/src/main/java`) — no
dynamic upstream-URI construction found; routing is declarative/config-driven,
consistent with the documented gateway architecture.

---

## Finding 5 — XXE-adjacent DOM parsing: reviewed, not an SSRF vector

**Severity**: N/A (no finding, noted for completeness)

Two `DocumentBuilderFactory.newInstance()` call sites exist:
- `AlloggiatiWebSenderServiceImpl.java:330-332` — parses the SOAP *response*
  from the trusted Alloggiati Web portal, and explicitly sets
  `disallow-doctype-decl = true` before parsing (line 332) — DTD/external-entity
  fetches are blocked.
- `billing-service/.../service/impl/FatturaPAServiceImpl.java:319-322` — per
  its own code comment, this factory only ever *builds* a fresh, empty
  `Document` programmatically (constructing the FatturaPA XML for output); it
  never parses untrusted/external XML, so XXE/SSRF-via-DTD does not apply,
  though the comment notes DOCTYPE is disabled anyway as defense-in-depth.

Neither path allows an attacker to smuggle a `<!DOCTYPE ... SYSTEM "http://...">`
that would make the JVM XML parser fetch an internal/metadata URL.

---

## Summary Table

| # | Location | Severity | SSRF? | Notes |
|---|---|---|---|---|
| 1 | `AlloggiatiWebSenderServiceImpl` SOAP endpoint | — | No | URL is a fixed `@Value` config constant; per-hotel override is credentials-only, never the endpoint. Confirmed. |
| 2 | `AlloggiatiLookupDataLoader` CSV downloader | — | No | Second, previously unlisted outbound-to-internet call; URLs are compile-time constants. Recon doc should be updated to include it (docs gap, not a vuln). |
| 3 | `HotelSettings.logoUrl` (admin-settable, unvalidated) | INFO / LOW | Not currently — no service fetches it server-side | Flows only into an email `<img src>` (client-fetched, out of scope) and is unused in PDF generation, which deliberately uses a bundled classpath logo instead. Add scheme/host validation as defense-in-depth *if* a future feature ever fetches it server-side (e.g. CID-embedding, thumbnailing, proxying). |
| 4 | All `@FeignClient` inter-service calls | — | No | Every target host/URL traced to fixed config-service YAML values or hardcoded internal Docker service names; nothing request/DB-derived. |
| 5 | XML DOM parsing (`FatturaPAServiceImpl`, `AlloggiatiWebSenderServiceImpl`) | — | No (XXE-adjacent, checked for completeness) | Response parser disables DOCTYPE; XML-builder path never parses untrusted input. |

**Overall conclusion**: No exploitable SSRF vulnerability was found in
`guest-service`, `billing-service`, `notification-service`, or
`frontdesk-service` (including the Alloggiati Web integration). The one
user-suppliable URL field in the system (`logoUrl`) is currently inert from
an SSRF standpoint because no backend service dereferences it — it is
recorded here as a hardening recommendation should that ever change, not as
an active finding.
