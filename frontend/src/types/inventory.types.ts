
export type RoomStatus = 'CLEAN' | 'DIRTY' | 'MAINTENANCE';

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
  pricePerNight?: number;
  status: RoomStatus;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RoomRequest {
  hotelId?: string; // Optional for frontend request payload if defaulted by backend/gateway
  roomNumber: string;
  roomTypeId: string;
  status: RoomStatus;
}


