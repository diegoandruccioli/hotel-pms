import api from './api';
import type { MenuItemRequest, MenuItemResponse, RestaurantOrderRequest, RestaurantOrderResponse } from '../types/fb.types';
import type { SpringPage } from '../types/page.types';

const BASE_PATH = '/api/v1/fb/orders';
const MENU_ITEMS_PATH = '/api/v1/fb/menu-items';

export const fbService = {
  createOrder: async (data: RestaurantOrderRequest): Promise<RestaurantOrderResponse> => {
    const response = await api.post<RestaurantOrderResponse>(BASE_PATH, data);
    return response.data;
  },

  getOrderById: async (id: string): Promise<RestaurantOrderResponse> => {
    const response = await api.get<RestaurantOrderResponse>(`${BASE_PATH}/${id}`);
    return response.data;
  },

  getAllOrders: async (): Promise<RestaurantOrderResponse[]> => {
    const response = await api.get<SpringPage<RestaurantOrderResponse>>(`${BASE_PATH}?size=500`);
    return response.data.content;
  },

  confirmOrder: async (id: string): Promise<RestaurantOrderResponse> => {
    const response = await api.post<RestaurantOrderResponse>(`${BASE_PATH}/${id}/confirm`);
    return response.data;
  },

  getMenuItems: async (): Promise<MenuItemResponse[]> => {
    const response = await api.get<MenuItemResponse[]>(MENU_ITEMS_PATH);
    return response.data;
  },

  createMenuItem: async (data: MenuItemRequest): Promise<MenuItemResponse> => {
    const response = await api.post<MenuItemResponse>(MENU_ITEMS_PATH, data);
    return response.data;
  },

  updateMenuItem: async (id: string, data: MenuItemRequest): Promise<MenuItemResponse> => {
    const response = await api.put<MenuItemResponse>(`${MENU_ITEMS_PATH}/${id}`, data);
    return response.data;
  },

  deleteMenuItem: async (id: string): Promise<void> => {
    await api.delete(`${MENU_ITEMS_PATH}/${id}`);
  },
};
