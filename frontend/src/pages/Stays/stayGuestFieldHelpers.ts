import { z } from 'zod';
import type {
  AlloggiatiStato,
  GuestDocumentType,
  StayGuestRequest,
  StayGuestResponse,
  TravellerType,
} from '../../types';
import type { IdentityDocumentResponseDTO } from '../../types/guest.types';
import { todayIsoDate } from '../../utils';

export const TYPES_WITHOUT_DOC: TravellerType[] = ['FAMILIARE', 'MEMBRO_GRUPPO'];

/** Alloggiati tipdoc code for a guest-service identity document; '' when there is no matching code. */
export const mapDocType = (documentType: GuestDocumentType): string => {
  switch (documentType) {
    case 'PASSPORT':    return 'PASOR';
    case 'NATIONAL_ID': return 'CARTE';
    default:            return '';
  }
};
export const CODICE_ITALIA = '100000100';

/**
 * Document fields a guest profile can prefill on a check-in form (first identity document).
 * `filled` names the prefilled fields for the banner; the type (and the expiry that only makes
 * sense with it) is claimed only when the document maps to an Alloggiati code.
 */
export const profileDocumentPrefill = (
  profile: { identityDocuments?: Pick<IdentityDocumentResponseDTO, 'documentType' | 'documentNumber' | 'expiryDate'>[] },
): { updates: Partial<IdentifiableGuest>; filled: string[] } => {
  const doc = profile.identityDocuments?.[0];
  const updates: Partial<IdentifiableGuest> = {};
  const filled: string[] = [];
  const tipdoc = doc?.documentType ? mapDocType(doc.documentType) : '';
  if (tipdoc) { updates.documentType = tipdoc; filled.push('documentType'); }
  if (doc?.documentNumber) { updates.documentNumber = doc.documentNumber; filled.push('documentNumber'); }
  if (tipdoc && doc?.expiryDate) { updates.documentExpiryDate = doc.expiryDate; filled.push('documentExpiryDate'); }
  return { updates, filled };
};

export interface IdentifiableGuest extends StayGuestRequest {
  _id: string;
  /** UI-only: stato codice for placeOfBirth logic */
  _statoDiNascita: string;
  /** UI-only: stato codice for documentPlaceOfIssue logic */
  _statoRilascioDoc: string;
  /** UI-only: document expiry (YYYY-MM-DD), prefilled from the guest profile; never sent to the stay API. */
  documentExpiryDate?: string;
}

export const emptyGuest = (isPrimary: boolean): IdentifiableGuest => ({
  _id: Math.random().toString(36).substring(2, 11),
  firstName: '',
  lastName: '',
  gender: '',
  dateOfBirth: '',
  placeOfBirth: '',
  citizenship: '',
  documentType: '',
  documentNumber: '',
  documentPlaceOfIssue: '',
  isPrimaryGuest: isPrimary,
  travellerType: isPrimary ? 'OSPITE_SINGOLO' : undefined,
  travelPurpose: '',
  _statoDiNascita: '',
  _statoRilascioDoc: '',
});

export type GuestErrorTranslator = (key: string, options?: Record<string, unknown>) => string;

export type GuestField =
  | 'firstName' | 'lastName' | 'gender' | 'dateOfBirth' | 'citizenship' | 'travellerType'
  | 'statoNascita' | 'comuneNascita' | 'documentType' | 'documentNumber' | 'statoRilascio' | 'comuneRilascio';

/** i18n key (stays namespace) of each field's form label. */
const GUEST_FIELD_LABEL_KEYS: Record<GuestField, string> = {
  firstName: 'label_first_name',
  lastName: 'label_last_name',
  gender: 'label_gender',
  dateOfBirth: 'label_date_of_birth',
  citizenship: 'label_citizenship',
  travellerType: 'label_guest_type',
  statoNascita: 'label_stato_nascita',
  comuneNascita: 'label_comune_nascita',
  documentType: 'label_doc_type',
  documentNumber: 'label_doc_number',
  statoRilascio: 'label_stato_rilascio_doc',
  comuneRilascio: 'label_comune_rilascio_doc',
};

/** Fields already reported by their own, more specific message in {@link alloggiatiPlaceIssues}. */
const FIELDS_WITH_OWN_MESSAGE: GuestField[] = [
  'dateOfBirth', 'statoNascita', 'comuneNascita', 'statoRilascio', 'comuneRilascio',
];

/**
 * Every required field still empty for a guest, in form order. Single source of truth for the
 * check-in checklist and the submit validation: a guest has every field when this is empty (an expired document is a separate, `hasExpiredDocument` check).
 */
export const getGuestMissingFields = (g: IdentifiableGuest): GuestField[] => {
  const hasDoc = !TYPES_WITHOUT_DOC.includes(g.travellerType as TravellerType);
  const missing: GuestField[] = [];
  if (!g.firstName) missing.push('firstName');
  if (!g.lastName) missing.push('lastName');
  if (!g.gender) missing.push('gender');
  if (!g.dateOfBirth) missing.push('dateOfBirth');
  if (!g.citizenship) missing.push('citizenship');
  if (!g.travellerType) missing.push('travellerType');
  if (!g._statoDiNascita) missing.push('statoNascita');
  if (g._statoDiNascita === CODICE_ITALIA && !g.placeOfBirth) missing.push('comuneNascita');
  if (hasDoc) {
    if (!g.documentType) missing.push('documentType');
    if (!g.documentNumber) missing.push('documentNumber');
    if (!g._statoRilascioDoc) missing.push('statoRilascio');
    if (g._statoRilascioDoc === CODICE_ITALIA && !g.documentPlaceOfIssue) missing.push('comuneRilascio');
  }
  return missing;
};

/** True when the guest carries a document whose expiry date is set and already past. */
export const hasExpiredDocument = (g: IdentifiableGuest): boolean =>
  !TYPES_WITHOUT_DOC.includes(g.travellerType as TravellerType)
  && !!g.documentExpiryDate
  && g.documentExpiryDate < todayIsoDate();

/**
 * Alloggiati Web stato/comune-di-nascita and stato/comune-di-rilascio-documento
 * rules for one guest — the actual point of duplication between the full check-in
 * guest-list validator below and StayGuestManagerDialog's single-guest add/edit
 * form (which can't reuse the array-level checks: "at least one primary guest" and
 * the required-field checks only make sense across a fresh check-in's whole list,
 * not a lone correction on an already-open stay). Returns every violation, in the
 * same order as the original sequential checks, so a "stop at first error" caller
 * can just take the first one.
 *
 * @param g      the guest to check
 * @param t      translator for the error messages
 * @param number 1-based position, interpolated into each message
 */
export const alloggiatiPlaceIssues = (
  g: Pick<IdentifiableGuest, 'travellerType' | '_statoDiNascita' | 'placeOfBirth' | '_statoRilascioDoc' | 'documentPlaceOfIssue' | 'dateOfBirth'>,
  t: GuestErrorTranslator,
  number: number,
): string[] => {
  const hasDoc = !TYPES_WITHOUT_DOC.includes(g.travellerType as TravellerType);
  const isItalianBorn = g._statoDiNascita === CODICE_ITALIA;
  const isItalianDocIssue = g._statoRilascioDoc === CODICE_ITALIA;
  const issues: string[] = [];

  if (!g._statoDiNascita) {
    issues.push(t('err_stato_nascita_required', { number }));
  }
  if (isItalianBorn && !g.placeOfBirth) {
    issues.push(t('err_comune_nascita_required', { number }));
  }
  if (hasDoc) {
    if (!g._statoRilascioDoc) {
      issues.push(t('err_stato_rilascio_required', { number }));
    }
    if (isItalianDocIssue && !g.documentPlaceOfIssue) {
      issues.push(t('err_comune_rilascio_required', { number }));
    }
  }
  // Checked last: stay_guests.date_of_birth is NOT NULL in Postgres for
  // every guest regardless of traveller type, but this was never
  // validated client-side (found via frontend/e2e-live/walk-in-live.spec.ts
  // against the real backend — a FAMILIARE guest with no date of birth
  // used to reach the database's NOT NULL constraint and 500).
  if (!g.dateOfBirth) {
    issues.push(t('err_date_of_birth_required', { number }));
  }
  return issues;
};

/**
 * Alloggiati Web compliance rules shared by CheckInForm and WalkInCheckInForm.
 * Issues are added in the same order as the original sequential checks so
 * the first one matches what a "stop at first error" caller would report.
 */
const buildAlloggiatiGuestsSchema = (t: GuestErrorTranslator) =>
  z.array(z.custom<IdentifiableGuest>()).superRefine((guests, ctx) => {
    if (!guests.some((g) => g.isPrimaryGuest)) {
      ctx.addIssue({ code: 'custom', path: [], message: t('err_primary_guest_required') });
    }

    guests.forEach((g, idx) => {
      alloggiatiPlaceIssues(g, t, idx + 1).forEach((message) => {
        ctx.addIssue({ code: 'custom', path: [idx], message });
      });

      // After the specific place messages, so a guest with several gaps still reports
      // the same first error as before; this only adds the plain required fields.
      const missing = getGuestMissingFields(g).filter((f) => !FIELDS_WITH_OWN_MESSAGE.includes(f));
      if (missing.length > 0) {
        const fields = missing.map((f) => t(GUEST_FIELD_LABEL_KEYS[f])).join(', ');
        ctx.addIssue({ code: 'custom', path: [idx], message: t('err_guest_fields_required', { number: idx + 1, fields }) });
      }
      if (hasExpiredDocument(g)) {
        ctx.addIssue({ code: 'custom', path: [idx], message: t('err_document_expired', { number: idx + 1 }) });
      }
    });
  });

/**
 * Validates the full guest list against Alloggiati Web rules.
 * Returns the first violation message, or null when the list is valid —
 * matches the original hand-rolled "stop at first error" behavior.
 */
export const validateAlloggiatiGuests = (guests: IdentifiableGuest[], t: GuestErrorTranslator): string | null => {
  const result = buildAlloggiatiGuestsSchema(t).safeParse(guests);
  return result.success ? null : (result.error.issues[0]?.message ?? null);
};

/** Maps a persisted stay guest back to the editable form shape (UI-only stato codes derived from the lookup table). */
export const toIdentifiableGuest = (g: StayGuestResponse, stati: AlloggiatiStato[]): IdentifiableGuest => {
  const isStatoCode = (code: string) => stati.some((s) => s.codice === code);
  return {
    _id: g.id,
    firstName: g.firstName,
    lastName: g.lastName,
    gender: g.gender,
    dateOfBirth: g.dateOfBirth,
    placeOfBirth: g.placeOfBirth,
    citizenship: g.citizenship,
    documentType: g.documentType ?? '',
    documentNumber: g.documentNumber ?? '',
    documentPlaceOfIssue: g.documentPlaceOfIssue ?? '',
    isPrimaryGuest: g.isPrimaryGuest,
    travellerType: g.travellerType,
    travelPurpose: g.travelPurpose ?? '',
    version: g.version,
    _statoDiNascita: isStatoCode(g.placeOfBirth) ? g.placeOfBirth : CODICE_ITALIA,
    _statoRilascioDoc: g.documentPlaceOfIssue && isStatoCode(g.documentPlaceOfIssue) ? g.documentPlaceOfIssue : CODICE_ITALIA,
  };
};

/** Strips the UI-only fields and empty optionals before sending to the API. */
export const toRequest = (g: IdentifiableGuest): StayGuestRequest => ({
  firstName: g.firstName,
  lastName: g.lastName,
  gender: g.gender,
  dateOfBirth: g.dateOfBirth,
  placeOfBirth: g.placeOfBirth,
  citizenship: g.citizenship,
  documentType: g.documentType || undefined,
  documentNumber: g.documentNumber || undefined,
  documentPlaceOfIssue: g.documentPlaceOfIssue || undefined,
  isPrimaryGuest: g.isPrimaryGuest,
  travellerType: g.travellerType,
  travelPurpose: g.travelPurpose || undefined,
  version: g.version,
});

/** Validates the lone guest being added/corrected on an already-open stay; returns the first problem or null. */
export const validateSingleGuest = (g: IdentifiableGuest, t: GuestErrorTranslator): string | null => {
  const hasDoc = !TYPES_WITHOUT_DOC.includes(g.travellerType as never);

  if (!g.firstName || !g.lastName || !g.gender || !g.dateOfBirth || !g.travellerType) {
    return t('err_required_fields');
  }
  if (hasDoc && (!g.documentType || !g.documentNumber)) {
    return t('err_required_fields');
  }
  // Stato/comune-di-nascita and stato/comune-di-rilascio-documento rules are the
  // same ones CheckInForm/WalkInCheckInForm enforce for every guest at check-in —
  // shared here rather than re-derived, so a future Alloggiati rule change only
  // needs to be made once (see alloggiatiPlaceIssues' own doc for why the
  // required-field and primary-guest checks above/below aren't also shared: they
  // don't apply the same way to a lone correction on an already-open stay).
  return alloggiatiPlaceIssues(g, t, 1)[0] ?? null;
};
