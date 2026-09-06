import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { reservationGroupService } from './reservationGroupService';

vi.mock('./api');

describe('reservationGroupService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should create a group', async () => {
    const request = {
      name: 'Acme Corp Offsite',
      contactGuestId: 'g1',
      checkInDate: '2026-10-01',
      checkOutDate: '2026-10-03',
      openMasterFolio: true,
      rooms: [{ guestId: 'g1', roomId: 'r1', expectedGuests: 2, billedToMasterFolio: true }],
    } as never;
    const mockResponse = { id: 'group-1' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await reservationGroupService.createGroup(request);

    expect(api.post).toHaveBeenCalledWith('/api/v1/reservation-groups', request);
    expect(result).toEqual(mockResponse);
  });

  it('should fetch a group by id', async () => {
    const mockResponse = { id: 'group-1', name: 'Acme Corp Offsite' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockResponse });

    const result = await reservationGroupService.getGroup('group-1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/reservation-groups/group-1');
    expect(result).toEqual(mockResponse);
  });

  it('should fetch a paginated list of groups', async () => {
    const mockPage = { content: [], totalPages: 1, totalElements: 0 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockPage });

    const result = await reservationGroupService.getAllGroups(1, 10);

    expect(api.get).toHaveBeenCalledWith('/api/v1/reservation-groups?page=1&size=10');
    expect(result).toEqual(mockPage);
  });

  it('should cancel a group with a version param when given', async () => {
    const mockResponse = { id: 'group-1', status: 'CANCELLED' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await reservationGroupService.cancelGroup('group-1', 3);

    expect(api.post).toHaveBeenCalledWith('/api/v1/reservation-groups/group-1/cancel?version=3');
    expect(result).toEqual(mockResponse);
  });

  it('should cancel a group without a version param when omitted', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { id: 'group-1' } });

    await reservationGroupService.cancelGroup('group-1');

    expect(api.post).toHaveBeenCalledWith('/api/v1/reservation-groups/group-1/cancel');
  });

  it('should check out a group and return per-room outcomes', async () => {
    const mockOutcomes = [{ reservationId: 'r1', stayId: 's1', success: true, errorCode: null }];
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockOutcomes });

    const result = await reservationGroupService.checkoutGroup('group-1');

    expect(api.post).toHaveBeenCalledWith('/api/v1/reservation-groups/group-1/checkout');
    expect(result).toEqual(mockOutcomes);
  });
});
