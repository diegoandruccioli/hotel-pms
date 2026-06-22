export type OrderStatus = 'PENDING' | 'PREPARING' | 'PREPARED' | 'READY' | 'DELIVERED' | 'CANCELLED' | 'BILLED_TO_ROOM';

export interface MenuItemResponse {
  id: string;
  name: string;
  price: number;
  category: string;
  description: string | null;
  available: boolean;
}

export interface MenuItemRequest {
  name: string;
  price: number;
  category: string;
  description?: string;
  available: boolean;
}

export interface OrderItemRequest {
  menuItemId: string;
  quantity: number;
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
  roomNumber: string | null;
  guestDisplayName: string | null;
  orderDate: string; // ISO DateTime
  totalAmount: number;
  status: OrderStatus;
  items: OrderItemResponse[];
  createdAt: string;
  updatedAt: string;
}
