import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { stayService } from './stayService';

vi.mock('./api');

describe('stayService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fetch all stays paginated', async () => {
    const mockStays = [{ id: '1', status: 'CHECKED_IN' }];
    const mockPage = { content: mockStays, totalElements: 1, totalPages: 1, number: 0, size: 20,
      numberOfElements: 1, first: true, last: true, empty: false };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockPage });

    const result = await stayService.getAllStays(0, 20);

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays?page=0&size=20&sort=actualCheckInTime,desc');
    expect(result).toEqual(mockPage);
  });

  it('should fetch stay by id', async () => {
    const mockStay = { id: '1', status: 'CHECKED_IN' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockStay });

    const result = await stayService.getStayById('1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/1');
    expect(result).toEqual(mockStay);
  });

  it('should create a stay (check-in)', async () => {
    const request = { guestId: 'g1', reservationId: 'r1', roomId: 'rm1' };
    const mockResponse = { id: '1', ...request, status: 'CHECKED_IN' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.createStay(request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/stays', request);
    expect(result).toEqual(mockResponse);
  });

  it('should submit alloggiati report', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: null, status: 200 });

    await stayService.submitAlloggiatiReport('2026-05-15');

    expect(api.post).toHaveBeenCalledWith(
      '/api/v1/stays/reports/alloggiati/submit',
      null,
      { params: { date: '2026-05-15' } },
    );
  });

  it('should propagate error from submitAlloggiatiReport', async () => {
    vi.mocked(api.post).mockRejectedValueOnce(new Error('Portal error'));

    await expect(stayService.submitAlloggiatiReport('2026-05-15')).rejects.toThrow('Portal error');
  });

  it('should check out', async () => {
    const mockResponse = { id: '1', status: 'CHECKED_OUT' };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.checkOut('1');

    expect(api.put).toHaveBeenCalledWith('/api/v1/stays/1/check-out', {});
    expect(result).toEqual(mockResponse);
  });

  it('should retry invoice creation', async () => {
    const mockResponse = { id: '1', invoiceCreationFailed: false };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.retryInvoiceCreation('1');

    expect(api.post).toHaveBeenCalledWith('/api/v1/stays/1/invoice/retry', {});
    expect(result).toEqual(mockResponse);
  });

  it('should retry checkout email', async () => {
    const mockResponse = { id: '1', checkoutEmailFailed: false };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.retryCheckoutEmail('1');

    expect(api.post).toHaveBeenCalledWith('/api/v1/stays/1/checkout-email/retry', {});
    expect(result).toEqual(mockResponse);
  });
});

describe('stayService — settings, lookups, downloads', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fetch hotel settings', async () => {
    const mockSettings = { hotelId: 'h1', alloggiatiAutoSend: true, hotelName: 'Hotel Test' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockSettings });

    const result = await stayService.getHotelSettings();

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/settings');
    expect(result).toEqual(mockSettings);
  });

  it('should update hotel settings', async () => {
    const request = { alloggiatiAutoSend: false, hotelName: 'Hotel Updated' };
    const mockResponse = { hotelId: 'h1', ...request };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.updateHotelSettings(request);

    expect(api.put).toHaveBeenCalledWith('/api/v1/stays/settings', request);
    expect(result).toEqual(mockResponse);
  });

  it('should fetch the last completed stay for a guest', async () => {
    const mockStay = { id: '1', status: 'CHECKED_OUT' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockStay, status: 200 });

    const result = await stayService.getLastCompletedStayForGuest('g1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/guest/g1/latest', {
      validateStatus: expect.any(Function),
    });
    expect(result).toEqual(mockStay);
  });

  it('should return null when no completed stay exists for a guest (204)', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: null, status: 204 });

    const result = await stayService.getLastCompletedStayForGuest('g1');

    expect(result).toBeNull();
  });

  it('accepts 200 and 204 as valid statuses but rejects others in validateStatus', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: null, status: 200 });

    await stayService.getLastCompletedStayForGuest('g1');

    const { validateStatus } = vi.mocked(api.get).mock.calls[0][1] as {
      validateStatus: (status: number) => boolean;
    };
    expect(validateStatus(200)).toBe(true);
    expect(validateStatus(204)).toBe(true);
    expect(validateStatus(404)).toBe(false);
  });

  it('should fetch Alloggiati stati lookup', async () => {
    const mockStati = [{ codice: '100000100', descrizione: 'ITALIA' }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockStati });

    const result = await stayService.getLookupStati();

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/lookup/stati');
    expect(result).toEqual(mockStati);
  });

  it('should fetch Alloggiati tipdoc lookup', async () => {
    const mockTipdoc = [{ codice: 'IDENT', descrizione: "CARTA DI IDENTITA'" }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockTipdoc });

    const result = await stayService.getLookupTipdoc();

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/lookup/tipdoc');
    expect(result).toEqual(mockTipdoc);
  });

  it('should search Alloggiati comuni lookup with provincia', async () => {
    const mockComuni = [{ codice: '412058036', descrizione: 'FIANO ROMANO', provincia: 'RM' }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockComuni });

    const result = await stayService.searchLookupComuni('Fiano', 'RM');

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/lookup/comuni', {
      params: { q: 'Fiano', provincia: 'RM' },
    });
    expect(result).toEqual(mockComuni);
  });

  it('should search Alloggiati comuni lookup without provincia', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] });

    await stayService.searchLookupComuni('Roma');

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/lookup/comuni', {
      params: { q: 'Roma', provincia: undefined },
    });
  });

  it('should fetch Alloggiati failure summary', async () => {
    const mockSummary = { failedCount: 2, mostRecentFailureAt: '2026-06-19T10:00:00', mostRecentFailureReason: 'PS portal down' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockSummary });

    const result = await stayService.getAlloggiatiFailureSummary();

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/reports/alloggiati/failures/summary');
    expect(result).toEqual(mockSummary);
  });

  it('should fetch available (CLEAN) rooms only', async () => {
    const mockRooms = [
      { id: 'r1', roomNumber: '101', status: 'CLEAN' },
      { id: 'r2', roomNumber: '102', status: 'DIRTY' },
    ];
    vi.mocked(api.get).mockResolvedValueOnce({ data: { content: mockRooms } });

    const result = await stayService.getAvailableRooms();

    expect(api.get).toHaveBeenCalledWith('/api/v1/rooms', { params: { size: 200 } });
    expect(result).toEqual([mockRooms[0]]);
  });

  it('should return an empty array when rooms response has no content', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: {} });

    const result = await stayService.getAvailableRooms();

    expect(result).toEqual([]);
  });

  it('should download the Alloggiati txt report as a blob', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: 'plain text content' });

    const createObjectURL = vi.fn(() => 'blob:http://test/txt');
    const revokeObjectURL = vi.fn();
    const clickFn = vi.fn();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });
    const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue({
      href: '',
      download: '',
      click: clickFn,
    } as unknown as HTMLAnchorElement);

    await stayService.downloadAlloggiatiReport('2026-06-20');

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/reports/alloggiati', {
      params: { date: '2026-06-20' },
      responseType: 'blob',
    });
    expect(createObjectURL).toHaveBeenCalled();
    expect(clickFn).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:http://test/txt');
    createElementSpy.mockRestore();
  });

  it('should download the Alloggiati json export as a blob', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: '[]' });

    const createObjectURL = vi.fn(() => 'blob:http://test/json');
    const revokeObjectURL = vi.fn();
    const clickFn = vi.fn();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });
    const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue({
      href: '',
      download: '',
      click: clickFn,
    } as unknown as HTMLAnchorElement);

    await stayService.downloadAlloggiatiJson('2026-06-20');

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/reports/alloggiati/json', {
      params: { date: '2026-06-20' },
      responseType: 'blob',
    });
    expect(createObjectURL).toHaveBeenCalled();
    expect(clickFn).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:http://test/json');
    createElementSpy.mockRestore();
  });
});
