import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { guestService } from './guestService';

vi.mock('./api');

describe('guestService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should fetch all guests', async () => {
    const mockGuests = [{ id: '1', firstName: 'John' }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: { content: mockGuests } });

    const result = await guestService.getAllGuests();

    expect(api.get).toHaveBeenCalledWith('/api/v1/guests');
    expect(result).toEqual(mockGuests);
  });

  it('should fetch guest by id', async () => {
    const mockGuest = { id: '1', firstName: 'John' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockGuest });

    const result = await guestService.getGuestById('1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/guests/1');
    expect(result).toEqual(mockGuest);
  });

  it('should create a guest', async () => {
    const request = { firstName: 'John', lastName: 'Doe' };
    const mockResponse = { id: '1', ...request };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await guestService.createGuest(request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/guests', request);
    expect(result).toEqual(mockResponse);
  });

  it('should update a guest', async () => {
    const request = { firstName: 'Jane', lastName: 'Doe' };
    const mockResponse = { id: '1', ...request };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await guestService.updateGuest('1', request as never);

    expect(api.put).toHaveBeenCalledWith('/api/v1/guests/1', request);
    expect(result).toEqual(mockResponse);
  });

  it('should delete a guest', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({});

    await guestService.deleteGuest('1');

    expect(api.delete).toHaveBeenCalledWith('/api/v1/guests/1');
  });
});
