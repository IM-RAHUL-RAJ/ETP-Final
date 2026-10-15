import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { AccountResponse, BalanceResponse, PositionResponse } from '../models/account.model';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);

  getAccount(accountId: number): Observable<AccountResponse> {
    return this.http.get<AccountResponse>(`${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}`);
  }

  getBalance(accountId: number): Observable<BalanceResponse> {
    return this.http.get<BalanceResponse>(
      `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}/balance`
    );
  }

  getPositions(accountId: number): Observable<PositionResponse[]> {
    return this.http.get<PositionResponse[]>(
      `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}/positions`
    );
  }
}

