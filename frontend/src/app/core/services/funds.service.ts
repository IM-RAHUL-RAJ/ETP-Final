import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_ENDPOINTS } from '../config/api-endpoints';

export type FundsMethod = 'BANK_TRANSFER' | 'UPI' | 'CARD';

export const FUNDS_METHODS: ReadonlyArray<{ value: FundsMethod; label: string }> = [
  { value: 'BANK_TRANSFER', label: 'Bank transfer' },
  { value: 'UPI', label: 'UPI' },
  { value: 'CARD', label: 'Card' }
];

export interface FundsRequest {
  amount: number;
  method?: FundsMethod;
}

export interface FundsMovement {
  id: string;
  accountId: number;
  kind: 'DEPOSIT' | 'WITHDRAWAL';
  amount: number;
  currency: string;
  method?: FundsMethod;
  balanceAfter: number;
  createdAt: string;
}

/** Adding and withdrawing cash. Simulated: the method is recorded, no real payment is taken. */
@Injectable({ providedIn: 'root' })
export class FundsService {
  private readonly http = inject(HttpClient);

  private base(accountId: number): string {
    return `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}/funds`;
  }

  deposit(accountId: number, request: FundsRequest): Observable<FundsMovement> {
    return this.http.post<FundsMovement>(`${this.base(accountId)}/deposit`, request);
  }

  withdraw(accountId: number, request: FundsRequest): Observable<FundsMovement> {
    return this.http.post<FundsMovement>(`${this.base(accountId)}/withdraw`, request);
  }

  history(accountId: number, limit = 20): Observable<FundsMovement[]> {
    return this.http.get<FundsMovement[]>(this.base(accountId), { params: { limit } });
  }
}
