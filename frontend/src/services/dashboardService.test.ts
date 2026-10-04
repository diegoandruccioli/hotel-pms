import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import api from './api';
import { dashboardService } from './dashboardService';

vi.mock('./api');

describe('dashboardService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-20T12:00:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('fetches the day-sheet for today', async () => {
    const mockDaySheet = {
      date: '2026-08-20',
      todayArrivals: 3,
      todayDepartures: 2,
      guestsInHouse: 5,
      currentStays: 4,
      availableRooms: 7,
      roomStatusCounts: { CLEAN: 10, DIRTY: 2 },
    };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockDaySheet });

    const result = await dashboardService.getDaySheet();

    expect(api.get).toHaveBeenCalledWith('/api/v1/frontdesk/day-sheet', {
      params: { date: '2026-08-20' },
    });
    expect(result).toEqual(mockDaySheet);
  });

  describe('getDaySheetTrend', () => {
    const mockTrend = {
      from: '2026-08-13',
      to: '2026-08-19',
      points: [{ date: '2026-08-19', arrivals: 3, departures: 2, guestsInHouse: 5, availableRooms: 7 }],
    };

    it('fetches the last 7 days before today by default', async () => {
      vi.mocked(api.get).mockResolvedValueOnce({ status: 200, data: mockTrend });

      const result = await dashboardService.getDaySheetTrend();

      expect(api.get).toHaveBeenCalledWith('/api/v1/frontdesk/day-sheet/trend', {
        params: { date: '2026-08-20', days: 7 },
        validateStatus: expect.any(Function),
      });
      expect(result).toEqual(mockTrend);
    });

    it('passes a custom window length', async () => {
      vi.mocked(api.get).mockResolvedValueOnce({ status: 200, data: mockTrend });

      await dashboardService.getDaySheetTrend(14);

      expect(api.get).toHaveBeenCalledWith(
        '/api/v1/frontdesk/day-sheet/trend',
        expect.objectContaining({ params: { date: '2026-08-20', days: 14 } }),
      );
    });

    it('resolves to null on 404 (backend without the endpoint) and rejects any other error status', async () => {
      vi.mocked(api.get).mockResolvedValueOnce({ status: 404, data: undefined });

      expect(await dashboardService.getDaySheetTrend()).toBeNull();

      const config = vi.mocked(api.get).mock.calls[0][1] as { validateStatus: (s: number) => boolean };
      expect(config.validateStatus(200)).toBe(true);
      expect(config.validateStatus(404)).toBe(true);
      expect(config.validateStatus(400)).toBe(false);
      expect(config.validateStatus(403)).toBe(false);
      expect(config.validateStatus(500)).toBe(false);
    });
  });
});
