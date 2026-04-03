export type OrderStatus = 'PENDING' | 'PREPARING' | 'READY' | 'DELIVERED' | 'CANCELLED';

export interface OrderItemRequest {
  itemName: string;
  quantity: number;
  unitPrice: number;
}

export interface OrderItemResponse {
  id: string; // UUID
  itemName: string;
  quantity: number;
  unitPrice: number;
}

export interface RestaurantOrderRequest {
  stayId: string;
  items: OrderItemRequest[];
}

export interface RestaurantOrderResponse {
  id: string; // UUID
  stayId: string;
  orderDate: string; // ISO DateTime
  totalAmount: number;
  status: OrderStatus;
  items: OrderItemResponse[];
  createdAt: string;
  updatedAt: string;
}
