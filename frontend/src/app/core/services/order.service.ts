import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { OrderHistoryEntry, OrderResponse, PlaceOrderRequest } from '../models/order.model';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);

  placeOrder(request: PlaceOrderRequest): Observable<OrderResponse> {
    return this.http.post<OrderResponse>(`${API_ENDPOINTS.tradeApiBase}/api/v1/orders`, request);
  }

  /** DELETE /api/v1/orders/{id}. Only an order still at NEW can be cancelled; anything else answers ORD-409. */
  cancelOrder(orderId: string): Observable<OrderResponse> {
    return this.http.delete<OrderResponse>(`${API_ENDPOINTS.tradeApiBase}/api/v1/orders/${orderId}`);
  }

  getOrderHistory(accountId: number): Observable<OrderHistoryEntry[]> {
    return this.http.get<OrderHistoryEntry[]>(
      `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}/orders`
    );
  }
}

