import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { AlertTriggersResponse, CreateAlertRequest, PriceAlert, UpdateAlertRequest } from '../models/alert.model';

// Price alerts. A fired alert is delivered by the platform through notifications; nothing here sends a message.
@Injectable({ providedIn: 'root' })
export class AlertService {
  private readonly http = inject(HttpClient);

  private base(accountId: number): string {
    return `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}/alerts`;
  }

  list(accountId: number): Observable<PriceAlert[]> {
    return this.http.get<PriceAlert[]>(this.base(accountId));
  }

  createAlert(accountId: number, request: CreateAlertRequest): Observable<PriceAlert> {
    return this.http.post<PriceAlert>(this.base(accountId), request);
  }

  /** Changes what is in the request and nothing else. Editing a fired alert arms it again. */
  updateAlert(accountId: number, alertId: number, request: UpdateAlertRequest): Observable<PriceAlert> {
    return this.http.put<PriceAlert>(`${this.base(accountId)}/${alertId}`, request);
  }

  /** Switches an alert on or off. Answers with the switch as the server now holds it. */
  toggleAlert(accountId: number, alertId: number, enabled: boolean): Observable<boolean> {
    return this.http
      .put<{ enabled: boolean }>(`${this.base(accountId)}/${alertId}/enabled`, { enabled })
      .pipe(map((reply) => reply.enabled));
  }

  /** Removes the alert and, with it, its history. */
  deleteAlert(accountId: number, alertId: number): Observable<PriceAlert> {
    return this.http.delete<PriceAlert>(`${this.base(accountId)}/${alertId}`);
  }

  /** Each time the alert fired, newest first. */
  getTriggerHistory(accountId: number, alertId: number): Observable<AlertTriggersResponse> {
    return this.http.get<AlertTriggersResponse>(`${this.base(accountId)}/${alertId}/triggers`);
  }
}
