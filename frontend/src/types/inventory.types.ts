
export type RoomStatus = 'CLEAN' | 'DIRTY' | 'MAINTENANCE' | 'OCCUPIED';

export interface RoomTypeResponse {
  id: string;
  name: string;
  description?: string;
  maxOccupancy: number;
  basePrice: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RoomTypeRequest {
  name: string;
  description?: string;
  maxOccupancy: number;
  basePrice: number;
}

export interface RoomResponse {
  id: string;
  hotelId: string;
  roomNumber: string;
  roomType: RoomTypeResponse;
  type?: string;
  status: RoomStatus;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  /**
   * The total price for a specific stay, resolved server-side (RatePricingService);
   * only present when this response comes from `getAvailableRooms` (the one
   * endpoint that knows the stay's dates) — undefined otherwise.
   */
  resolvedTotalPrice?: number;
}

export interface RoomRequest {
  hotelId?: string; // Optional for frontend request payload if defaulted by backend/gateway
  roomNumber: string;
  roomTypeId: string;
  status: RoomStatus;
}

export interface RateSeasonResponse {
  id: string;
  roomTypeId: string;
  name?: string;
  startDate: string;
  endDate: string;
  nightlyPrice: number;
}

export interface RateSeasonRequest {
  name?: string;
  startDate: string;
  endDate: string;
  nightlyPrice: number;
}

export interface RateCalendarDay {
  date: string;
  price: number;
  /** The covering season's id, or null when this day uses the room type's base price. */
  rateSeasonId: string | null;
  seasonName: string | null;
}

export interface RateCalendarRow {
  roomTypeId: string;
  roomTypeName: string;
  basePrice: number;
  days: RateCalendarDay[];
}

export interface RateCalendarResponse {
  rows: RateCalendarRow[];
}

export interface RateBulkApplyRequest {
  roomTypeIds: string[];
  startDate: string;
  endDate: string;
  nightlyPrice: number;
  name?: string;
}


