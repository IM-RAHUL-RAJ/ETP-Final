import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, take, timeout } from 'rxjs/operators';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { AuthService } from '../auth/auth.service';
import { PreferencesResponse, UpdatePreferencesRequest } from '../models/preferences.model';
import { AccountContextService } from './account-context.service';

/** The customer's default account and alert channel, held by the Trade REST API's preferences module. */
@Injectable({ providedIn: 'root' })
export class PreferencesService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly accountContext = inject(AccountContextService);

  /** How long sign-in will wait for the stored default before simply carrying on. */
  static readonly SIGN_IN_WAIT_MS = 3000;

  get(accountId: number): Observable<PreferencesResponse> {
    return this.http.get<PreferencesResponse>(`${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}/preferences`);
  }

  update(accountId: number, request: UpdatePreferencesRequest): Observable<PreferencesResponse> {
    return this.http.put<PreferencesResponse>(
      `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}/preferences`,
      request
    );
  }
 
  applyDefaultAccountAtSignIn(): Observable<void> {
    if (!this.auth.isAuthenticated() || this.auth.isAdmin()) {
      return of(undefined);
    }
    const tokenAccountId = this.accountContext.getTokenAccountId();
    if (tokenAccountId <= 0) {
      return of(undefined);
    }
    // A new sign-in starts from the stored default, not from whatever an earlier session picked.
    this.accountContext.clearActiveAccount();

    return this.get(tokenAccountId).pipe(
      timeout(PreferencesService.SIGN_IN_WAIT_MS),
      map((preferences) => {
        this.accountContext.setActiveAccount(preferences.defaultAccountId);
      }),
      catchError(() => of(undefined)),
      take(1)
    );
  }
}