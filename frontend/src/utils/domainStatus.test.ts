import { describe, it, expect } from 'vitest';
import {
  groupStatusTone,
  invoiceStatusTone,
  quotationStatusTone,
  sdiStatusTone,
} from './domainStatus';

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
});
