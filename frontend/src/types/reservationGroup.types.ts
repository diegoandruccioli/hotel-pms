export type GroupStatus = 'PLANNED' | 'CONFIRMED' | 'CHECKED_IN' | 'CHECKED_OUT' | 'CANCELLED';

/** One rooming-list row submitted when creating a group. */
export interface RoomingListEntryRequest {
  guestId: string;
  roomId: string;
  expectedGuests: number;
  billedToMasterFolio: boolean;
}

export interface ReservationGroupCreateRequest {
  name: string;
  companyName?: string | null;
  contactGuestId: string;
  checkInDate: string;
  checkOutDate: string;
  groupRatePerNight?: number | null;
  notes?: string | null;
  openMasterFolio: boolean;
  rooms: RoomingListEntryRequest[];
}

/** One rooming-list row as returned by the server, enriched with the guest's name. */
export interface GroupMemberResponse {
  reservationId: string;
  guestId: string;
  guestFullName: string;
  roomId: string | null;
  expectedGuests: number;
  actualGuests: number;
  checkInDate: string;
  checkOutDate: string;
  status: string;
  billedToMasterFolio: boolean;
  price: number | null;
}

export interface ReservationGroupResponse {
  id: string;
  name: string;
  companyName: string | null;
  contactGuestId: string;
  contactGuestName: string;
  checkInDate: string;
  checkOutDate: string;
  status: GroupStatus;
  groupRatePerNight: number | null;
  masterFolioInvoiceId: string | null;
  notes: string | null;
  members: GroupMemberResponse[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
  version: number;
}

/** Per-room outcome of a bulk group check-out. */
export interface GroupCheckoutOutcome {
  reservationId: string;
  stayId: string | null;
  success: boolean;
  errorCode: string | null;
}
