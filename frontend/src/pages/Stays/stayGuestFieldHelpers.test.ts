import { describe, expect, it } from 'vitest';
import {
  CODICE_ITALIA, emptyGuest, getGuestMissingFields, hasExpiredDocument, toIdentifiableGuest, toRequest, validateAlloggiatiGuests,
  validateSingleGuest,
  type IdentifiableGuest,
  mapDocType,
} from './stayGuestFieldHelpers';
import type { StayGuestResponse } from '../../types';
import type { DocumentType } from '../../types/guest.types';

const t = (key: string): string => key;

function validGuest(overrides: Partial<IdentifiableGuest> = {}): IdentifiableGuest {
  return {
    ...emptyGuest(true),
    firstName: 'Ada',
    lastName: 'Rossi',
    gender: '2',
    citizenship: '100000100',
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

describe('validateAlloggiatiGuests — strict required fields', () => {
  const tFields = (k: string, o?: Record<string, unknown>) => (o ? `${k}|${o.number}|${o.fields}` : k);

  it('rejects a guest with a missing name and lists the field', () => {
    expect(validateAlloggiatiGuests([validGuest({ firstName: '' })], tFields))
      .toBe('err_guest_fields_required|1|label_first_name');
  });

  it('lists every missing required field in one message', () => {
    expect(validateAlloggiatiGuests([validGuest({ lastName: '', gender: '', citizenship: '' })], tFields))
      .toBe('err_guest_fields_required|1|label_last_name, label_gender, label_citizenship');
  });

  it('requires document type and number when the traveller carries a document', () => {
    const guest = validGuest({
      travellerType: 'OSPITE_SINGOLO', _statoRilascioDoc: CODICE_ITALIA, documentPlaceOfIssue: 'x',
    });
    expect(validateAlloggiatiGuests([guest], tFields))
      .toBe('err_guest_fields_required|1|label_doc_type, label_doc_number');
  });

  it('keeps the specific place error ahead of the generic required-fields one', () => {
    expect(validateAlloggiatiGuests([validGuest({ firstName: '', _statoDiNascita: '' })], t))
      .toBe('err_stato_nascita_required');
  });

  it('rejects an expired document', () => {
    const guest = validGuest({
      travellerType: 'OSPITE_SINGOLO', documentType: 'PASOR', documentNumber: 'X1',
      _statoRilascioDoc: 'FR', documentExpiryDate: '2000-01-01',
    });
    expect(validateAlloggiatiGuests([guest], t)).toBe('err_document_expired');
  });

  it('ignores an expiry date on a traveller type that carries no document', () => {
    expect(validateAlloggiatiGuests([validGuest({ documentExpiryDate: '2000-01-01' })], t)).toBeNull();
  });

  it('accepts a document without an expiry date (optional)', () => {
    const guest = validGuest({
      travellerType: 'OSPITE_SINGOLO', documentType: 'PASOR', documentNumber: 'X1', _statoRilascioDoc: 'FR',
    });
    expect(validateAlloggiatiGuests([guest], t)).toBeNull();
  });
});

describe('getGuestMissingFields', () => {
  it('reports every field of an empty guest', () => {
    expect(getGuestMissingFields(emptyGuest(true))).toEqual([
      'firstName', 'lastName', 'gender', 'dateOfBirth', 'citizenship',
      'statoNascita', 'documentType', 'documentNumber', 'statoRilascio',
    ]);
  });

  it('reports nothing for a complete family member (no document block)', () => {
    expect(getGuestMissingFields(validGuest())).toEqual([]);
  });

  it('asks for the comune di nascita only for Italian-born guests', () => {
    expect(getGuestMissingFields(validGuest({ _statoDiNascita: CODICE_ITALIA }))).toEqual(['comuneNascita']);
  });

  it('asks for the comune di rilascio only for Italian-issued documents', () => {
    const guest = validGuest({
      travellerType: 'OSPITE_SINGOLO', documentType: 'PASOR', documentNumber: 'X1', _statoRilascioDoc: CODICE_ITALIA,
    });
    expect(getGuestMissingFields(guest)).toEqual(['comuneRilascio']);
  });

  it('asks for the traveller type when unset', () => {
    expect(getGuestMissingFields(validGuest({ travellerType: undefined }))).toContain('travellerType');
  });

  it('keeps an expired document out of the missing fields (reported separately)', () => {
    const guest = validGuest({
      travellerType: 'OSPITE_SINGOLO', documentType: 'PASOR', documentNumber: 'X1',
      _statoRilascioDoc: 'FR', documentExpiryDate: '2000-01-01',
    });
    expect(getGuestMissingFields(guest)).toEqual([]);
    expect(hasExpiredDocument(guest)).toBe(true);
  });

  it('is empty exactly when the strict validator accepts the guest', () => {
    const cases = [
      validGuest(),
      validGuest({ firstName: '' }),
      validGuest({ _statoDiNascita: CODICE_ITALIA }),
      validGuest({ travellerType: 'OSPITE_SINGOLO' }),
      validGuest({ dateOfBirth: '' }),
    ];
    cases.forEach((g) => {
      expect(getGuestMissingFields(g).length === 0).toBe(validateAlloggiatiGuests([g], t) === null);
    });
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

describe('mapDocType', () => {
  it.each<[DocumentType, string]>([
    ['PASSPORT', 'PASOR'],
    ['NATIONAL_ID', 'CARTE'],
    ['DRIVERS_LICENSE', ''],
    ['OTHER', ''],
  ])('maps the guest-service %s document to the Alloggiati code %j', (documentType, tipdoc) => {
    expect(mapDocType(documentType)).toBe(tipdoc);
  });
});
