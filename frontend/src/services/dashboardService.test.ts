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
});
