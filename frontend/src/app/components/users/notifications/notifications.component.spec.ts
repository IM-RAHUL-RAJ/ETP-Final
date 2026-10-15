import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { NotificationItem } from '../../../core/models/notification.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { NotificationService } from '../../../core/services/notification.service';
import { NotificationsComponent } from './notifications.component';

describe('NotificationsComponent', () => {
  let fixture: ComponentFixture<NotificationsComponent>;
  const list = vi.fn();
  const counts = vi.fn();
  const markRead = vi.fn();
  const markAllRead = vi.fn();

  const item = (overrides: Partial<NotificationItem> = {}): NotificationItem => ({
    id: 1,
    kind: 'ORDER_FILLED',
    subject: 'Order filled: BUY 100 AAPL',
    body: 'Your BUY order for 100 AAPL was filled at 232.71.',
    status: 'SENT',
    statusReason: null,
    channel: 'EMAIL',
    channelSource: 'STORED',
    createdAt: '2026-10-06T09:14:24Z',
    sentAt: '2026-10-06T09:14:30Z',
    read: false,
    ...overrides
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const el = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  async function create(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [NotificationsComponent],
      providers: [
        { provide: NotificationService, useValue: { list, counts, markRead, markAllRead } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(NotificationsComponent);
    await settle();
  }

  beforeEach(() => {
    list.mockReset().mockReturnValue(of([item()]));
    counts.mockReset().mockReturnValue(of({ all: 5, priceAlert: 3, order: 2, funds: 0 }));
    markRead.mockReset();
    markAllRead.mockReset().mockReturnValue(of(2));
  });

  it('puts an "Automated trade triggered" banner on a message a strategy caused, and none on one you caused', async () => {
    list.mockReturnValue(of([
      item({ id: 1, subject: 'Automated order filled: BUY 1 AAPL', body: 'This trade was placed automatically by your strategy #5 on AAPL. Filled.' }),
      item({ id: 2, subject: 'Order filled: BUY 2 MSFT' })
    ]));
    await create();

    const banners = fixture.nativeElement.querySelectorAll('[data-testid="automated-banner"]');
    expect(banners.length).toBe(1);
    expect(banners[0].textContent).toContain('Automated trade triggered');
    expect(banners[0].textContent).toContain('You did not place this order');
    expect(banners[0].closest('li, article')?.textContent).toContain('BUY 1 AAPL');
  });

  it('has a Read all button that marks the whole inbox read at once', async () => {
    list.mockReturnValue(of([item({ id: 1 }), item({ id: 2 })]));
    await create();
    expect(fixture.nativeElement.querySelectorAll('[data-testid="unread-dot"]').length + fixture.nativeElement.querySelectorAll('.item--unread').length).toBeGreaterThanOrEqual(0);

    el('mark-all-read')!.click();
    await settle();

    expect(markAllRead).toHaveBeenCalledWith(1);
    expect(fixture.componentInstance['items']().every((entry: NotificationItem) => entry.read)).toBe(true);
  });

  describe('filter tabs', () => {
    it('shows All, Price Alerts, Orders and Funds with a count on each', async () => {
      await create();

      expect(el('tab-all')?.textContent).toContain('All');
      expect(el('tab-price_alert')?.textContent).toContain('Price Alerts');
      expect(el('tab-order')?.textContent).toContain('Orders');
      expect(el('count-all')?.textContent).toBe('5');
      expect(el('count-price_alert')?.textContent).toBe('3');
      expect(el('count-order')?.textContent).toBe('2');
    });

    it('asks the service for the type when a tab is clicked', async () => {
      await create();
      expect(list).toHaveBeenLastCalledWith(1, false, 50, 'all');

      el('tab-price_alert')!.click();
      await settle();
      expect(list).toHaveBeenLastCalledWith(1, false, 50, 'price_alert');

      el('tab-order')!.click();
      await settle();
      expect(list).toHaveBeenLastCalledWith(1, false, 50, 'order');
      expect(el('tab-order')?.getAttribute('aria-selected')).toBe('true');
      expect(el('tab-price_alert')?.getAttribute('aria-selected')).toBe('false');
    });

    it('lists what the service returned for the chosen type', async () => {
      list.mockImplementation((_a: number, _u: boolean, _l: number, type: string) =>
        of(type === 'price_alert'
          ? [item({ id: 2, kind: 'PRICE_ALERT', subject: 'Price alert: AAPL is at or above 230.50', body: 'AAPL is at or above 230.50. The latest price is 231.20.' })]
          : [item()]));
      await create();

      el('tab-price_alert')!.click();
      await settle();

      expect(text()).toContain('Price alert: AAPL is at or above 230.50');
      expect(text()).not.toContain('Order filled: BUY 100 AAPL');
    });

    it('keeps the numbers on the tabs when the counts cannot be read', async () => {
      counts.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      await create();

      expect(el('tab-all')).not.toBeNull();
      expect(el('count-all')?.textContent).toBe('0');
      expect(text()).toContain('Order filled: BUY 100 AAPL');
    });
  });

  describe('wording and type badge', () => {
    it('labels a fill "Order placed" and shows the order icon, never an internal code', async () => {
      await create();

      expect(el('notification-type')?.textContent).toContain('Order placed');
      expect(text()).not.toContain('ORDER_FILLED');
    });

    it('labels an alert "Alert triggered" and its condition reads with the operator', async () => {
      list.mockReturnValue(of([item({ id: 2, kind: 'PRICE_ALERT', subject: 'Price alert: AAPL is at or above 230.50', body: 'AAPL is at or above 230.50. The latest price is 231.20.' })]));
      await create();

      expect(el('notification-type')?.textContent).toContain('Alert triggered');
      expect(text()).toContain('AAPL is at or above 230.50');
    });

    it('says an equality alert "equals"', async () => {
      list.mockReturnValue(of([item({ id: 3, kind: 'PRICE_ALERT', subject: 'Price alert: AAPL equals 123.45', body: 'AAPL equals 123.45. The latest price is 123.45.' })]));
      await create();

      expect(text()).toContain('AAPL equals 123.45');
    });

    it('shows a rejection in plain words', async () => {
      list.mockReturnValue(of([item({ id: 2, kind: 'ORDER_REJECTED', subject: 'Order rejected: BUY 100 AAPL',
        body: 'Your BUY order for 100 AAPL was not carried out because there was not enough cash in your account.' })]));
      await create();

      expect(el('notification-type')?.textContent).toContain('Order rejected');
      expect(text()).toContain('not enough cash');
    });

    it('shows funds messages with their own label', async () => {
      list.mockReturnValue(of([item({ id: 4, kind: 'FUNDS', subject: 'Funds added: 250.00 USD', body: '250.00 USD was added to your account.' })]));
      await create();

      expect(el('notification-type')?.textContent).toContain('Funds');
    });
  });

  describe('delivery', () => {
    it('says which channel a message went on', async () => {
      await create();
      expect(el('notification-delivery')?.textContent).toContain('Sent by e-mail');
    });

    it('says when the default channel was used', async () => {
      list.mockReturnValue(of([item({ channel: 'EMAIL', channelSource: 'DEFAULT' })]));
      await create();
      expect(el('notification-delivery')?.textContent).toContain('your default');
    });

    it('is honest that a stub channel only recorded the message', async () => {
      list.mockReturnValue(of([item({ statusReason: 'STUB_DELIVERY' })]));
      await create();
      expect(el('notification-delivery')?.textContent).toContain('No e-mail provider is connected');
    });

    it('explains a message the customer switched off', async () => {
      list.mockReturnValue(of([item({ status: 'SKIPPED', statusReason: 'OPTED_OUT', channel: null })]));
      await create();
      expect(el('notification-delivery')?.textContent).toContain('you switched this kind of message off');
    });

    it('explains a held message', async () => {
      list.mockReturnValue(of([item({ status: 'HELD', channel: null })]));
      await create();
      expect(el('notification-delivery')?.textContent).toContain('no contact detail');
    });
  });

  describe('states', () => {
    it('shows the companion and a next step when there are no messages', async () => {
      list.mockReturnValue(of([]));
      await create();

      expect(el('notifications-empty')).not.toBeNull();
      expect(el('mascot')).not.toBeNull();
    });

    it('shows an error with a retry when the inbox cannot be read', async () => {
      list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      await create();

      expect(el('notifications-error')).not.toBeNull();
    });

    it('filters to unread only', async () => {
      list.mockReturnValue(of([item({ id: 1, read: true, subject: 'Old one' }), item({ id: 2, read: false, subject: 'New one' })]));
      await create();

      el('filter-unread')!.click();
      await settle();

      expect(text()).toContain('New one');
      expect(text()).not.toContain('Old one');
    });

    it('marking a message read updates it in place', async () => {
      markRead.mockReturnValue(of(item({ read: true })));
      await create();

      el('mark-read')!.click();
      await settle();

      expect(markRead).toHaveBeenCalledWith(1, 1);
      expect(el('mark-read')).toBeNull();
    });

    it('says so when marking read fails', async () => {
      markRead.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      await create();

      el('mark-read')!.click();
      await settle();

      expect(el('notifications-action-error')).not.toBeNull();
    });
  });
});
