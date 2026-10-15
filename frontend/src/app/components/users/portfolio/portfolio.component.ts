import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { BalanceResponse, PositionResponse } from '../../../core/models/account.model';
import {
  HistoryRange,
  PnlResponse,
  PortfolioHistory,
  PortfolioSummary,
  PricedPosition
} from '../../../core/models/portfolio.model';
import { BenchmarkSummary } from '../../../core/models/benchmark.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';
import { PortfolioService } from '../../../core/services/portfolio.service';
import { BenchmarkService } from '../../../core/services/benchmark.service';
import { ExportDocument, ExportService, ExportTable } from '../../../core/export/export.service';
import { ThemeService } from '../../../core/theme/theme.service';
import { DownloadMenuComponent, DownloadRequest } from '../shared/download-menu/download-menu.component';
import { ChartOption, EchartComponent, chartText, chartTooltip, token } from '../shared/echart/echart.component';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { IconComponent } from '../shared/icon/icon.component';
import { PortfolioBenchmarkComponent } from '../shared/portfolio-benchmark/portfolio-benchmark.component';

/** True for the documented "no price for any holding" answer: 503 with MKT-503. */
export function isPricingUnavailable(error: unknown): boolean {
  return error instanceof HttpErrorResponse && error.status === 503 && error.error?.errorCode === 'MKT-503';
}

export type Category = 'ALL' | 'EQUITY' | 'CRYPTO' | 'FOREX' | 'OTHER';
type SortKey = 'symbol' | 'quantity' | 'invested' | 'value' | 'gain' | 'gainPct';
export type Freshness = 'live' | 'delayed' | 'stale' | 'none';

const FILTER_KEY = 'quorum-portfolio-filter';
const RANGE_KEY = 'quorum-portfolio-range';

const RANGE_DAYS: Record<HistoryRange, number> = { '30d': 30, '3m': 92, '1y': 366 };

/** What the axis prints at a tick: the day for short ranges, the month (and year) for a long one. */
export function axisDate(value: number, range: HistoryRange): string {
  const when = new Date(value);
  return range === '1y'
    ? when.toLocaleDateString(undefined, { month: 'short', year: '2-digit' })
    : when.toLocaleDateString(undefined, { day: 'numeric', month: 'short' });
}

/** The tooltip: the exact date and time of the reading, then the value. */
function tooltipText(params: unknown, range: HistoryRange): string {
  const first = (Array.isArray(params) ? params[0] : params) as { value?: [number | string, number] } | undefined;
  if (!first?.value) {
    return '';
  }
  const when = new Date(first.value[0]);
  const stamp = when.toLocaleString(undefined, { day: 'numeric', month: 'short', year: range === '30d' ? undefined : 'numeric', hour: '2-digit', minute: '2-digit' });
  return stamp + '<br/><b>' + first.value[1].toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + '</b>';
}

const CATEGORY_LABEL: Record<Category, string> = { ALL: 'All', EQUITY: 'Equity', CRYPTO: 'Crypto', FOREX: 'Forex', OTHER: 'Other' };

/** Equity, crypto and so on, from the instrument's asset class. */
export function categoryOf(assetClass: string | undefined): Exclude<Category, 'ALL'> {
  switch (assetClass) {
    case 'US_EQUITY':
    case 'INDIAN_EQUITY':
      return 'EQUITY';
    case 'CRYPTO':
      return 'CRYPTO';
    case 'FX':
      return 'FOREX';
    default:
      return 'OTHER';
  }
}

/** Under five minutes is live, up to fifteen is delayed, older is stale. A price that never came is none. */
export function freshnessOf(priceAsOf: string | null, now: number = Date.now()): Freshness {
  if (!priceAsOf) {
    return 'none';
  }
  const minutes = (now - Date.parse(priceAsOf)) / 60_000;
  if (Number.isNaN(minutes)) {
    return 'none';
  }
  return minutes < 5 ? 'live' : minutes <= 15 ? 'delayed' : 'stale';
}

function stored<T extends string>(key: string, allowed: readonly T[], fallback: T): T {
  try {
    const value = localStorage.getItem(key);
    return allowed.find((candidate) => candidate === value) ?? fallback;
  } catch {
    return fallback;
  }
}

function remember(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // Storage blocked: the choice holds for this visit.
  }
}

interface HoldingRow {
  position: PricedPosition;
  category: Exclude<Category, 'ALL'>;
  invested: number;
  value: number | null;
  gain: number | null;
  gainPct: number | null;
  freshness: Freshness;
}

/**
 * What you hold, what it is worth, and how you are doing: a value chart over time, an allocation donut, and a
 * holdings table you can filter and sort. Plain words (Invested, Current value, Gain/Loss). Prices come from the
 * platform, which reads the market-data feed first; the dot beside each price says how fresh it is.
 */
@Component({
  selector: 'app-portfolio',
  standalone: true,
  imports: [DecimalPipe, DatePipe, FormsModule, RouterLink, DownloadMenuComponent, EchartComponent, EmptyStateComponent, IconComponent, PortfolioBenchmarkComponent],
  templateUrl: './portfolio.component.html',
  styleUrl: './portfolio.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PortfolioComponent {
  private readonly portfolioService = inject(PortfolioService);
  private readonly accountService = inject(AccountService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessages = inject(ApiErrorMessageService);
  private readonly exporter = inject(ExportService);
  private readonly theme = inject(ThemeService);
  private readonly benchmarkService = inject(BenchmarkService);
  protected readonly home = inject(HomeCurrencyService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly pricingDown = signal(false);
  protected readonly pnlError = signal<string | null>(null);
  protected readonly exporting = signal(false);
  protected readonly benchmarkLoading = signal(false);

  protected readonly summary = signal<PortfolioSummary | null>(null);
  protected readonly positions = signal<PricedPosition[]>([]);
  protected readonly pnl = signal<PnlResponse | null>(null);
  protected readonly history = signal<PortfolioHistory | null>(null);
  protected readonly benchmarkSummary = signal<BenchmarkSummary | null>(null);

  /** What the page shows when no price can be had: the holdings and cash, unpriced. */
  protected readonly unpriced = signal<PositionResponse[]>([]);
  protected readonly cash = signal<BalanceResponse | null>(null);

  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly bySymbol = signal(false);

  protected readonly categories = ['ALL', 'EQUITY', 'CRYPTO', 'FOREX', 'OTHER'] as const;
  protected readonly category = signal<Category>(stored(FILTER_KEY, ['ALL', 'EQUITY', 'CRYPTO', 'FOREX', 'OTHER'] as const, 'ALL'));
  protected readonly ranges: ReadonlyArray<{ value: HistoryRange; label: string }> = [
    { value: '30d', label: '30D' },
    { value: '3m', label: '3M' },
    { value: '1y', label: '1Y' }
  ];
  protected readonly range = signal<HistoryRange>(stored(RANGE_KEY, ['30d', '3m', '1y'] as const, '30d'));
  protected readonly sortKey = signal<SortKey>('value');
  protected readonly sortAscending = signal(false);

  /** Rows in the holdings table; the download menu shows this as its row count. */
  protected readonly holdingCount = computed(() => (this.pricingDown() ? this.fallbackRows().length : this.positions().length));

  /** Positions that have a price but an old one. */
  protected readonly staleCount = computed(
    () => this.positions().filter((position) => position.lastPrice !== null && position.stale).length
  );
  /** Positions that could not be priced at all and are left out of the totals. */
  protected readonly unpricedCount = computed(
    () => this.positions().filter((position) => position.lastPrice === null).length
  );
  protected readonly fallbackRows = computed(() =>
    this.unpriced().map((position) => ({
      ...position,
      costBasis: position.quantity * position.averageCost
    }))
  );

  protected readonly rows = computed<HoldingRow[]>(() =>
    this.positions().map((position) => ({
      position,
      category: categoryOf(this.home.instrument(position.symbol)?.assetClass),
      invested: position.costBasis,
      value: position.marketValue,
      gain: position.unrealisedPnl,
      gainPct: position.unrealisedPnlPercent,
      freshness: position.lastPrice === null ? 'none' : freshnessOf(position.priceAsOf)
    }))
  );

  protected readonly categoryCounts = computed(() => {
    const counts: Record<Category, number> = { ALL: this.rows().length, EQUITY: 0, CRYPTO: 0, FOREX: 0, OTHER: 0 };
    for (const row of this.rows()) {
      counts[row.category] += 1;
    }
    return counts;
  });

  /** Only the categories that hold something are offered, plus All. */
  protected readonly offeredCategories = computed(() =>
    this.categories.filter((category) => category === 'ALL' || this.categoryCounts()[category] > 0)
  );

  protected readonly visibleRows = computed(() => {
    const category = this.category();
    const key = this.sortKey();
    const direction = this.sortAscending() ? 1 : -1;
    const rows = this.rows().filter((row) => category === 'ALL' || row.category === category);
    return [...rows].sort((left, right) => direction * this.compare(key, left, right));
  });

  /** The currency per instrument matters only when more than one is held. */
  protected readonly mixedCurrencies = computed(() => new Set(this.positions().map((position) => position.currency)).size > 1);

  protected readonly allTime = computed(() => {
    const s = this.summary();
    return s ? s.unrealisedPnl + s.realisedPnl : null;
  });

  protected readonly allTimePercent = computed(() => {
    const s = this.summary();
    const all = this.allTime();
    return s && all !== null && s.costBasis > 0 ? (all / s.costBasis) * 100 : null;
  });

  /** Portfolio return percentage for benchmarking. */
  protected readonly portfolioReturn = computed(() => this.allTimePercent() ?? 0);

  /** Compute benchmark summary whenever portfolio return or range changes. */
  protected readonly benchmarkComparison = computed(() => {
    if (this.benchmarkSummary()) {
      return this.benchmarkSummary();
    }
    return null;
  });

  protected readonly dayChange = computed(() => this.history()?.dayChange ?? null);

  /** The line of value over time. Colours come from the theme tokens, so it is rebuilt when the theme changes. */
  protected readonly historyOption = computed<ChartOption>(() => {
    this.theme.changes();
    const points = this.history()?.points ?? [];
    const range = this.range();
    // The axis always spans the whole range asked for, ending now, so 30D, 3M and 1Y each look like what they say.
    const end = Date.now();
    const start = end - RANGE_DAYS[range] * 86_400_000;
    const line = token('--accent', '#1f5fd6');
    const muted = token('--muted', '#6b7078');
    const grid = token('--line', '#e5e5ea');
    return {
      grid: { left: 8, right: 16, top: 24, bottom: 8, containLabel: true },
      tooltip: chartTooltip({
        trigger: 'axis',
        axisPointer: { type: 'line', lineStyle: { color: muted, type: 'dashed', width: 1 } },
        formatter: (params: unknown) => tooltipText(params, range),
        valueFormatter: (value: number) => value.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      }),
      xAxis: {
        type: 'time',
        min: start,
        max: end,
        axisLine: { lineStyle: { color: grid } },
        axisTick: { show: false },
        splitNumber: range === '30d' ? 6 : 5,
        axisLabel: { ...chartText(muted, 12), hideOverlap: true, formatter: (value: number) => axisDate(value, range) }
      },
      yAxis: {
        type: 'value',
        scale: true,
        splitLine: { lineStyle: { color: grid, type: 'dashed' } },
        axisLabel: { ...chartText(muted, 12), formatter: (value: number) => value.toLocaleString(undefined, { maximumFractionDigits: 0 }) }
      },
      series: [
        {
          type: 'line',
          smooth: false,
          symbol: 'circle',
          symbolSize: 6,
          showSymbol: points.length < 40,
          lineStyle: { color: line, width: 2 },
          itemStyle: { color: line },
          areaStyle: { color: line, opacity: 0.08 },
          data: points.map((point) => [point.at, point.totalValue])
        }
      ]
    };
  });

  /** Share of current value per holding. */
  protected readonly allocationOption = computed<ChartOption>(() => {
    this.theme.changes();
    const palette = ['--c1', '--c2', '--c3', '--c4', '--c5', '--c6'].map((name) => token(name, '#888'));
    const surface = token('--surface', '#fff');
    const slices = this.rows()
      .filter((row) => row.value !== null && row.value > 0)
      .sort((left, right) => (right.value ?? 0) - (left.value ?? 0))
      .map((row) => ({ name: row.position.symbol, value: row.value as number }));
    return {
      tooltip: chartTooltip({ trigger: 'item', formatter: '{b}<br/>{c} ({d}%)' }),
      legend: { bottom: 0, icon: 'rect', itemWidth: 10, itemHeight: 10, textStyle: chartText(token('--muted', '#737373'), 12) },
      color: palette,
      series: [
        {
          type: 'pie',
          radius: ['52%', '74%'],
          center: ['50%', '44%'],
          itemStyle: { borderColor: surface, borderWidth: 3, borderRadius: 4 },
          label: { show: false },
          emphasis: { scale: true, scaleSize: 8 },
          data: slices
        }
      ]
    };
  });

  protected readonly hasAllocation = computed(() => this.rows().some((row) => row.value !== null && row.value > 0));

  constructor() {
    this.home.ensureLoaded();
    this.load();
  }

  protected load(): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    this.loading.set(true);
    this.error.set(null);
    this.pricingDown.set(false);

    this.portfolioService.getSummary(accountId).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.loadDetails(accountId);
        this.loadBenchmarks();
      },
      error: (error: unknown) => {
        if (isPricingUnavailable(error)) {
          this.pricingDown.set(true);
          this.summary.set(null);
          this.positions.set([]);
          this.pnl.set(null);
          this.loadUnpriced(accountId);
        } else {
          this.error.set(this.errorMessages.getMessage(error));
          this.loading.set(false);
        }
      }
    });
  }

  private loadBenchmarks(): void {
    this.benchmarkLoading.set(true);
    this.benchmarkService.fetchBenchmarks(this.range()).subscribe({
      next: () => {
        const summary = this.benchmarkService.getSummary(this.portfolioReturn());
        this.benchmarkSummary.set(summary);
        this.benchmarkLoading.set(false);
      },
      error: () => {
        this.benchmarkLoading.set(false);
      }
    });
  }

  protected applyRange(): void {
    this.loadPnl(this.accountContext.getCurrentAccount().accountNumericId);
  }

  protected toggleBySymbol(checked: boolean): void {
    this.bySymbol.set(checked);
    this.applyRange();
  }

  protected setCategory(category: Category): void {
    this.category.set(category);
    remember(FILTER_KEY, category);
  }

  protected setRange(range: HistoryRange): void {
    if (range === this.range()) {
      return;
    }
    this.range.set(range);
    remember(RANGE_KEY, range);
    this.loadHistory(this.accountContext.getCurrentAccount().accountNumericId);
    this.loadBenchmarks();
  }

  protected sortBy(key: SortKey): void {
    if (this.sortKey() === key) {
      this.sortAscending.update((value) => !value);
    } else {
      this.sortKey.set(key);
      this.sortAscending.set(key === 'symbol');
    }
  }

  protected sortState(key: SortKey): 'ascending' | 'descending' | 'none' {
    return this.sortKey() === key ? (this.sortAscending() ? 'ascending' : 'descending') : 'none';
  }

  protected categoryLabel(category: Category): string {
    return CATEGORY_LABEL[category];
  }

  /** The sign of a profit or loss, as a word as well as a colour. */
  protected direction(value: number | null): 'gain' | 'loss' | 'flat' {
    if (value === null || value === 0) {
      return 'flat';
    }
    return value > 0 ? 'gain' : 'loss';
  }

  protected freshnessLabel(row: HoldingRow): string {
    const when = row.position.priceAsOf ? new Date(row.position.priceAsOf).toLocaleString() : '';
    switch (row.freshness) {
      case 'live':
        return `Live. Price as of ${when}`;
      case 'delayed':
        return `Delayed (5 to 15 minutes old). Price as of ${when}`;
      case 'stale':
        return `Stale (over 15 minutes old). Price as of ${when}`;
      default:
        return 'No price available';
    }
  }

  /**
   * Downloads what the page shows. "Current filter" is the category on screen; "everything" is every holding.
   * The date range filters only the realised-gain tables, and the file says so in its header.
   */
  protected async download(request: DownloadRequest): Promise<void> {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    try {
      await this.exporter.export(request.format, this.buildExport(request), 'portfolio');
    } catch (error) {
      console.error('[Portfolio] export failed', error);
      this.error.set('The file could not be created. Please try again.');
    } finally {
      this.exporting.set(false);
    }
  }

  private buildExport(request: DownloadRequest): ExportDocument {
    const summary = this.summary();
    const pnl = this.pnl();
    const tables: ExportTable[] = [];
    const subtitle = [`Generated ${new Date().toLocaleString()}`];

    if (summary) {
      subtitle.push(`Valued ${new Date(summary.asOf).toLocaleString()}. Totals in ${summary.baseCurrency}.`);
      tables.push({
        title: 'Summary',
        columns: [{ header: 'Measure' }, { header: 'Value', numeric: true }],
        rows: [
          ['Total value', summary.totalValue],
          ['Cash', summary.cashBalance],
          ['Invested', summary.costBasis],
          ['Current value', summary.marketValue],
          ['Gain/Loss on holdings', summary.unrealisedPnl],
          ['Gain/Loss on holdings %', summary.unrealisedPnlPercent],
          ['Gain/Loss from sales', summary.realisedPnl],
          ['Today', this.dayChange()?.amount ?? null],
          ['Today %', this.dayChange()?.percent ?? null]
        ]
      });
    }

    if (this.pricingDown()) {
      subtitle.push('Prices were not available: holdings are at cost.');
      tables.push({
        title: 'Holdings at cost',
        columns: [{ header: 'Instrument' }, { header: 'Quantity', numeric: true }, { header: 'Average cost', numeric: true }, { header: 'Invested', numeric: true }],
        rows: this.fallbackRows().map((row) => [row.symbol, row.quantity, row.averageCost, row.costBasis])
      });
    } else {
      const rows = request.scope === 'filtered' ? this.visibleRows() : this.rows();
      subtitle.push(
        request.scope === 'filtered' && this.category() !== 'ALL' ? `Holdings shown: ${CATEGORY_LABEL[this.category()]} only.` : 'Holdings shown: all.'
      );
      tables.push({
        title: 'Holdings',
        columns: [
          { header: 'Instrument' },
          { header: 'Type' },
          { header: 'Currency' },
          { header: 'Quantity', numeric: true },
          { header: 'Avg cost', numeric: true },
          { header: 'Current price', numeric: true },
          { header: 'Invested', numeric: true },
          { header: 'Current value', numeric: true },
          { header: 'Gain/Loss', numeric: true },
          { header: 'Gain/Loss %', numeric: true },
          { header: 'Price freshness' }
        ],
        rows: rows.map((row) => [
          row.position.symbol,
          CATEGORY_LABEL[row.category],
          row.position.currency,
          row.position.quantity,
          row.position.averageCost,
          row.position.lastPrice,
          row.invested,
          row.value,
          row.gain,
          row.gainPct,
          row.freshness === 'none' ? 'Unavailable' : row.freshness === 'live' ? 'Live' : row.freshness === 'delayed' ? 'Delayed' : 'Stale'
        ])
      });
    }

    if (pnl) {
      subtitle.push(`Realised gains range: ${pnl.from ?? (this.from() || 'start')} to ${pnl.to ?? (this.to() || 'today')}.`);
      tables.push({
        title: 'Gain/Loss',
        columns: [{ header: 'Measure' }, { header: `Value (${pnl.baseCurrency})`, numeric: true }],
        rows: [['From sales (date range)', pnl.realisedPnl], ['On holdings (now)', pnl.unrealisedPnl], ['Total', pnl.totalPnl]]
      });
      if (pnl.bySymbol && pnl.bySymbol.length > 0) {
        tables.push({
          title: 'Gain/Loss by instrument',
          columns: [{ header: 'Instrument' }, { header: 'From sales', numeric: true }, { header: 'On holdings', numeric: true }, { header: 'Total', numeric: true }],
          rows: pnl.bySymbol.map((row) => [row.symbol, row.realisedPnl, row.unrealisedPnl, row.totalPnl])
        });
      }
    }

    const charts: NonNullable<ExportDocument['charts']> = [];
    if (request.includeCharts) {
      const points = this.history()?.points ?? [];
      if (points.length >= 2) {
        charts.push({
          title: `Portfolio value, last ${this.range()}`,
          kind: 'line',
          unit: this.history()?.baseCurrency ?? '',
          bars: points.map((point) => ({ label: new Date(point.at).toLocaleDateString(), value: point.totalValue }))
        });
      }
      const held = (request.scope === 'filtered' ? this.visibleRows() : this.rows()).filter((row) => row.value !== null && row.value > 0);
      if (held.length > 0) {
        charts.push({
          title: 'Allocation by current value',
          bars: held.map((row) => ({ label: row.position.symbol, value: row.value as number })).sort((a, b) => b.value - a.value)
        });
      }
    }

    return { title: 'Portfolio', subtitle, tables, charts };
  }

  private loadDetails(accountId: number): void {
    forkJoin({
      positions: this.portfolioService.getPositions(accountId),
      pnl: this.pnlRequest(accountId),
      history: this.portfolioService.getHistory(accountId, this.range()).pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ positions, pnl, history }) => {
        this.positions.set(positions);
        this.pnl.set(pnl);
        this.history.set(history);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        // The summary worked a moment ago; if the prices have gone since, say so instead of showing half a page.
        if (isPricingUnavailable(error)) {
          this.pricingDown.set(true);
          this.summary.set(null);
          this.loadUnpriced(accountId);
        } else {
          this.error.set(this.errorMessages.getMessage(error));
          this.loading.set(false);
        }
      }
    });
  }

  private loadHistory(accountId: number): void {
    this.portfolioService.getHistory(accountId, this.range()).subscribe({
      next: (history) => this.history.set(history),
      // A chart that cannot load must not take the holdings with it.
      error: () => undefined
    });
  }

  private loadPnl(accountId: number): void {
    this.pnlError.set(null);
    this.pnlRequest(accountId).subscribe((pnl) => {
      // A failed request keeps the figures already on screen next to the error message.
      if (pnl) {
        this.pnl.set(pnl);
      }
    });
  }

  private pnlRequest(accountId: number) {
    this.pnlError.set(null);
    return this.portfolioService
      .getPnl(accountId, this.from() || undefined, this.to() || undefined, this.bySymbol())
      .pipe(
        catchError((error: unknown) => {
          // A bad date range or a P&L hiccup must not blank the holdings next to it.
          this.pnlError.set(
            error instanceof HttpErrorResponse && error.status === 422
              ? 'That date range is not valid. The start date must not be after the end date.'
              : this.errorMessages.getMessage(error)
          );
          return of(null);
        })
      );
  }

  private loadUnpriced(accountId: number): void {
    forkJoin({
      balance: this.accountService.getBalance(accountId).pipe(catchError(() => of(null))),
      positions: this.accountService.getPositions(accountId).pipe(catchError(() => of([] as PositionResponse[])))
    }).subscribe(({ balance, positions }) => {
      this.cash.set(balance);
      this.unpriced.set(positions.filter((position) => position.quantity > 0));
      this.loading.set(false);
    });
  }

  private compare(key: SortKey, left: HoldingRow, right: HoldingRow): number {
    const number = (value: number | null) => value ?? Number.NEGATIVE_INFINITY;
    switch (key) {
      case 'symbol':
        return left.position.symbol.localeCompare(right.position.symbol);
      case 'quantity':
        return left.position.quantity - right.position.quantity;
      case 'invested':
        return left.invested - right.invested;
      case 'value':
        return number(left.value) - number(right.value);
      case 'gain':
        return number(left.gain) - number(right.gain);
      default:
        return number(left.gainPct) - number(right.gainPct);
    }
  }
}
