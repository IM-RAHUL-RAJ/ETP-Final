import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { WatchlistComponent, QUOTE_REFRESH_MS } from './watchlist.component';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { InstrumentService } from '../../../core/services/instrument.service';
import { OrderService } from '../../../core/services/order.service';

describe('WatchlistComponent', () => {
  const getWatchlist = vi.fn();
  const getWatchlistQuotes = vi.fn();
  const removeFromWatchlist = vi.fn();

  const entry = (symbol: string, currency = 'USD') => ({
    symbol,
    name: `${symbol} Inc.`,
    exchange: 'NASDAQ',
    assetClass: 'US_EQUITY',
    currency,
    tradable: true,
    addedAt: '2026-10-01T10:00:00Z'
  });

  beforeEach(async () => {
    vi.useFakeTimers();
    getWatchlist.mockReset().mockReturnValue(of([entry('AAPL'), entry('MSFT')]));
    getWatchlistQuotes.mockReset().mockReturnValue(
      of([
        {
          symbol: 'AAPL',
          price: 193.12,
          bid: 193.1,
          ask: 193.14,
          currency: 'USD',
          change: -1.2,
          changePercent: -0.62,
          previousClose: 194.32,
          marketState: 'REGULAR',
          stale: false,
          quoteAsOf: '2026-10-03T10:00:00Z',
          receivedAt: '2026-10-03T10:00:01Z'
        }
      ])
    );
    removeFromWatchlist.mockReset().mockReturnValue(of([entry('MSFT')]));

    await TestBed.configureTestingModule({
      imports: [WatchlistComponent],
      providers: [
        provideRouter([]),
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } },
        {
          provide: AccountService,
          useValue: {
            getBalance: () => of({ accountId: 1, cashBalance: 1, currency: 'INR', asOf: '' }),
            getPositions: () => of([{ accountId: 1, symbol: 'AAPL', quantity: 3, averageCost: 100 }])
          }
        },
        { provide: OrderService, useValue: { getOrderHistory: () => of([]) } },
        {
          provide: InstrumentService,
          useValue: {
            getInstruments: () => of([entry('AAPL'), entry('MSFT'), entry('TSLA')]),
            getWatchlist,
            getWatchlistQuotes,
            removeFromWatchlist,
            addToWatchlist: vi.fn()
          }
        }
      ]
    }).compileComponents();
  });

  afterEach(() => vi.useRealTimers());

  function create() {
    const fixture = TestBed.createComponent(WatchlistComponent);
    fixture.detectChanges();
    vi.advanceTimersByTime(0);
    fixture.detectChanges();
    return fixture;
  }

  const root = (fixture: ReturnType<typeof create>) => fixture.nativeElement as HTMLElement;

  function openRow(fixture: ReturnType<typeof create>, index: number) {
    root(fixture).querySelectorAll<HTMLElement>('[data-testid="watch-row"]')[index].click();
    fixture.detectChanges();
  }

  it('lists one row per watched instrument, with the quote figures inline', () => {
    const fixture = create();

    const rows = root(fixture).querySelectorAll('[data-testid="watch-row"]');
    expect(rows.length).toBe(2);

    // AAPL has a quote: last price, the change in percent, and bid / ask on the row itself.
    expect(rows[0].textContent).toContain('AAPL');
    expect(rows[0].textContent).toContain('193.12');
    expect(rows[0].textContent).toContain('-0.62%');
    expect(rows[0].textContent).toContain('193.10');
    expect(rows[0].textContent).toContain('193.14');
    // 193.12 USD at 95 INR per USD
    expect(rows[0].textContent).toContain('18,346.40');
    expect(rows[0].getAttribute('data-dir')).toBe('down');

    // MSFT has no quote yet, so its figures show a dash rather than inventing numbers.
    expect(rows[1].textContent).toContain('MSFT');
    expect(rows[1].textContent).not.toContain('193.12');
    expect(rows[1].getAttribute('data-dir')).toBe('flat');
  });

  it('opens the details for a row with the price, the chart and what the customer holds, and none of the repeated figures', () => {
    const fixture = create();

    expect(root(fixture).querySelector('[data-testid="watch-overlay"]')).toBeNull();
    openRow(fixture, 0);

    const card = root(fixture).querySelector('[data-testid="watch-card"]');
    expect(card).not.toBeNull();
    const quote = root(fixture).querySelector('[data-testid="watch-quote"]');
    expect(quote?.textContent).toContain('193.12');
    expect(quote?.textContent).toContain('-0.62%');
    expect(card?.textContent).not.toContain('REGULAR');
    expect(card?.querySelector('app-candle-chart')).not.toBeNull();
    expect(card?.querySelector('app-mascot')).not.toBeNull();
    expect(card?.textContent).toContain('You hold');
    expect(card?.textContent).toContain('3');
  });

  it('says it is waiting for an instrument that has no quote yet', () => {
    const fixture = create();

    openRow(fixture, 1);

    expect(root(fixture).querySelector('[data-testid="watch-quote"]')).toBeNull();
    expect(root(fixture).querySelector('[data-testid="watch-quote-waiting"]')).not.toBeNull();
  });

  it('closes the details with the close button and with Escape', () => {
    const fixture = create();

    openRow(fixture, 0);
    root(fixture).querySelector<HTMLElement>('[data-testid="watch-close"]')!.click();
    fixture.detectChanges();
    expect(root(fixture).querySelector('[data-testid="watch-overlay"]')).toBeNull();

    openRow(fixture, 0);
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(root(fixture).querySelector('[data-testid="watch-overlay"]')).toBeNull();
  });

  it('re-reads the quotes on a timer', () => {
    create();
    expect(getWatchlistQuotes).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(QUOTE_REFRESH_MS * 2);

    expect(getWatchlistQuotes).toHaveBeenCalledTimes(3);
  });

  it('the remove control is a minus, takes the instrument off the list, and does not open the details', () => {
    const fixture = create();
    const remove = root(fixture).querySelector<HTMLButtonElement>('[data-testid="watch-row-remove"]');

    expect(remove?.querySelector('path')?.getAttribute('d')).toBe('M5 10h10');
    remove?.click();
    fixture.detectChanges();

    expect(removeFromWatchlist).toHaveBeenCalledWith(1, 'AAPL');
    expect(root(fixture).querySelectorAll('[data-testid="watch-row"]').length).toBe(1);
    expect(root(fixture).querySelector('[data-testid="watch-overlay"]')).toBeNull();
  });

  it('removing the instrument whose details are open closes them', () => {
    const fixture = create();
    openRow(fixture, 0);

    root(fixture).querySelector<HTMLButtonElement>('[data-testid="watch-row-remove"]')!.click();
    fixture.detectChanges();

    expect(root(fixture).querySelector('[data-testid="watch-overlay"]')).toBeNull();
  });
});
