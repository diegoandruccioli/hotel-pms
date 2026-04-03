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
});
