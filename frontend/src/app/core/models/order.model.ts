export type OrderSide = 'BUY' | 'SELL';

export type OrderStatus = 'NEW' | 'FILLED' | 'REJECTED' | 'CANCELLED';

export interface PlaceOrderRequest {
  accountId: number;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
  idempotencyKey: string;
}

export interface OrderResponse {
  orderId: string;
  status: OrderStatus;
  message: string;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number;
}

export interface OrderHistoryEntry {
  orderId: string;
  accountId: number;
  symbol: string;
  side: OrderSide;
  quantity: number;
  price: number | null;
  status: OrderStatus;
  createdOn: string;
  executedPrice: number | null;
  idempotencyKey: string;
}
