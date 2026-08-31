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

  // 2026-08-24 follow-up (REPORT.md §6 #3b): these used to fetch a Blob and drive a
  // synthetic <a>.click() + immediate URL.revokeObjectURL() — the exact pattern
  // billingService.ts's downloadPdf documents as verified-broken in real Chrome (no
  // visible file save, silently dropped). Switched to the same hidden-iframe pattern
  // billingService.ts already uses; tests mirror billingService.test.ts's iframe test.

  it('should trigger the Alloggiati txt report download via a hidden iframe', async () => {
    vi.useFakeTimers();
    // validate-then-download (REPORT.md difetto #3): downloadAlloggiatiReport now
    // awaits a real GET before creating the iframe, so a failed/empty report never
    // shows a false-success download — mock that call succeeding.
    vi.mocked(api.get).mockResolvedValueOnce({ data: '' });
    const iframe = { style: {} as CSSStyleDeclaration, src: '' } as HTMLIFrameElement;
    const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue(iframe);
    const appendChildSpy = vi.spyOn(document.body, 'appendChild').mockImplementation((n) => n);
    const removeChildSpy = vi.spyOn(document.body, 'removeChild').mockImplementation((n) => n);

    await stayService.downloadAlloggiatiReport('2026-06-20');

    expect(iframe.src).toBe('/api/v1/stays/reports/alloggiati?date=2026-06-20');
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

  it('should trigger the Alloggiati json export download via a hidden iframe', async () => {
    vi.useFakeTimers();
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] });
    const iframe = { style: {} as CSSStyleDeclaration, src: '' } as HTMLIFrameElement;
    const createElementSpy = vi.spyOn(document, 'createElement').mockReturnValue(iframe);
    const appendChildSpy = vi.spyOn(document.body, 'appendChild').mockImplementation((n) => n);
    const removeChildSpy = vi.spyOn(document.body, 'removeChild').mockImplementation((n) => n);

    await stayService.downloadAlloggiatiJson('2026-06-20');

    expect(iframe.src).toBe('/api/v1/stays/reports/alloggiati/json?date=2026-06-20');
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

describe('stayService — guest lifecycle, extension, city tax', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fetch stays by reservation id', async () => {
    const mockPage = { content: [{ id: 's1' }], totalPages: 1 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockPage });

    const result = await stayService.getStaysByReservationId('res1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays?reservationId=res1');
    expect(result).toEqual(mockPage);
  });

  it('should add a guest to an open stay', async () => {
    const request = { firstName: 'Mario', lastName: 'Rossi' };
    const mockResponse = { id: 'g1', ...request };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.addGuest('stay1', request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/stays/stay1/guests', request);
    expect(result).toEqual(mockResponse);
  });

  it('should update a guest on an open stay', async () => {
    const request = { firstName: 'Mario', lastName: 'Bianchi' };
    const mockResponse = { id: 'g1', ...request };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.updateGuest('stay1', 'g1', request as never);

    expect(api.put).toHaveBeenCalledWith('/api/v1/stays/stay1/guests/g1', request);
    expect(result).toEqual(mockResponse);
  });

  it('should remove a guest from an open stay', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({ data: undefined });

    await stayService.removeGuest('stay1', 'g1');

    expect(api.delete).toHaveBeenCalledWith('/api/v1/stays/stay1/guests/g1');
  });

  it('should record an early departure for a guest', async () => {
    const mockResponse = { id: 'g1', departureDate: '2026-09-05' };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.recordGuestDeparture('stay1', 'g1', '2026-09-05');

    expect(api.put).toHaveBeenCalledWith('/api/v1/stays/stay1/guests/g1/departure', {
      departureDate: '2026-09-05',
    });
    expect(result).toEqual(mockResponse);
  });

  it('should promote a guest to primary', async () => {
    const mockResponse = { id: 'g1', isPrimaryGuest: true };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.promoteGuestToPrimary('stay1', 'g1');

    expect(api.put).toHaveBeenCalledWith('/api/v1/stays/stay1/guests/g1/primary');
    expect(result).toEqual(mockResponse);
  });

  it('should extend a stay to a new check-out date', async () => {
    const mockResponse = { id: 'stay1', checkOutDate: '2026-09-10' };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.extendStay('stay1', '2026-09-10');

    expect(api.put).toHaveBeenCalledWith('/api/v1/stays/stay1', { newCheckOutDate: '2026-09-10' });
    expect(result).toEqual(mockResponse);
  });

  it('should fetch hotel category history', async () => {
    const mockHistory = [{ category: '4 stelle', validFrom: '2026-01-01' }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockHistory });

    const result = await stayService.getHotelCategoryHistory();

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/hotel-category');
    expect(result).toEqual(mockHistory);
  });

  it('should record a new hotel category', async () => {
    const request = { category: '4 stelle', validFrom: '2026-01-01' };
    const mockResponse = { id: 'c1', ...request };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.recordHotelCategory(request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/stays/hotel-category', request);
    expect(result).toEqual(mockResponse);
  });

  it('should fetch city tax rates', async () => {
    const mockRates = [{ id: 'r1', amountPerNight: 2 }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockRates });

    const result = await stayService.getCityTaxRates();

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/city-tax-rates');
    expect(result).toEqual(mockRates);
  });

  it('should create a city tax rate', async () => {
    const request = { category: '4 stelle', amountPerNight: 2 };
    const mockResponse = { id: 'r1', ...request };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.createCityTaxRate(request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/stays/city-tax-rates', request);
    expect(result).toEqual(mockResponse);
  });

  it('should fetch city tax applicability', async () => {
    const mockResponse = { applicability: 'APPLICABLE' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.getCityTaxApplicability();

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/city-tax-rates/applicability');
    expect(result).toEqual(mockResponse);
  });

  it('should update city tax applicability', async () => {
    const request = { applicability: 'NOT_APPLICABLE' };
    const mockResponse = { ...request };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.updateCityTaxApplicability(request as never);

    expect(api.put).toHaveBeenCalledWith('/api/v1/stays/city-tax-rates/applicability', request);
    expect(result).toEqual(mockResponse);
  });

  it('should fetch the city tax configuration status pre-flight check', async () => {
    const mockResponse = { configured: false, reason: 'COMUNE_NOT_CONFIGURED' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.getCityTaxConfigurationStatus();

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/city-tax/configuration-status');
    expect(result).toEqual(mockResponse);
  });

  it('should fetch the city tax unassessed summary', async () => {
    const mockResponse = { count: 3, totalAmount: 12 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.getCityTaxUnassessedSummary();

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/city-tax/unassessed/summary');
    expect(result).toEqual(mockResponse);
  });

  it('should preview a city tax backfill', async () => {
    const mockResponse = { rows: [], totalAmount: 0 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.previewCityTaxBackfill();

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays/city-tax/backfill/preview');
    expect(result).toEqual(mockResponse);
  });

  it('should confirm a city tax backfill', async () => {
    const mockResponse = { rows: [], totalAmount: 0 };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.confirmCityTaxBackfill();

    expect(api.post).toHaveBeenCalledWith('/api/v1/stays/city-tax/backfill/confirm');
    expect(result).toEqual(mockResponse);
  });
});
