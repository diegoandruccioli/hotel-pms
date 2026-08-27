import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { APIRequestContext } from '@playwright/test';
import { csrfHeader } from '../../fixtures/api';
import { SEED_HOTEL_ID, OTHER_HOTEL_ID } from '../../fixtures/hotel';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SNAPSHOT_DIR = path.resolve(__dirname, '..', '..', '..', '..', 'qa-artifacts', '2026-08-25');
mkdirSync(SNAPSHOT_DIR, { recursive: true });

const SNAPSHOT_FILE = (hotelId: string) => path.join(SNAPSHOT_DIR, `hotel-settings-snapshot.${hotelId}.json`);

/**
 * Reads /api/v1/stays/settings for a tenant and writes it to disk, once,
 * before the portal-matrix suite (Blocco 5) is allowed to mutate it. If a
 * snapshot already exists from an earlier (unfinished) run in the same day,
 * it is NOT overwritten — the first snapshot of the day is the ground truth
 * to restore to, so a crashed mid-run doesn't get baked in as "clean".
 */
export async function snapshotHotelSettings(request: APIRequestContext, hotelId: string): Promise<void> {
  const file = SNAPSHOT_FILE(hotelId);
  if (existsSync(file)) return;
  const response = await request.get('/api/v1/stays/settings');
  if (response.status() !== 200) {
    throw new Error(`Failed to snapshot hotel settings for ${hotelId}: ${response.status()} ${await response.text()}`);
  }
  writeFileSync(file, await response.text(), 'utf-8');
}

/**
 * Restores hotel_settings to the pre-run snapshot. Only writes fields the
 * PUT endpoint accepts (HotelSettingsRequest) — response-only fields like
 * alloggiatiCredentialsConfigured are stripped. Passwords/wsKey are
 * write-only in the response DTO (never echoed back), so a restore cannot
 * recover them if the matrix run overwrote them with bad values; the matrix
 * specs must therefore save/restore alloggiati credentials themselves via a
 * separate explicit backup before mutating them (see qa2508 D1 specs).
 */
export async function restoreHotelSettings(request: APIRequestContext, hotelId: string): Promise<void> {
  const file = SNAPSHOT_FILE(hotelId);
  if (!existsSync(file)) {
    throw new Error(`No snapshot found for ${hotelId} — cannot restore. Was snapshotHotelSettings() called first?`);
  }
  const snapshot = JSON.parse(readFileSync(file, 'utf-8'));
  const headers = await csrfHeader(request);
  const payload = {
    hotelName: snapshot.hotelName ?? '',
    address: snapshot.address ?? '',
    vatNumber: snapshot.vatNumber ?? '',
    fiscalCode: snapshot.fiscalCode ?? '',
    logoUrl: snapshot.logoUrl ?? '',
    cap: snapshot.cap ?? '',
    comune: snapshot.comune ?? '',
    provincia: snapshot.provincia ?? '',
    alloggiatiAutoSend: snapshot.alloggiatiAutoSend,
    sendReservationConfirmedEmail: snapshot.sendReservationConfirmedEmail,
    sendCheckoutEmail: snapshot.sendCheckoutEmail,
    emailSubjectReservationConfirmed: snapshot.emailSubjectReservationConfirmed ?? '',
    emailSubjectCheckout: snapshot.emailSubjectCheckout ?? '',
    emailGreetingText: snapshot.emailGreetingText ?? '',
  };
  const response = await request.put('/api/v1/stays/settings', { headers, data: payload });
  if (response.status() !== 200) {
    throw new Error(`Failed to restore hotel settings for ${hotelId}: ${response.status()} ${await response.text()}`);
  }
}

export async function snapshotAllTenants(request: APIRequestContext): Promise<void> {
  await snapshotHotelSettings(request, SEED_HOTEL_ID);
  // Hotel B's settings require the OTHER_HOTEL_ADMIN session, not this
  // request context's ADMIN-on-hotel-A cookies — see qa2508 bootstrap spec
  // for the per-tenant context switch.
}

export async function restoreAllTenants(request: APIRequestContext): Promise<void> {
  await restoreHotelSettings(request, SEED_HOTEL_ID);
}

/** Prefix used on every record this round's suites create, so they're identifiable and prunable. */
export const QA25_PREFIX = 'QA25-';

export { SEED_HOTEL_ID, OTHER_HOTEL_ID };

const FIXTURE_IDS_FILE = path.join(SNAPSHOT_DIR, 'fixture-ids.json');

export interface FixtureIds {
  reservationId: string;
  quotationId: string;
}

/**
 * Creates a reservation and a quotation owned by this round (guest name
 * prefixed QA25-) and persists their ids to disk, so every spec in the round
 * references the SAME known-good, known-active fixtures instead of each
 * spec scraping "any row" out of the DB — which is exactly what broke
 * 02-route-sweep.spec.ts's hardcoded quotation id: it borrowed an id from an
 * unrelated prior QA round that had since been soft-deleted
 * (`quotations.active = false`), and Quotation's `@SQLRestriction("active =
 * true")` correctly 404'd it. Idempotent: skips creation if the file already
 * exists from an earlier run today.
 */
export async function ensureFixtureIds(request: APIRequestContext): Promise<FixtureIds> {
  if (existsSync(FIXTURE_IDS_FILE)) {
    return JSON.parse(readFileSync(FIXTURE_IDS_FILE, 'utf-8'));
  }

  const headers = await csrfHeader(request);

  // firstName/lastName must match "^[\p{L} '\-]+$" (letters/space/apostrophe/
  // hyphen only, no digits) — the QA25 marker lives in the email instead,
  // same convention as e2e-live/fixtures/api.ts's createGuest().
  const guestResponse = await request.post('/api/v1/guests', {
    headers,
    data: { firstName: 'Qa Round', lastName: 'Fixture Guest', email: `qa25.fixture.${Date.now()}@example.com` },
  });
  if (guestResponse.status() !== 201) {
    throw new Error(`Failed to create fixture guest: ${guestResponse.status()} ${await guestResponse.text()}`);
  }
  const guestId = (await guestResponse.json()).id as string;

  const roomTypesResponse = await request.get('/api/v1/room-types');
  const roomTypes = await roomTypesResponse.json();
  const roomTypeId = roomTypes[0]?.id as string;
  if (!roomTypeId) throw new Error('No room type available to build fixture reservation/quotation — seed data missing?');

  const roomsResponse = await request.get(`/api/v1/rooms?roomTypeId=${roomTypeId}`);
  const rooms = await roomsResponse.json();
  const roomId = (rooms.content ?? rooms)[0]?.id as string;
  if (!roomId) throw new Error('No room available to build fixture reservation/quotation — seed data missing?');

  const inThreeMonths = new Date();
  inThreeMonths.setMonth(inThreeMonths.getMonth() + 3);
  const checkIn = inThreeMonths.toISOString().split('T')[0];
  const checkOutDate = new Date(inThreeMonths);
  checkOutDate.setDate(checkOutDate.getDate() + 2);
  const checkOut = checkOutDate.toISOString().split('T')[0];

  const reservationResponse = await request.post('/api/v1/reservations', {
    headers,
    data: {
      guestId,
      expectedGuests: 1,
      checkInDate: checkIn,
      checkOutDate: checkOut,
      status: 'CONFIRMED',
      lineItems: [{ roomId }],
    },
  });
  if (reservationResponse.status() !== 201) {
    throw new Error(`Failed to create fixture reservation: ${reservationResponse.status()} ${await reservationResponse.text()}`);
  }
  const reservationId = (await reservationResponse.json()).id as string;

  const validUntil = new Date(inThreeMonths);
  validUntil.setDate(validUntil.getDate() - 1);
  const quotationResponse = await request.post('/api/v1/quotations', {
    headers,
    data: {
      guestId,
      checkInDate: checkIn,
      checkOutDate: checkOut,
      options: [{ label: 'QA25 fixture option', roomIds: [roomId] }],
      validUntil: validUntil.toISOString().split('T')[0],
    },
  });
  if (quotationResponse.status() !== 201) {
    throw new Error(`Failed to create fixture quotation: ${quotationResponse.status()} ${await quotationResponse.text()}`);
  }
  const quotationId = (await quotationResponse.json()).id as string;

  const ids: FixtureIds = { reservationId, quotationId };
  writeFileSync(FIXTURE_IDS_FILE, JSON.stringify(ids, null, 2), 'utf-8');
  return ids;
}
