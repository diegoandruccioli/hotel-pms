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

  it('should confirm an order', async () => {
    const mockResponse = { id: 'o1', status: 'BILLED_TO_ROOM' };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await fbService.confirmOrder('o1');

    expect(api.post).toHaveBeenCalledWith('/api/v1/fb/orders/o1/confirm');
    expect(result).toEqual(mockResponse);
  });

  it('should fetch menu items', async () => {
    const mockItems = [{ id: 'm1', name: 'Espresso', price: 2.5 }];
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockItems });

    const result = await fbService.getMenuItems();

    expect(api.get).toHaveBeenCalledWith('/api/v1/fb/menu-items');
    expect(result).toEqual(mockItems);
  });

  it('should create a menu item', async () => {
    const request = { name: 'Tiramisù', price: 6, category: 'Generale' };
    const mockResponse = { id: 'm2', ...request };
    vi.mocked(api.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await fbService.createMenuItem(request as never);

    expect(api.post).toHaveBeenCalledWith('/api/v1/fb/menu-items', request);
    expect(result).toEqual(mockResponse);
  });

  it('should update a menu item', async () => {
    const request = { name: 'Tiramisù', price: 6.5, category: 'Generale' };
    const mockResponse = { id: 'm2', ...request };
    vi.mocked(api.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await fbService.updateMenuItem('m2', request as never);

    expect(api.put).toHaveBeenCalledWith('/api/v1/fb/menu-items/m2', request);
    expect(result).toEqual(mockResponse);
  });

  it('should delete a menu item', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({ data: undefined });

    await fbService.deleteMenuItem('m2');

    expect(api.delete).toHaveBeenCalledWith('/api/v1/fb/menu-items/m2');
  });
});
