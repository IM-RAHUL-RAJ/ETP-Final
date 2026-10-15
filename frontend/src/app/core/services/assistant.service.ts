import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { ChatRequest, ChatResponse, QuickAction } from '../models/assistant.model';

/**
 * The in-app companion. The browser only ever talks to the trade API: the language model is called from the server,
 * and its key never leaves it. Every call is for one of the signed-in customer's own accounts.
 */
@Injectable({ providedIn: 'root' })
export class AssistantService {
  private readonly http = inject(HttpClient);

  private base(accountId: number): string {
    return `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}/assistant`;
  }

  chat(accountId: number, request: ChatRequest): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(`${this.base(accountId)}/chat`, request);
  }

  quickActions(accountId: number): Observable<QuickAction[]> {
    return this.http.get<QuickAction[]>(`${this.base(accountId)}/quick-actions`);
  }
}
