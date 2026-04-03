import { describe, it, expect, vi, beforeEach } from 'vitest';
import api from './api';
import { fbService } from './fbService';

vi.mock('./api');

describe('fbService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should create an order', async () => {
    const request = { stayId: 's1', items: [] };
    const mockResponse = { id: 'o1', ...request, status: 'OPEN' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await fbService.createOrder(request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/fb/orders', request);
    expect(result).toEqual(mockResponse);
  });

  it('should fetch order by id', async () => {
    const mock = { id: 'o1', status: 'OPEN' };
    vi.mocked(api.get).mockResolvedValueOnce({ data: mock });

    const result = await fbService.getOrderById('o1');

    expect(api.get).toHaveBeenCalledWith('/api/v1/fb/orders/o1');
    expect(result).toEqual(mock);
  });

  it('should fetch all orders', async () => {
    const mockOrders = [{ id: 'o1', status: 'OPEN' }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: { content: mockOrders } });

    const result = await fbService.getAllOrders();

    expect(api.get).toHaveBeenCalledWith('/api/v1/fb/orders');
    expect(result).toEqual(mockOrders);
  });
});
