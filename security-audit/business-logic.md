# Business Logic Abuse Audit — hotel-pms

Read-only audit. No source file modified. Scope: double-booking, double-payment/financial
races, price/quantity manipulation, and state-machine/workflow-skip gaps across
`frontdesk-service` (reservations, quotations, rate calendar, stays), `billing-service`
(invoices, payments), and `fb-service` (restaurant orders). Builds on
`security-audit/00-recon.md`, which notes several race-condition/business-logic bugs were
already found and fixed in prior sessions (invoice numbering, double-payment, room
housekeeping-status TOCTOU) and flags rate-season bulk-apply and quotation conversion as
not yet checked. Every claim below was verified directly against current source, not taken
on recon's word.

---

## 1. NEW — CRITICAL — Check-in accepts a client-controlled `status`, letting any operational
role fabricate an already-`CHECKED_OUT` stay and skip the `BILLING_NOT_PAID` guard entirely

**Files**:
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/stays/dto/StayRequest.java:37`
  (`@NotNull StayStatus status` — client-supplied, no `@AssertTrue`/allow-list restricting it)
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/stays/mapper/StayMapper.java:31-45`
  (`toEntity` maps every field it doesn't explicitly `@Mapping(..., ignore = true)` —
  `status` is **not** in the ignore list, unlike `invoiceId`, `active`, `alloggiatiSent`, etc.)
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/stays/service/impl/StayServiceImpl.java:78-128`
  (`checkIn`) and `:133-170` (`checkOut`, holding the guard that gets bypassed)
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/stays/controller/StayController.java:68-83`
  (`POST /api/v1/stays` — no `@PreAuthorize`, reachable by any gateway `OPERATIONAL_ROLE`:
  RECEPTIONIST/ADMIN/OWNER, per recon §3)

**What's wrong**: `StayServiceImpl.checkIn` only forces the new stay to `CHECKED_IN` when the
request's status is `null` or `EXPECTED`:

```java
if (newStay.getStatus() == null || newStay.getStatus() == StayStatus.EXPECTED) {
    newStay.setStatus(StayStatus.CHECKED_IN);
}
```

Any other client-supplied `StayStatus` — in particular `CHECKED_OUT` — passes through
untouched, because `StayMapper.toEntity` maps `status` straight from the DTO and nothing in
`StayCheckInValidator` (guest/reservation/room checks only) or `StayRequest`'s Bean
Validation constrains which status values are acceptable at check-in. The `checkOut()`
method — the only place `BillingNotPaidException`/`resolveInvoiceForCheckOut` is enforced —
is a completely separate code path (`PUT /stays/{id}/check-out`) that a stay created this
way never needs to go through, since it is inserted already in the terminal status.

**Exploit scenario**: RECEPTIONIST X sends `POST /api/v1/stays` with
`{ "reservationId": <valid CONFIRMED reservation>, "guestId": ..., "roomId": ...,
"status": "CHECKED_OUT", ... }`. The saga proceeds exactly as a normal check-in — guest/
reservation/room validated, `Stay` row persisted (now with `status = CHECKED_OUT` from the
start), `markRoomOccupied` unconditionally flips the room to `OCCUPIED` (saga step 3 has no
status guard), and `stayBillingCoordinator.openInvoiceForStay` opens an invoice and posts the
room-night charge — but that invoice is never checked against `BILLING_NOT_PAID` because the
`checkOut()` method that owns that check is never invoked for this stay. The result: a stay
that the system considers already checked out and settled, with no payment ever taken and no
occupancy record indicating a guest is (or was) actually in the room.

Secondary damage, worse than the billing bypass alone: because `checkOut()` never ran, the
room is **never** transitioned to `DIRTY` (that only happens inside `checkOut()`), yet it is
already `OCCUPIED`. `RoomServiceImpl.updateRoom`/`updateHousekeepingStatus` both explicitly
refuse to change status away from `OCCUPIED` ("`ROOM_OCCUPIED_CLEARED_BY_CHECKOUT_SAGA_ONLY`"),
and the only path that clears `OCCUPIED` is `checkOut()` acting on a `CHECKED_IN` stay for
that room — which no longer exists (this stay is already `CHECKED_OUT`). The room is
permanently stuck `OCCUPIED` with no supported recovery path short of a direct DB fix — an
availability/DoS side effect on top of the billing-skip.

Also note the parent reservation is left inconsistent: `stayReservationSync
.updateReservationStatusAfterCheckOut` is only called from the real `checkOut()`, so the
reservation stays `CONFIRMED`/`PARTIALLY_CHECKED_IN` forever while its stay shows
`CHECKED_OUT`.

**Severity**: CRITICAL — any authenticated operational-role user (no elevated role required;
the endpoint carries no `@PreAuthorize`) can both bypass the "checkout requires paid invoice"
control and permanently soft-lock a room, with a single crafted request.

**Remediation**:
- Remove `status` from `StayRequest` entirely (or make it write-only for `EXPECTED`, rejected
  otherwise via `@AssertTrue`) — check-in should always compute the resulting status
  server-side from `reservationId`/room-count, exactly as `updateStatusOnCheckIn` in
  `ReservationServiceImpl` already does on the reservation side. Do not accept a terminal
  status (`CHECKED_OUT`, `CANCELLED`) from the client on a creation endpoint.
- If some legitimate caller needs to seed historical/imported stays as already checked out,
  build a dedicated, `@PreAuthorize`-guarded admin import endpoint that runs the same
  `BILLING_NOT_PAID`-equivalent checks (or explicitly documents why they're skipped for
  back-dated data), rather than overloading the regular check-in endpoint.
- Add a DB-level `CHECK` constraint or trigger that only allows a new `stays` row to be
  inserted with `status IN ('EXPECTED','CHECKED_IN')`, as defense in depth against future
  application-layer regressions of this kind.

---

## 2. NEW — HIGH — Double-booking: reservation creation has a TOCTOU gap for rooms with no
prior overlapping reservation (no DB-level exclusion/unique constraint backs the check)

**Files**:
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/reservations/service/impl/ReservationServiceImpl.java:76-99`
  (`createReservation`), `:463-478` (`verifyRoomsAvailability` — plain read, no lock),
  `:625-647` (`verifyNoOverlappingReservations`)
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/reservations/repository/ReservationRepository.java:54-106`
  (`findOverlappingReservations`/`findOverlappingReservationsForNew` — both
  `@Lock(LockModeType.PESSIMISTIC_WRITE)`, but on a `SELECT` that can match **zero rows**)
- `frontdesk-service/src/main/resources/db/migration/V1__frontdesk_baseline.sql:79-142`
  (`reservations`/`reservation_line_items` — only a PK, FKs, and a `CHECK` on the date
  range; **no** `EXCLUDE USING gist` or unique constraint on `(room_id, daterange)`, unlike
  `rate_seasons` in `V9__add_rate_seasons.sql:42-48`, which has exactly that)

**What's wrong**: `createReservation` calls `verifyRoomsAvailability` (a plain
`findByIdAndActiveTrueAndHotelId` read of the `Room`, no row lock) and then
`verifyNoOverlappingReservations`, which runs `findOverlappingReservationsForNew` — a query
correctly annotated `@Lock(LockModeType.PESSIMISTIC_WRITE)`. That lock, however, only takes
effect on rows the `SELECT` actually returns. `PESSIMISTIC_WRITE` on a query that matches
**no existing rows** locks nothing, because there is no row to lock — it cannot serialize
against another transaction's concurrent, equally-empty read. This is the textbook gap that
V9's `EXCLUDE USING gist` constraint was built to close for `rate_seasons`; the equivalent
constraint was never added for `reservations`/`reservation_line_items`.

**Exploit/concurrency scenario**: Room R has no reservation at all for 10–15 March (the
common case — most room/date combinations are unbooked). Two concurrent requests, from
receptionist X and receptionist Y (or the same user double-clicking / a scripted race), both
call `POST /api/v1/reservations` for Room R, 10–15 March:

1. TX1 and TX2 both call `verifyRoomsAvailability` — both see Room R as active. No lock taken.
2. TX1 and TX2 both call `findOverlappingReservationsForNew(roomIds=[R], 10 Mar, 15 Mar)`.
   Since no reservation for R exists yet, **both** queries return an empty list — the
   `PESSIMISTIC_WRITE` clause has nothing to lock, so neither transaction blocks the other.
3. Both transactions proceed to build and `save()` a new `Reservation` + `ReservationLineItem`
   for Room R, 10–15 March. Both `INSERT`s succeed — there is no unique/exclusion constraint
   to reject the second one at the database level.

Result: Room R is now double-booked for the same dates, silently. The read-side availability
endpoint (`GET /rooms/availability`, `getAvailableRooms`) will keep showing the room as
booked for one of the two reservations depending on query timing, but both reservations
persist, both can be checked in (racing again at check-in, potentially assigning the same
physical room to two guests), and downstream billing will create two separate invoices for
the same room-nights.

**Severity**: HIGH — this is the primary "sell the same room twice" business-logic risk the
recon flagged as unverified, and it is confirmed exploitable by any two authenticated
operational-role users (or a single user submitting two near-simultaneous requests, e.g. via
a slow network + double-click, or deliberately scripted).

**Remediation** (mirror the `rate_seasons` fix, V9):
- Add a Postgres `EXCLUDE USING gist` constraint on `reservation_line_items` (or a
  materialized `room_id + daterange(check_in, check_out)` derived column) scoped to active,
  non-cancelled reservations — the same `btree_gist` extension is already enabled by V9.
  This is the only fix that actually closes the race, because it enforces uniqueness at
  `INSERT` time regardless of what any prior `SELECT` observed.
- Keep the existing `PESSIMISTIC_WRITE` overlap query as a fast-path check that gives a clean
  `400 ROOM_UNAVAILABLE_DATES` in the common (non-racing) case; catch the new constraint's
  `DataIntegrityViolationException`/SQLSTATE `23P01` the same way
  `RateCalendarServiceImpl.saveTranslatingOverlap` does, and translate it to a `409` instead
  of a raw `500` for the rare case where two requests race past the check.

---

## 3. NEW — HIGH — Quotation conversion race: no lock/version on `Quotation` lets two
concurrent `convert` calls both pass the status guard and both create a reservation

**Files**:
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/quotations/service/impl/QuotationServiceImpl.java:280-315`
  (`convertToReservation`)
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/quotations/domain/Quotation.java`
  (no `@Version` field, unlike `Reservation` — recon §3/DB comment: "JPA optimistic-lock
  counter" only exists on `reservations.version`, not `quotations`)
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/quotations/repository/QuotationRepository.java:34`
  (`findByIdAndHotelId` — plain `Optional<Quotation>`, no `@Lock`)

**What's wrong**: `convertToReservation` reads the quotation via a plain, unlocked
`findByIdAndHotelOrThrow`, checks `status != DECLINED/ACCEPTED` and `!isExpired()`, then —
only at the very end — calls `quotation.setStatus(ACCEPTED)` and
`quotationRepository.save(quotation)`. Nothing prevents a second `convert` call, racing in
before the first commits, from reading the same `DRAFT`/`SENT` status and passing the same
guard. `Quotation` has no `@Version` column, so even the “last write wins” safety net that
protects `Invoice`/`Reservation` mutations elsewhere in this codebase (see confirmed-fixed
items below) is entirely absent here.

**Concurrency scenario**: A quotation is `SENT` with one option (Rooms R1+R2, 1–5 April). Two
staff members (or the guest confirming by phone while a staff member is also processing an
email reply) both trigger `POST /quotations/{id}/convert` at nearly the same time:

1. Both TX1 and TX2 load the quotation, see `status = SENT`, `isExpired() = false` — both
   pass every guard.
2. Both resolve the same chosen option (single option, no `optionId` ambiguity) and both call
   `reservationService.createReservationFromPricedRooms(guestId, ..., roomPrices)` for
   Rooms R1+R2, 1–5 April.
3. Because of Finding #2 above, if this is the first reservation ever made for R1/R2 in that
   window, **both** calls can succeed — two separate `Reservation` rows are created for the
   same rooms/dates from a single quotation. Even if Finding #2 were fixed, one of the two
   `createReservationFromPricedRooms` calls would fail with a `400`/`409` at the reservation
   layer — but only after a wasted `guestClient.createGuest` call (for prospect quotations)
   may have already fired twice, and the failure is confusing for staff since the quotation
   itself still shows `ACCEPTED` from whichever `save()` landed last (silent overwrite, no
   version check to signal the race occurred).

**Severity**: HIGH — combined with Finding #2 this is a second, independent path to the same
double-booking outcome, and even standing alone it is a real quotation-integrity gap (a
quotation could show `ACCEPTED` while, depending on timing, zero, one, or two reservations
actually exist for it).

**Remediation**:
- Add `@Version` to `Quotation`, matching `Reservation`/`Invoice`, so the second concurrent
  `save()` throws `ObjectOptimisticLockingFailureException` → `409` via the existing
  `GlobalExceptionHandler` mapping (already wired in `frontdesk-service`, see confirmed-fixed
  items).
- Stronger fix: take a `@Lock(LockModeType.PESSIMISTIC_WRITE)` read of the quotation at the
  start of `convertToReservation` (mirroring the pattern already used for reservation overlap
  checks), so the second concurrent caller blocks until the first transaction commits and then
  observes `status = ACCEPTED` and fails cleanly with `QUOTATION_ALREADY_ACCEPTED` — this also
  avoids the wasted double `guestClient.createGuest` call for prospect conversions.

---

## 4. NEW — MEDIUM — Rate-calendar bulk-apply: the new-season insert is race-safe (EXCLUDE
constraint), but the split/trim `UPDATE` on pre-existing overlapping seasons is not

**Files**:
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/pricing/service/impl/RateCalendarServiceImpl.java:122-153`
  (`bulkApply`), `:174-206` (`applySplitTrim`/`splitAroundNewRange` — plain
  `rateSeasonRepository.save(existing)`, not wrapped in `saveTranslatingOverlap`), `:216-225`
  (`saveTranslatingOverlap` — only guards the **new** season's `saveAndFlush`)
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/pricing/domain/RateSeason.java`
  (no `@Version` field)

**What's wrong**: `bulkApply`'s final `INSERT` of the newly-applied season is correctly
protected — `saveTranslatingOverlap` catches the `excl_rate_seasons_no_overlap` GiST
exclusion violation (SQLSTATE `23P01`) and turns it into a clean `409 RATE_SEASON_OVERLAP`.
But before that insert, `applySplitTrim` reads every **pre-existing** season that overlaps the
requested range (`findOverlapping`, a plain `SELECT`, no lock) and trims/splits/deletes each
one via ordinary `rateSeasonRepository.save(existing)` calls, entirely outside that try/catch
and with no `@Version` on `RateSeason` to detect a lost update.

**Concurrency scenario**: An existing season S covers the whole of January for Room Type T.
Two admins concurrently bulk-apply to Room Type T:

1. Admin A applies 5–10 Jan. TX1's `findOverlapping` sees S (1–31 Jan, not yet committed
   changes from anyone else) and computes a split: trim S to end 4 Jan, and insert a new tail
   season for 11–31 Jan.
2. Before TX1 commits, Admin B (racing) applies 20–25 Jan to the same room type. Under READ
   COMMITTED, if TX1 hasn't committed yet, TX2's `findOverlapping` **also** reads the original
   S (1–31 Jan) — it has no way to see TX1's in-flight, uncommitted split. TX2 computes its own
   split of the *original* S: trim S to end 19 Jan, insert its own tail for 26–31 Jan.
3. TX2's `UPDATE` on season S's row blocks until TX1 commits (both mutate the same row `id`),
   then proceeds using Postgres's default read-committed re-evaluation — but since the
   application already computed the new `start_date`/`end_date` values *before* seeing TX1's
   result, TX2 simply overwrites S's boundaries with its own stale calculation (`end_date =
   19 Jan`), discarding TX1's `4 Jan` trim. The database has no way to detect this as a
   conflict (no `@Version`, and the `UPDATE`'s `WHERE` clause only matches on `id`, not on the
   previously-read boundary values).

Net result: S ends up with whatever boundary the transaction that updates last happened to
compute, silently discarding the other admin's already-committed change — a lost update that
can leave `rate_seasons` in a state where the same date is priced by two different, mutually
inconsistent season rows (S post-TX2 vs. TX1's own inserted tail season), or where a date
range that should still be covered by S has silently reverted to `RoomType.basePrice`. If the
split/trim `UPDATE` itself happens to collide with the exclusion constraint (e.g. it produces
an overlap with a season inserted by the other transaction), it throws a raw, un-translated
`DataIntegrityViolationException` — a `500` instead of the clean `409` the new-season path
gets.

**Severity**: MEDIUM — requires ADMIN/OWNER role (not attacker-reachable by a lower-privilege
account) and a genuine timing race between two admins editing overlapping ranges concurrently,
but it is a real data-integrity gap in pricing data with no compensating control, and the
UI pattern (drag-select a range on a shared rate calendar) makes two admins editing adjacent/
overlapping ranges at once plausible in normal operation, not just adversarially.

**Remediation**:
- Add `@Version` to `RateSeason` so a concurrent, conflicting `UPDATE` on the same
  pre-existing season row throws `ObjectOptimisticLockingFailureException` → `409` via the
  existing `GlobalExceptionHandler` mapping, instead of silently overwriting.
- Wrap `applySplitTrim`'s `save()`/`delete()` calls in the same
  `isExclusionViolation`-translating helper `saveTranslatingOverlap` already provides, so any
  constraint violation surfaced by the trim/split path also returns a clean `409` rather than
  a raw `500`.
- Consider taking a `SELECT ... FOR UPDATE` on the overlapping seasons for a given
  `room_type_id` at the start of `bulkApply` (same pattern as the reservation overlap query),
  so the second concurrent bulk-apply blocks and re-reads the first transaction's committed
  result instead of racing on a stale snapshot.

---

## 5. Quantity/price manipulation — confirmed still fully server-resolved

Re-verified against current code (recon's claim held up in every case checked):

- **Reservations**: `ReservationServiceImpl.applyResolvedPrices` (lines 503-518) resolves
  every line item's price via `RatePricingService.resolveStayRates`, never from
  `ReservationLineItemRequest` — that DTO
  (`reservations/dto/ReservationLineItemRequest.java`) carries only `roomId`, no price field.
  Same for `createReservationFromPricedRooms` (called from quotation conversion) — prices come
  from the quotation's own already-server-resolved `roomPrices`, never from a fresh client
  input at that point.
- **Quotations**: `QuotationServiceImpl.resolveLineItems` (lines 397-413) resolves every
  price server-side the same way; `QuotationOptionRequest` (dto) has no price field, only
  `label`/`roomIds`.
- **F&B orders**: `RestaurantOrderServiceImpl.buildItemsFromCatalog` (lines 257-278) resolves
  `unitPrice` exclusively from the `menu_items` catalog (`menuItem.getPrice()`), keyed by a
  server-validated, hotel-scoped `menuItemId` lookup — `OrderItemRequest` (dto) accepts only
  `menuItemId` + `quantity`, no price. T-FB-02 confirmed still enforced, no regression.

No client-supplied price/amount field was found anywhere in the reservation, quotation, or
F&B order creation paths.

---

## 6. NEW — LOW — No upper bound on order/reservation/quotation-option item counts or F&B
order-line quantity

**Files**:
- `fb-service/src/main/java/com/hotelpms/fb/dto/OrderItemRequest.java:20` (`quantity` —
  `@NotNull @Positive`, no `@Max`)
- `fb-service/src/main/java/com/hotelpms/fb/dto/RestaurantOrderRequest.java:21` (`items` —
  `@NotEmpty`, no `@Size(max=...)`)
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/reservations/dto/ReservationRequest.java:35`
  (`lineItems` — `@NotEmpty @Valid`, no `@Size(max=...)`)
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/quotations/dto/QuotationOptionRequest.java:20`
  (`roomIds` — `@NotEmpty`, no `@Size(max=...)`; note `QuotationRequest.options` itself
  **is** capped at `MAX_OPTIONS = 5`, so this cap was deliberately applied at one level but
  not the nested one)
- `frontdesk-service/src/main/java/com/hotelpms/frontdesk/pricing/dto/RateBulkApplyRequest.java`
  (`roomTypeIds` — `@NotEmpty`, no `@Size(max=...)`)

**What's wrong**: None of these collections have an upper-bound validation constraint, and
`OrderItemRequest.quantity` accepts any positive `Integer` (up to `Integer.MAX_VALUE`). Each
room/menu-item ID in a submitted list triggers its own downstream lookup/processing in a loop
(`verifyRoomsAvailability`, `resolveRooms`, `buildItemsFromCatalog`, one `roomTypeService
.getRoomTypeById` + overlap query per `roomTypeId` in `bulkApply`) inside a single
`@Transactional` method, so an authenticated operational-role user could submit an
unreasonably large list to inflate one request/transaction's cost (N sequential lookups,
large `INSERT` batch) — a resource-exhaustion lever, not a privilege-escalation one, since it
requires an authenticated operational-role account.

**Severity**: LOW — bounded to already-authenticated operational users, no data-integrity
impact, but cheap to fix.

**Remediation**: add `@Size(max = N)` to each of the above list fields (a hotel realistically
never books hundreds of rooms in one reservation, offers hundreds of room combinations in one
quotation option, or bulk-applies rates to hundreds of room types at once) and a
`@Max(...)` on `OrderItemRequest.quantity` (a restaurant order line for more than, say, 100
portions is already an operational anomaly worth a validation error rather than silent
acceptance).

---

## 7. Confirmed-fixed / confirmed-safe (re-verified, not re-written up in full)

- **Double-payment race** (`PaymentServiceImpl.addPayment`,
  `billing-service/.../PaymentServiceImpl.java:44-108`): still protected by `Invoice.version`
  (`@Version`, `Invoice.java:63-65`) — a second concurrent `addPayment` whose
  `invoiceRepository.save(invoice)` races against an already-committed first payment throws
  `ObjectOptimisticLockingFailureException`, correctly mapped to `409` by
  `billing-service/src/main/java/com/hotelpms/billing/exception/GlobalExceptionHandler.java:91-93`.
  The whole transaction (including the just-inserted `Payment` row) rolls back, so no
  overpayment can be persisted. **Confirmed still fixed.**
- **`InvoiceController.addCharge` / `InvoiceServiceImpl.addCharge`** (billing-service): the
  recon task flagged this as a *possible* new gap ("especially if it's a JSON list field
  rather than a separate charge row"). Verified: charges are a proper relational
  `InvoiceCharge` table (`invoice.addCharge(charge)` + `invoiceChargeRepository.save(charge)`,
  `InvoiceServiceImpl.java:113-127`), and the same `Invoice.version` optimistic lock that
  protects `addPayment` also protects `addCharge`'s `invoice.setTotalAmount(...)` +
  `invoiceRepository.save(invoice)`. Two concurrent `addCharge` calls (e.g. two F&B orders
  confirmed near-simultaneously against the same stay's invoice) cannot silently lose an
  update — the second commit fails with the same `409`-mapped
  `ObjectOptimisticLockingFailureException` instead. **Not a lost-update bug** — this is a
  resilience/retry gap at worst (the losing caller, `RestaurantOrderServiceImpl.confirmOrder`,
  currently only logs a `FeignException` at ERROR and does not retry — see
  `RestaurantOrderServiceImpl.java:189-200` — so a raced charge can be silently dropped from
  the invoice rather than causing incorrect totals; worth a retry/backoff for reliability, but
  out of scope for this business-logic-abuse audit since it fails safe, not open).
- **Invoice numbering concurrency** (`InvoiceServiceImpl.generateInvoiceNumber`, lines
  332-349): unchanged since the prior session's verification (`PESSIMISTIC_WRITE` on the
  `(hotelId, year)` `InvoiceSequence` row). Not re-audited in depth here per the task's
  framing that this one was already verified safe under load; no code changes found in this
  area to invalidate that conclusion.
- **`RoomService.updateHousekeepingStatus` TOCTOU guard** (`RoomServiceImpl.java:214-232`,
  `RoomRepository.findByIdAndActiveTrueAndHotelIdForUpdate`, `RoomRepository.java:65-67`):
  still present and unchanged — `SELECT ... FOR UPDATE` correctly held across the read-check-
  write of `status`. **Confirmed still fixed.** (Note: this lock only helps when a room row
  already exists to lock, which it always does for status *transitions* — unlike Finding #2,
  which is about the absence of any row to lock against for a *new* reservation.)
- **Stay check-out billing guard, via the intended path** (`StayServiceImpl.checkOut`, lines
  133-170): for any stay that actually goes through the normal `checkIn()` → `checkOut()`
  lifecycle, `BILLING_NOT_PAID` is still correctly enforced before the room is released to
  `DIRTY` and the stay is marked `CHECKED_OUT`. The only bypass found is Finding #1 (a
  different *creation* path that never needs `checkOut()` at all), not a flaw in `checkOut()`
  itself. No other endpoint accepts a stay-status mutation — `StayController` exposes no
  `PATCH /stays/{id}/status` or similar.
- **Quotation double-decline / decline-after-accept**: `declineQuotation` (lines 342-354)
  correctly rejects `status == ACCEPTED` with `QUOTATION_ALREADY_ACCEPTED`. No missing guard
  found here; the only quotation-state gap is the conversion race in Finding #3.

---

## Severity-sorted summary

| # | Finding | Severity | File(s) | Status |
|---|---|---|---|---|
| 1 | Client-controlled `Stay.status` at check-in bypasses `BILLING_NOT_PAID` and strands the room `OCCUPIED` forever | **CRITICAL** | `StayRequest.java:37`, `StayMapper.java:31-45`, `StayServiceImpl.java:78-128` | NEW — open |
| 2 | Reservation creation double-booking TOCTOU (no DB exclusion constraint; `PESSIMISTIC_WRITE` on an empty result locks nothing) | **HIGH** | `ReservationServiceImpl.java:76-99,625-647`, `ReservationRepository.java:54-106`, `V1__frontdesk_baseline.sql:79-142` | NEW — open |
| 3 | Quotation conversion race — no `@Version`/lock lets two concurrent `convert` calls both create a reservation | **HIGH** | `QuotationServiceImpl.java:280-315`, `Quotation.java`, `QuotationRepository.java:34` | NEW — open |
| 4 | Rate-calendar bulk-apply split/trim `UPDATE` on pre-existing seasons is an unguarded lost-update race (new-season insert is safe; the trim step isn't) | **MEDIUM** | `RateCalendarServiceImpl.java:122-153,174-206`, `RateSeason.java` | NEW — open |
| 5 | No upper bound on order/reservation/quotation-option list sizes or F&B order-line quantity (resource exhaustion) | **LOW** | `OrderItemRequest.java:20`, `RestaurantOrderRequest.java:21`, `ReservationRequest.java:35`, `QuotationOptionRequest.java:20`, `RateBulkApplyRequest.java` | NEW — open |
| — | Double-payment race (`PaymentServiceImpl.addPayment`) protected by `Invoice.version` + 409 mapping | — | `PaymentServiceImpl.java:44-108`, `Invoice.java:63-65` | Confirmed-fixed |
| — | `InvoiceServiceImpl.addCharge` — relational charge table + same `@Version` guard, no lost update | — | `InvoiceServiceImpl.java:99-133` | Confirmed-safe |
| — | Invoice numbering concurrency (`PESSIMISTIC_WRITE` on sequence row) | — | `InvoiceServiceImpl.java:332-349` | Confirmed-fixed (not re-audited in depth) |
| — | `RoomService.updateHousekeepingStatus` TOCTOU guard (`SELECT ... FOR UPDATE`) | — | `RoomServiceImpl.java:214-232` | Confirmed-fixed |
| — | Stay check-out `BILLING_NOT_PAID` guard, via the intended `checkOut()` path | — | `StayServiceImpl.java:133-170` | Confirmed-fixed (bypassed only via Finding #1's separate path) |
| — | Reservation/quotation/F&B pricing fully server-resolved, no client-supplied price field anywhere | — | see §5 above | Confirmed-safe |
