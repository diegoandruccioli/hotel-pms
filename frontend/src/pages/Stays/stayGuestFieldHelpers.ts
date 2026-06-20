import { z } from 'zod';
import type { StayGuestRequest, TravellerType } from '../../types/stay.types';

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
      const number = idx + 1;
      const hasDoc = !TYPES_WITHOUT_DOC.includes(g.travellerType as TravellerType);
      const isItalianBorn = g._statoDiNascita === CODICE_ITALIA;
      const isItalianDocIssue = g._statoRilascioDoc === CODICE_ITALIA;

      if (!g._statoDiNascita) {
        ctx.addIssue({ code: 'custom', path: [idx, '_statoDiNascita'], message: t('err_stato_nascita_required', { number }) });
      }
      if (isItalianBorn && !g.placeOfBirth) {
        ctx.addIssue({ code: 'custom', path: [idx, 'placeOfBirth'], message: t('err_comune_nascita_required', { number }) });
      }
      if (hasDoc) {
        if (!g._statoRilascioDoc) {
          ctx.addIssue({ code: 'custom', path: [idx, '_statoRilascioDoc'], message: t('err_stato_rilascio_required', { number }) });
        }
        if (isItalianDocIssue && !g.documentPlaceOfIssue) {
          ctx.addIssue({ code: 'custom', path: [idx, 'documentPlaceOfIssue'], message: t('err_comune_rilascio_required', { number }) });
        }
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
