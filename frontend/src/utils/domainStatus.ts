import type { GroupStatus, InvoiceStatus, QuotationStatus, SdiStatus } from '../types';

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
