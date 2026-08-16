# Broken Access Control Audit (OWASP A01) — hotel-pms

Read-only audit. No source file modified. Scope: every REST controller in
auth-service, guest-service, frontdesk-service, billing-service, fb-service,
notification-service, plus the gateway's `AuthenticationFilter`. Built on top
of `security-audit/00-recon.md`, verified independently against source — every
recon claim below was checked against the actual code, not taken at face
value; discrepancies are called out explicitly.

---

## 1. NEW — CRITICAL — Public self-registration allows arbitrary role and hotelId (vertical privilege escalation)

**Files**:
- `auth-service/src/main/java/com/hotelpms/auth/controller/AuthController.java:71-84` (`POST /register`)
- `auth-service/src/main/java/com/hotelpms/auth/dto/RegisterRequest.java:20-28`
- `auth-service/src/main/java/com/hotelpms/auth/mapper/UserAccountMapper.java:23-24`
- `auth-service/src/main/java/com/hotelpms/auth/service/AuthServiceImpl.java:57-83`
- `config-service/src/main/resources/config/api-gateway.yml:42-52` (route `auth-service`, no `AuthenticationFilter`)

**What's wrong**: `RegisterRequest` is `(username, password, email, role, hotelId)` — the
client supplies `role` (a `Role` enum: `ADMIN`, `OWNER`, `RECEPTIONIST`, `GUEST`) and
`hotelId` directly. `AuthServiceImpl.register()` maps the request straight to a
`UserAccount` via `UserAccountMapper.toEntity()` (only `passwordHash` is
`@Mapping(ignore = true)` — `role` and `hotelId` pass through unmodified) and persists it
with **no server-side override, no role restriction, and no hotelId existence
check**. `POST /api/v1/auth/register` sits on the `auth-service` gateway route
(`Path=/api/v1/auth/**`), which carries **no `AuthenticationFilter`** — only a
5 req/replenish per-IP rate limiter. This matches recon's claim that
`/api/v1/auth/**` is public, but recon did not flag the mass-assignment
implication of that combined with `RegisterRequest`'s fields.

**Exploit scenario**: An unauthenticated attacker, with no prior credentials,
sends:

```json
POST /api/v1/auth/register
{
  "username": "attacker",
  "password": "SomeStr0ng!!Pass1234",
  "email": "attacker@evil.example",
  "role": "ADMIN",
  "hotelId": "<any existing hotel's UUID>"
}
```

The response sets valid httpOnly `jwt`/`refreshToken` cookies for a brand-new
**ADMIN** account scoped to that `hotelId`. `hotelId` values are UUIDs
referenced throughout the API (invoice IDs, guest IDs, room IDs, etc. are all
returned in JSON bodies to any authenticated user of that hotel) and are not
treated as secret — an attacker with even one low-privilege account (or one
leaked UUID from a screenshot, URL, support ticket, etc.) can target a specific
hotel. Once registered as ADMIN for hotel X, the attacker has full access to
that hotel's data via every `@PreAuthorize("hasAnyRole('ADMIN','OWNER')")`
endpoint in the platform: guest PII export (`GET /guests/{id}/export`),
FatturaPA XML generation, invoice `document-type`/`sdi-status` mutation,
Alloggiati (Polizia di Stato) report submission, and critically
**`UserManagementController`** — the attacker can deactivate the hotel's real
admin/owner accounts (`PATCH /auth/users/{userId}/deactivate`) and lock out
the legitimate tenant entirely, or create further users. If `hotelId` is
instead a fresh random UUID never used before, the attacker silently creates
an unvetted phantom tenant with no operator awareness (resource/DB pollution,
and a way to test payloads against production infrastructure without ever
touching a real hotel's boundary — useful as a reconnaissance foothold).

This is unrelated to and not mitigated by any existing threat-model entry:
T-AUTH-01 through T-AUTH-10 cover login enumeration, brute force, password
hashing, refresh-token revocation, `mustChangePassword` bypass, reactivation,
`/me` refresh-retry, voluntary password-change logout, and `AccessDeniedException`
mapping — none touch `/register`'s field-level trust boundary.

**Severity**: **CRITICAL** — unauthenticated, direct vertical privilege
escalation to ADMIN on an arbitrary (or guessed) tenant, no precondition
beyond network reachability of the gateway.

**Remediation**:
- Remove `role` and `hotelId` from `RegisterRequest` entirely, or ignore them
  server-side the same way `passwordHash` is ignored in
  `UserAccountMapper.toEntity()`.
- Decide what `/register` is actually for: if it's meant only for
  first-admin-of-a-new-hotel bootstrap, gate it behind an explicit
  hotel-provisioning flow (e.g. an invite token, or an operator-only endpoint)
  rather than a public, unauthenticated POST. If ordinary staff accounts
  should never self-register (which `UserManagementController`'s existence
  strongly implies — it's the intended way an ADMIN/OWNER creates a user), the
  simplest fix is to delete public self-registration entirely and route all
  account creation through `UserManagementController`, forcing
  `role=RECEPTIONIST` (or similar low-privilege default) and deriving
  `hotelId` from context if any self-serve path is kept.
- Regardless of the design decision, `hotelId` must never be attacker-supplied
  on a public endpoint without a corresponding proof of authorization to act
  within that tenant.

---

## 2. NEW — MEDIUM — `QuotationRepository` not covered by the `TenantIsolationArchTest` regression guard

**Files**:
- `frontdesk-service/src/test/java/com/hotelpms/frontdesk/architecture/TenantIsolationArchTest.java:52-57`
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/quotations/repository/QuotationRepository.java`

**What's wrong**: `Quotation` carries its own `hotel_id` (confirmed —
`QuotationServiceImpl` builds it with `.hotelId(hotelId)` from
`resolveHotelId()` everywhere), making `QuotationRepository` a tenant-root
repository by the same definition the ArchUnit rule's own Javadoc uses. It
currently *is* correctly scoped (`findByIdAndHotelId`, `findAllByHotelId`,
and every call site in `QuotationServiceImpl` uses
`findByIdAndHotelOrThrow(id, hotelId)` — verified line-by-line, no unscoped
`findById` anywhere in that service). But `QuotationRepository` is **not**
listed in `TENANT_ROOT_REPOSITORIES` for frontdesk-service, unlike
`RoomTypeRepository`, `ReservationRepository`, `RoomRepository`,
`StayRepository`, `HotelSettingsRepository`. This means a future change that
adds an unscoped custom query method to `QuotationRepository` (the exact bug
class T-BILL-04/T-STAY-06/T-ROOM-02 were about) would **not** be caught by
the architecture test — it's a live regression gap in the project's own
stated defense mechanism, not a currently-exploitable IDOR.

**Severity**: MEDIUM (no active vulnerability today; missing regression
protection on a financially-adjacent, cross-hotel-sensitive entity).

**Remediation**: add
`"com.hotelpms.frontdesk.quotations.repository.QuotationRepository"` to
`TENANT_ROOT_REPOSITORIES` in `TenantIsolationArchTest`.

---

## 3. NEW — LOW/MEDIUM — `GuestController.removeIdentityDocument` has no `@PreAuthorize`, unlike the rest of the GDPR-sensitive surface fixed under T-GST-07

**Files**:
- `guest-service/src/main/java/com/hotelpms/guest/controller/GuestController.java:155-161`
- `guest-service/src/main/java/com/hotelpms/guest/service/impl/GuestServiceImpl.java:350-366`

**What's wrong**: `DELETE /api/v1/guests/{id}/documents/{documentId}` carries
no `@PreAuthorize`, so any operational role (RECEPTIONIST included) can
permanently delete a guest's identity-document record (passport/ID card
number, used for Alloggiati/Polizia di Stato reporting and legal retention).
Contrast with `deleteGuest` and `exportGuestData` on the same controller,
which are `@PreAuthorize("hasAnyRole('ADMIN','OWNER')")` — that restriction
was added specifically under **T-GST-07** ("`GuestController`
(delete/anonimizzazione, export Art. 20) ... senza alcuna `@PreAuthorize`"),
but `removeIdentityDocument` was not included in that fix and is not
mentioned by T-GST-07's description or any other THREAT_MODEL.md entry.

Note on recon: `00-recon.md` describes this endpoint as "candidato T-GST-08,
già segnalato in sessione precedente come fuori scope, mai fixato" — I
searched `THREAT_MODEL.md` for `T-GST-08` and found **no such entry exists**
(the threat model jumps from T-GST-07 to T-GST-01..07 plus a T-GST-06 in the
impact matrix; there is no T-GST-08 anywhere in the file). The recon note
appears to be describing an ID that was never actually assigned/tracked —
per the task instructions to verify rather than trust recon claims, I'm
reporting this as a live, currently-untracked gap rather than citing it as
already covered.

**IDOR check** (separate from the role-restriction question): the underlying
service call is tenant/ownership-safe. `GuestServiceImpl.removeIdentityDocument`
resolves the guest via `resolveGuest(guestId, hotelId)` (hotel-scoped) before
touching the document, and even though `identityDocumentRepository.findById(documentId)`
is an inherited, unscoped lookup, the subsequent
`document.getGuest().getId().equals(guestId)` check (line 359-361) rejects any
document that doesn't belong to the already-hotel-verified guest. So this is
**not** a cross-tenant IDOR — a RECEPTIONIST cannot delete another hotel's
document by guessing `documentId`. The gap is purely the missing role
restriction.

**Exploit scenario**: A RECEPTIONIST (a role explicitly described in
`Role.java` as unable to "manage room types, delete rooms, or view financial
reports", i.e. a deliberately lower-trust operational role) can delete any
identity document belonging to any guest of their own hotel, including one
already used in a submitted Alloggiati report, with no confirmation, audit
requirement, or admin oversight — potentially destroying a legally-required
record or covering up a data-entry/fraud issue.

**Severity**: LOW/MEDIUM — same class of issue as the already-fixed T-GST-07,
narrower blast radius (single document, not full guest erasure/export), no
cross-tenant component.

**Remediation**: add `@PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")` to
`removeIdentityDocument`, consistent with `deleteGuest`/`exportGuestData` on
the same controller. If there's a legal-hold concern (document referenced by
an already-submitted Alloggiati report), consider the same kind of guard used
elsewhere in the codebase (cf. `InvoiceServiceImpl.assertNotFiscallyLocked`)
before allowing removal.

---

## 4. Endpoints recon flagged as "Gateway — no `@PreAuthorize`" — verified against service-layer hotel scoping

All four endpoints recon called out were checked against their service-layer
implementation, not just the controller annotation. All four are
**correctly tenant-scoped** and match the project's documented
"operational-role-only" pattern (any RECEPTIONIST/ADMIN/OWNER via the
gateway's `OPERATIONAL_ROLES` gate; no local `@PreAuthorize` needed because
the operation is a normal front-desk/service duty, not an admin-only one).
None of these are IDOR or privilege-escalation gaps.

| Endpoint | Service-layer scoping | Verdict |
|---|---|---|
| `POST /api/v1/invoices/stay` (`InvoiceController.createInvoiceForStay`) | `InvoiceServiceImpl.createInvoiceForStay` derives `hotelId` from `resolveHotelId()` (SecurityContext, never the body) and uses `findByStayIdAndHotelId` for the duplicate-check | Deliberate pattern — fine |
| `POST /api/v1/invoices/stay/{stayId}/charges` (`InvoiceController.addCharge`) | `InvoiceServiceImpl.addCharge` looks up the invoice via `findByStayIdAndHotelId(stayId, hotelId)`; 404 if the stay belongs to another hotel | Deliberate pattern — fine |
| `POST /api/v1/invoices/{invoiceId}/payments` (`PaymentController.addPayment`) | `PaymentServiceImpl.addPayment` looks up via `findByIdAndHotelId(invoiceId, hotelId)`; also independently re-checks `assertNotFiscallyLocked`-equivalent guard (T-BILL-06 round 2) | Deliberate pattern — fine |
| `POST /api/v1/fb/orders/{id}/confirm` (`RestaurantOrderController.confirmOrder`) | `RestaurantOrderServiceImpl.confirmOrder` looks up via `findByIdAndHotelId(orderId, hotelId)`; re-verifies stay is still `CHECKED_IN` at confirm time (race-condition fix already documented in code comments) | Deliberate pattern — fine |

These four match the same "operational-role-only" rationale documented for
`ReservationController`/`QuotationController` in `backup/SUMMARY.md`
(2026-08-09 21:10) — adding a payment or confirming an F&B order at checkout
is routine RECEPTIONIST activity, not something that should require
ADMIN/OWNER. No new finding here.

---

## 5. `GuestController.removeIdentityDocument` IDOR analysis — see finding #3 above (role gap, not IDOR)

Already covered in full above — repeating only to make clear it was checked
per the task's explicit list.

---

## 6. Admin-only functionality reachable by a normal authenticated user — none found

Checked every controller for an admin/owner-only capability reachable without
the corresponding role gate:

- **User management** (`UserManagementController`): all 5 endpoints
  `@PreAuthorize("hasAnyRole('ADMIN','OWNER')")`, plus gateway
  `USERS_PATH_PREFIX` always-restricted regardless of method — double-gated.
- **Hotel settings** (`HotelSettingsController.update`,
  `GuestPrivacySettingsController` class-level `@PreAuthorize`): both
  ADMIN/OWNER-only, confirmed in source.
- **Financial reports** (`OwnerReportController`): `@PreAuthorize("hasAnyRole('OWNER','ADMIN')")`
  **and** gateway `FULLY_RESTRICTED_PREFIXES = {"/api/v1/reports"}` blocks
  RECEPTIONIST even on GET — double-gated, matches T-BILL-04's fix.
- **Fiscal operations** (`InvoiceController` document-type/fatturaPA/sdi-status/export):
  all ADMIN/OWNER, matches T-BILL-07.
- **Alloggiati (Polizia di Stato) reports** (`StayController` reports/*):
  all ADMIN/OWNER, though note the gateway's `FULLY_RESTRICTED_PREFIXES`
  (`/api/v1/reports`) does **not** cover `/api/v1/stays/reports/**` (different
  path) — this is fine because the controller-level `@PreAuthorize` is the
  actual enforcement point here, so it's not a gap, just worth noting the
  gateway-prefix protection is not doing double duty for this path the way it
  does for `OwnerReportController`.
- **Room/room-type structural writes**: ADMIN/OWNER, plus gateway
  `ROOM_ADMIN_METHODS`/`WRITE_RESTRICTED_PREFIXES` double-gate consistently.

No admin-only capability was found reachable by a lower-privileged role
through a missing gate.

---

## 7. Gateway RBAC (`AuthenticationFilter.java`) — consistency check

Read in full (`api-gateway/src/main/java/com/hotelpms/gateway/filter/AuthenticationFilter.java`).
`isAccessAllowed()` logic:
- Non-`OPERATIONAL_ROLES` (i.e. `GUEST`, or a `null`/unrecognized role) →
  always denied. Correct — `GUEST` role currently has zero API access as
  documented in `Role.java`.
- `ADMIN`/`OWNER` → always allowed (further gated per-endpoint by
  `@PreAuthorize` downstream where relevant).
- `RECEPTIONIST` → denied on `/api/v1/auth/users*`, denied on
  POST/PUT/DELETE `/api/v1/rooms*`, denied on any `FULLY_RESTRICTED_PREFIXES`
  path, denied on non-GET `WRITE_RESTRICTED_PREFIXES` paths. Cross-checked
  against every controller's own `@PreAuthorize` — no path found where the
  gateway allows RECEPTIONIST but the controller/service still assumes
  ADMIN/OWNER-only trust without its own check (i.e. no reliance-mismatch).
- `X-Auth-Hotel`/`X-Auth-User`/`X-Auth-Role` are stripped from the inbound
  request before being re-set (lines 192-198) — a client cannot inject these
  headers directly; confirmed no path bypasses this strip-then-set sequence.
- HMAC signing includes `hotelId` in the signed payload (T-GW-07) with
  timestamp+nonce anti-replay (T-GW-08) — downstream `InternalAuthFilter`
  trusts the headers only because of this signature, not because of network
  topology alone. Not re-verified byte-for-byte against `InternalAuthFilter`
  in this pass (out of this task's scope — recon already covers this as the
  third RBAC layer), but the gateway side of the contract is sound.

No new finding here — matches recon's description.

---

## 8. Tenant-isolation (IDOR) spot-check across all five `TenantIsolationArchTest` instances

Read all five (auth-service has none — no tenant-root repository there,
correct since `UserAccount` rows belong to exactly one hotel by design and
`UserManagementController` always resolves via `X-Auth-Hotel` + explicit
`hotelId` filters in `UserManagementService`, not spot-checked line-by-line
in this pass but consistent with the header-injection guarantee). Findings:

- **guest-service, billing-service, fb-service**: `TENANT_ROOT_REPOSITORIES`
  sets look complete against their respective repository directories — no
  omission found.
- **frontdesk-service**: omits `QuotationRepository` — see finding #2.
- Grepped every service's `src/main` for `.findById(` (the inherited,
  unscoped `JpaRepository` method the rule's Javadoc explicitly calls out as
  a blind spot) to check for a call-site bypass the ArchUnit rule structurally
  cannot catch:
  - `guest-service GuestPrivacySettingsServiceImpl:45` /
    `frontdesk-service HotelSettingsServiceImpl:34,43` /
    `AlloggiatiWebSenderServiceImpl:141` — all call `findById(hotelId)`, where
    `hotelId` **is the primary key** of a one-row-per-hotel settings table —
    inherently tenant-scoped by construction, not a gap.
  - `frontdesk-service AlloggiatiLookupServiceImpl:76,85,94` — `findById` on
    stato/comune/tipdoc reference tables (Italian province/municipality/
    document-type lookup codes) — global reference data, not hotel-scoped by
    design, not a gap.
  - `guest-service GuestServiceImpl:356` (`identityDocumentRepository.findById(documentId)`)
    — see finding #3; safe because of the subsequent ownership check, but
    it's the one call site worth flagging since it's on a genuinely
    hotel-adjacent entity accessed via an inherited method.
  - `guest-service GuestRetentionJobServiceImpl:89` — batch job, not
    reachable via any REST endpoint, out of this audit's scope (no
    controller involved).
  - No other unscoped `.findById(` call found in any service's `src/main`
    against a tenant-root entity reachable from a controller.

---

## Summary (severity-sorted)

| # | Finding | File:Line | Severity |
|---|---|---|---|
| 1 | Public `/register` accepts client-supplied `role` + `hotelId` — unauthenticated vertical privilege escalation to ADMIN on any tenant | `auth-service/.../AuthController.java:71`, `RegisterRequest.java:20`, `UserAccountMapper.java:23`, `AuthServiceImpl.java:59` | **CRITICAL** |
| 2 | `QuotationRepository` missing from `TenantIsolationArchTest`'s tenant-root set — no regression guard against a future unscoped query (currently correctly scoped in code) | `frontdesk-service/.../TenantIsolationArchTest.java:52` | MEDIUM |
| 3 | `GuestController.removeIdentityDocument` has no `@PreAuthorize` — any RECEPTIONIST can delete a guest's identity document (not an IDOR — ownership-checked; role-gate gap only, same class as fixed T-GST-07) | `guest-service/.../GuestController.java:155` | LOW/MEDIUM |
| 4 | billing-service `POST /stay`, `POST /stay/{stayId}/charges`, `PaymentController.addPayment`; fb-service `POST /orders/{id}/confirm` — no `@PreAuthorize` | (see §4 table) | Not a finding — verified deliberate operational-role-only pattern, correctly hotel-scoped |
| 5 | Admin-only functionality reachable by lower role | — | Not found — none |
| 6 | Gateway RBAC vs. per-controller `@PreAuthorize` consistency | `AuthenticationFilter.java` | Not a finding — consistent |

Every finding above was cross-checked against `THREAT_MODEL.md` by name/ID
search before being reported; none duplicate an already-tracked, already-fixed
entry. Finding #1 in particular is new and not mitigated by any existing
T-AUTH-* entry.
