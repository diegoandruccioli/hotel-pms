import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { rateSeasonService } from './rateSeasonService';

vi.mock('./api');

describe('rateSeasonService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fetch the rate calendar for a date range', async () => {
    const mockCalendar = { roomTypes: [], days: [] };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockCalendar });

    const result = await rateSeasonService.getRateCalendar('2026-09-01', '2026-09-30');

    expect(api.get).toHaveBeenCalledWith('/api/v1/rate-calendar', {
      params: { from: '2026-09-01', to: '2026-09-30' },
    });
    expect(result).toEqual(mockCalendar);
  });

  it('should bulk-apply a rate to a period', async () => {
    const request = { roomTypeIds: ['rt1'], from: '2026-09-01', to: '2026-09-10', pricePerNight: 150 };
    const mockResponse = [{ id: 's1', ...request }];
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await rateSeasonService.bulkApplyRate(request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/rate-calendar/bulk-apply', request);
    expect(result).toEqual(mockResponse);
  });

  it('should list rate seasons for a room type', async () => {
    const mockSeasons = [{ id: 's1' }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockSeasons });

    const result = await rateSeasonService.listSeasons('rt1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/room-types/rt1/rate-seasons');
    expect(result).toEqual(mockSeasons);
  });

  it('should create a rate season for a room type', async () => {
    const request = { pricePerNight: 150, validFrom: '2026-09-01', validTo: '2026-09-10' };
    const mockResponse = { id: 's1', ...request };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await rateSeasonService.createSeason('rt1', request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/room-types/rt1/rate-seasons', request);
    expect(result).toEqual(mockResponse);
  });

  it('should update a rate season for a room type', async () => {
    const request = { pricePerNight: 175 };
    const mockResponse = { id: 's1', ...request };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await rateSeasonService.updateSeason('rt1', 's1', request as never);

    expect(api.put).toHaveBeenCalledWith('/api/v1/room-types/rt1/rate-seasons/s1', request);
    expect(result).toEqual(mockResponse);
  });

  it('should delete a rate season for a room type', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({ data: undefined });

    await rateSeasonService.deleteSeason('rt1', 's1');

    expect(api.delete).toHaveBeenCalledWith('/api/v1/room-types/rt1/rate-seasons/s1');
  });
});
