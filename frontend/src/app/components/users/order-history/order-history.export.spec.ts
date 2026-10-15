import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { ExportService } from '../../../core/export/export.service';
import { AccountContextService } from '../../../core/services/account-context.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';
import { OrderService } from '../../../core/services/order.service';
import { OrderHistoryComponent } from './order-history.component';

function order(orderId: string, symbol: string, status: string, createdOn: string) {
  return {
    orderId,
    accountId: 1,
    symbol,
    side: 'BUY',
    quantity: 10,
    price: status === 'FILLED' ? null : 100,
    status,
    createdOn,
    executedPrice: status === 'FILLED' ? 101.5 : null,
    idempotencyKey: orderId
  };
}

describe('OrderHistoryComponent download', () => {
  const exportFile = vi.fn();
  const getOrderHistory = vi.fn();

  const orders = [
    order('a', 'AAPL', 'FILLED', '2026-10-03T10:00:00Z'),
    order('b', 'MSFT', 'REJECTED', '2026-10-02T10:00:00Z'),
    order('c', 'AAPL', 'CANCELLED', '2026-10-01T10:00:00Z')
  ];

  async function create() {
    exportFile.mockReset().mockResolvedValue(undefined);
    getOrderHistory.mockReset().mockReturnValue(of(orders));
    await TestBed.configureTestingModule({
      imports: [OrderHistoryComponent],
      providers: [
        provideRouter([]),
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } },
        { provide: OrderService, useValue: { getOrderHistory } },
        { provide: ExportService, useValue: { export: exportFile } },
        { provide: HomeCurrencyService, useValue: { ensureLoaded: vi.fn(), homeCurrency: () => 'USD', toHome: (amount: number) => amount } }
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(OrderHistoryComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  const request = (fixture: { componentInstance: unknown }, value: unknown) =>
    (fixture.componentInstance as { download(r: unknown): Promise<void> }).download(value);

  beforeEach(() => vi.spyOn(console, 'info').mockImplementation(() => undefined));
  afterEach(() => vi.restoreAllMocks());

  it('puts a Download button beside Refresh once there are orders', async () => {
    const fixture = await create();

    expect(fixture.nativeElement.querySelector('[data-testid="download-toggle"]')).not.toBeNull();
  });

  it('offers the current filter and everything once a filter narrows the list', async () => {
    const fixture = await create();
    fixture.nativeElement.querySelector('[data-testid="filters-toggle"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="filter-FILLED"]').click();
    fixture.detectChanges();

    fixture.nativeElement.querySelector('[data-testid="download-toggle"]').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="scope-filtered"]').parentElement.textContent).toContain('Current filter (1)');
    expect(fixture.nativeElement.querySelector('[data-testid="scope-all"]').parentElement.textContent).toContain('Everything (3)');
  });

  it('exports only what the filter shows, saying which filter it was', async () => {
    const fixture = await create();
    fixture.nativeElement.querySelector('[data-testid="filters-toggle"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="filter-FILLED"]').click();
    fixture.detectChanges();

    await request(fixture, { format: 'xlsx', scope: 'filtered', includeCharts: false });

    const [format, document, name] = exportFile.mock.calls[0];
    expect(format).toBe('xlsx');
    expect(name).toBe('order-blotter');
    expect(document.tables[0].rows.length).toBe(1);
    expect(document.tables[0].rows[0][2]).toBe('AAPL');
    expect(document.subtitle.join(' ')).toContain('FILLED');
  });

  it('exports every order when asked for everything, filter or not', async () => {
    const fixture = await create();
    fixture.nativeElement.querySelector('[data-testid="filters-toggle"]').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="filter-FILLED"]').click();
    fixture.detectChanges();

    await request(fixture, { format: 'csv', scope: 'all', includeCharts: false });

    expect(exportFile.mock.calls[0][1].tables[0].rows.length).toBe(3);
    expect(exportFile.mock.calls[0][1].subtitle.join(' ')).toContain('none');
  });

  it('adds an orders-by-status chart to a PDF when asked', async () => {
    const fixture = await create();

    await request(fixture, { format: 'pdf', scope: 'all', includeCharts: true });

    const charts = exportFile.mock.calls[0][1].charts;
    expect(charts[0].title).toBe('Orders by status');
    expect(charts[0].bars).toEqual(expect.arrayContaining([{ label: 'FILLED', value: 1 }, { label: 'REJECTED', value: 1 }]));
  });

  it('tells the customer when the file could not be made', async () => {
    const fixture = await create();
    exportFile.mockRejectedValue(new Error('boom'));
    vi.spyOn(console, 'error').mockImplementation(() => undefined);

    await request(fixture, { format: 'pdf', scope: 'all', includeCharts: false });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="cancel-error"]')?.textContent).toContain('could not be created');
  });
});
