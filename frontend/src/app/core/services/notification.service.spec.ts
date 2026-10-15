import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { AuthService } from '../auth/auth.service';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { NotificationItem } from '../models/notification.model';
import { AccountContextService } from './account-context.service';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let http: HttpTestingController;
  let service: NotificationService;
  const authenticated = signal(true);

  const base = `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/1/notifications`;
  const item = (id: number, read: boolean): NotificationItem => ({
    id,
    kind: 'ORDER_FILLED',
    subject: 'Order filled: BUY 1 AAPL',
    body: 'Filled.',
    status: 'SENT',
    statusReason: null,
    channel: 'SMS',
    channelSource: 'STORED',
    createdAt: '2026-10-06T09:00:00Z',
    sentAt: '2026-10-06T09:00:05Z',
    read
  });

  beforeEach(() => {
    authenticated.set(true);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { isAuthenticated: authenticated } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } }
      ]
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(NotificationService);
  });

  afterEach(() => http.verify());

  it('lists the account\'s notifications and learns the unread count from them', () => {
    service.list(1).subscribe();

    const request = http.expectOne((r) => r.url === base);
    expect(request.request.params.get('unreadOnly')).toBe('false');
    request.flush([item(2, false), item(1, true)]);

    expect(service.unread()).toBe(1);
  });

  it('marks one read and lowers the badge', () => {
    service.list(1).subscribe();
    http.expectOne((r) => r.url === base).flush([item(2, false), item(1, false)]);
    expect(service.unread()).toBe(2);

    service.markRead(1, 2).subscribe();
    const request = http.expectOne(`${base}/2/read`);
    expect(request.request.method).toBe('POST');
    request.flush(item(2, true));

    expect(service.unread()).toBe(1);
  });

  it('reads the unread count for the badge', () => {
    let count = -1;
    service.refreshUnread().subscribe((value) => (count = value));

    http.expectOne(`${base}/unread-count`).flush({ unread: 4 });

    expect(count).toBe(4);
    expect(service.unread()).toBe(4);
  });

  it('keeps the badge as it was when the count cannot be read: a badge must never break a page', () => {
    service.list(1).subscribe();
    http.expectOne((r) => r.url === base).flush([item(1, false)]);

    let failed = false;
    service.refreshUnread().subscribe({ error: () => (failed = true) });
    http.expectOne(`${base}/unread-count`).flush('boom', { status: 500, statusText: 'Server Error' });

    expect(failed).toBe(false);
    expect(service.unread()).toBe(1);
  });

  it('forgets the count when the customer signs out', async () => {
    service.list(1).subscribe();
    http.expectOne((r) => r.url === base).flush([item(1, false), item(2, false)]);
    expect(service.unread()).toBe(2);

    authenticated.set(false);
    TestBed.tick();

    expect(service.unread()).toBe(0);
  });

  it('sends the type only when a group is chosen, and does not let a filtered list set the badge', () => {
    service.list(1, false, 50, 'price_alert').subscribe();
    const request = http.expectOne((r) => r.url === base);
    expect(request.request.params.get('type')).toBe('price_alert');
    request.flush([item(1, false), item(2, false)]);

    expect(service.unread()).toBe(0);

    service.list(1).subscribe();
    const plain = http.expectOne((r) => r.url === base);
    expect(plain.request.params.has('type')).toBe(false);
    plain.flush([item(1, false)]);
    expect(service.unread()).toBe(1);
  });

  it('reads how many messages each tab holds', () => {
    let counts: unknown;
    service.counts(1).subscribe((value) => (counts = value));

    http.expectOne(`${base}/counts`).flush({ all: 5, priceAlert: 3, order: 2, funds: 0 });
    expect(counts).toEqual({ all: 5, priceAlert: 3, order: 2, funds: 0 });
  });
});
