import type {
  GroupStatus,
  InvoiceStatus,
  NightAuditRunResponse,
  OrderStatus,
  QuotationStatus,
  ReservationStatus,
  Role,
  RoomStatus,
  SdiStatus,
  StayStatus,
} from '../types';

export type StatusTone = 'success' | 'warning' | 'error' | 'info' | 'neutral';

export const invoiceStatusTone: Record<InvoiceStatus, StatusTone> = {
  ISSUED: 'warning',
  PAID: 'success',
  CANCELLED: 'error',
};

export const sdiStatusTone: Record<SdiStatus, StatusTone> = {
  NOT_SENT: 'neutral',
  SENT: 'warning',
  ACCEPTED: 'success',
  REJECTED: 'error',
};

export const quotationStatusTone: Record<QuotationStatus, StatusTone> = {
  DRAFT: 'neutral',
  SENT: 'info',
  ACCEPTED: 'success',
  DECLINED: 'error',
  EXPIRED: 'warning',
};

export const groupStatusTone: Record<GroupStatus, StatusTone> = {
  PLANNED: 'neutral',
  CONFIRMED: 'neutral',
  CHECKED_IN: 'warning',
  CHECKED_OUT: 'success',
  CANCELLED: 'error',
};

/** Canonical room colours: dirty = needs attention (warning), maintenance =
 * out of service (error). */
export const roomStatusTone: Record<RoomStatus, StatusTone> = {
  CLEAN: 'success',
  DIRTY: 'warning',
  MAINTENANCE: 'error',
  OCCUPIED: 'info',
};

/** Staff role chip: the two management roles stand out from front-desk and guest accounts. */
export const userRoleTone: Record<Role, StatusTone> = {
  ADMIN: 'info',
  OWNER: 'success',
  RECEPTIONIST: 'neutral',
  GUEST: 'neutral',
};

export const reservationStatusTone: Record<ReservationStatus, StatusTone> = {
  PENDING: 'warning',
  CONFIRMED: 'info',
  PARTIALLY_CHECKED_IN: 'warning',
  CHECKED_IN: 'success',
  CHECKED_OUT: 'neutral',
  CANCELLED: 'error',
  NO_SHOW: 'error',
};

export const stayStatusTone: Record<StayStatus, StatusTone> = {
  EXPECTED: 'info',
  CHECKED_IN: 'success',
  CHECKED_OUT: 'neutral',
  CANCELLED: 'error',
};

export const orderStatusTone: Record<OrderStatus, StatusTone> = {
  PENDING: 'warning',
  PREPARING: 'info',
  PREPARED: 'info',
  READY: 'success',
  DELIVERED: 'neutral',
  CANCELLED: 'error',
  BILLED_TO_ROOM: 'info',
};

export type OrderFilter = 'ALL' | 'OPEN' | 'CLOSED' | 'CANCELLED';

/** Chip order of the restaurant order list. */
export const ORDER_FILTERS: readonly OrderFilter[] = ['ALL', 'OPEN', 'CLOSED', 'CANCELLED'];

const orderFilterStatuses: Record<Exclude<OrderFilter, 'ALL'>, readonly OrderStatus[]> = {
  OPEN: ['PENDING', 'PREPARING', 'PREPARED', 'READY'],
  CLOSED: ['DELIVERED', 'BILLED_TO_ROOM'],
  CANCELLED: ['CANCELLED'],
};

export const matchesOrderFilter = (status: OrderStatus, filter: OrderFilter): boolean =>
  filter === 'ALL' || orderFilterStatuses[filter].includes(status);

export const nightAuditStatusTone: Record<NightAuditRunResponse['status'], StatusTone> = {
  RUNNING: 'neutral',
  COMPLETED: 'success',
  FAILED: 'error',
};
