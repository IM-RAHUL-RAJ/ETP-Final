import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { OrderTicketComponent } from './order-ticket.component';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { InstrumentService } from '../../../core/services/instrument.service';
import { OrderService } from '../../../core/services/order.service';

describe('OrderTicketComponent', () => {
  const placeOrder = vi.fn();
  const getAccount = vi.fn();
  const getBalance = vi.fn();
  const getPositions = vi.fn();
  const getInstruments = vi.fn();

  const placed = {
    orderId: 'id-1',
    status: 'NEW',
    message: 'ok',
    symbol: 'AAPL',
    side: 'BUY',
    quantity: 10,
    price: 150.5
  };

  beforeEach(async () => {
    placeOrder.mockReset();
    sessionStorage.clear();
    getAccount.mockReset().mockReturnValue(of({ id: 1042, accountId: 'ACC-7731', holderName: 'T', cashBalance: 0, status: 'ACTIVE', version: 1, lastUpdated: '' }));
    getPositions.mockReset().mockReturnValue(of([{ accountId: 1042, symbol: 'AAPL', quantity: 5, averageCost: 100 }]));
    getInstruments.mockReset().mockReturnValue(
      of([{ symbol: 'AAPL', name: 'Apple Inc.', exchange: 'NASDAQ', assetClass: 'US_EQUITY', currency: 'USD', tradable: true }])
    );
    getBalance.mockReset().mockReturnValue(of({ accountId: 1042, cashBalance: 2500.5, currency: 'INR', asOf: '2026-01-01T10:00:00Z' }));

    await TestBed.configureTestingModule({
      imports: [OrderTicketComponent],
      providers: [
        provideRouter([]),
        {
          provide: AccountContextService,
          useValue: {
            getCurrentAccount: () => ({
              accountNumericId: 1042,
              accountReference: '1042',
              displayName: 'trader01'
            })
          }
        },
        { provide: OrderService, useValue: { placeOrder } },
        { provide: AccountService, useValue: { getAccount, getBalance, getPositions } },
        { provide: InstrumentService, useValue: { getInstruments } }
      ]
    }).compileComponents();
  });

  function create() {
    const fixture = TestBed.createComponent(OrderTicketComponent);
    fixture.detectChanges();
    return fixture;
  }

  /** The review step then the confirm: what the customer does to place an order. */
  function submit(fixture: ReturnType<typeof create>) {
    fixture.componentInstance.onSubmit();
    fixture.componentInstance.confirmOrder();
  }

  function fillValidForm(fixture: ReturnType<typeof create>) {
    fixture.componentInstance.form.patchValue({
      symbol: 'aapl',
      side: 'BUY',
      quantity: '10',
      price: '150.50'
    });
  }

  it('a valid order submits and shows the returned status', () => {
    placeOrder.mockReturnValue(of(placed));
    const fixture = create();

    fillValidForm(fixture);
    submit(fixture);
    fixture.detectChanges();

    expect(placeOrder).toHaveBeenCalledWith(
      expect.objectContaining({ accountId: 1042, symbol: 'AAPL', side: 'BUY', quantity: 10, price: 150.5 })
    );
    expect(placeOrder.mock.calls[0][0].idempotencyKey).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
    );
    const status = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="order-status"]');
    expect(status?.textContent).toContain('NEW');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('This order is being processed');
  });

  it('an invalid quantity is blocked before submission', () => {
    const fixture = create();

    for (const quantity of ['0', '-1', '10.5', 'abc', '']) {
      fixture.componentInstance.form.patchValue({ symbol: 'AAPL', side: 'BUY', quantity, price: '150.50' });
      fixture.componentInstance.onSubmit();
    }

    expect(placeOrder).not.toHaveBeenCalled();
  });

  it('an invalid price is blocked before submission', () => {
    const fixture = create();

    for (const price of ['0', '-5', '10.123', 'abc', '']) {
      fixture.componentInstance.form.patchValue({ symbol: 'AAPL', side: 'BUY', quantity: '10', price });
      fixture.componentInstance.onSubmit();
    }

    expect(placeOrder).not.toHaveBeenCalled();
  });

  it('an empty or over-long symbol is blocked before submission', () => {
    const fixture = create();

    for (const symbol of [' ', 'A'.repeat(21), 'AB CD']) {
      fixture.componentInstance.form.patchValue({ symbol, side: 'BUY', quantity: '10', price: '150.50' });
      fixture.componentInstance.onSubmit();
    }

    expect(placeOrder).not.toHaveBeenCalled();
  });

  it('the account is rendered read-only', () => {
    const fixture = create();
    const account = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
      '[data-testid="order-account"]'
    );

    expect(account?.readOnly).toBe(true);
    expect(account?.value).toBe('ACC-7731');
    expect(account?.value).not.toBe('1042');
  });

  it('a business-rule rejection shows the mapped message, not the developer message', () => {
    placeOrder.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { errorCode: 'ORD-400', message: 'InsufficientFundsException required=99999' }
          })
      )
    );
    const fixture = create();

    fillValidForm(fixture);
    submit(fixture);
    fixture.detectChanges();

    const banner = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="order-error"]');
    expect(banner?.textContent).toContain('not enough cash');
    expect(banner?.textContent).not.toContain('InsufficientFundsException');
  });

  it('disables submit while submitting', () => {
    const pending = new Subject<unknown>();
    placeOrder.mockReturnValue(pending.asObservable());
    const fixture = create();

    fillValidForm(fixture);
    submit(fixture);
    submit(fixture);

    expect(fixture.componentInstance.isSubmitting()).toBe(true);
    expect(placeOrder).toHaveBeenCalledTimes(1);
  });

  it('retrying after a response that never arrived reuses the idempotency key', () => {
    placeOrder
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })))
      .mockReturnValueOnce(of(placed));
    const fixture = create();

    fillValidForm(fixture);
    submit(fixture);
    submit(fixture);

    const [first, second] = placeOrder.mock.calls.map(([request]) => request.idempotencyKey);
    expect(first).toBe(second);
  });

  it('a definite answer means the next order gets a fresh idempotency key', () => {
    placeOrder.mockReturnValue(of(placed));
    const fixture = create();

    fillValidForm(fixture);
    submit(fixture);
    submit(fixture);

    const [first, second] = placeOrder.mock.calls.map(([request]) => request.idempotencyKey);
    expect(first).not.toBe(second);
  });

  it('the summary echoes the typed order and estimates its value', () => {
    const fixture = create();

    fixture.componentInstance.form.patchValue({ symbol: 'abc', side: 'SELL', quantity: '4', price: '2.50' });
    fixture.detectChanges();

    expect(fixture.componentInstance.summary()).toEqual({
      side: 'Sell',
      symbol: 'ABC',
      quantity: 4,
      price: 2.5,
      estimate: 10
    });
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[data-testid="order-estimate"]')?.textContent
    ).toContain('10.00');
  });

  it('the summary shows no estimate until quantity and price are valid', () => {
    const fixture = create();

    fixture.componentInstance.form.patchValue({ symbol: 'ABC', side: 'BUY', quantity: '4.5', price: '2.50' });

    expect(fixture.componentInstance.summary().estimate).toBeNull();
  });

  it('resets order fields', () => {
    const fixture = create();
    fillValidForm(fixture);

    fixture.componentInstance.onReset();

    expect(fixture.componentInstance.form.controls.symbol.value).toBe('');
    expect(fixture.componentInstance.form.controls.quantity.value).toBe('');
    expect(fixture.componentInstance.form.controls.price.value).toBe('');
  });
  it('Get balance reads the balance on click and shows its currency', () => {
    const fixture = create();
    const root = fixture.nativeElement as HTMLElement;
    getBalance.mockClear();

    expect(root.querySelector('[data-testid="balance-amount"]')).toBeNull();
    root.querySelector<HTMLButtonElement>('[data-testid="get-balance"]')?.click();
    fixture.detectChanges();

    expect(getBalance).toHaveBeenCalledWith(1042);
    const amount = root.querySelector('[data-testid="balance-amount"]');
    expect(amount?.textContent).toContain('INR');
    expect(amount?.textContent).toContain('2,500.50');
  });

  it('the converter uses the backend rate table (USD to INR at 95)', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    component.chooseFrom('USD');
    component.chooseTo('INR');
    component.onFxAmountInput('10');

    expect(component.fxRate()).toBe(95);
    expect(component.converted()).toBe(950);

    component.chooseFrom('EUR');
    component.chooseTo('GBP');
    component.onFxAmountInput('85');
    // 85 EUR = 100 USD = 75 GBP
    expect(component.converted()).toBe(75);
  });

  it('a half-filled ticket survives leaving the page and coming back', () => {
    const first = create();
    first.componentInstance.form.patchValue({ symbol: 'TSLA', quantity: '7', price: '210.25', side: 'SELL' });
    first.destroy();

    const second = create();
    const value = second.componentInstance.form.getRawValue();
    expect(value).toEqual(expect.objectContaining({ symbol: 'TSLA', quantity: '7', price: '210.25', side: 'SELL' }));
  });

  it('reset clears the remembered draft', () => {
    const first = create();
    first.componentInstance.form.patchValue({ symbol: 'TSLA' });
    first.componentInstance.onReset();
    first.destroy();

    expect(create().componentInstance.form.controls.symbol.value).toBe('');
  });
  it('placing an order needs a confirmation: the review step sends nothing', () => {
    placeOrder.mockReturnValue(of(placed));
    const fixture = create();
    fillValidForm(fixture);

    fixture.componentInstance.onSubmit();
    fixture.detectChanges();

    expect(placeOrder).not.toHaveBeenCalled();
    expect(fixture.componentInstance.confirming()).toBe(true);
    const dialog = (fixture.nativeElement as HTMLElement).querySelector('[role="dialog"]');
    expect(dialog?.textContent).toContain('Confirm your order');
    expect(dialog?.textContent).toContain('AAPL');

    fixture.componentInstance.confirmOrder();
    expect(placeOrder).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.confirming()).toBe(false);
  });

  it('editing from the review keeps what was typed and sends nothing', () => {
    const fixture = create();
    fillValidForm(fixture);
    fixture.componentInstance.onSubmit();

    fixture.componentInstance.cancelReview();

    expect(fixture.componentInstance.confirming()).toBe(false);
    expect(fixture.componentInstance.form.controls.symbol.value).toBe('aapl');
    expect(placeOrder).not.toHaveBeenCalled();
  });

  it('warns when a buy costs more than the cash on hand', () => {
    const fixture = create();
    // Cash is 2,500.50; this buy is 10 x 300.00 = 3,000.00.
    fixture.componentInstance.form.patchValue({ symbol: 'AAPL', side: 'BUY', quantity: '10', price: '300' });
    fixture.detectChanges();

    const warning = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="affordability-warning"]');
    expect(warning?.textContent).toContain('short by 499.50');

    fixture.componentInstance.form.patchValue({ quantity: '5' });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="affordability-warning"]')).toBeNull();
  });

  it('warns when a sell is larger than the holding', () => {
    const fixture = create();
    fixture.componentInstance.form.patchValue({ symbol: 'AAPL', side: 'SELL', quantity: '9', price: '100' });
    fixture.detectChanges();

    const warning = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="holdings-warning"]');
    expect(warning?.textContent).toContain('You hold 5');
  });

  it('prices in the home currency and shows the instrument currency alongside', () => {
    const fixture = create();
    fixture.componentInstance.form.patchValue({ symbol: 'aapl', quantity: '10', price: '9500' });
    fixture.detectChanges();

    // Home is INR (95 per USD) and AAPL trades in USD.
    expect(fixture.componentInstance.accountCurrency()).toBe('INR');
    expect(fixture.componentInstance.instrumentCurrency()).toBe('USD');
    expect(fixture.componentInstance.priceInInstrumentCurrency()).toBe(100);
    expect(fixture.componentInstance.estimateInInstrumentCurrency()).toBe(1000);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-testid="instrument-info"]')?.textContent)
      .toContain('Apple Inc.');
    // The converter followed along: home currency to the instrument currency.
    expect(fixture.componentInstance.fxFrom()).toBe('INR');
    expect(fixture.componentInstance.fxTo()).toBe('USD');
  });
});
