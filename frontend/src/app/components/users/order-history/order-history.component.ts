import { ChangeDetectionStrategy, Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { AccountContextService } from '../../../core/services/account-context.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';
import { OrderService } from '../../../core/services/order.service';
import { OrderHistoryEntry, OrderStatus } from '../../../core/models/order.model';

export type OrderFilter = 'ALL' | OrderStatus;

interface NumberFilter {
  op: '=' | '>' | '>=' | '<' | '<=';
  value: number;
}

/** "100", "=100", ">100", ">=100", "<100", "<=100". Anything else means no filter. */
function parseNumberFilter(raw: string): NumberFilter | null {
  const match = /^\s*(>=|<=|>|<|=)?\s*(\d+(?:\.\d+)?)\s*$/.exec(raw);
  return match ? { op: (match[1] as NumberFilter['op'] | undefined) ?? '=', value: Number(match[2]) } : null;
}

function matchesNumber(actual: number | null, filter: NumberFilter | null): boolean {
  if (filter === null) {
    return true;
  }
  if (actual === null) {
    return false;
  }
  switch (filter.op) {
    case '>':
      return actual > filter.value;
    case '>=':
      return actual >= filter.value;
    case '<':
      return actual < filter.value;
    case '<=':
      return actual <= filter.value;
    default:
      return actual === filter.value;
  }
}

/** yyyy-mm-dd in the browser's own day, to compare with a date input. */
function localDay(iso: string): string {
  const date = new Date(iso);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function localDayFromDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
import { StatusBadgeComponent } from '../shared/status-badge/status-badge.component';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { IconComponent } from '../shared/icon/icon.component';
import { DownloadMenuComponent, DownloadRequest } from '../shared/download-menu/download-menu.component';
import { ExportDocument, ExportService } from '../../../core/export/export.service';

// An order at NEW is the working state: the executor has not resolved it yet. The blotter
// re-reads order history while anything is working, and stops. Ten reads three seconds apart
// is a thirty second window (the executor normally resolves within seconds) and at most
// ten extra requests per placed order, not a load test that runs for as long as the tab is
// open. The user can press Refresh to open another window. Nothing here ever re-posts an order.
export const POLL_INTERVAL_MS = 3000;
export const MAX_POLL_ATTEMPTS = 10;
type DatePresetId = 'today' | 'last7' | 'last30' | 'thisMonth';

@Component({
  selector: 'app-order-history',
  standalone: true,
  imports: [CommonModule, RouterLink, StatusBadgeComponent, DownloadMenuComponent, EmptyStateComponent, IconComponent],
  templateUrl: './order-history.component.html',
  styleUrl: './order-history.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OrderHistoryComponent implements OnDestroy {
  private readonly orderService = inject(OrderService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessageService = inject(ApiErrorMessageService);
  private readonly exporter = inject(ExportService);
  protected readonly home = inject(HomeCurrencyService);

  private pollTimer: ReturnType<typeof setTimeout> | null = null;

  readonly loading = signal(true);
  readonly refreshing = signal(false);
  readonly error = signal<string | null>(null);
  readonly orders = signal<OrderHistoryEntry[]>([]);
  readonly pollAttempt = signal(0);
  readonly pollingStopped = signal(false);

  /** The order the user is being asked to confirm cancelling, and the one whose cancel is in flight. */
  readonly confirmingId = signal<string | null>(null);
  readonly cancellingId = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  private noticeTimer: ReturnType<typeof setTimeout> | null = null;

  readonly filter = signal<OrderFilter>('ALL');
  readonly exporting = signal(false);
  /** The filters stay out of the way until the customer asks for them with the filter icon. */
  readonly filtersOpen = signal(false);

  readonly filters = computed(() => {
    const orders = this.orders();
    const count = (status: OrderStatus) => orders.filter((order) => order.status === status).length;
    return [
      { value: 'ALL' as OrderFilter, label: 'All', count: orders.length },
      { value: 'NEW' as OrderFilter, label: 'Working', count: count('NEW') },
      { value: 'FILLED' as OrderFilter, label: 'Filled', count: count('FILLED') },
      { value: 'REJECTED' as OrderFilter, label: 'Rejected', count: count('REJECTED') },
      { value: 'CANCELLED' as OrderFilter, label: 'Cancelled', count: count('CANCELLED') }
    ];
  });

  readonly datePresets: ReadonlyArray<{ id: DatePresetId; label: string }> = [
    { id: 'today', label: 'Today' },
    { id: 'last7', label: 'Last 7d' },
    { id: 'last30', label: 'Last 30d' },
    { id: 'thisMonth', label: 'This month' }
  ];

  // Column filters. Text filters match anywhere in the value; number filters take a plain
  // number or an operator, such as 100, >100, <=50 or =10.
  readonly fromDateFilter = signal('');
  readonly toDateFilter = signal('');
  readonly symbolFilter = signal('');
  readonly sideFilter = signal<'' | 'BUY' | 'SELL'>('');
  readonly quantityFilter = signal('');
  readonly priceFilter = signal('');
  readonly executedFilter = signal('');

  readonly activeColumnFilters = computed(
    () =>
      [
        this.fromDateFilter(),
        this.toDateFilter(),
        this.symbolFilter(),
        this.sideFilter(),
        this.quantityFilter(),
        this.priceFilter(),
        this.executedFilter()
      ].filter((value) => value.trim() !== '').length + (this.filter() === 'ALL' ? 0 : 1)
  );

  readonly hasActiveFilters = computed(() => this.activeColumnFilters() > 0);

  readonly dateRangeLabel = computed(() => {
    const fromDate = this.fromDateFilter().trim();
    const toDate = this.toDateFilter().trim();
    if (fromDate === '' && toDate === '') {
      return 'All dates';
    }
    if (fromDate !== '' && toDate !== '') {
      const start = fromDate > toDate ? toDate : fromDate;
      const end = fromDate > toDate ? fromDate : toDate;
      return `${start} to ${end}`;
    }
    return fromDate !== '' ? `From ${fromDate}` : `Up to ${toDate}`;
  });

  readonly clearFiltersLabel = computed(() => {
    const count = this.activeColumnFilters();
    return count > 0 ? `Reset all filters (${count})` : 'Reset all filters';
  });

  readonly activeDatePreset = computed<DatePresetId | null>(() => {
    const fromDate = this.fromDateFilter().trim();
    const toDate = this.toDateFilter().trim();
    const today = localDayFromDate(new Date());
    const last7Start = localDayFromDate(new Date(new Date().getFullYear(), new Date().getMonth(), new Date().getDate() - 6));
    const last30Start = localDayFromDate(new Date(new Date().getFullYear(), new Date().getMonth(), new Date().getDate() - 29));
    const monthStart = localDayFromDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1));

    if (fromDate === today && toDate === today) {
      return 'today';
    }
    if (fromDate === last7Start && toDate === today) {
      return 'last7';
    }
    if (fromDate === last30Start && toDate === today) {
      return 'last30';
    }
    if (fromDate === monthStart && toDate === today) {
      return 'thisMonth';
    }
    return null;
  });

  /** The table shows what every filter allows; polling always looks at every order. */
  readonly visibleOrders = computed(() => {
    const status = this.filter();
    const fromDate = this.fromDateFilter().trim();
    const toDate = this.toDateFilter().trim();
    const rangeStart = fromDate !== '' && toDate !== '' && fromDate > toDate ? toDate : fromDate;
    const rangeEnd = fromDate !== '' && toDate !== '' && fromDate > toDate ? fromDate : toDate;
    const symbol = this.symbolFilter().trim().toLowerCase();
    const side = this.sideFilter();
    const quantity = parseNumberFilter(this.quantityFilter());
    const price = parseNumberFilter(this.priceFilter());
    const executed = parseNumberFilter(this.executedFilter());

    return this.orders().filter(
      (order) => {
        const placedDay = localDay(order.createdOn);
        return (
          (status === 'ALL' || order.status === status) &&
          (rangeStart === '' || placedDay >= rangeStart) &&
          (rangeEnd === '' || placedDay <= rangeEnd) &&
          (symbol === '' || order.symbol.toLowerCase().includes(symbol)) &&
          (side === '' || order.side === side) &&
          matchesNumber(order.quantity, quantity) &&
          matchesNumber(order.price, price) &&
          matchesNumber(order.executedPrice === null ? null : this.home.toHome(order.executedPrice, order.symbol), executed)
        );
      }
    );
  });

  readonly workingCount = computed(
    () => this.orders().filter((order) => order.status === 'NEW').length
  );

  constructor() {
    this.home.ensureLoaded();
    this.load();
  }

  /** The button the user can press. It opens a fresh polling window if anything is still working. */
  refreshOrders(): void {
    this.stopPolling();
    this.pollAttempt.set(0);
    this.pollingStopped.set(false);
    this.load();
  }

  toggleFilters(): void {
    this.filtersOpen.update((open) => !open);
  }

  setFilter(filter: OrderFilter): void {
    this.filter.set(filter);
  }

  clearFilters(): void {
    this.filter.set('ALL');
    this.fromDateFilter.set('');
    this.toDateFilter.set('');
    this.symbolFilter.set('');
    this.sideFilter.set('');
    this.quantityFilter.set('');
    this.priceFilter.set('');
    this.executedFilter.set('');
  }

  applyDatePreset(preset: DatePresetId): void {
    const now = new Date();
    const today = localDayFromDate(now);
    switch (preset) {
      case 'today':
        this.fromDateFilter.set(today);
        this.toDateFilter.set(today);
        return;
      case 'last7':
        this.fromDateFilter.set(localDayFromDate(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6)));
        this.toDateFilter.set(today);
        return;
      case 'last30':
        this.fromDateFilter.set(localDayFromDate(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 29)));
        this.toDateFilter.set(today);
        return;
      case 'thisMonth':
        this.fromDateFilter.set(localDayFromDate(new Date(now.getFullYear(), now.getMonth(), 1)));
        this.toDateFilter.set(today);
        return;
    }
  }

  /** Downloads the blotter. "filtered" is exactly the rows on screen; "all" ignores every filter. */
  async download(request: DownloadRequest): Promise<void> {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    try {
      await this.exporter.export(request.format, this.buildExport(request), 'order-blotter');
    } catch (error) {
      console.error('[Blotter] export failed', error);
      this.actionError.set('The file could not be created. Please try again.');
    } finally {
      this.exporting.set(false);
    }
  }

  private buildExport(request: DownloadRequest): ExportDocument {
    const rows = request.scope === 'filtered' ? this.visibleOrders() : this.orders();
    const currency = this.home.homeCurrency();
    const filterLines =
      request.scope === 'filtered' && this.hasActiveFilters()
        ? [`Filters: status ${this.filter()}, dates ${this.dateRangeLabel()}${this.symbolFilter().trim() ? `, symbol ${this.symbolFilter().trim()}` : ''}${this.sideFilter() ? `, side ${this.sideFilter()}` : ''}`]
        : ['Filters: none (all orders)'];
    const counts = new Map<string, number>();
    for (const order of rows) {
      counts.set(order.status, (counts.get(order.status) ?? 0) + 1);
    }
    return {
      title: 'Order blotter',
      subtitle: [`${rows.length} order(s). Generated ${new Date().toLocaleString()}`, ...filterLines],
      tables: [
        {
          title: 'Orders',
          columns: [
            { header: 'Placed' },
            { header: 'Order ID' },
            { header: 'Symbol' },
            { header: 'Side' },
            { header: 'Qty', numeric: true },
            { header: currency ? `Limit price (${currency})` : 'Limit price', numeric: true },
            { header: currency ? `Executed at (${currency})` : 'Executed at', numeric: true },
            { header: 'Status' }
          ],
          rows: rows.map((order) => [
            new Date(order.createdOn).toLocaleString(),
            order.orderId,
            order.symbol,
            order.side,
            order.quantity,
            order.price ?? 'Market',
            order.executedPrice,
            order.status
          ])
        }
      ],
      charts: request.includeCharts
        ? [{ title: 'Orders by status', bars: [...counts].map(([label, value]) => ({ label, value })) }]
        : []
    };
  }

  askCancel(orderId: string): void {
    this.actionError.set(null);
    this.confirmingId.set(orderId);
  }

  keepOrder(): void {
    this.confirmingId.set(null);
  }

  /** DELETE /api/v1/orders/{id}. Only an order still at NEW can be cancelled; the API decides. */
  confirmCancel(order: OrderHistoryEntry): void {
    if (this.cancellingId() !== null) {
      return;
    }

    this.cancellingId.set(order.orderId);
    this.actionError.set(null);

    this.orderService.cancelOrder(order.orderId).subscribe({
      next: () => {
        this.orders.update((orders) =>
          orders.map((entry) =>
            entry.orderId === order.orderId ? { ...entry, status: 'CANCELLED' as const } : entry
          )
        );
        this.cancellingId.set(null);
        this.confirmingId.set(null);
        this.showNotice(`Cancelled your ${order.side === 'BUY' ? 'buy' : 'sell'} order for ${order.symbol}.`);
        // Nothing may be working any more; if so the re-read stops, otherwise it carries on.
        this.schedulePollIfWorking();
      },
      error: (error: unknown) => {
        this.actionError.set(this.errorMessageService.getMessage(error, 'cancel'));
        this.cancellingId.set(null);
        this.confirmingId.set(null);
        // The order changed under us (filled, say). Re-read so the row tells the truth.
        this.load();
      }
    });
  }

  dismissNotice(): void {
    this.notice.set(null);
    this.clearNoticeTimer();
  }

  ngOnDestroy(): void {
    this.stopPolling();
    this.clearNoticeTimer();
  }

  private showNotice(message: string): void {
    this.clearNoticeTimer();
    this.notice.set(message);
    this.noticeTimer = setTimeout(() => this.notice.set(null), 6000);
  }

  private clearNoticeTimer(): void {
    if (this.noticeTimer !== null) {
      clearTimeout(this.noticeTimer);
      this.noticeTimer = null;
    }
  }

  private load(): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    this.refreshing.set(true);

    this.orderService.getOrderHistory(accountId).subscribe({
      next: (orders) => {
        // Newest first, rejections included: the rejection is the record that the desk tried.
        this.orders.set(
          [...orders].sort((left, right) => Date.parse(right.createdOn) - Date.parse(left.createdOn))
        );
        this.error.set(null);
        this.loading.set(false);
        this.refreshing.set(false);
        this.schedulePollIfWorking();
      },
      error: (error: unknown) => {
        this.error.set(this.errorMessageService.getMessage(error));
        this.loading.set(false);
        this.refreshing.set(false);
        this.stopPolling();
      }
    });
  }

  private schedulePollIfWorking(): void {
    this.stopPolling();

    if (this.workingCount() === 0) {
      this.pollAttempt.set(0);
      this.pollingStopped.set(false);
      return;
    }

    if (this.pollAttempt() >= MAX_POLL_ATTEMPTS) {
      this.pollingStopped.set(true);
      // Progress belongs in the console, not on the blotter.
      console.info(
        `[Blotter] ${this.workingCount()} order(s) still working. Automatic checking stopped after ${MAX_POLL_ATTEMPTS} attempts. Press Refresh to check again.`
      );
      return;
    }

    console.info(
      `[Blotter] ${this.workingCount()} order(s) still working. Checking again in ${POLL_INTERVAL_MS / 1000}s, attempt ${this.pollAttempt() + 1} of ${MAX_POLL_ATTEMPTS}.`
    );

    this.pollTimer = setTimeout(() => {
      this.pollTimer = null;
      this.pollAttempt.update((attempt) => attempt + 1);
      this.load();
    }, POLL_INTERVAL_MS);
  }

  private stopPolling(): void {
    if (this.pollTimer !== null) {
      clearTimeout(this.pollTimer);
      this.pollTimer = null;
    }
  }
}
