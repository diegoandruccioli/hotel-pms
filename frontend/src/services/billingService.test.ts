import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { billingService } from './billingService';

vi.mock('./api');

describe('billingService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
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

  it('should search invoices with default pagination when no filters are given', async () => {
    const mockPage = { content: [{ invoice: { id: 'inv1', status: 'ISSUED' }, guestName: 'Mario Rossi' }] };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockPage });

    const result = await billingService.searchInvoices({});

    expect(api.get).toHaveBeenCalledWith('/api/v1/invoices/search?page=0&size=20');
    expect(result).toEqual(mockPage);
  });

  it('should search invoices with all filters set', async () => {
    const mockPage = { content: [] };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockPage });

    await billingService.searchInvoices({
      status: 'PAID',
      query: '  mario  ',
      dateFrom: '2026-08-01',
      dateTo: '2026-08-31',
      page: 2,
      size: 10,
    });

    expect(api.get).toHaveBeenCalledWith(
      '/api/v1/invoices/search?page=2&size=10&status=PAID&query=mario&dateFrom=2026-08-01&dateTo=2026-08-31',
    );
  });

  it('should omit an empty/whitespace-only search query from the request', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: { content: [] } });

    await billingService.searchInvoices({ query: '   ' });

    expect(api.get).toHaveBeenCalledWith('/api/v1/invoices/search?page=0&size=20');
  });

  it('should trigger the invoice PDF download via a hidden iframe', () => {
    vi.useFakeTimers();
    const iframe = { style: {} as CSSStyleDeclaration, src: '' } as HTMLIFrameElement;
    const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue(iframe);
    const appendChildSpy = vi.spyOn(document.body, 'appendChild').mockImplementation((n) => n);
    const removeChildSpy = vi.spyOn(document.body, 'removeChild').mockImplementation((n) => n);

    billingService.downloadPdf('inv1');

    expect(iframe.src).toBe('/api/v1/invoices/inv1/pdf');
    expect(iframe.style.display).toBe('none');
    expect(appendChildSpy).toHaveBeenCalledWith(iframe);
    expect(removeChildSpy).not.toHaveBeenCalled();

    vi.runAllTimers();
    expect(removeChildSpy).toHaveBeenCalledWith(iframe);

    createElementSpy.mockRestore();
    appendChildSpy.mockRestore();
    removeChildSpy.mockRestore();
    vi.useRealTimers();
  });
});
