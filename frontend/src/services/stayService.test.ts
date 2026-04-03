import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { stayService } from './stayService';

vi.mock('./api');

describe('stayService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fetch all stays', async () => {
    const mockStays = [{ id: '1', status: 'CHECKED_IN' }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: { content: mockStays } });

    const result = await stayService.getAllStays();

    expect(api.get).toHaveBeenCalledWith('/api/v1/stays');
    expect(result).toEqual(mockStays);
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

  it('should update a stay', async () => {
    const request = { guestId: 'g1', reservationId: 'r1', roomId: 'rm1' };
    const mockResponse = { id: '1', ...request };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.updateStay('1', request as never);

    expect(api.put).toHaveBeenCalledWith('/api/v1/stays/1', request);
    expect(result).toEqual(mockResponse);
  });

  it('should extend a stay', async () => {
    const mockResponse = { id: '1', status: 'CHECKED_IN' };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.extendStay('1', '2026-04-01');

    expect(api.put).toHaveBeenCalledWith('/api/v1/stays/1/extend', { newCheckOutDate: '2026-04-01' });
    expect(result).toEqual(mockResponse);
  });

  it('should check out', async () => {
    const mockResponse = { id: '1', status: 'CHECKED_OUT' };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await stayService.checkOut('1');

    expect(api.put).toHaveBeenCalledWith('/api/v1/stays/1/check-out', {});
    expect(result).toEqual(mockResponse);
  });
});
