# XSS Audit — hotel-pms

Read-only audit. Scope: frontend React rendering, server-side Thymeleaf HTML
generation (PDFs + transactional emails), and reflected-input surfaces in
backend error responses. Builds on `security-audit/00-recon.md` §6/§8.

---

## 1. Frontend — `dangerouslySetInnerHTML` / raw DOM sinks

Re-verified independently (not just trusting recon):

```
grep -rn "dangerouslySetInnerHTML|innerHTML|outerHTML|eval\(|new Function\(" frontend/src
→ no matches
```

**Confirmed: zero occurrences** of `dangerouslySetInnerHTML`, `innerHTML`,
`outerHTML`, `eval(`, `new Function(` anywhere in `frontend/src`.

Static enforcement confirmed at `frontend/eslint.config.js:26-41`:

```js
'no-restricted-syntax': [
  'error',
  { selector: 'JSXAttribute[name.name="dangerouslySetInnerHTML"]', ... },
  { selector: 'AssignmentExpression[left.property.name="innerHTML"]', ... },
  { selector: 'AssignmentExpression[left.property.name="outerHTML"]', ... },
]
```

`npm run lint` (zero-warnings policy, CLAUDE.md) fails the build if any of
these are reintroduced. This is a real, load-bearing control, not just a
convention — confirmed as claimed in `00-recon.md` §6 (T-FE-01).

No `<Trans>` component from `react-i18next` is used anywhere in
`frontend/src` (`grep "Trans\s|<Trans"` → no matches). This matters because
`<Trans>` is the one common i18next pattern that *can* render raw HTML by
design (translation strings containing `<0>bold</0>` etc.) — it isn't present,
so locale files (`frontend/src/locales/`) aren't a viable injection path via
that mechanism either.

## 2. Frontend — attribute-based sinks (`href`/`src`, inline `<style>`)

Grepped for `href={`, `src={`, `javascript:`, `window.location =`,
`document.title`, `<style` across all `.tsx` files. Two real hits:

- `frontend/src/pages/HotelProfile.tsx:217` — `<img src={form.logoUrl} ... />`.
  `logoUrl` is a free-text field on `HotelSettingsRequest`
  (`frontdesk-service/src/main/java/com/hotelpms/frontdesk/stays/dto/HotelSettingsRequest.java:54`)
  with **no `@URL`/`@Pattern` Bean Validation** and no client-side URL-shape
  check either — an ADMIN/OWNER could set it to `javascript:alert(1)` or any
  string. **Not exploitable as XSS**: browsers do not execute `javascript:`
  URIs placed in an `<img src>` attribute (that behavior is specific to
  `<a href>`/`<iframe src>`/form actions) — a malformed value just fails to
  load as an image. Noted as a hardening gap (missing input validation), not
  an XSS finding: severity **Informational**. If this field is ever reused
  for an `<a href>`, iframe embed, or CSS `background: url()`, it would need
  a scheme allow-list (`http:`/`https:`/`data:image/`) before that reuse.
  Also: this endpoint is ADMIN/OWNER-only and the value is only ever rendered
  back to the same hotel's own admins on their own `HotelProfile` page — no
  cross-user/cross-tenant reflection.
- `frontend/src/pages/Quotations/QuotationPdfPreviewDialog.tsx:77` —
  `<iframe src={blobUrl ?? undefined} ... />`. `blobUrl` is produced locally
  via `URL.createObjectURL()` on a PDF `Blob` fetched from the backend (not a
  user-supplied string, not attacker-influenceable) — not an injection point.

No inline `<style>` tags with interpolated values were found anywhere in
`frontend/src`.

## 3. Third-party components — `react-big-calendar`

Present in `frontend/package.json` (`react-big-calendar: ^1.19.4`) and used
in `frontend/src/pages/CalendarPlanning.tsx`. Checked for custom renderers
that could bypass JSX escaping (`components=`, `eventPropGetter`,
`tooltipAccessor`, `titleAccessor`, custom `Event`/`Toolbar` overrides,
`dangerouslySetInnerHTML`) — **none found**. The calendar's event titles are
rendered through the library's default JSX-based `EventWrapper`, which does
not use raw HTML injection internally; reservation/room data flowing into it
(guest name, room number) stays inside React's normal escaping path.

## 4. Server-side — `pdf-template-engine` (Thymeleaf → PDF)

`pdf-template-engine/src/main/java/com/hotelpms/pdftemplate/ThymeleafPdfTemplateRenderer.java`
uses `TemplateMode.HTML` with a plain `TemplateEngine`/`Context` — standard
Thymeleaf, which auto-escapes `th:text` (does **not** auto-escape `th:utext`).

Searched every `.html` template under `templates/pdf/` (billing-service,
frontdesk-service) and `templates/email/` (notification-service) plus the
whole repo for `th:utext`:

```
grep -rn "th:utext|utext" --include="*.html" .
→ no matches anywhere in the repo
```

**Confirmed: zero `th:utext` usage anywhere.** Every field populated from
user-controllable data uses `th:text`, including the fields that matter most:

- `billing-service/src/main/resources/templates/pdf/invoice-fragments.html:48` —
  `hotelName` (`th:text`)
- `invoice-fragments.html:63-67` — `guestDisplayName`, `guestPersonalName`,
  `guestFiscalCode`, `guestVat`, `guestPec` (all `th:text`)
- `invoice-fragments.html:79-100` — `charge.typeLabel`, `charge.description`
  (free-text, operator-entered), `payment.methodLabel`, `payment.reference`
  (free-text) — all `th:text`
- `frontdesk-service/src/main/resources/templates/pdf/quotation.html:39,51,56,64` —
  `hotelName`, `guestDisplayName`, `option.label`, `room.label` — all `th:text`

Even string-concatenation expressions like
`th:text="'Fattura ' + ${invoiceNumber} + ' — ' + ${hotelName}"`
(`invoice-fattura.html:5`) still route the *interpolated variable* through
Thymeleaf's escaping — only the literal template string is unescaped, which
is developer-authored, not attacker-controlled.

**Verdict: no exploitable HTML/script injection in generated PDFs.** A guest
name of `<img src=x onerror=alert(document.cookie)>` (plausible payload,
since `guest-service`'s `GuestController.create`/`update` has no HTML-tag
stripping on the name field per `00-recon.md`) would render in the PDF as the
literal text `<img src=x onerror=alert(document.cookie)>`, not as a live
element — and even if it somehow rendered, `openhtmltopdf` PDFs are static
documents with no script execution context, so this class of payload has no
viable execution surface in a PDF regardless of escaping.

## 5. Server-side — `notification-service` email templates

Same result. Checked
`notification-service/src/main/resources/templates/email/{checkin,checkout,reservation-confirmed,quotation}-{en,it}.html`:

- `req.guestName` — `th:text` in every template (e.g.
  `checkin-en.html:20`, `checkout-en.html:24`, `reservation-confirmed-en.html:25`,
  `quotation-en.html:24`)
- `req.hotelName` — `th:text` (e.g. `checkin-en.html:14`)
- `req.greetingText` (free-text, hotel-configurable per-notification message)
  — `th:text` in every template that has it (e.g. `checkout-en.html:66`,
  `quotation-en.html:71`, `reservation-confirmed-en.html:75`)
- `req.roomDetails`, `req.roomNumber`, `req.reservationId`, `req.nights` — all
  `th:text`

**Verdict: no `th:utext`, no injection point.** A payload in `guestName` or
`greetingText` would render as literal escaped text in the guest's inbox
(`&lt;img src=x onerror=...&gt;` visible as plain characters), not execute —
even accounting for the fact that some webmail clients render HTML emails in
an unsandboxed way, Thymeleaf's escaping means no raw tag ever reaches the
client's HTML parser here.

### Adjacent finding (not XSS, flagged for completeness): unescaped values reaching mail *headers*, not HTML

`notification-service/src/main/java/com/hotelpms/notification/service/impl/NotificationServiceImpl.java`:

- `buildSubject()` (line 177) — when `HotelSettings.customSubject` (an
  ADMIN/OWNER-configurable per-hotel field, plumbed via
  `frontdesk-service`'s `NotificationReservationRequest`/`NotificationCheckoutRequest`
  → `StayNotificationCoordinator`) is set, it is used **verbatim**
  (`customSubject.trim()`) as the MIME `Subject` header via
  `helper.setSubject(subject)` (line 134) — this path never touches
  Thymeleaf/HTML at all, so `th:text` vs `th:utext` doesn't apply to it.
- `resolveFromName(hotelName)` (line 152) — `hotelName` (also
  ADMIN/OWNER-configurable, no length/charset validation found on
  `HotelSettingsRequest`) is passed as the MIME `personal` (display) name to
  `new InternetAddress(fromAddress, resolveFromName(hotelName), CHARSET)`
  (line 132).

This is a **header-injection surface (CRLF in a Subject/display-name field),
not an XSS surface** — out of strict scope for this audit, but worth flagging
because it sits right next to the fields checked above and shares the same
trust boundary. It is not remotely exploitable by a guest (both fields are
ADMIN/OWNER-only hotel-settings values, not per-reservation guest input), and
`jakarta.mail`'s `MimeMessageHelper`/`InternetAddress` machinery generally
folds/encodes header values rather than passing raw bytes to the socket, so
classic SMTP header injection is unlikely to succeed — but it hasn't been
proven safe against a crafted `\r\n` sequence either. Recommend a dedicated
`access-control.md`/`misconfig.md`-style follow-up (out of this file's scope)
rather than treating it as a finding here: add `@Pattern` validation
rejecting `\r`/`\n` on `HotelSettingsRequest.hotelName`/wherever
`customSubject` is defined, if not already covered by Bean Validation
upstream. **Severity if it turns out exploitable: Low** (admin-only actor,
attacking their own outbound mail, not a guest-facing vector).

## 6. Backend — reflected input in error responses

Reviewed `common-web-lib/src/main/java/com/hotelpms/commonweb/exception/AbstractProblemDetailAdvice.java`,
the shared `@ExceptionHandler` base every service's `GlobalExceptionHandler`
extends:

- `MethodArgumentNotValidException` handler (line 92) — returns
  `fe.getDefaultMessage()` per field, i.e. the **validation constraint
  message** (developer-authored strings like `"must not be blank"`), never
  the raw submitted value. No reflection of attacker input.
- `MethodArgumentTypeMismatchException` handler (line 191) — returns the
  **parameter name** (`ex.getName()`), not its value.
- `FeignException` handler (line 138) — returns `ex.getMessage()` from an
  internal downstream call, not directly attacker-controlled from the
  browser's perspective (the caller is another internal service, itself
  behind Bean Validation).
- Generic 500 handler (line 264) — returns a fixed `"INTERNAL_SERVER_ERROR"`
  string; the actual exception message is only logged server-side
  (`LOG.error`), never serialized to the client.

**No endpoint found that echoes raw, attacker-controlled input back into an
HTML-interpretable response.** All responses are `ProblemDetail` /
`application/problem+json` (or plain `application/json` for normal payloads)
— never `text/html`.

**Content-Type sniffing**: `X-Content-Type-Options: nosniff` is enforced at
two layers — `api-gateway/src/main/java/com/hotelpms/gateway/filter/SecurityHeadersFilter.java`
(applies to every proxied backend response) and independently at
`frontend/nginx.conf` (lines 38, 78, 92, 111, applies to the SPA's own
static/proxy responses). Even a hypothetical JSON response that somehow
contained HTML-like content could not be sniffed as `text/html` by a
compliant browser.

## 7. CSP — defense in depth

`frontend/nginx.conf:34-36` (and repeated per-location at lines 75-77, 89-91,
108-109 — see the file's own comment on nginx's `add_header` inheritance
gotcha, already documented there):

```
Content-Security-Policy:
  default-src 'self' blob:; script-src 'self'; style-src 'self' 'unsafe-inline';
  img-src 'self' data:; font-src 'self'; connect-src 'self';
  frame-ancestors 'self' | 'none' (location-dependent); form-action 'self'; base-uri 'self'
```

`script-src 'self'` (no `'unsafe-inline'`, no `'unsafe-eval'`, no external
hosts) is a real, meaningful mitigating control: even if a JSX-escaping
bypass were somehow found in the future, this CSP would block execution of
any inline `<script>` or `onerror=`/`onclick=` handler injected via HTML
attributes, and blocks loading attacker-hosted script from a third-party
origin. `style-src` allows `'unsafe-inline'` (required for TailwindCSS
runtime utility injection, per the file's own comment) — this is a narrower
residual risk (CSS-based data exfiltration via attribute selectors is a real
but much less severe class than script XSS) and does not affect the findings
above since no injection point was found to combine it with.

Noted as **defense-in-depth**, not as a compensating control for a known gap
— per §1-§6 above there is no active injection point for it to mitigate in
this codebase today.

---

## Summary

| # | Location | Finding | Severity | Status |
|---|----------|---------|----------|--------|
| 1 | `frontend/src` (whole tree) | `dangerouslySetInnerHTML`/`innerHTML`/`outerHTML`/`eval`/`new Function` — none present, ESLint-enforced ban (`frontend/eslint.config.js:26-41`) | — | No finding (control verified) |
| 2 | All `templates/pdf/*.html` (billing-service, frontdesk-service) | `th:utext` usage — none found; all user-controllable fields use `th:text` | — | No finding (control verified) |
| 3 | All `templates/email/*.html` (notification-service) | `th:utext` usage — none found; `guestName`, `hotelName`, `greetingText` all use `th:text` | — | No finding (control verified) |
| 4 | Backend `AbstractProblemDetailAdvice` (common-web-lib) | Error responses never echo raw attacker input; always `application/problem+json`; `nosniff` enforced at gateway + nginx | — | No finding (control verified) |
| 5 | `frontend/src/pages/HotelProfile.tsx:217` + `HotelSettingsRequest.java:54` (`logoUrl`) | No URL-shape validation on `logoUrl` (client or server); currently only reaches an `<img src>`, where `javascript:` doesn't execute | Informational | Hardening gap, not exploitable today — add scheme allow-list if the field is ever reused in `<a href>`/iframe/CSS context |
| 6 | `notification-service/.../NotificationServiceImpl.java:132,134` (`hotelName`, `customSubject` in mail headers) | Admin-configurable values placed into MIME `Subject`/display-name without explicit CRLF rejection — header-injection class, not XSS, unproven exploitability against `jakarta.mail`'s encoding | Low | Adjacent observation, out of XSS scope — recommend `@Pattern` guard as a follow-up, not urgent |

**Overall verdict: no exploitable XSS found**, in either the browser
(frontend) or the server-side HTML-generation paths (PDF/email). The
"zero `dangerouslySetInnerHTML`" and "zero `th:utext`" claims from
`00-recon.md` are both re-confirmed independently. The two items in the
table above are hardening/defense-in-depth notes, not active vulnerabilities.
