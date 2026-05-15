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
});
