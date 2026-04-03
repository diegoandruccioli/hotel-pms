import api from './api';
import type { RestaurantOrderRequest, RestaurantOrderResponse } from '../types/fb.types';
import type { SpringPage } from '../types/page.types';

const BASE_PATH = '/api/v1/fb/orders';

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
    const response = await api.get<SpringPage<RestaurantOrderResponse>>(BASE_PATH);
    return response.data.content;
  }
};
