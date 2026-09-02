import { z } from 'zod';
import type { StayGuestRequest, TravellerType } from '../../types';

export const TYPES_WITHOUT_DOC: TravellerType[] = ['FAMILIARE', 'MEMBRO_GRUPPO'];
export const CODICE_ITALIA = '100000100';

export interface IdentifiableGuest extends StayGuestRequest {
  _id: string;
  /** UI-only: stato codice for placeOfBirth logic */
  _statoDiNascita: string;
  /** UI-only: stato codice for documentPlaceOfIssue logic */
  _statoRilascioDoc: string;
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

type GuestErrorTranslator = (key: string, options?: Record<string, unknown>) => string;

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
