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

  it('should fetch available rooms for a date range', async () => {
    const mockRooms = [{ id: '1', roomNumber: '101', status: 'CLEAN' }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockRooms });

    const result = await inventoryService.getAvailableRooms('2026-06-23', '2026-06-24');

    expect(api.get).toHaveBeenCalledWith('/api/v1/rooms/availability', {
      params: { checkInDate: '2026-06-23', checkOutDate: '2026-06-24' },
    });
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
      { status: 'CLEAN' }
    );
    expect(result).toEqual(mockResponse);
  });

  it('should delete a room', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({ data: undefined });

    await inventoryService.deleteRoom('1');

    expect(api.delete).toHaveBeenCalledWith('/api/v1/rooms/1');
  });

  it('should create a room', async () => {
    const request = { roomNumber: '201', roomTypeId: 'rt1' };
    const mockResponse = { id: '2', ...request, status: 'CLEAN' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await inventoryService.createRoom(request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/rooms', request);
    expect(result).toEqual(mockResponse);
  });

  it('should update a room', async () => {
    const request = { roomNumber: '201', roomTypeId: 'rt1' };
    const mockResponse = { id: '2', ...request, status: 'CLEAN' };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await inventoryService.updateRoom('2', request as never);

    expect(api.put).toHaveBeenCalledWith('/api/v1/rooms/2', request);
    expect(result).toEqual(mockResponse);
  });

  it('should fetch all room types', async () => {
    const mockTypes = [{ id: 'rt1', name: 'Standard', basePrice: 80 }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockTypes });

    const result = await inventoryService.getAllRoomTypes();

    expect(api.get).toHaveBeenCalledWith('/api/v1/room-types');
    expect(result).toEqual(mockTypes);
  });

  it('should fetch a room type by id', async () => {
    const mockType = { id: 'rt1', name: 'Standard', basePrice: 80 };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockType });

    const result = await inventoryService.getRoomTypeById('rt1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/room-types/rt1');
    expect(result).toEqual(mockType);
  });

  it('should create a room type', async () => {
    const request = { name: 'Suite', basePrice: 200, maxOccupancy: 4 };
    const mockResponse = { id: 'rt2', ...request };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await inventoryService.createRoomType(request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/room-types', request);
    expect(result).toEqual(mockResponse);
  });

  it('should update a room type', async () => {
    const request = { name: 'Suite', basePrice: 220, maxOccupancy: 4 };
    const mockResponse = { id: 'rt2', ...request };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await inventoryService.updateRoomType('rt2', request as never);

    expect(api.put).toHaveBeenCalledWith('/api/v1/room-types/rt2', request);
    expect(result).toEqual(mockResponse);
  });

  it('should delete a room type', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({ data: undefined });

    await inventoryService.deleteRoomType('rt2');

    expect(api.delete).toHaveBeenCalledWith('/api/v1/room-types/rt2');
  });
});
