import { describe, it, expect } from 'vitest';
import {
  groupStatusTone,
  invoiceStatusTone,
  matchesOrderFilter,
  nightAuditStatusTone,
  orderStatusTone,
  quotationStatusTone,
  reservationStatusTone,
  roomStatusTone,
  sdiStatusTone,
  stayStatusTone,
} from './domainStatus';
import type { OrderStatus } from '../types';

describe('domainStatus tone maps', () => {
  it('maps invoice statuses', () => {
    expect(invoiceStatusTone).toEqual({ ISSUED: 'warning', PAID: 'success', CANCELLED: 'error' });
  });

  it('maps SDI statuses', () => {
    expect(sdiStatusTone).toEqual({
      NOT_SENT: 'neutral',
      SENT: 'warning',
      ACCEPTED: 'success',
      REJECTED: 'error',
    });
  });

  it('maps quotation statuses', () => {
    expect(quotationStatusTone).toEqual({
      DRAFT: 'neutral',
      SENT: 'info',
      ACCEPTED: 'success',
      DECLINED: 'error',
      EXPIRED: 'warning',
    });
  });

  it('maps reservation group statuses', () => {
    expect(groupStatusTone).toEqual({
      PLANNED: 'neutral',
      CONFIRMED: 'neutral',
      CHECKED_IN: 'warning',
      CHECKED_OUT: 'success',
      CANCELLED: 'error',
    });
  });

  it('maps room statuses (canonical: dirty = warning, maintenance = error)', () => {
    expect(roomStatusTone).toEqual({
      CLEAN: 'success',
      DIRTY: 'warning',
      MAINTENANCE: 'error',
      OCCUPIED: 'info',
    });
  });

  it('maps reservation statuses (confirmed = info, like the calendar and stays)', () => {
    expect(reservationStatusTone).toEqual({
      PENDING: 'warning',
      CONFIRMED: 'info',
      PARTIALLY_CHECKED_IN: 'warning',
      CHECKED_IN: 'success',
      CHECKED_OUT: 'neutral',
      CANCELLED: 'error',
      NO_SHOW: 'error',
    });
  });

  it('maps stay statuses', () => {
    expect(stayStatusTone).toEqual({
      EXPECTED: 'info',
      CHECKED_IN: 'success',
      CHECKED_OUT: 'neutral',
      CANCELLED: 'error',
    });
  });

  it('maps F&B order statuses', () => {
    expect(orderStatusTone).toEqual({
      PENDING: 'warning',
      PREPARING: 'info',
      PREPARED: 'info',
      READY: 'success',
      DELIVERED: 'neutral',
      CANCELLED: 'error',
      BILLED_TO_ROOM: 'info',
    });
  });

  it('groups every F&B order status into exactly one of open, closed or cancelled', () => {
    const statuses = Object.keys(orderStatusTone) as OrderStatus[];
    for (const status of statuses) {
      const hits = (['OPEN', 'CLOSED', 'CANCELLED'] as const).filter((f) => matchesOrderFilter(status, f));
      expect(hits, status).toHaveLength(1);
      expect(matchesOrderFilter(status, 'ALL')).toBe(true);
    }
    expect(matchesOrderFilter('PENDING', 'OPEN')).toBe(true);
    expect(matchesOrderFilter('BILLED_TO_ROOM', 'CLOSED')).toBe(true);
  });

  it('maps night audit run statuses', () => {
    expect(nightAuditStatusTone).toEqual({ RUNNING: 'neutral', COMPLETED: 'success', FAILED: 'error' });
  });
});
