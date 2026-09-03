-- Room-change feature (Parte 6): moving a checked-in stay to a room with a different
-- RoomType needs to reprice the not-yet-consumed nights, and — per the same "an amount
-- already charged never silently changes" principle already applied to city tax
-- (CityTaxAssessment) and extendStay — the *already-consumed* nights must keep the
-- price the guest actually agreed to, not a live re-quote against today's rates.
--
-- The check-in charge amount already lives in billing-service, but the ChargeResponse
-- contract frontdesk-service gets back from it only carries an id, never the amount —
-- so without this snapshot, frontdesk-service has no faithful way to reconstruct what
-- "already consumed" was worth. StayBillingCoordinator computes exactly this at
-- check-in time already (unitPrice + nights, right next to roomChargeId); it was
-- simply discarded after posting the charge. This persists it instead.

ALTER TABLE stays ADD COLUMN room_charge_unit_price NUMERIC(10, 2);
ALTER TABLE stays ADD COLUMN room_charge_nights INTEGER;

COMMENT ON COLUMN stays.room_charge_unit_price IS
    'Per-night price the current room_charge_id charge was actually posted at, snapshotted at check-in (or at the last room change) — never re-derived live, so a room change can price the already-consumed nights at what the guest actually agreed to. NULL for stays checked in before this column existed.';
COMMENT ON COLUMN stays.room_charge_nights IS
    'Night count the current room_charge_id charge covers, snapshotted alongside room_charge_unit_price.';
