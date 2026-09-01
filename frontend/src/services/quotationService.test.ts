import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { quotationService } from './quotationService';

vi.mock('./api');

describe('quotationService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fetch all quotations with pagination defaults', async () => {
    const mockPage = { content: [{ id: 'q1' }], totalPages: 1 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockPage });

    const result = await quotationService.getAllQuotations();

    expect(api.get).toHaveBeenCalledWith('/api/v1/quotations?page=0&size=20&sort=createdAt,desc');
    expect(result).toEqual(mockPage);
  });

  it('should fetch quotations for a given page and size', async () => {
    const mockPage = { content: [], totalPages: 3 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockPage });

    const result = await quotationService.getAllQuotations(2, 10);

    expect(api.get).toHaveBeenCalledWith('/api/v1/quotations?page=2&size=10&sort=createdAt,desc');
    expect(result).toEqual(mockPage);
  });

  it('should fetch quotation by id', async () => {
    const mock = { id: 'q1', status: 'DRAFT' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mock });

    const result = await quotationService.getQuotationById('q1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/quotations/q1');
    expect(result).toEqual(mock);
  });

  it('should create a quotation', async () => {
    const request = { guestId: 'g1' };
    const mockResponse = { id: 'q1', ...request };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await quotationService.createQuotation(request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/quotations', request);
    expect(result).toEqual(mockResponse);
  });

  it('should update a quotation', async () => {
    const request = { guestId: 'g1' };
    const mockResponse = { id: 'q1', ...request };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await quotationService.updateQuotation('q1', request as never);

    expect(api.put).toHaveBeenCalledWith('/api/v1/quotations/q1', request);
    expect(result).toEqual(mockResponse);
  });

  it('should duplicate a quotation', async () => {
    const mockResponse = { id: 'q2', status: 'DRAFT' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await quotationService.duplicateQuotation('q1');

    expect(api.post).toHaveBeenCalledWith('/api/v1/quotations/q1/duplicate', {});
    expect(result).toEqual(mockResponse);
  });

  it('should send a quotation', async () => {
    const mockResponse = { id: 'q1', status: 'SENT' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await quotationService.sendQuotation('q1');

    expect(api.post).toHaveBeenCalledWith('/api/v1/quotations/q1/send', {});
    expect(result).toEqual(mockResponse);
  });

  it('should convert a quotation to a reservation without an option id', async () => {
    const mockResponse = { id: 'r1', status: 'CONFIRMED' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await quotationService.convertToReservation('q1');

    expect(api.post).toHaveBeenCalledWith('/api/v1/quotations/q1/convert', {});
    expect(result).toEqual(mockResponse);
  });

  it('should convert a quotation to a reservation with a chosen option id', async () => {
    const mockResponse = { id: 'r1', status: 'CONFIRMED' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await quotationService.convertToReservation('q1', 'opt2');

    expect(api.post).toHaveBeenCalledWith('/api/v1/quotations/q1/convert', { optionId: 'opt2' });
    expect(result).toEqual(mockResponse);
  });

  it('should decline a quotation', async () => {
    const mockResponse = { id: 'q1', status: 'DECLINED' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await quotationService.declineQuotation('q1');

    expect(api.post).toHaveBeenCalledWith('/api/v1/quotations/q1/decline', {});
    expect(result).toEqual(mockResponse);
  });

  it('should delete a quotation', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({ data: undefined });

    await quotationService.deleteQuotation('q1');

    expect(api.delete).toHaveBeenCalledWith('/api/v1/quotations/q1');
  });

  it('should trigger the quotation PDF download via a hidden iframe', () => {
    vi.useFakeTimers();
    const iframe = { style: {} as CSSStyleDeclaration, src: '' } as HTMLIFrameElement;
    const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue(iframe);
    const appendChildSpy = vi.spyOn(document.body, 'appendChild').mockImplementation((n) => n);
    const removeChildSpy = vi.spyOn(document.body, 'removeChild').mockImplementation((n) => n);

    quotationService.downloadPdf('q1');

    expect(iframe.src).toBe('/api/v1/quotations/q1/pdf');
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

  it('should fetch the quotation PDF as a blob for inline preview', async () => {
    const mockBlob = new Blob(['pdf-bytes']);
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockBlob });

    const result = await quotationService.getPdfBlob('q1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/quotations/q1/pdf', { responseType: 'blob' });
    expect(result).toBe(mockBlob);
  });
});
