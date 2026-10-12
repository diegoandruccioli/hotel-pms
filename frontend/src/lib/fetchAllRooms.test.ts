import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fetchAllRooms } from './fetchAllRooms';
import { inventoryService } from '../services/inventoryService';

vi.mock('../services/inventoryService', () => ({
  inventoryService: { getAllRooms: vi.fn() },
}));

const rooms = (from: number, count: number) =>
  Array.from({ length: count }, (_, i) => ({ id: `r${from + i}`, roomNumber: String(from + i) }));
const page = (content: unknown[], totalPages: number) => ({ content, totalPages, totalElements: content.length }) as never;

describe('fetchAllRooms', () => {
  beforeEach(() => vi.clearAllMocks());

  it('makes a single request when everything fits in one page', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce(page(rooms(1, 30), 1));

    const result = await fetchAllRooms();

    expect(result).toHaveLength(30);
    expect(inventoryService.getAllRooms).toHaveBeenCalledTimes(1);
    expect(inventoryService.getAllRooms).toHaveBeenCalledWith(0, 200);
  });

  it('reads every remaining page and keeps the room order', async () => {
    vi.mocked(inventoryService.getAllRooms).mockImplementation(((p: number) =>
      Promise.resolve(page(rooms(p * 200 + 1, p === 2 ? 50 : 200), 3))) as never);

    const result = await fetchAllRooms();

    expect(result).toHaveLength(450);
    expect(result[0].roomNumber).toBe('1');
    expect(result[449].roomNumber).toBe('450');
    expect(vi.mocked(inventoryService.getAllRooms).mock.calls.map(([p]) => p).sort()).toEqual([0, 1, 2]);
  });

  it('treats a response without page info as the only page', async () => {
    vi.mocked(inventoryService.getAllRooms).mockResolvedValueOnce({ content: rooms(1, 2) } as never);

    expect(await fetchAllRooms()).toHaveLength(2);
    expect(inventoryService.getAllRooms).toHaveBeenCalledTimes(1);
  });

  it('fails as a whole when any page fails', async () => {
    vi.mocked(inventoryService.getAllRooms).mockImplementation(((p: number) =>
      p === 1 ? Promise.reject(new Error('boom')) : Promise.resolve(page(rooms(1, 200), 2))) as never);

    await expect(fetchAllRooms()).rejects.toThrow('boom');
  });
});
