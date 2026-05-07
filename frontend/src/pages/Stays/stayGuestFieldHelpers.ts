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
