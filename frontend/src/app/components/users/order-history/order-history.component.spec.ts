import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { MAX_POLL_ATTEMPTS, OrderHistoryComponent, POLL_INTERVAL_MS } from './order-history.component';
import { AccountContextService } from '../../../core/services/account-context.service';
import { OrderService } from '../../../core/services/order.service';

function order(orderId: string, status: string, createdOn: string) {
  return {
    orderId,
    accountId: 1,
    symbol: 'AAPL',
    side: 'BUY',
    quantity: 10,
    price: 100,
    status,
    createdOn,
    executedPrice: status === 'FILLED' ? 100 : null,
    idempotencyKey: orderId
  };
}

describe('OrderHistoryComponent', () => {
  const getOrderHistory = vi.fn();
  const cancelOrder = vi.fn();
  let consoleInfo: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    vi.useFakeTimers();
    consoleInfo = vi.spyOn(console, 'info').mockImplementation(() => undefined);
    getOrderHistory.mockReset();
    cancelOrder.mockReset();

    await TestBed.configureTestingModule({
      imports: [OrderHistoryComponent],
      providers: [
        provideRouter([]),
        {
          provide: AccountContextService,
          useValue: {
            getCurrentAccount: () => ({ accountNumericId: 1, accountReference: '1', displayName: 'trader01' })
          }
        },
        { provide: OrderService, useValue: { getOrderHistory, cancelOrder } }
      ]
    }).compileComponents();
  });

  afterEach(() => {
    consoleInfo.mockRestore();
    vi.useRealTimers();
  });

  function create() {
    const fixture = TestBed.createComponent(OrderHistoryComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('order history renders newest first including rejections', () => {
    getOrderHistory.mockReturnValue(
      of([
        order('older', 'REJECTED', '2026-01-01T10:00:00Z'),
        order('newer', 'FILLED', '2026-01-01T11:00:00Z'),
        order('oldest', 'CANCELLED', '2026-01-01T09:00:00Z')
      ])
    );

    const fixture = create();

    expect(fixture.componentInstance.orders().map((o) => o.orderId)).toEqual(['newer', 'older', 'oldest']);
    const badges = [...(fixture.nativeElement as HTMLElement).querySelectorAll('app-status-badge')].map(
      (badge) => badge.textContent?.trim()
    );
    expect(badges).toEqual(['FILLED', 'REJECTED', 'CANCELLED']);
  });

  it('column filters narrow the table by symbol, side and a number expression', () => {
    getOrderHistory.mockReturnValue(
      of([
        { ...order('a', 'FILLED', '2026-01-01T10:00:00Z'), symbol: 'AAPL', side: 'BUY', quantity: 10 },
        { ...order('b', 'FILLED', '2026-01-01T11:00:00Z'), symbol: 'MSFT', side: 'SELL', quantity: 50 },
        { ...order('c', 'NEW', '2026-01-01T12:00:00Z'), symbol: 'AAPL', side: 'SELL', quantity: 100 }
      ])
    );
    const component = create().componentInstance;

    component.symbolFilter.set('aap');
    expect(component.visibleOrders().map((o) => o.orderId)).toEqual(['c', 'a']);

    component.sideFilter.set('SELL');
    expect(component.visibleOrders().map((o) => o.orderId)).toEqual(['c']);

    component.symbolFilter.set('');
    component.sideFilter.set('');
    component.quantityFilter.set('>=50');
    expect(component.visibleOrders().map((o) => o.orderId)).toEqual(['c', 'b']);

    component.quantityFilter.set('nonsense');
    expect(component.visibleOrders().length).toBe(3);

    component.quantityFilter.set('10');
    component.filter.set('FILLED');
    expect(component.activeColumnFilters()).toBe(2);
    component.clearFilters();
    expect(component.activeColumnFilters()).toBe(0);
    expect(component.visibleOrders().length).toBe(3);
  });

  it('an order at NEW is shown as still working', () => {
    getOrderHistory.mockReturnValue(of([order('a', 'NEW', '2026-01-01T10:00:00Z')]));

    const fixture = create();

    // Progress goes to the console, not onto the blotter.
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="polling-message"]')).toBeNull();
    expect(fixture.componentInstance.workingCount()).toBe(1);
    expect(consoleInfo).toHaveBeenCalledWith(expect.stringContaining(`attempt 1 of ${MAX_POLL_ATTEMPTS}`));
  });

  it('the re-read stops when nothing is at NEW', () => {
    getOrderHistory
      .mockReturnValueOnce(of([order('a', 'NEW', '2026-01-01T10:00:00Z')]))
      .mockReturnValue(of([order('a', 'FILLED', '2026-01-01T10:00:00Z')]));

    const fixture = create();
    vi.advanceTimersByTime(POLL_INTERVAL_MS);
    fixture.detectChanges();

    expect(getOrderHistory).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.workingCount()).toBe(0);

    vi.advanceTimersByTime(POLL_INTERVAL_MS * 5);
    expect(getOrderHistory).toHaveBeenCalledTimes(2);
  });

  it('the re-read is bounded and says so when it gives up', () => {
    getOrderHistory.mockReturnValue(of([order('a', 'NEW', '2026-01-01T10:00:00Z')]));

    const fixture = create();
    vi.advanceTimersByTime(POLL_INTERVAL_MS * (MAX_POLL_ATTEMPTS + 5));
    fixture.detectChanges();

    // One initial read plus at most MAX_POLL_ATTEMPTS re-reads, then silence.
    expect(getOrderHistory).toHaveBeenCalledTimes(MAX_POLL_ATTEMPTS + 1);
    expect(fixture.componentInstance.pollingStopped()).toBe(true);
    expect(consoleInfo).toHaveBeenCalledWith(expect.stringContaining('Automatic checking stopped'));
  });

  it('pressing refresh after the re-read gave up opens a new window', () => {
    getOrderHistory.mockReturnValue(of([order('a', 'NEW', '2026-01-01T10:00:00Z')]));
    const fixture = create();
    vi.advanceTimersByTime(POLL_INTERVAL_MS * (MAX_POLL_ATTEMPTS + 1));

    fixture.componentInstance.refreshOrders();
    vi.advanceTimersByTime(POLL_INTERVAL_MS);

    expect(getOrderHistory.mock.calls.length).toBeGreaterThan(MAX_POLL_ATTEMPTS + 1);
    expect(fixture.componentInstance.pollingStopped()).toBe(false);
  });

  it('a status filter narrows the table but counts every order', () => {
    getOrderHistory.mockReturnValue(
      of([
        order('a', 'FILLED', '2026-01-01T11:00:00Z'),
        order('b', 'REJECTED', '2026-01-01T10:00:00Z'),
        order('c', 'REJECTED', '2026-01-01T09:00:00Z')
      ])
    );
    const fixture = create();

    fixture.componentInstance.setFilter('REJECTED');
    fixture.detectChanges();

    expect(fixture.componentInstance.visibleOrders().map((o) => o.orderId)).toEqual(['b', 'c']);
    const rejected = fixture.componentInstance.filters().find((f) => f.value === 'REJECTED');
    expect(rejected?.count).toBe(2);
    expect(fixture.componentInstance.filters().find((f) => f.value === 'ALL')?.count).toBe(3);
    expect(fixture.nativeElement.querySelectorAll('[data-testid="order-row"]').length).toBe(2);
  });

  it('cancelling a working order asks first, then marks it cancelled and stops the re-read', () => {
    getOrderHistory.mockReturnValue(of([order('a', 'NEW', '2026-01-01T10:00:00Z')]));
    cancelOrder.mockReturnValue(of({ orderId: 'a', status: 'CANCELLED', message: 'Order cancelled successfully' }));
    const fixture = create();
    const component = fixture.componentInstance;

    component.askCancel('a');
    expect(component.confirmingId()).toBe('a');
    expect(cancelOrder).not.toHaveBeenCalled();

    component.confirmCancel(component.orders()[0]);
    fixture.detectChanges();

    expect(cancelOrder).toHaveBeenCalledWith('a');
    expect(component.orders()[0].status).toBe('CANCELLED');
    expect(component.workingCount()).toBe(0);
    expect(component.notice()).toContain('Cancelled your buy order for AAPL');

    vi.advanceTimersByTime(POLL_INTERVAL_MS * 3);
    expect(getOrderHistory).toHaveBeenCalledTimes(1);
  });

  it('keeping an order closes the confirmation without calling the API', () => {
    getOrderHistory.mockReturnValue(of([order('a', 'NEW', '2026-01-01T10:00:00Z')]));
    const fixture = create();

    fixture.componentInstance.askCancel('a');
    fixture.componentInstance.keepOrder();

    expect(fixture.componentInstance.confirmingId()).toBeNull();
    expect(cancelOrder).not.toHaveBeenCalled();
  });

  it('a cancel the API refuses shows the mapped message and re-reads the order', () => {
    getOrderHistory.mockReturnValue(of([order('a', 'NEW', '2026-01-01T10:00:00Z')]));
    cancelOrder.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({ status: 409, error: { errorCode: 'ORD-409', message: 'Failed to cancel order: state' } })
      )
    );
    const fixture = create();

    fixture.componentInstance.confirmCancel(fixture.componentInstance.orders()[0]);
    fixture.detectChanges();

    const banner = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="cancel-error"]');
    expect(banner?.textContent).toContain('can no longer be cancelled');
    expect(banner?.textContent).not.toContain('Failed to cancel order');
    expect(getOrderHistory).toHaveBeenCalledTimes(2);
  });

  it('only a working order offers a cancel button', () => {
    getOrderHistory.mockReturnValue(
      of([order('a', 'NEW', '2026-01-01T11:00:00Z'), order('b', 'FILLED', '2026-01-01T10:00:00Z')])
    );
    const fixture = create();

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="cancel-order"]').length).toBe(1);
  });

  it('an order with no limit price is shown as a market order', () => {
    getOrderHistory.mockReturnValue(of([{ ...order('m', 'FILLED', '2026-01-01T10:00:00Z'), price: null }]));
    const fixture = create();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Market');
  });

  it('empty history is handled gracefully', () => {
    getOrderHistory.mockReturnValue(of([]));

    const fixture = create();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No orders yet');
  });

  it('leaving the screen stops the re-read', () => {
    getOrderHistory.mockReturnValue(of([order('a', 'NEW', '2026-01-01T10:00:00Z')]));
    const fixture = create();

    fixture.destroy();
    vi.advanceTimersByTime(POLL_INTERVAL_MS * 3);

    expect(getOrderHistory).toHaveBeenCalledTimes(1);
  });
});
