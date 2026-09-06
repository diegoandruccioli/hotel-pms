import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { nightAuditService } from './nightAuditService';

vi.mock('./api');

describe('nightAuditService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should run the night audit for the given date', async () => {
    const mockResponse = { id: '1', businessDate: '2026-06-15', status: 'COMPLETED' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await nightAuditService.run('2026-06-15');

    expect(api.post).toHaveBeenCalledWith('/api/v1/frontdesk/night-audit?date=2026-06-15');
    expect(result).toEqual(mockResponse);
  });

  it('should fetch history with default page size', async () => {
    const mockResponse = { content: [], totalPages: 1 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockResponse });

    const result = await nightAuditService.getHistory(0);

    expect(api.get).toHaveBeenCalledWith(
      '/api/v1/frontdesk/night-audit?page=0&size=20&sort=businessDate,desc',
    );
    expect(result).toEqual(mockResponse);
  });

  it('should fetch history with a custom page size', async () => {
    const mockResponse = { content: [], totalPages: 1 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockResponse });

    await nightAuditService.getHistory(1, 50);

    expect(api.get).toHaveBeenCalledWith(
      '/api/v1/frontdesk/night-audit?page=1&size=50&sort=businessDate,desc',
    );
  });
});
