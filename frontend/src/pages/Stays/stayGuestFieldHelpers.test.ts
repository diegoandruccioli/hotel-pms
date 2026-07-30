import { describe, expect, it } from 'vitest';
import { CODICE_ITALIA, emptyGuest, validateAlloggiatiGuests, type IdentifiableGuest } from './stayGuestFieldHelpers';

const t = (key: string): string => key;

function validGuest(overrides: Partial<IdentifiableGuest> = {}): IdentifiableGuest {
  return {
    ...emptyGuest(true),
    dateOfBirth: '1990-01-01',
    _statoDiNascita: 'FR', // any non-Italy code — placeOfBirth not required
    travellerType: 'FAMILIARE', // TYPES_WITHOUT_DOC — document fields not required
    ...overrides,
  };
}

describe('validateAlloggiatiGuests', () => {
  it('accepts a fully valid single-guest list', () => {
    expect(validateAlloggiatiGuests([validGuest()], t)).toBeNull();
  });

  it('rejects when no guest is marked primary', () => {
    const guest = validGuest({ isPrimaryGuest: false });
    expect(validateAlloggiatiGuests([guest], t)).toBe('err_primary_guest_required');
  });

  it('rejects when dateOfBirth is missing — the gap found via frontend/e2e-live/walk-in-live.spec.ts '
      + '(stay_guests.date_of_birth is NOT NULL in Postgres for every guest, regardless of traveller type)', () => {
    const guest = validGuest({ dateOfBirth: '' });
    expect(validateAlloggiatiGuests([guest], t)).toBe('err_date_of_birth_required');
  });

  it('requires dateOfBirth even for a FAMILIARE guest exempt from document fields', () => {
    const guest = validGuest({ dateOfBirth: '', travellerType: 'FAMILIARE' });
    expect(validateAlloggiatiGuests([guest], t)).toBe('err_date_of_birth_required');
  });

  it('rejects when country of birth is missing', () => {
    const guest = validGuest({ _statoDiNascita: '' });
    expect(validateAlloggiatiGuests([guest], t)).toBe('err_stato_nascita_required');
  });

  it('requires the municipality of birth for an Italian-born guest', () => {
    const guest = validGuest({ _statoDiNascita: CODICE_ITALIA, placeOfBirth: '' });
    expect(validateAlloggiatiGuests([guest], t)).toBe('err_comune_nascita_required');
  });

  it('does not require the municipality of birth for a foreign-born guest', () => {
    const guest = validGuest({ _statoDiNascita: 'FR', placeOfBirth: '' });
    expect(validateAlloggiatiGuests([guest], t)).toBeNull();
  });

  it('requires document-issuing country for a guest whose traveller type carries a document', () => {
    const guest = validGuest({ travellerType: 'OSPITE_SINGOLO', _statoRilascioDoc: '' });
    expect(validateAlloggiatiGuests([guest], t)).toBe('err_stato_rilascio_required');
  });

  it('does not require document fields for a FAMILIARE guest', () => {
    const guest = validGuest({
      travellerType: 'FAMILIARE',
      _statoRilascioDoc: '',
      documentPlaceOfIssue: '',
    });
    expect(validateAlloggiatiGuests([guest], t)).toBeNull();
  });
});
