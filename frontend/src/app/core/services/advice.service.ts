import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { AutomationStatus, AutomationStrategy, CreateStrategyRequest, SimulateResponse, TradeAdvice } from '../models/advice.model';

/** Trade advice (recommendations only) and the read-only status of automated trading. */
@Injectable({ providedIn: 'root' })
export class AdviceService {
  private readonly http = inject(HttpClient);

  advice(accountId: number): Observable<TradeAdvice[]> {
    return this.http.get<TradeAdvice[]>(`${API_ENDPOINTS.tradeApiBase}/api/v1/trade-advice`, { params: { accountId } });
  }

  automationStatus(): Observable<AutomationStatus> {
    return this.http.get<AutomationStatus>(`${API_ENDPOINTS.tradeApiBase}/api/v1/automation`);
  }

  /** A new strategy always starts switched off. */
  createStrategy(request: CreateStrategyRequest): Observable<AutomationStrategy> {
    return this.http.post<AutomationStrategy>(`${API_ENDPOINTS.tradeApiBase}/api/v1/automation/strategies`, request);
  }

  /** Switching on is what lets a strategy place real orders, so it must say it was acknowledged. */
  setStrategyEnabled(id: string, enabled: boolean, acknowledged = false): Observable<AutomationStrategy> {
    return this.http.put<AutomationStrategy>(`${API_ENDPOINTS.tradeApiBase}/api/v1/automation/strategies/${id}/enabled`, { enabled, acknowledged });
  }

  /** Demo: makes a running strategy trigger now from a scripted price path. The order it places is real. */
  simulateStrategy(id: string, direction: 'BUY' | 'SELL'): Observable<SimulateResponse> {
    return this.http.post<SimulateResponse>(`${API_ENDPOINTS.tradeApiBase}/api/v1/automation/strategies/${id}/simulate`, { direction });
  }

  removeStrategy(id: string): Observable<void> {
    return this.http.delete<void>(`${API_ENDPOINTS.tradeApiBase}/api/v1/automation/strategies/${id}`);
  }
}
