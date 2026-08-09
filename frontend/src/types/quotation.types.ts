/**
 * No price field on any option — every total is always resolved server-side
 * (RatePricingService) and frozen on each line item at creation time.
 */
export interface QuotationOptionRequest {
  label: string;
  roomIds: string[];
}

export interface QuotationRequest {
  guestId?: string | null;
  prospectFirstName?: string | null;
  prospectLastName?: string | null;
  prospectEmail?: string | null;
  checkInDate: string; // YYYY-MM-DD
  checkOutDate: string; // YYYY-MM-DD
  expectedGuests?: number | null;
  options: QuotationOptionRequest[];
  validUntil: string; // YYYY-MM-DD
}

export interface QuotationLineItemResponse {
  id: string;
  roomId: string;
  roomNumber: string;
  roomTypeName: string;
  price: number;
}

export interface QuotationOptionResponse {
  id: string;
  label: string;
  position: number;
  totalPrice: number;
  lineItems: QuotationLineItemResponse[];
}

export type QuotationStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED';

export interface QuotationResponse {
  id: string;
  guestId: string | null;
  guestFullName: string;
  prospectEmail: string | null;
  checkInDate: string;
  checkOutDate: string;
  expectedGuests: number | null;
  status: QuotationStatus;
  validUntil: string;
  /** The lowest option's totalPrice — used for list sorting/display. */
  totalPrice: number;
  options: QuotationOptionResponse[];
  acceptedOptionId: string | null;
  sendFailed: boolean;
  sendFailureReason: string | null;
  createdAt: string;
  updatedAt: string;
}
