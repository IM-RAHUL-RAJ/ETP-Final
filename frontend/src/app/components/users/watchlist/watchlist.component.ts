import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, of, timer } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { convertAmount, isSupportedCurrency } from '../../../core/config/currency-rates';
import { PositionResponse } from '../../../core/models/account.model';
import { MarketQuote, WatchlistEntry } from '../../../core/models/instrument.model';
import { OrderHistoryEntry } from '../../../core/models/order.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';
import { InstrumentService } from '../../../core/services/instrument.service';
import { OrderService } from '../../../core/services/order.service';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { IconComponent } from '../shared/icon/icon.component';
import { MascotComponent } from '../shared/mascot/mascot.component';
import { SelectComponent, SelectOption } from '../shared/select/select.component';
import { CandleChartComponent } from './candle-chart.component';

/** How often the quotes are re-read. The executor publishes about once a minute, so this only shortens the wait. */
export const QUOTE_REFRESH_MS = 10_000;

/** The instruments the account follows, from the platform's watchlist table. */
@Component({
  selector: 'app-watchlist',
  standalone: true,
  imports: [CommonModule, RouterLink, CandleChartComponent, EmptyStateComponent, IconComponent, MascotComponent, SelectComponent],
  templateUrl: './watchlist.component.html',
  styleUrl: './watchlist.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WatchlistComponent {
  private readonly instrumentService = inject(InstrumentService);
  private readonly accountService = inject(AccountService);
  private readonly orderService = inject(OrderService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessages = inject(ApiErrorMessageService);
  protected readonly home = inject(HomeCurrencyService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly entries = signal<WatchlistEntry[]>([]);
  protected readonly positions = signal<PositionResponse[]>([]);
  protected readonly orders = signal<OrderHistoryEntry[]>([]);
  protected readonly quotes = signal<ReadonlyMap<string, MarketQuote>>(new Map());
  protected readonly busySymbol = signal<string | null>(null);
  protected readonly pick = signal('');
  protected readonly adding = signal(false);

  /** Symbol of the instrument whose details card is open, or null when closed. */
  protected readonly selected = signal<string | null>(null);

  /** Tradable instruments that are not already watched, for the add control. */
  protected readonly addable = computed(() => {
    const watched = new Set(this.entries().map((entry) => entry.symbol));
    return this.home.instrumentList().filter((item) => item.tradable && !watched.has(item.symbol));
  });

  /** What the add control offers: symbol, name, and what kind of instrument it is. */
  protected readonly addOptions = computed<SelectOption[]>(() =>
    this.addable().map((item) => ({ value: item.symbol, label: item.symbol, hint: item.name, tag: this.label(item.assetClass) }))
  );

  /** One card per watched instrument, with what the customer already has with it. */
  protected readonly cards = computed(() =>
    this.entries().map((entry) => {
      const held = this.positions().find((position) => position.symbol === entry.symbol)?.quantity ?? 0;
      const lastFill = this.orders()
        .filter((order) => order.symbol === entry.symbol && order.status === 'FILLED' && order.executedPrice !== null)
        .sort((left, right) => Date.parse(right.createdOn) - Date.parse(left.createdOn))[0];
      const quote = this.quotes().get(entry.symbol) ?? null;
      return {
        ...entry,
        quote: quote ? this.describe(quote) : null,
        held,
        lastFillHome: lastFill ? this.home.toHome(lastFill.executedPrice as number, entry.symbol) : null,
        lastFillOn: lastFill?.createdOn ?? null,
        accent: this.accentFor(entry.assetClass)
      };
    })
  );

  /**
   * The card shown in the overlay. Derived from cards() so it keeps updating with live quotes,
   * and becomes null on its own if the instrument disappears from the watchlist.
   */
  protected readonly selectedCard = computed(() => {
    const symbol = this.selected();
    return symbol ? (this.cards().find((card) => card.symbol === symbol) ?? null) : null;
  });

  constructor() {
    this.home.ensureLoaded();
    this.load();

    // Quotes arrive on their own schedule, so they are read separately and repeatedly.
    timer(0, QUOTE_REFRESH_MS)
      .pipe(
        switchMap(() =>
          this.instrumentService
            .getWatchlistQuotes(this.accountContext.getCurrentAccount().accountNumericId)
            .pipe(catchError(() => of(null)))
        ),
        takeUntilDestroyed()
      )
      .subscribe((quotes) => {
        if (quotes) {
          this.quotes.set(new Map(quotes.map((quote) => [quote.symbol, quote])));
        }
      });
  }

  /** What a card shows for a quote: the figures, the spread, direction, and the last price in home currency. */
  private describe(quote: MarketQuote) {
    const home = this.home.homeCurrency();
    const currency = quote.currency?.trim().toUpperCase() ?? null;
    const spread = quote.bid !== null && quote.ask !== null ? quote.ask - quote.bid : null;
    return {
      ...quote,
      currency,
      spread,
      up: (quote.change ?? 0) >= 0,
      priceHome:
        quote.price !== null && home && isSupportedCurrency(currency) && currency !== home
          ? convertAmount(currency, home, quote.price)
          : null,
      open: quote.marketState?.toUpperCase() === 'REGULAR'
    };
  }

  protected load(): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    this.loading.set(true);
    this.error.set(null);

    forkJoin({
      entries: this.instrumentService.getWatchlist(accountId),
      positions: this.accountService.getPositions(accountId).pipe(catchError(() => of([] as PositionResponse[]))),
      orders: this.orderService.getOrderHistory(accountId).pipe(catchError(() => of([] as OrderHistoryEntry[])))
    }).subscribe({
      next: ({ entries, positions, orders }) => {
        this.entries.set(entries);
        this.positions.set(positions);
        this.orders.set(orders);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(this.errorMessages.getMessage(error));
        this.loading.set(false);
      }
    });
  }

  protected add(): void {
    const symbol = this.pick();
    if (!symbol || this.adding()) {
      return;
    }
    this.adding.set(true);
    this.actionError.set(null);

    this.instrumentService.addToWatchlist(this.accountContext.getCurrentAccount().accountNumericId, symbol).subscribe({
      next: (entries) => {
        this.entries.set(entries);
        this.pick.set('');
        this.adding.set(false);
      },
      error: (error: unknown) => {
        this.actionError.set(this.errorMessages.getMessage(error));
        this.adding.set(false);
      }
    });
  }

  protected remove(symbol: string): void {
    if (this.busySymbol() !== null) {
      return;
    }
    this.busySymbol.set(symbol);
    this.actionError.set(null);

    this.instrumentService
      .removeFromWatchlist(this.accountContext.getCurrentAccount().accountNumericId, symbol)
      .subscribe({
        next: (entries) => {
          this.entries.set(entries);
          this.busySymbol.set(null);
          if (this.selected() === symbol) {
            this.close();
          }
        },
        error: (error: unknown) => {
          this.actionError.set(this.errorMessages.getMessage(error));
          this.busySymbol.set(null);
        }
      });
  }

  /** Opens the details card for a symbol. */
  protected open(symbol: string): void {
    this.selected.set(symbol);
  }

  /** Closes the details card. */
  protected close(): void {
    this.selected.set(null);
  }

  /** Price direction for colour coding: a missing quote or a change of exactly 0 counts as flat. */
  protected dir(card: { quote?: { change: number | null } | null }): 'up' | 'down' | 'flat' {
    const change = card.quote?.change;
    if (change === null || change === undefined || change === 0) {
      return 'flat';
    }
    return change > 0 ? 'up' : 'down';
  }

  protected label(assetClass: string): string {
    const labels: Record<string, string> = {
      US_EQUITY: 'US equity',
      INDIAN_EQUITY: 'Indian equity',
      FX: 'FX',
      CRYPTO: 'Crypto'
    };
    return labels[assetClass] ?? assetClass.replace('_', ' ').toLowerCase();
  }

  /** A colour per asset class, so a glance tells equities from FX from crypto. */
  private accentFor(assetClass: string): string {
    switch (assetClass) {
      case 'US_EQUITY':
        return 'blue';
      case 'INDIAN_EQUITY':
        return 'amber';
      case 'FX':
        return 'teal';
      case 'CRYPTO':
        return 'violet';
      default:
        return 'green';
    }
  }
}