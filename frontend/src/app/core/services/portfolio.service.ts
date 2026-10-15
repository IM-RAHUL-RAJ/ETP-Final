import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { HistoryRange, PnlResponse, PortfolioHistory, PortfolioSummary, PricedPosition } from '../models/portfolio.model';

// Priced holdings and profit and loss (contracts/portfolio-api.yaml)
 
@Injectable({ providedIn: 'root' })
export class PortfolioService {
  private readonly http = inject(HttpClient);

  private base(accountId: number): string {
    return `${API_ENDPOINTS.tradeApiBase}/api/v1/portfolio/${accountId}`;
  }

  getSummary(accountId: number): Observable<PortfolioSummary> {
    return this.http.get<PortfolioSummary>(this.base(accountId));
  }

  getPositions(accountId: number): Observable<PricedPosition[]> {
    return this.http.get<PricedPosition[]>(`${this.base(accountId)}/positions`);
  }

  getPnl(accountId: number, from?: string, to?: string, bySymbol = false): Observable<PnlResponse> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    if (bySymbol) {
      params = params.set('bySymbol', true);
    }
    return this.http.get<PnlResponse>(`${this.base(accountId)}/pnl`, { params });
  }

  /** The value over time, from what the platform has recorded as the portfolio was viewed. */
  getHistory(accountId: number, range: HistoryRange = '30d'): Observable<PortfolioHistory> {
    return this.http.get<PortfolioHistory>(`${this.base(accountId)}/history`, { params: { range } });
  }
}
