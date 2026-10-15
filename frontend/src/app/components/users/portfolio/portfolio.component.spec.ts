import { HttpErrorResponse } from '@angular/common/http';
import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ExportService } from '../../../core/export/export.service';
import {
  PnlResponse,
  PortfolioHistory,
  PortfolioSummary,
  PricedPosition
} from '../../../core/models/portfolio.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';
import { PortfolioService } from '../../../core/services/portfolio.service';
import { EchartComponent } from '../shared/echart/echart.component';
import { PortfolioComponent, categoryOf, freshnessOf, isPricingUnavailable } from './portfolio.component';

/** The real chart loads a canvas library; these tests care what the page asks it to draw, not the drawing. */
@Component({ selector: 'app-echart', standalone: true, template: '<div data-testid="chart-stub"></div>' })
class EchartStub {
  readonly option = input<unknown>();
  readonly label = input('');
  readonly height = input(0);
}

describe('PortfolioComponent', () => {
  let fixture: ComponentFixture<PortfolioComponent>;
  const getSummary = vi.fn();
  const getPositions = vi.fn();
  const getPnl = vi.fn();
  const getHistory = vi.fn();
  const accountPositions = vi.fn();
  const accountBalance = vi.fn();
  const exportFile = vi.fn();

  const now = Date.parse('2026-10-19T14:05:11Z');
  const iso = (minutesAgo: number) => new Date(Date.now() - minutesAgo * 60_000).toISOString();

  const summary = (overrides: Partial<PortfolioSummary> = {}): PortfolioSummary => ({
    accountId: 1,
    baseCurrency: 'USD',
    cashBalance: 24500.75,
    marketValue: 65120,
    costBasis: 61300,
    unrealisedPnl: 3820,
    unrealisedPnlPercent: 6.23,
    realisedPnl: 1145.5,
    totalValue: 89620.75,
    positionCount: 2,
    partial: false,
    asOf: '2026-10-19T14:05:11Z',
    ...overrides
  });

  const position = (overrides: Partial<PricedPosition> = {}): PricedPosition => ({
    accountId: 1,
    symbol: 'AAPL',
    quantity: 200,
    averageCost: 228.4,
    costBasis: 45680,
    lastPrice: 232.71,
    marketValue: 46542,
    unrealisedPnl: 862,
    unrealisedPnlPercent: 1.89,
    currency: 'USD',
    priceAsOf: iso(1),
    stale: false,
    ...overrides
  });

  const pnl = (overrides: Partial<PnlResponse> = {}): PnlResponse => ({
    accountId: 1,
    baseCurrency: 'USD',
    from: null,
    to: null,
    realisedPnl: 1145.5,
    unrealisedPnl: 3820,
    totalPnl: 4965.5,
    asOf: '2026-10-19T14:05:11Z',
    ...overrides
  });

  const history = (overrides: Partial<PortfolioHistory> = {}): PortfolioHistory => ({
    accountId: 1,
    baseCurrency: 'USD',
    range: '30d',
    points: [
      { at: '2026-10-17T10:00:00Z', totalValue: 88000 },
      { at: '2026-10-18T10:00:00Z', totalValue: 89000 }
    ],
    dayChange: { amount: 125.5, percent: 0.14, since: '2026-10-18T10:00:00Z' },
    ...overrides
  });

  const assetClasses: Record<string, string> = { AAPL: 'US_EQUITY', MSFT: 'US_EQUITY', BTC: 'CRYPTO', EURUSD: 'FX' };

  const el = <T extends HTMLElement = HTMLElement>(testId: string): T | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  const all = (testId: string): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll(`[data-testid="${testId}"]`));
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  async function build(): Promise<void> {
    TestBed.configureTestingModule({
      imports: [PortfolioComponent],
      providers: [
        provideRouter([]),
        { provide: PortfolioService, useValue: { getSummary, getPositions, getPnl, getHistory } },
        { provide: AccountService, useValue: { getPositions: accountPositions, getBalance: accountBalance } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } },
        { provide: HomeCurrencyService, useValue: { ensureLoaded: vi.fn(), instrument: (symbol: string) => ({ assetClass: assetClasses[symbol] }) } },
        { provide: ExportService, useValue: { export: exportFile } }
      ]
    });
    TestBed.overrideComponent(PortfolioComponent, { remove: { imports: [EchartComponent] }, add: { imports: [EchartStub] } });
    await TestBed.compileComponents();
    fixture = TestBed.createComponent(PortfolioComponent);
    await settle();
  }

  beforeEach(() => {
    localStorage.clear();
    getSummary.mockReset().mockReturnValue(of(summary()));
    getPositions.mockReset().mockReturnValue(of([position(), position({ symbol: 'BTC', quantity: 1, averageCost: 60000, costBasis: 60000, lastPrice: 61000, marketValue: 61000, unrealisedPnl: 1000, unrealisedPnlPercent: 1.67 })]));
    getPnl.mockReset().mockReturnValue(of(pnl()));
    getHistory.mockReset().mockReturnValue(of(history()));
    accountPositions.mockReset().mockReturnValue(of([]));
    accountBalance.mockReset().mockReturnValue(of({ accountId: 1, cashBalance: 500, currency: 'USD', asOf: '' }));
    exportFile.mockReset().mockResolvedValue(undefined);
  });

  it('opens with the total value large, and today and all-time beside it with a gain dot', async () => {
    await build();

    expect(el('tile-total')?.textContent).toContain('89,620.75');
    expect(el('tile-today')?.textContent).toContain('+125.50');
    expect(el('tile-today')?.textContent).toContain('+0.14%');
    expect(el('tile-today')?.querySelector('.dot.live')).not.toBeNull();
    // all time = what the holdings gained plus what sales booked
    expect(el('tile-unrealised')?.textContent).toContain('+4,965.50');
  });

  it('says today is not known yet rather than guessing', async () => {
    getHistory.mockReturnValue(of(history({ dayChange: null })));
    await build();

    expect(el('tile-today')?.textContent).toContain('n/a');
  });

  it('uses plain words: Invested, Current value, Gain/Loss', async () => {
    await build();

    const page = text();
    expect(page).toContain('Invested');
    expect(page).toContain('Current value');
    expect(page).toContain('Gain/Loss');
    expect(page).not.toContain('Cost basis');
    expect(page).not.toContain('Market value');
    expect(page).not.toContain('Unrealised');
  });

  it('keeps the advanced figures inside a Details section', async () => {
    await build();

    const details = el('details')!;
    expect(details.tagName).toBe('DETAILS');
    expect(details.hasAttribute('open')).toBe(false);
    expect(el('tile-cash')?.textContent).toContain('24,500.75');
    expect(el('tile-realised')?.textContent).toContain('1,145.50');
  });

  it('draws the value history and the allocation donut', async () => {
    await build();

    expect(all('chart-stub').length).toBe(2);
  });

  it('says the history builds with use, when there are not yet two points', async () => {
    getHistory.mockReturnValue(of(history({ points: [] })));
    await build();

    expect(el('history-empty')).not.toBeNull();
    expect(all('chart-stub').length).toBe(1);
  });

  it('asks for another range when 3M is chosen, and remembers it', async () => {
    await build();

    el('range-3m')!.click();
    await settle();

    expect(getHistory).toHaveBeenLastCalledWith(1, '3m');
    expect(localStorage.getItem('quorum-portfolio-range')).toBe('3m');
  });

  describe('holdings', () => {
    it('lists each holding with invested, current value and gain, and tints the edge by the sign', async () => {
      await build();

      const row = el('position-AAPL')!;
      expect(row.textContent).toContain('45,680.00');
      expect(row.textContent).toContain('46,542.00');
      expect(row.textContent).toContain('+862.00');
      expect(row.textContent).toContain('+1.89%');
      expect(row.getAttribute('data-dir')).toBe('gain');
    });

    it('shows a loss with its own sign and direction', async () => {
      getPositions.mockReturnValue(of([position({ unrealisedPnl: -300, unrealisedPnlPercent: -0.65 })]));
      await build();

      expect(el('position-AAPL')?.getAttribute('data-dir')).toBe('loss');
      expect(el('position-AAPL')?.textContent).toContain('-300.00');
    });

    it('does not repeat the currency on every row when everything is in one', async () => {
      await build();

      expect(el('position-AAPL')?.textContent).not.toContain('USD');
    });

    it('offers only the categories that hold something, with counts, and filters by one', async () => {
      await build();

      expect(el('category-ALL')?.textContent).toContain('2');
      expect(el('category-EQUITY')?.textContent).toContain('1');
      expect(el('category-CRYPTO')?.textContent).toContain('1');
      expect(el('category-FOREX')).toBeNull();

      el('category-CRYPTO')!.click();
      await settle();

      expect(el('position-BTC')).not.toBeNull();
      expect(el('position-AAPL')).toBeNull();
      expect(localStorage.getItem('quorum-portfolio-filter')).toBe('CRYPTO');
    });

    it('remembers the filter the next time the page opens', async () => {
      localStorage.setItem('quorum-portfolio-filter', 'EQUITY');
      await build();

      expect(el('position-AAPL')).not.toBeNull();
      expect(el('position-BTC')).toBeNull();
    });

    it('sorts by a column and flips on a second click', async () => {
      await build();
      const order = () => all('positions-table').length && Array.from(fixture.nativeElement.querySelectorAll('tbody tr strong')).map((node) => (node as HTMLElement).textContent!.trim());

      // default: current value, largest first
      expect(order()).toEqual(['BTC', 'AAPL']);

      (fixture.nativeElement.querySelector('th[aria-sort] .sort') as HTMLButtonElement).click();
      await settle();
      expect(order()).toEqual(['AAPL', 'BTC']);
    });

    it('shows the companion when nothing is held', async () => {
      getSummary.mockReturnValue(of(summary({ positionCount: 0 })));
      getPositions.mockReturnValue(of([]));
      await build();

      expect(el('no-holdings')).not.toBeNull();
      expect(el('mascot')).not.toBeNull();
    });
  });

  describe('price freshness', () => {
    it('classifies by age: live under 5 minutes, delayed to 15, stale beyond', () => {
      expect(freshnessOf(new Date(now - 60_000).toISOString(), now)).toBe('live');
      expect(freshnessOf(new Date(now - 5 * 60_000).toISOString(), now)).toBe('delayed');
      expect(freshnessOf(new Date(now - 15 * 60_000).toISOString(), now)).toBe('delayed');
      expect(freshnessOf(new Date(now - 16 * 60_000).toISOString(), now)).toBe('stale');
      expect(freshnessOf(null, now)).toBe('none');
    });

    it('puts a dot beside each price, with the exact time on hover', async () => {
      getPositions.mockReturnValue(of([
        position({ priceAsOf: iso(1) }),
        position({ symbol: 'MSFT', priceAsOf: iso(10), stale: true }),
        position({ symbol: 'BTC', priceAsOf: iso(30), stale: true }),
        position({ symbol: 'EURUSD', lastPrice: null, marketValue: null, unrealisedPnl: null, unrealisedPnlPercent: null, priceAsOf: null })
      ]));
      await build();

      expect(el('position-AAPL')?.querySelector('[data-testid="price-live"]')).not.toBeNull();
      expect(el('position-MSFT')?.querySelector('[data-testid="price-delayed"]')).not.toBeNull();
      expect(el('position-BTC')?.querySelector('[data-testid="price-stale"]')).not.toBeNull();
      expect(el('position-EURUSD')?.querySelector('[data-testid="price-none"]')).not.toBeNull();
      expect(el('position-AAPL')?.querySelector('[data-testid="price-live"]')?.getAttribute('title')).toMatch(/^Live\. Price as of /);
    });
  });

  describe('categories', () => {
    it('maps asset classes to the groups on offer', () => {
      expect(categoryOf('US_EQUITY')).toBe('EQUITY');
      expect(categoryOf('INDIAN_EQUITY')).toBe('EQUITY');
      expect(categoryOf('CRYPTO')).toBe('CRYPTO');
      expect(categoryOf('FX')).toBe('FOREX');
      expect(categoryOf(undefined)).toBe('OTHER');
    });
  });

  describe('download', () => {
    it('exports exactly the filtered holdings, with a line chart of value and a donut of allocation', async () => {
      await build();
      el('category-CRYPTO')!.click();
      await settle();

      await (fixture.componentInstance as unknown as { download(r: unknown): Promise<void> }).download({ format: 'pdf', scope: 'filtered', includeCharts: true });

      const [format, document] = exportFile.mock.calls[0];
      expect(format).toBe('pdf');
      const holdings = document.tables.find((table: { title: string }) => table.title === 'Holdings');
      expect(holdings.rows.map((row: unknown[]) => row[0])).toEqual(['BTC']);
      expect(document.charts.map((chart: { title: string }) => chart.title)).toEqual(['Portfolio value, last 30d', 'Allocation by current value']);
      expect(document.charts[0].kind).toBe('line');
    });

    it('exports every holding when asked for everything', async () => {
      await build();
      el('category-CRYPTO')!.click();
      await settle();

      await (fixture.componentInstance as unknown as { download(r: unknown): Promise<void> }).download({ format: 'csv', scope: 'all', includeCharts: false });

      const holdings = exportFile.mock.calls[0][1].tables.find((table: { title: string }) => table.title === 'Holdings');
      expect(holdings.rows.length).toBe(2);
      expect(exportFile.mock.calls[0][1].charts).toEqual([]);
    });
  });

  describe('when things go wrong', () => {
    it('says the prices are down and still lists the holdings at cost', async () => {
      getSummary.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: { errorCode: 'MKT-503' } })));
      accountPositions.mockReturnValue(of([{ accountId: 1, symbol: 'AAPL', quantity: 10, averageCost: 100 }]));
      await build();

      expect(el('pricing-unavailable')).not.toBeNull();
      expect(el('fallback-table')?.textContent).toContain('1,000.00');
    });

    it('shows an error with a retry for any other failure', async () => {
      getSummary.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      await build();

      expect(el('portfolio-error')).not.toBeNull();
    });

    it('a chart that cannot load does not take the holdings with it', async () => {
      getHistory.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      await build();

      expect(el('position-AAPL')).not.toBeNull();
      expect(el('history-empty')).not.toBeNull();
    });

    it('recognises the pricing-unavailable answer and nothing else', () => {
      expect(isPricingUnavailable(new HttpErrorResponse({ status: 503, error: { errorCode: 'MKT-503' } }))).toBe(true);
      expect(isPricingUnavailable(new HttpErrorResponse({ status: 503, error: { errorCode: 'OTHER' } }))).toBe(false);
      expect(isPricingUnavailable(new HttpErrorResponse({ status: 500 }))).toBe(false);
    });

    it('flags unpriced holdings in a banner, and leaves delayed prices to the table colouring', async () => {
      getSummary.mockReturnValue(of(summary({ partial: true })));
      getPositions.mockReturnValue(of([
        position({ stale: true, priceAsOf: iso(10) }),
        position({ symbol: 'EURUSD', lastPrice: null, marketValue: null, unrealisedPnl: null, unrealisedPnlPercent: null, priceAsOf: null })
      ]));
      await build();

      expect(el('partial-banner')).not.toBeNull();
      expect(el('stale-banner')).toBeNull();
    });

    it('shows the profit-and-loss error without blanking the holdings', async () => {
      getPnl.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422 })));
      await build();

      expect(el('pnl-error')?.textContent).toContain('date range is not valid');
      expect(el('position-AAPL')).not.toBeNull();
    });
  });
});
