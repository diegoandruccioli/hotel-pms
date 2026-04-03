import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import type { RoomStatus } from '../types/inventory.types';
import { inventoryService } from './inventoryService';

vi.mock('./api');

describe('inventoryService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fetch all rooms', async () => {
    const mockRooms = { content: [{ id: '1', roomNumber: '101' }], totalElements: 1 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockRooms });

    const result = await inventoryService.getAllRooms();

    expect(api.get).toHaveBeenCalledWith('/api/v1/rooms?page=0&size=100&sort=roomNumber,asc');
    expect(result).toEqual(mockRooms);
  });

  it('should fetch room by id', async () => {
    const mockRoom = { id: '1', roomNumber: '101' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockRoom });

    const result = await inventoryService.getRoomById('1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/rooms/1');
    expect(result).toEqual(mockRoom);
  });

  it('should update room status', async () => {
    const status: RoomStatus = 'CLEAN';
    const mockResponse = { id: '1', roomNumber: '101', status: 'CLEAN' };
    vi.mocked(api.patch).mockResolvedValueOnce({ data: mockResponse });

    const result = await inventoryService.updateRoomStatus('1', status);

    expect(api.patch).toHaveBeenCalledWith(
      '/api/v1/rooms/1/status',
      '"CLEAN"',
      { headers: { 'Content-Type': 'application/json' } }
    );
    expect(result).toEqual(mockResponse);
  });
});
