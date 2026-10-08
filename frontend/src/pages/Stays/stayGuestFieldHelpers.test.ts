import { describe, expect, it } from 'vitest';
import {
  CODICE_ITALIA, emptyGuest, toIdentifiableGuest, toRequest, validateAlloggiatiGuests, validateSingleGuest,
  type IdentifiableGuest,
} from './stayGuestFieldHelpers';
import type { StayGuestResponse } from '../../types';

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

describe('toIdentifiableGuest / toRequest', () => {
  const persisted = {
    id: 'g1', firstName: 'Ada', lastName: 'Rossi', gender: '2', dateOfBirth: '1990-01-01',
    placeOfBirth: '100000212', citizenship: '100000100', documentType: null, documentNumber: null,
    documentPlaceOfIssue: null, isPrimaryGuest: true, travellerType: 'OSPITE_SINGOLO',
    arrivalDate: '2026-10-01', alloggiatiSent: false, needsResubmit: false, version: 3,
  } as unknown as StayGuestResponse;

  it('maps a foreign-born guest to its stato code and defaults the document place to Italy', () => {
    const g = toIdentifiableGuest(persisted, [{ codice: '100000212', descrizione: 'FRANCIA' }] as never);
    expect(g._id).toBe('g1');
    expect(g._statoDiNascita).toBe('100000212');
    expect(g._statoRilascioDoc).toBe(CODICE_ITALIA);
    expect(g.documentType).toBe('');
  });

  it('falls back to Italy for an unknown birth stato', () => {
    expect(toIdentifiableGuest(persisted, [])._statoDiNascita).toBe(CODICE_ITALIA);
  });

  it('drops UI-only fields and empty optionals in the request', () => {
    const req = toRequest(toIdentifiableGuest(persisted, []));
    expect(req).not.toHaveProperty('_id');
    expect(req.documentType).toBeUndefined();
    expect(req.travelPurpose).toBeUndefined();
    expect(req.version).toBe(3);
  });
});

describe('validateSingleGuest', () => {
  it('requires the base fields', () => {
    expect(validateSingleGuest(emptyGuest(true), t)).toBe('err_required_fields');
  });

  it('requires a document for traveller types that carry one', () => {
    const g = { ...validGuest(), travellerType: 'OSPITE_SINGOLO' as const, documentType: '', documentNumber: '' };
    expect(validateSingleGuest(g, t)).toBe('err_required_fields');
  });

  it('accepts a complete guest without document requirements', () => {
    expect(validateSingleGuest(validGuest({ gender: '1', firstName: 'A', lastName: 'B' }), t)).toBeNull();
  });
});
