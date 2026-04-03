import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { billingService } from './billingService';

vi.mock('./api');

describe('billingService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should create an invoice', async () => {
    const request = { reservationId: 'r1', guestId: 'g1', totalAmount: 200 };
    const mockResponse = { id: 'inv1', ...request, status: 'ISSUED' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await billingService.createInvoice(request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/invoices', request);
    expect(result).toEqual(mockResponse);
  });

  it('should fetch invoice by id', async () => {
    const mock = { id: 'inv1', status: 'ISSUED' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mock });

    const result = await billingService.getInvoiceById('inv1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/invoices/inv1');
    expect(result).toEqual(mock);
  });

  it('should process a payment', async () => {
    const request = { amount: 100, method: 'CASH' };
    const mockResponse = { id: 'p1', ...request };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await billingService.processPayment('inv1', request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/invoices/inv1/payments', request);
    expect(result).toEqual(mockResponse);
  });
});
