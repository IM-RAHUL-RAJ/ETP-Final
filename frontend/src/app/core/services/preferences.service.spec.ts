import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { AuthService } from '../auth/auth.service';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { PreferencesResponse } from '../models/preferences.model';
import { AccountContextService } from './account-context.service';
import { PreferencesService } from './preferences.service';

describe('PreferencesService', () => {
  let http: HttpTestingController;
  let service: PreferencesService;
  let auth: { isAuthenticated: ReturnType<typeof vi.fn>; isAdmin: ReturnType<typeof vi.fn> };
  let accountContext: {
    getTokenAccountId: ReturnType<typeof vi.fn>;
    setActiveAccount: ReturnType<typeof vi.fn>;
    clearActiveAccount: ReturnType<typeof vi.fn>;
  };

  const url = (id: number) => `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${id}/preferences`;
  const stored = (defaultAccountId: number): PreferencesResponse => ({
    accountId: 1,
    defaultAccountId,
    alertChannel: 'SMS',
    contactDetail: '******3210',
    source: 'STORED',
    channels: [],
    updatedAt: '2026-10-06T10:00:00Z',
    mascot: 'OWL',
    notificationsEnabled: true,
    notificationKinds: ['ORDER_FILLED']
  });

  beforeEach(() => {
    auth = { isAuthenticated: vi.fn().mockReturnValue(true), isAdmin: vi.fn().mockReturnValue(false) };
    accountContext = {
      getTokenAccountId: vi.fn().mockReturnValue(1),
      setActiveAccount: vi.fn().mockReturnValue(true),
      clearActiveAccount: vi.fn()
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: AccountContextService, useValue: accountContext }
      ]
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(PreferencesService);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('reads the preferences of an account', () => {
    let result: PreferencesResponse | undefined;
    service.get(1).subscribe((value) => (result = value));

    const request = http.expectOne(url(1));
    expect(request.request.method).toBe('GET');
    request.flush(stored(1));

    expect(result?.alertChannel).toBe('SMS');
  });

  it('sends only the channel and the default account, never a destination', () => {
    service.update(1, { defaultAccountId: 1, alertChannel: 'EMAIL' }).subscribe();

    const request = http.expectOne(url(1));
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ defaultAccountId: 1, alertChannel: 'EMAIL' });
    request.flush(stored(1));
  });

  describe('the default account at sign-in', () => {
    it('makes the stored default the active account', () => {
      let completed = false;
      service.applyDefaultAccountAtSignIn().subscribe({ complete: () => (completed = true) });

      http.expectOne(url(1)).flush(stored(2));

      expect(accountContext.clearActiveAccount).toHaveBeenCalled();
      expect(accountContext.setActiveAccount).toHaveBeenCalledWith(2);
      expect(completed).toBe(true);
    });

    it('does not break sign-in when the preference cannot be read', () => {
      let completed = false;
      let failed = false;
      service.applyDefaultAccountAtSignIn().subscribe({ complete: () => (completed = true), error: () => (failed = true) });

      http.expectOne(url(1)).flush({ errorCode: 'AUTH-401', message: 'x' }, { status: 500, statusText: 'Server Error' });

      expect(failed).toBe(false);
      expect(completed).toBe(true);
      expect(accountContext.setActiveAccount).not.toHaveBeenCalled();
    });

    it('does not hold sign-in up when the service is slow', () => {
      vi.useFakeTimers();
      let completed = false;
      service.applyDefaultAccountAtSignIn().subscribe({ complete: () => (completed = true) });
      http.expectOne(url(1));

      vi.advanceTimersByTime(PreferencesService.SIGN_IN_WAIT_MS + 1);

      expect(completed).toBe(true);
      http.match(url(1)).forEach((request) => expect(request.cancelled).toBe(true));
    });

    it('leaves a customer on their own account when the stored default is not one their token carries', () => {
      accountContext.setActiveAccount.mockReturnValue(false);
      service.applyDefaultAccountAtSignIn().subscribe();

      http.expectOne(url(1)).flush(stored(99));

      expect(accountContext.setActiveAccount).toHaveBeenCalledWith(99);
    });

    it('does nothing for an admin, who has no trading preferences', () => {
      auth.isAdmin.mockReturnValue(true);
      let completed = false;
      service.applyDefaultAccountAtSignIn().subscribe({ complete: () => (completed = true) });

      http.expectNone(url(1));
      expect(completed).toBe(true);
    });

    it('does nothing when nobody is signed in', () => {
      auth.isAuthenticated.mockReturnValue(false);
      service.applyDefaultAccountAtSignIn().subscribe();

      http.expectNone(url(1));
    });
  });
});