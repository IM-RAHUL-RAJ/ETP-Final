import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, combineLatest, map, throwError } from 'rxjs';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { MarketInstrumentRow } from '../models/account.model';
import { CurrencyConversionRequest, CurrencyConversionResponse } from '../models/instrument.model';
import { AccountService } from './account.service';
import { OrderService } from './order.service';

@Injectable({ providedIn: 'root' })
export class MarketService {
  private readonly accountService = inject(AccountService);
  private readonly orderService = inject(OrderService);
  private readonly http = inject(HttpClient);

  getMarketOverview(accountId: number): Observable<MarketInstrumentRow[]> {
    return combineLatest([
      this.accountService.getPositions(accountId),
      this.orderService.getOrderHistory(accountId)
    ]).pipe(
      map(([positions, orders]) => {
        const latestBySymbol = new Map<string, MarketInstrumentRow>();

        for (const order of orders) {
          if (!latestBySymbol.has(order.symbol)) {
            latestBySymbol.set(order.symbol, {
              symbol: order.symbol,
              assetClass: 'Not exposed by current backend endpoint',
              currency: 'Not exposed by current backend endpoint',
              price: order.executedPrice ?? order.price,
              status: order.status
            });
          }
        }

        for (const position of positions) {
          if (!latestBySymbol.has(position.symbol)) {
            latestBySymbol.set(position.symbol, {
              symbol: position.symbol,
              assetClass: 'Not exposed by current backend endpoint',
              currency: 'Not exposed by current backend endpoint',
              price: position.averageCost,
              status: 'ACTIVE'
            });
          }
        }

        return [...latestBySymbol.values()].sort((a, b) => a.symbol.localeCompare(b.symbol));
      })
    );
  }

  convertCurrency(
    request: CurrencyConversionRequest
  ): Observable<CurrencyConversionResponse> {
    if (!API_ENDPOINTS.currencyConverter) {
      return throwError(
        () =>
          new HttpErrorResponse({
            status: 501,
            error: { errorCode: 'API-NOT-AVAILABLE' }
          })
      );
    }

    return this.http.post<CurrencyConversionResponse>(
      API_ENDPOINTS.currencyConverter,
      request
    );
  }
}

