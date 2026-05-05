export type StayStatus = 'EXPECTED' | 'CHECKED_IN' | 'CHECKED_OUT';

export interface AvailableRoom {
  id: string;
  roomNumber: string;
  status: string;
  roomType?: { name: string; basePrice?: number } | null;
}

export type TravellerType =
  | 'OSPITE_SINGOLO'
  | 'CAPOFAMIGLIA'
  | 'CAPOGRUPPO'
  | 'FAMILIARE'
  | 'MEMBRO_GRUPPO';

export interface AlloggiatiStato {
  codice: string;
  descrizione: string;
  dataFineVal?: string | null;
}

export interface AlloggiatiComune {
  codice: string;
  descrizione: string;
  provincia: string;
  dataFineVal?: string | null;
}

export interface AlloggiatiTipdoc {
  codice: string;
  descrizione: string;
}

export interface StayGuestResponse {
  id: string;
  firstName: string;
  lastName: string;
  /** "1" = Maschio, "2" = Femmina */
  gender: string;
  dateOfBirth: string;
  /** 9-char comune code (Italian-born) or 9-char stato code (foreign-born) */
  placeOfBirth: string;
  /** 9-char stato code from the Portale Alloggiati Web lookup */
  citizenship: string;
  /** 5-char tipdoc code — null for FAMILIARE/MEMBRO_GRUPPO */
  documentType?: string | null;
  documentNumber?: string | null;
  /** 9-char comune or stato code — null for FAMILIARE/MEMBRO_GRUPPO */
  documentPlaceOfIssue?: string | null;
  isPrimaryGuest: boolean;
  travellerType?: TravellerType;
  travelPurpose?: string;
}

export interface StayGuestRequest {
  firstName: string;
  lastName: string;
  /** "1" = Maschio, "2" = Femmina */
  gender: string;
  dateOfBirth: string;
  /** 9-char comune code (Italian-born) or 9-char stato code (foreign-born) */
  placeOfBirth: string;
  /** 9-char stato code */
  citizenship: string;
  /** 5-char tipdoc code — omit for FAMILIARE/MEMBRO_GRUPPO */
  documentType?: string;
  documentNumber?: string;
  /** 9-char comune or stato code — omit for FAMILIARE/MEMBRO_GRUPPO */
  documentPlaceOfIssue?: string;
  isPrimaryGuest: boolean;
  travellerType?: TravellerType;
  travelPurpose?: string;
}

export interface StayRequest {
  hotelId?: string;
  /** Null for walk-in check-ins (no reservation). */
  reservationId?: string;
  guestId: string;
  roomId: string;
  status: StayStatus;
  /** Required for walk-in check-ins (ISO date string YYYY-MM-DD). */
  expectedCheckOutDate?: string;
  actualCheckInTime?: string;
  actualCheckOutTime?: string;
  guests: StayGuestRequest[];
}

export interface StayResponse {
  id: string; // UUID
  reservationId: string;
  guestId: string;
  roomId: string;
  status: StayStatus;
  actualCheckInTime?: string;
  actualCheckOutTime?: string;
  createdAt: string;
  updatedAt: string;
  alloggiatiSent: boolean;
  guests?: StayGuestResponse[];
}

export interface HotelSettingsRequest {
  alloggiatiAutoSend: boolean;
}

export interface HotelSettingsResponse {
  hotelId: string;
  alloggiatiAutoSend: boolean;
}
