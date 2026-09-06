import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { reservationService } from './reservationService';

vi.mock('./api');

describe('reservationService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fetch all reservations', async () => {
    const mockReservations = [{ id: '1', status: 'CONFIRMED' }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: { content: mockReservations } });

    const result = await reservationService.getAllReservations();

    expect(api.get).toHaveBeenCalledWith('/api/v1/reservations?size=500');
    expect(result).toEqual(mockReservations);
  });

  it('should fetch reservation by id', async () => {
    const mock = { id: '1', status: 'CONFIRMED' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mock });

    const result = await reservationService.getReservationById('1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/reservations/1');
    expect(result).toEqual(mock);
  });

  it('should create a reservation', async () => {
    const request = { guestId: 'g1', lineItems: [] };
    const mockResponse = { id: '1', ...request };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await reservationService.createReservation(request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/reservations', request);
    expect(result).toEqual(mockResponse);
  });

  it('should update a reservation', async () => {
    const request = { guestId: 'g1' };
    const mockResponse = { id: '1', ...request };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await reservationService.updateReservation('1', request);

    expect(api.put).toHaveBeenCalledWith('/api/v1/reservations/1', request);
    expect(result).toEqual(mockResponse);
  });

  it('should delete a reservation', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({ data: undefined });

    await reservationService.deleteReservation('1');

    expect(api.delete).toHaveBeenCalledWith('/api/v1/reservations/1');
  });

  it('should retry confirmation email', async () => {
    const mockResponse = { id: '1', confirmationEmailFailed: false };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await reservationService.retryConfirmationEmail('1');

    expect(api.post).toHaveBeenCalledWith('/api/v1/reservations/1/confirmation-email/retry', {});
    expect(result).toEqual(mockResponse);
  });

  it('should update status with the given actualGuests', async () => {
    const mockResponse = { id: '1', status: 'NO_SHOW', version: 4 };
    vi.mocked(api.patch).mockResolvedValueOnce({ data: mockResponse });

    const result = await reservationService.updateStatus('1', 'NO_SHOW', 3, 2);

    expect(api.patch).toHaveBeenCalledWith('/api/v1/reservations/1/status-and-guests', {
      status: 'NO_SHOW',
      actualGuests: 2,
      version: 3,
    });
    expect(result).toEqual(mockResponse);
  });

  it('should update status with actualGuests defaulted to null when omitted', async () => {
    const mockResponse = { id: '1', status: 'NO_SHOW', version: 4 };
    vi.mocked(api.patch).mockResolvedValueOnce({ data: mockResponse });

    await reservationService.updateStatus('1', 'NO_SHOW', 3);

    expect(api.patch).toHaveBeenCalledWith('/api/v1/reservations/1/status-and-guests', {
      status: 'NO_SHOW',
      actualGuests: null,
      version: 3,
    });
  });

  it('should download the CSV export with every filter as a query param', () => {
    reservationService.exportReservationsCsv({
      query: '  mario  ',
      upcomingOnly: true,
      dateFrom: '2026-01-01',
      dateTo: '2026-01-31',
      status: 'CONFIRMED',
    });

    const iframe = document.body.querySelector('iframe');
    expect(iframe).not.toBeNull();
    expect(iframe?.src).toContain('/api/v1/reservations/export.csv?');
    expect(iframe?.src).toContain('query=mario');
    expect(iframe?.src).toContain('upcomingOnly=true');
    expect(iframe?.src).toContain('dateFrom=2026-01-01');
    expect(iframe?.src).toContain('dateTo=2026-01-31');
    expect(iframe?.src).toContain('status=CONFIRMED');

    document.body.replaceChildren();
  });

  it('should download every reservation as CSV, with no query params, when no filters are set', () => {
    reservationService.exportReservationsCsv({});

    const iframe = document.body.querySelector('iframe');
    expect(iframe).not.toBeNull();
    expect(iframe?.src).toMatch(/\/api\/v1\/reservations\/export\.csv$/);

    document.body.replaceChildren();
  });
});
