import { describe, expect, it } from 'vitest';
import { buildCheckInChecklist } from './checkInReadiness';
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
