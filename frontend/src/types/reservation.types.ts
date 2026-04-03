export interface ReservationLineItemRequest {
  roomId: string;
  price: number;
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
}
