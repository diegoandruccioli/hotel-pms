export type StayStatus = 'EXPECTED' | 'CHECKED_IN' | 'CHECKED_OUT';

export interface StayGuestResponse {
  id: string;
  firstName: string;
  lastName: string;
  gender: string;
  dateOfBirth: string;
  placeOfBirth: string;
  citizenship: string;
  documentType: string;
  documentNumber: string;
  documentPlaceOfIssue: string;
  isPrimaryGuest: boolean;
}

export interface StayGuestRequest {
  firstName: string;
  lastName: string;
  gender: string;
  dateOfBirth: string;
  placeOfBirth: string;
  citizenship: string;
  documentType: string;
  documentNumber: string;
  documentPlaceOfIssue: string;
  isPrimaryGuest: boolean;
  travellerType?: string;
  travelPurpose?: string;
}

export interface StayRequest {
  hotelId?: string;
  reservationId: string;
  guestId: string;
  roomId: string;
  status: StayStatus;
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
  guests?: StayGuestResponse[];
}
