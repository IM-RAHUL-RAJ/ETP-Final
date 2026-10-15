import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, forkJoin, map, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { ServiceStatus } from '../models/account.model';

@Injectable({ providedIn: 'root' })
export class ApiStatusService {
  private readonly http = inject(HttpClient);

  getStatuses(accountId: number): Observable<ServiceStatus[]> {
    return forkJoin([
      this.probe('Trading API', `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}`),
      this.probe('Trading Health', API_ENDPOINTS.tradeApiHealth),
      this.probe('Auth Service', API_ENDPOINTS.authApiHealth),
      of({
        name: 'Market Data',
        status: 'NOT_CONFIGURED',
        detail: 'No market-data health endpoint is present in the current contracts.'
      } satisfies ServiceStatus),
      of({
        name: 'Currency Service',
        status: 'NOT_CONFIGURED',
        detail: 'No currency-converter API endpoint is exposed in the current contracts.'
      } satisfies ServiceStatus)
    ]);
  }

  private probe(name: string, endpoint: string): Observable<ServiceStatus> {
    return this.http.get(endpoint).pipe(
      map(
        () =>
          ({
            name,
            status: 'ONLINE',
            detail: 'Online'
          }) satisfies ServiceStatus
      ),
      catchError(() =>
        of({
          name,
          status: 'UNAVAILABLE',
          detail: 'Unavailable'
        } satisfies ServiceStatus)
      )
    );
  }
}

