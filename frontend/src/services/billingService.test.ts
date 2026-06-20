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

  it('should fetch all invoices', async () => {
    const mockInvoices = [{ id: 'inv1', status: 'ISSUED' }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: { content: mockInvoices } });

    const result = await billingService.getAllInvoices();

    expect(api.get).toHaveBeenCalledWith('/api/v1/invoices');
    expect(result).toEqual(mockInvoices);
  });

  it('should download the invoice PDF as a blob', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: 'pdf-bytes' });

    const createObjectURL = vi.fn(() => 'blob:http://test/pdf');
    const revokeObjectURL = vi.fn();
    const clickFn = vi.fn();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });
    const link = { href: '', download: '', click: clickFn } as unknown as HTMLAnchorElement;
    const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue(link);
    const appendChildSpy = vi.spyOn(document.body, 'appendChild').mockImplementation((n) => n);
    const removeChildSpy = vi.spyOn(document.body, 'removeChild').mockImplementation((n) => n);

    await billingService.downloadPdf('inv1', 'INV-001');

    expect(api.get).toHaveBeenCalledWith('/api/v1/invoices/inv1/pdf', { responseType: 'blob' });
    expect(createObjectURL).toHaveBeenCalled();
    expect(link.download).toBe('INV-001.pdf');
    expect(clickFn).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:http://test/pdf');
    createElementSpy.mockRestore();
    appendChildSpy.mockRestore();
    removeChildSpy.mockRestore();
  });
});
