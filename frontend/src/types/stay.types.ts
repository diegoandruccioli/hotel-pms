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
  /** Whether the most recent Alloggiati Web submission attempt for this stay failed. */
  alloggiatiSendFailed: boolean;
  /** Error message from the most recent failed attempt; null once resolved. */
  alloggiatiFailureReason?: string | null;
  guests?: StayGuestResponse[];
  /** Denormalized "Cognome Nome" set at check-in; null for legacy stays. */
  guestDisplayName?: string | null;
  /** Denormalized room number set at check-in; null for legacy stays. */
  roomNumber?: string | null;
  /** Expected check-out date sourced from the reservation (or walk-in request) at check-in; null for legacy stays. */
  expectedCheckOutDate?: string | null;
  /** Whether the most recent billing-invoice-creation attempt at check-in failed. */
  invoiceCreationFailed: boolean;
  /** Error message from the most recent failed invoice-creation attempt; null once resolved. */
  invoiceCreationFailureReason?: string | null;
  /** Whether the most recent checkout summary email attempt failed. */
  checkoutEmailFailed: boolean;
  /** Error message from the most recent failed checkout email attempt; null once resolved. */
  checkoutEmailFailureReason?: string | null;
  /**
   * Non-null only on the response from the check-in call itself, when the tourist tax
   * could not actually be assessed (configuration gap, or the hotel declared it not
   * applicable). Always null on every other response.
   */
  cityTaxWarning?: CityTaxUnassessedReason | null;
}

/** Why a stay's tourist tax has no amount assessed — see StayResponse.cityTaxWarning. */
export type CityTaxUnassessedReason =
  | 'COMUNE_NOT_CONFIGURED'
  | 'CATEGORY_NOT_RECORDED'
  | 'NO_RATE_FOR_DATE'
  | 'NOT_APPLICABLE';

/** Whether a hotel's comune levies a tourist tax at all — GET/PUT city-tax-rates/applicability. */
export type CityTaxApplicability = 'UNKNOWN' | 'NOT_APPLICABLE' | 'APPLICABLE';

export interface CityTaxApplicabilityRequest {
  applicability: CityTaxApplicability;
}

export interface CityTaxApplicabilityResponse {
  applicability: CityTaxApplicability;
}

/** Whether a check-in today would actually get its tourist tax assessed — the check-in form's pre-flight check. */
export interface CityTaxConfigurationStatusResponse {
  configured: boolean;
  reason?: CityTaxUnassessedReason | null;
}

/** Summary of stays whose tourist tax was never assessed because of a configuration gap. */
export interface CityTaxUnassessedSummaryResponse {
  unassessedCount: number;
  mostRecentUnassessedAt?: string | null;
  mostRecentReason?: CityTaxUnassessedReason | null;
}

/** One stay's tourist-tax correction in a backfill preview/confirm result. */
export interface CityTaxBackfillLineResponse {
  stayId: string;
  checkInDate: string;
  amount: number;
  charged: boolean;
  skipReason?: string | null;
}

export interface CityTaxBackfillResponse {
  lines: CityTaxBackfillLineResponse[];
  totalAmount: number;
  chargedCount: number;
  skippedCount: number;
}

/** Summary of unresolved Alloggiati Web submission failures for the caller's hotel. */
export interface AlloggiatiFailureSummaryResponse {
  failedCount: number;
  mostRecentFailureAt?: string | null;
  mostRecentFailureReason?: string | null;
}

export interface HotelSettingsRequest {
  /** Undefined = leave unchanged (partial-patch semantics on every field here). */
  alloggiatiAutoSend?: boolean;
  hotelName?: string;
  address?: string;
  vatNumber?: string;
  fiscalCode?: string;
  logoUrl?: string;
  alloggiatiUsername?: string;
  /** Write-only: blank/undefined leaves the currently stored password unchanged. */
  alloggiatiPassword?: string;
  /** Write-only: blank/undefined leaves the currently stored WsKey unchanged. */
  alloggiatiWsKey?: string;
  /** Undefined = leave unchanged. Whether the guest is emailed on reservation creation. */
  sendReservationConfirmedEmail?: boolean;
  /** Undefined = leave unchanged. Whether the guest is emailed a summary at check-out. */
  sendCheckoutEmail?: boolean;
  /** Custom subject line for the reservation-confirmed email; blank/undefined = default. */
  emailSubjectReservationConfirmed?: string;
  /** Custom subject line for the checkout email; blank/undefined = default. */
  emailSubjectCheckout?: string;
  /** Greeting/signature line appended to every transactional email footer. */
  emailGreetingText?: string;
  /** CAP — Italian 5-digit postal code. Required only to export a valid FatturaPA XML. */
  cap?: string;
  /** Comune — municipality name, validated together with provincia. */
  comune?: string;
  /** Provincia — 2-letter province code, e.g. "RM". */
  provincia?: string;
}

export interface HotelSettingsResponse {
  hotelId: string;
  alloggiatiAutoSend: boolean;
  hotelName?: string | null;
  address?: string | null;
  vatNumber?: string | null;
  fiscalCode?: string | null;
  logoUrl?: string | null;
  alloggiatiUsername?: string | null;
  alloggiatiCredentialsConfigured: boolean;
  sendReservationConfirmedEmail: boolean;
  sendCheckoutEmail: boolean;
  emailSubjectReservationConfirmed?: string | null;
  emailSubjectCheckout?: string | null;
  emailGreetingText?: string | null;
  cap?: string | null;
  comune?: string | null;
  provincia?: string | null;
}

/** E18: request to record a new hotel classification/category entry (append-only). */
export interface HotelCategoryHistoryRequest {
  category: string;
  validFrom: string;
}

export interface HotelCategoryHistoryResponse {
  id: string;
  category: string;
  validFrom: string;
  /** null = this is the hotel's current category. */
  validTo: string | null;
}

/**
 * E18: request to create a tourist-tax rate rule. The comune is resolved
 * server-side from the hotel's own settings — never sent from the client.
 */
export interface CityTaxRateRequest {
  category: string;
  amountPerNight: number;
  /** Cap on taxable nights; omit/undefined = uncapped. */
  maxTaxableNights?: number | null;
  /** Guests strictly under this age at check-in are exempt; omit/undefined = no age exemption. */
  exemptUnderAge?: number | null;
  validFrom: string;
  note?: string | null;
}

export interface CityTaxRateResponse {
  id: string;
  comuneCodice: string;
  category: string;
  amountPerNight: number;
  maxTaxableNights: number | null;
  exemptUnderAge: number | null;
  validFrom: string;
  validTo: string | null;
  note: string | null;
}
