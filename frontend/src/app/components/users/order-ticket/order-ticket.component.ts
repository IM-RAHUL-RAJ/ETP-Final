import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';

import { ToastService } from '../../../core/ui/toast.service';
import { MascotComponent } from '../shared/mascot/mascot.component';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';
import { OrderService } from '../../../core/services/order.service';
import { OrderDraft, OrderDraftService } from '../../../core/services/order-draft.service';
import { CURRENCIES, convertAmount, isSupportedCurrency, rateBetween } from '../../../core/config/currency-rates';
import { AccountResponse, BalanceResponse, PositionResponse } from '../../../core/models/account.model';
import { OrderResponse, OrderSide } from '../../../core/models/order.model';
import { StatusBadgeComponent } from '../shared/status-badge/status-badge.component';
import { SelectComponent, SelectOption } from '../shared/select/select.component';

// The trade contract bounds a symbol at 1 to 20 characters. Anything richer is the API's call.
const SYMBOL_PATTERN = /^\S{1,20}$/;

function positiveIntegerValidator(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  return /^\d+$/.test(value) && Number(value) > 0 ? null : { invalidQuantity: true };
}

function priceValidator(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  if (!/^\d+(\.\d+)?$/.test(value)) {
    return { invalidPrice: true };
  }
  if ((value.split('.')[1] ?? '').length > 2) {
    return { tooManyDecimals: true };
  }
  return Number(value) > 0 ? null : { invalidPrice: true };
}

function symbolValidator(control: AbstractControl<string>): ValidationErrors | null {
  return SYMBOL_PATTERN.test(control.value.trim()) ? null : { invalidSymbol: true };
}

@Component({
  selector: 'app-order-ticket',
  standalone: true,
  imports: [MascotComponent, CommonModule, ReactiveFormsModule, RouterLink, StatusBadgeComponent, SelectComponent],
  templateUrl: './order-ticket.component.html',
  styleUrl: './order-ticket.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OrderTicketComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly accountContextService = inject(AccountContextService);
  private readonly orderService = inject(OrderService);
  private readonly apiErrorMessageService = inject(ApiErrorMessageService);
  private readonly accountService = inject(AccountService);
  private readonly draftService = inject(OrderDraftService);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  protected readonly home = inject(HomeCurrencyService);

  readonly currencies = CURRENCIES;

  // Reused only when the outcome of a post is unknown (the response never arrived), so a
  // retry cannot place a second order. Any definite answer clears it.
  private pendingIdempotencyKey: string | null = null;

  readonly isSubmitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly success = signal<OrderResponse | null>(null);
  /** The review step: the order is only sent once the customer confirms it. */
  readonly confirming = signal(false);

  readonly form = this.formBuilder.nonNullable.group({
    symbol: ['', [Validators.required, symbolValidator]],
    side: ['BUY' as OrderSide, [Validators.required]],
    quantity: ['', [Validators.required, positiveIntegerValidator]],
    price: ['', [Validators.required, priceValidator]],
    // Converter only, never sent to the API. They default to the home and instrument currencies.
    currency: ['USD'],
    convertTo: ['EUR']
  });

  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });

  /** What the customer is about to send, echoed back live. An estimate only: cash and holdings are checked by the API. */
  readonly summary = computed(() => {
    const value = this.formValue();
    const quantity = Number((value.quantity ?? '').trim());
    const price = Number((value.price ?? '').trim());
    const symbol = (value.symbol ?? '').trim().toUpperCase();
    const validNumbers =
      Number.isInteger(quantity) && quantity > 0 && Number.isFinite(price) && price > 0;

    return {
      side: value.side === 'SELL' ? 'Sell' : 'Buy',
      symbol,
      quantity: Number.isFinite(quantity) && quantity > 0 ? quantity : null,
      price: Number.isFinite(price) && price > 0 ? price : null,
      estimate: validNumbers ? quantity * price : null
    };
  });

  // Account reference: the customer sees what statements and support quote, never the
  // internal numeric id. The id is only used inside requests.
  readonly account = signal<AccountResponse | null>(null);
  readonly accountFailed = signal(false);

  readonly accountReference = computed(() => {
    const account = this.account();
    if (account) {
      return account.accountId;
    }
    return this.accountFailed() ? 'Unavailable' : 'Loading...';
  });

  /**
   * The home currency: the account's own. The limit price is entered in it (the trade API
   * converts it to the instrument's currency), and cash and the estimate are in it too.
   */
  readonly accountCurrency = computed(() => this.home.homeCurrency());

  // Balance. It is read quietly so the ticket can warn, but only shown once asked for.
  readonly balance = signal<BalanceResponse | null>(null);
  readonly balanceRevealed = signal(false);
  readonly balanceLoading = signal(false);
  readonly balanceError = signal<string | null>(null);
  private readonly positions = signal<PositionResponse[] | null>(null);

  /** The instrument the symbol field names, if the platform knows it. */
  readonly instrument = computed(() => {
    this.home.ready();
    return this.home.instrument(this.summary().symbol) ?? null;
  });

  /** What the symbol picker offers: every tradable instrument, with its name and what kind of thing it is. */
  protected readonly symbolOptions = computed<SelectOption[]>(() =>
    this.home
      .instrumentList()
      .filter((item) => item.tradable)
      .map((item) => ({ value: item.symbol, label: item.symbol, hint: item.name, tag: item.assetClass.replaceAll('_', ' ').toLowerCase() }))
  );

  readonly instrumentCurrency = computed(() => this.home.currencyOf(this.summary().symbol));

  /** Symbol typed, platform loaded, and no such instrument. A hint only: the API has the final say. */
  readonly unknownSymbol = computed(
    () => this.home.ready() && this.summary().symbol !== '' && this.instrument() === null
  );

  /** The limit price as the instrument's own currency sees it. */
  readonly priceInInstrumentCurrency = computed(() => {
    const home = this.accountCurrency();
    const target = this.instrumentCurrency();
    const price = this.summary().price;
    return home && target && price !== null ? convertAmount(home, target, price) : null;
  });

  readonly estimateInInstrumentCurrency = computed(() => {
    const home = this.accountCurrency();
    const target = this.instrumentCurrency();
    const estimate = this.summary().estimate;
    return home && target && estimate !== null ? convertAmount(home, target, estimate) : null;
  });

  /** A buy whose estimate is above the cash on hand. Both sides are in the home currency. */
  readonly affordability = computed(() => {
    const summary = this.summary();
    const balance = this.balance();
    if (summary.side !== 'Buy' || summary.estimate === null || balance === null) {
      return null;
    }
    return summary.estimate > balance.cashBalance
      ? { cash: balance.cashBalance, short: summary.estimate - balance.cashBalance, currency: balance.currency }
      : null;
  });

  /** A sell of more than is held. */
  readonly holdingsWarning = computed(() => {
    const summary = this.summary();
    const positions = this.positions();
    if (summary.side !== 'Sell' || summary.quantity === null || summary.symbol === '' || positions === null) {
      return null;
    }
    const held = positions.find((position) => position.symbol === summary.symbol)?.quantity ?? 0;
    return summary.quantity > held ? { held } : null;
  });

  // Currency converter, using the same rate table as the trade API.
  /** What the user typed into the converter. Null means: follow the order estimate. */
  private readonly fxAmountOverride = signal<string | null>(null);
  private readonly fromChosen = signal(false);
  private readonly toChosen = signal(false);

  readonly fxAmountText = computed(() => {
    const override = this.fxAmountOverride();
    if (override !== null) {
      return override;
    }
    const estimate = this.summary().estimate;
    return estimate !== null ? estimate.toFixed(2) : '';
  });

  readonly fxAmountIsEstimate = computed(() => this.fxAmountOverride() === null);
  readonly fxFrom = computed(() => this.formValue().currency ?? 'USD');
  readonly fxTo = computed(() => this.formValue().convertTo ?? 'EUR');
  readonly fxRate = computed(() => rateBetween(this.fxFrom(), this.fxTo()));

  readonly converted = computed(() => {
    const amount = Number(this.fxAmountText().trim());
    return Number.isFinite(amount) && amount > 0 ? convertAmount(this.fxFrom(), this.fxTo(), amount) : null;
  });

  constructor() {
    this.restoreDraft();
    this.applyDeepLink();

    // Remember the ticket while the user visits other tabs.
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      const { symbol, side, quantity, price } = this.form.getRawValue();
      this.draftService.save({ symbol, side, quantity, price });
    });

    this.loadAccount();
    this.home.ensureLoaded();

    // The converter starts from the home currency and the instrument's currency, until the user picks others.
    effect(() => {
      const home = this.accountCurrency();
      if (home && !this.fromChosen() && this.form.controls.currency.value !== home) {
        this.form.controls.currency.setValue(home);
      }
    });
    effect(() => {
      const target = this.instrumentCurrency();
      const home = this.accountCurrency();
      const next = target ?? (home === 'USD' ? 'EUR' : 'USD');
      if (!this.toChosen() && this.form.controls.convertTo.value !== next) {
        this.form.controls.convertTo.setValue(next);
      }
    });
  }

  private restoreDraft(): void {
    const draft = this.draftService.load();
    if (!draft) {
      return;
    }
    this.form.patchValue({
      symbol: typeof draft.symbol === 'string' ? draft.symbol.slice(0, 20) : '',
      side: draft.side === 'SELL' ? 'SELL' : 'BUY',
      quantity: typeof draft.quantity === 'string' ? draft.quantity.slice(0, 12) : '',
      price: typeof draft.price === 'string' ? draft.price.slice(0, 16) : ''
    });
  }

  /** The watchlist's Trade button opens the ticket with the symbol (and side) already filled in. */
  private applyDeepLink(): void {
    const params = this.route.snapshot.queryParamMap;
    const symbol = params.get('symbol')?.trim().toUpperCase();
    if (symbol && SYMBOL_PATTERN.test(symbol)) {
      this.form.patchValue({ symbol, side: params.get('side') === 'SELL' ? 'SELL' : 'BUY' });
    }
  }

  private loadAccount(): void {
    const accountId = this.accountContextService.getCurrentAccount().accountNumericId;

    this.accountService.getAccount(accountId).subscribe({
      next: (account) => this.account.set(account),
      error: () => this.accountFailed.set(true)
    });

    this.accountService.getBalance(accountId).subscribe({
      next: (balance) => this.balance.set(balance),
      error: () => undefined
    });

    this.accountService.getPositions(accountId).subscribe({
      next: (positions) => this.positions.set(positions),
      error: () => undefined
    });
  }

  /** Get balance: always a fresh read, and the first press reveals the figure. */
  getBalance(): void {
    if (this.balanceLoading()) {
      return;
    }
    this.balanceLoading.set(true);
    this.balanceError.set(null);

    this.accountService.getBalance(this.accountContextService.getCurrentAccount().accountNumericId).subscribe({
      next: (balance) => {
        this.balance.set(balance);
        this.balanceRevealed.set(true);
        this.balanceLoading.set(false);
      },
      error: (error: unknown) => {
        this.balanceError.set(this.apiErrorMessageService.getMessage(error));
        this.balanceLoading.set(false);
      }
    });
  }

  chooseFrom(code: string): void {
    this.fromChosen.set(true);
    this.form.controls.currency.setValue(code);
  }

  chooseTo(code: string): void {
    this.toChosen.set(true);
    this.form.controls.convertTo.setValue(code);
  }

  onFxAmountInput(value: string): void {
    this.fxAmountOverride.set(value);
  }

  useEstimate(): void {
    this.fxAmountOverride.set(null);
  }

  swapCurrencies(): void {
    const { currency, convertTo } = this.form.getRawValue();
    this.chooseFrom(convertTo);
    this.chooseTo(currency);
  }

  /** The place order button: check the form, then show the review. Nothing is sent yet. */
  onSubmit(): void {
    this.form.markAllAsTouched();

    if (this.form.invalid || this.isSubmitting()) {
      return;
    }

    this.errorMessage.set(null);
    this.success.set(null);
    this.confirming.set(true);
  }

  /** Back from the review to the form, keeping everything typed. */
  cancelReview(): void {
    this.confirming.set(false);
  }

  /** The review's confirm button: this is the only place an order is sent. */
  confirmOrder(): void {
    if (this.form.invalid || this.isSubmitting()) {
      return;
    }

    this.confirming.set(false);
    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    this.success.set(null);

    this.pendingIdempotencyKey ??= this.newIdempotencyKey();

    this.orderService
      .placeOrder({
        accountId: this.accountContextService.getCurrentAccount().accountNumericId,
        symbol: this.form.controls.symbol.value.trim().toUpperCase(),
        side: this.form.controls.side.value,
        quantity: Number(this.form.controls.quantity.value),
        price: Number(this.form.controls.price.value),
        idempotencyKey: this.pendingIdempotencyKey
      })
      .subscribe({
        next: (response) => {
          // What comes back is the state at acceptance, usually NEW. It is shown as it is:
          // execution is asynchronous and the blotter brings the row up to date.
          this.pendingIdempotencyKey = null;
          this.success.set(response);
          this.isSubmitting.set(false);
          this.draftService.clear();
          // Cash and holdings have just been committed against: read them again.
          this.refreshFigures();
        },
        error: (error: unknown) => {
          const outcomeUnknown = error instanceof HttpErrorResponse && error.status === 0;
          if (!outcomeUnknown) {
            this.pendingIdempotencyKey = null;
          }
          this.errorMessage.set(this.apiErrorMessageService.getMessage(error));
          this.isSubmitting.set(false);
        }
      });
  }

  private refreshFigures(): void {
    const accountId = this.accountContextService.getCurrentAccount().accountNumericId;
    this.accountService.getBalance(accountId).subscribe({ next: (b) => this.balance.set(b), error: () => undefined });
    this.accountService.getPositions(accountId).subscribe({ next: (p) => this.positions.set(p), error: () => undefined });
  }

  onReset(): void {
    this.form.reset({
      symbol: '',
      side: 'BUY',
      quantity: '',
      price: '',
      currency: this.accountCurrency() ?? 'USD',
      convertTo: 'USD'
    });
    this.fromChosen.set(false);
    this.toChosen.set(false);
    this.draftService.clear();
    this.fxAmountOverride.set(null);
    this.pendingIdempotencyKey = null;
    this.errorMessage.set(null);
    this.success.set(null);
    this.confirming.set(false);
  }

  selectSide(side: OrderSide): void {
    this.form.controls.side.setValue(side);
  }

  toggleSide(): void {
    this.selectSide(this.form.controls.side.value === 'BUY' ? 'SELL' : 'BUY');
  }

  getSymbolError(): string | null {
    const control = this.form.controls.symbol;
    if (!control.touched || control.valid) {
      return null;
    }
    return 'Enter a symbol of up to 20 characters, with no spaces.';
  }

  getQuantityError(): string | null {
    const control = this.form.controls.quantity;
    if (!control.touched || control.valid) {
      return null;
    }
    return 'Enter a whole number above zero.';
  }

  getPriceError(): string | null {
    const control = this.form.controls.price;
    if (!control.touched || control.valid) {
      return null;
    }
    if (control.hasError('tooManyDecimals')) {
      return 'Use at most two decimal places.';
    }
    return 'Enter a price above zero.';
  }

  private newIdempotencyKey(): string {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }

    if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
      const bytes = new Uint8Array(16);
      crypto.getRandomValues(bytes);
      bytes[6] = (bytes[6] & 0x0f) | 0x40;
      bytes[8] = (bytes[8] & 0x3f) | 0x80;
      const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
      return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
    }

    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
      const randomValue = Math.floor(Math.random() * 16);
      const value = character === 'x' ? randomValue : (randomValue & 0x3) | 0x8;
      return value.toString(16);
    });
  }
}
