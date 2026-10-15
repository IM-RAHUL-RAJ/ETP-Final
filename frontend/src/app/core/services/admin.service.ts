import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { AdminAccountSummary, RegistrationRequest, RegistrationResponse } from '../models/admin.model';

/** Admin-only endpoints of the trade API. The API refuses anyone without the ADMIN role. */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  listAccounts(): Observable<AdminAccountSummary[]> {
    return this.http.get<AdminAccountSummary[]>(`${API_ENDPOINTS.tradeApiBase}/api/v1/admin/accounts`);
  }

  /** Step 1: opens the client and the account. Step 2 is the auth service creating the login. */
  register(request: RegistrationRequest): Observable<RegistrationResponse> {
    return this.http.post<RegistrationResponse>(`${API_ENDPOINTS.tradeApiBase}/api/v1/admin/registrations`, request);
  }

  /** Takes back step 1 when step 2 failed. Only works on a brand-new, unused account. */
  undoRegistration(accountId: number): Observable<void> {
    return this.http.delete<void>(`${API_ENDPOINTS.tradeApiBase}/api/v1/admin/registrations/${accountId}`);
  }
}
