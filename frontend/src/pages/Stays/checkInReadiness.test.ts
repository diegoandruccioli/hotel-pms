import { describe, expect, it } from 'vitest';
import { buildCheckInChecklist, buildWalkInItems } from './checkInReadiness';
import { CODICE_ITALIA, emptyGuest } from './stayGuestFieldHelpers';
import type { IdentifiableGuest } from './stayGuestFieldHelpers';

const complete = (overrides: Partial<IdentifiableGuest> = {}): IdentifiableGuest => ({
  ...emptyGuest(true),
  firstName: 'Ada', lastName: 'Rossi', gender: '2', citizenship: CODICE_ITALIA, dateOfBirth: '1990-01-01',
  _statoDiNascita: 'FR', travellerType: 'FAMILIARE',
  ...overrides,
});

const withDoc = (expiry?: string): IdentifiableGuest => complete({
  travellerType: 'OSPITE_SINGOLO', documentType: 'PASOR', documentNumber: 'X1', _statoRilascioDoc: 'FR',
  documentExpiryDate: expiry,
});

const find = (items: ReturnType<typeof buildCheckInChecklist>, id: string) => items.find((i) => i.id === id);

describe('buildCheckInChecklist', () => {
  it('marks a complete guest ok and the Alloggiati item ok', () => {
    const items = buildCheckInChecklist({ guests: [complete()], cityTaxWarning: null });
    expect(find(items, 'guest-0')).toMatchObject({ tone: 'ok' });
    expect(find(items, 'alloggiati')).toMatchObject({ tone: 'ok' });
  });

  it('reports the number of missing fields for an incomplete guest and keeps Alloggiati pending', () => {
    const items = buildCheckInChecklist({ guests: [emptyGuest(true)], cityTaxWarning: null });
    expect(find(items, 'guest-0')).toMatchObject({
      tone: 'warn', detailKey: 'checklist_status_missing', detailParams: { count: 9 },
    });
    expect(find(items, 'alloggiati')?.tone).toBe('pending');
  });

  it('warns when nobody is the primary guest', () => {
    const items = buildCheckInChecklist({ guests: [complete({ isPrimaryGuest: false })], cityTaxWarning: null });
    expect(find(items, 'primary')?.tone).toBe('warn');
    expect(find(items, 'alloggiati')?.tone).toBe('pending');
  });

  it('has no primary item when a primary guest exists', () => {
    expect(find(buildCheckInChecklist({ guests: [complete()], cityTaxWarning: null }), 'primary')).toBeUndefined();
  });

  it('does not call a guest with an expired document complete', () => {
    const items = buildCheckInChecklist({ guests: [withDoc('2000-01-01')], cityTaxWarning: null });
    expect(find(items, 'guest-0')).toMatchObject({ tone: 'warn', detailKey: 'checklist_doc_expired_short' });
  });

  it('flags an expired document', () => {
    const items = buildCheckInChecklist({ guests: [withDoc('2000-01-01')], cityTaxWarning: null });
    expect(find(items, 'doc-0')).toMatchObject({ tone: 'warn', detailKey: 'checklist_doc_expired' });
    expect(find(items, 'alloggiati')?.tone).toBe('pending');
  });

  it('flags a document that expires before check-out', () => {
    const items = buildCheckInChecklist({
      guests: [withDoc('2999-06-02')], cityTaxWarning: null, checkOutDate: '2999-06-05',
    });
    expect(find(items, 'doc-0')).toMatchObject({ tone: 'warn', detailKey: 'checklist_doc_expires_in_stay' });
    expect(find(items, 'alloggiati')?.tone).toBe('ok');
  });

  it('does not flag a document valid past check-out, nor one without an expiry', () => {
    expect(find(buildCheckInChecklist({
      guests: [withDoc('2999-06-10')], cityTaxWarning: null, checkOutDate: '2999-06-05',
    }), 'doc-0')).toBeUndefined();
    expect(find(buildCheckInChecklist({ guests: [withDoc()], cityTaxWarning: null }), 'doc-0')).toBeUndefined();
  });

  it.each([
    [undefined, 'pending', 'checklist_city_tax_unknown'],
    [null, 'ok', 'checklist_city_tax_configured'],
    ['NOT_APPLICABLE', 'ok', 'checklist_city_tax_not_applicable'],
    ['COMUNE_NOT_CONFIGURED', 'warn', 'checklist_city_tax_not_configured'],
  ] as const)('city tax %s gives %s', (warning, tone, detailKey) => {
    const items = buildCheckInChecklist({ guests: [complete()], cityTaxWarning: warning });
    expect(find(items, 'city-tax')).toMatchObject({ tone, detailKey });
  });
});

describe('buildWalkInItems', () => {
  it('is pending for room, guest and departure while nothing is chosen', () => {
    const items = buildWalkInItems({ roomLabel: '', guestName: '', checkOutDate: '' });
    expect(items.map((i) => [i.id, i.tone])).toEqual([['room', 'pending'], ['walkin-guest', 'pending'], ['checkout', 'pending']]);
  });

  it('is ok once each is chosen, and carries the room label, the guest name and the departure date', () => {
    const items = buildWalkInItems({ roomLabel: '101 — Standard', guestName: 'Ada Rossi', checkOutDate: '2099-01-02' });
    expect(items.every((i) => i.tone === 'ok')).toBe(true);
    expect(items[0].detailParams).toMatchObject({ room: '101 — Standard' });
    expect(items[1].detailParams).toMatchObject({ name: 'Ada Rossi' });
    expect(items[2]).toMatchObject({ dateParam: 'date', detailParams: { date: '2099-01-02' } });
  });
});

describe('buildCheckInChecklist — extra items', () => {
  it('puts the extra items first', () => {
    const extra = buildWalkInItems({ roomLabel: '101', guestName: 'Ada', checkOutDate: '2099-01-02' });
    const items = buildCheckInChecklist({ guests: [complete()], cityTaxWarning: null, extra });
    expect(items.slice(0, 3).map((i) => i.id)).toEqual(['room', 'walkin-guest', 'checkout']);
  });

  it('keeps Alloggiati pending while an extra item is not ok, even with complete guests', () => {
    const extra = buildWalkInItems({ roomLabel: '', guestName: 'Ada', checkOutDate: '2099-01-02' });
    const items = buildCheckInChecklist({ guests: [complete()], cityTaxWarning: null, extra });
    expect(find(items, 'alloggiati')?.tone).toBe('pending');
  });

  it('is ok when every extra item and guest is ok', () => {
    const extra = buildWalkInItems({ roomLabel: '101', guestName: 'Ada', checkOutDate: '2099-01-02' });
    expect(find(buildCheckInChecklist({ guests: [complete()], cityTaxWarning: null, extra }), 'alloggiati')?.tone).toBe('ok');
  });

  it('warns about a document expiring before the walk-in departure', () => {
    const items = buildCheckInChecklist({ guests: [withDoc('2099-01-01')], cityTaxWarning: null, checkOutDate: '2099-01-05' });
    expect(find(items, 'doc-0')).toMatchObject({ detailKey: 'checklist_doc_expires_in_stay' });
  });
});
