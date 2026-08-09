/**
 * No price field — the total is always resolved server-side
 * (RatePricingService) and frozen on each line item at creation time.
 */
export interface QuotationRequest {
  guestId?: string | null;
  prospectFirstName?: string | null;
  prospectLastName?: string | null;
  prospectEmail?: string | null;
  checkInDate: string; // YYYY-MM-DD
  checkOutDate: string; // YYYY-MM-DD
  expectedGuests?: number | null;
  roomIds: string[];
  validUntil: string; // YYYY-MM-DD
}

export interface QuotationLineItemResponse {
  id: string;
  roomId: string;
  roomNumber: string;
  roomTypeName: string;
  price: number;
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
  totalPrice: number;
  lineItems: QuotationLineItemResponse[];
  sendFailed: boolean;
  sendFailureReason: string | null;
  createdAt: string;
  updatedAt: string;
}
