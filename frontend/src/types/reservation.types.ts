/**
 * Deliberately has no `price` field: the price is always resolved server-side
 * (RatePricingService) and snapshotted onto the line item at creation/update
 * time — a client-supplied price was never validated against anything and was
 * silently ignored downstream.
 */
export interface ReservationLineItemRequest {
  roomId: string;
}

export interface ReservationLineItemResponse {
  id: string; // UUID
  roomId: string;
  price: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ReservationRequest {
  guestId: string; // UUID
  checkInDate: string; // YYYY-MM-DD
  checkOutDate: string; // YYYY-MM-DD
  status: string;
  expectedGuests: number;
  lineItems: ReservationLineItemRequest[];
}

export interface ReservationResponse {
  id: string; // UUID
  guestId: string; // UUID
  guestFullName?: string;
  checkInDate: string;
  checkOutDate: string;
  status: string;
  expectedGuests: number;
  actualGuests?: number;
  lineItems: ReservationLineItemResponse[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
  /** Whether the most recent reservation-confirmed email attempt failed. */
  confirmationEmailFailed: boolean;
  /** Error message from the most recent failed attempt; null once resolved. */
  confirmationEmailFailureReason?: string | null;
}
