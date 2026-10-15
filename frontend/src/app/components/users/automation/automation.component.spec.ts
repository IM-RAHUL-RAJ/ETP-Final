import { Component, input, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AutomationStatus, AutomationStrategy } from '../../../core/models/advice.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { AdviceService } from '../../../core/services/advice.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';
import { EchartComponent } from '../shared/echart/echart.component';
import { AutomationComponent } from './automation.component';

/** The real chart loads a canvas library; these tests care what the page asks it to draw. */
@Component({ selector: 'app-echart', standalone: true, template: '<div data-testid="chart-stub"></div>' })
class EchartStub {
  readonly option = input<unknown>();
  readonly label = input('');
  readonly height = input(0);
}

describe('AutomationComponent', () => {
  const automationStatus = vi.fn();
  const createStrategy = vi.fn();
  const setStrategyEnabled = vi.fn();
  const removeStrategy = vi.fn();
  const simulateStrategy = vi.fn();

  const strategy = (overrides: Partial<AutomationStrategy> = {}): AutomationStrategy => ({
    id: '5', type: 'MA_CROSSOVER', accountId: 1, symbol: 'AAPL', quantity: 100, enabled: false,
    parameters: 'short 20, long 50', problems: [], collected: null, required: null, chart: null, ...overrides
  });
  const status = (running: boolean, strategies: AutomationStrategy[] = [strategy()], simulationEnabled = false): AutomationStatus => ({
    running,
    simulationEnabled,
    strategies,
    results: [
      { strategyId: '5', strategyType: 'MA_CROSSOVER', symbol: 'AAPL', limitPrice: 100.2, quantity: 100, accountId: 1, trigger: 'BUY', outcome: 'ORDER_SUBMITTED', orderId: 'o1', detail: 'status NEW', at: '2026-10-07T11:00:00Z' },
      { strategyId: '5', strategyType: 'MA_CROSSOVER', symbol: 'AAPL', limitPrice: 99.8, quantity: 100, accountId: 1, trigger: 'SELL', outcome: 'ORDER_SUBMISSION_FAILED', orderId: null, detail: 'HTTP 503', at: '2026-10-07T10:00:00Z' }
    ]
  });

  async function build(result: unknown) {
    automationStatus.mockReset().mockReturnValue(result);
    createStrategy.mockReset().mockReturnValue(of(strategy()));
    setStrategyEnabled.mockReset().mockReturnValue(of(strategy({ enabled: true })));
    removeStrategy.mockReset().mockReturnValue(of(undefined));
    simulateStrategy.mockReset().mockReturnValue(of({ ticks: 52, startPrice: 100, finalPrice: 103, trigger: 'BUY' }));
    await TestBed.configureTestingModule({
      imports: [AutomationComponent],
      providers: [
        { provide: AdviceService, useValue: { automationStatus, createStrategy, setStrategyEnabled, removeStrategy, simulateStrategy } },
        { provide: AccountService, useValue: { getAccount: () => of({ id: 1, accountId: 'ACC-0001-7421' }) } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } },
        {
          provide: HomeCurrencyService,
          useValue: { ensureLoaded: vi.fn(), instrumentList: signal([{ symbol: 'AAPL', name: 'Apple', exchange: 'NASDAQ', assetClass: 'US_EQUITY', currency: 'USD', tradable: true }]) }
        }
      ]
    });
    TestBed.overrideComponent(AutomationComponent, { remove: { imports: [EchartComponent] }, add: { imports: [EchartStub] } });
    await TestBed.compileComponents();
    const fixture = TestBed.createComponent(AutomationComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
  const el = (f: { nativeElement: HTMLElement }, id: string) => f.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;
  const all = (f: { nativeElement: HTMLElement }, id: string) => Array.from(f.nativeElement.querySelectorAll(`[data-testid="${id}"]`)) as HTMLElement[];

  it('shows each strategy as On, Off or Invalid, with what its rule does', async () => {
    const fixture = await build(of(status(true, [
      strategy({ id: '1', enabled: true }), strategy({ id: '2' }), strategy({ id: '3', problems: ['quantity must be at least 1'] })
    ])));

    expect(all(fixture, 'strategy-state').map((node) => node.textContent?.trim())).toEqual(['On', 'Off', 'Invalid']);
    expect(fixture.nativeElement.textContent).toContain('Buys 100 when the short average crosses above the long one');
    expect(fixture.nativeElement.textContent).toContain('quantity must be at least 1');
  });

  it('says how many prices a running strategy has collected, and that it cannot act yet', async () => {
    const fixture = await build(of(status(true, [strategy({ enabled: true, collected: 12, required: 51 })])));

    expect(el(fixture, 'strategy-progress')?.textContent).toContain('12 of 51');
  });

  it('says when it is watching and will trade by itself', async () => {
    const fixture = await build(of(status(true, [strategy({ enabled: true, collected: 51, required: 51 })])));

    expect(el(fixture, 'strategy-progress')?.textContent).toContain('trades by itself when the condition is met');
  });

  it('says automation is switched off on the platform when the kill switch is set', async () => {
    const fixture = await build(of(status(false)));

    expect(el(fixture, 'automation-state')?.textContent).toContain('switched off on this platform');
  });

  it('asks for a confirmation before switching on, and will not proceed until it is acknowledged', async () => {
    const fixture = await build(of(status(true)));

    el(fixture, 'strategy-toggle')!.click();
    fixture.detectChanges();

    expect(el(fixture, 'confirm-on')?.textContent).toContain('real orders');
    expect(setStrategyEnabled).not.toHaveBeenCalled();
    expect((el(fixture, 'confirm-switch-on') as HTMLButtonElement).disabled).toBe(true);

    const ack = el(fixture, 'confirm-ack') as HTMLInputElement;
    ack.checked = true;
    ack.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    el(fixture, 'confirm-switch-on')!.click();
    fixture.detectChanges();

    expect(setStrategyEnabled).toHaveBeenCalledWith('5', true, true);
    expect(el(fixture, 'confirm-on')).toBeNull();
  });

  it('names the account by its reference in the confirmation, never by its internal id', async () => {
    const fixture = await build(of(status(true, [strategy({ accountId: 21 })])));

    el(fixture, 'strategy-toggle')!.click();
    fixture.detectChanges();

    const text = el(fixture, 'confirm-on')!.textContent!;
    expect(text).toContain('ACC-0001-7421');
    expect(text).not.toContain('account 21');
  });

  it('cancelling the confirmation switches nothing on', async () => {
    const fixture = await build(of(status(true)));

    el(fixture, 'strategy-toggle')!.click();
    fixture.detectChanges();
    el(fixture, 'confirm-cancel')!.click();
    fixture.detectChanges();

    expect(setStrategyEnabled).not.toHaveBeenCalled();
    expect(el(fixture, 'confirm-on')).toBeNull();
  });

  it('switches a running strategy off at once, with no confirmation', async () => {
    const fixture = await build(of(status(true, [strategy({ enabled: true })])));

    el(fixture, 'strategy-toggle')!.click();
    fixture.detectChanges();

    expect(setStrategyEnabled).toHaveBeenCalledWith('5', false, false);
    expect(el(fixture, 'confirm-on')).toBeNull();
  });

  it('creates a strategy from the dialog, and refuses it without an instrument', async () => {
    const fixture = await build(of(status(true, [])));

    el(fixture, 'strategy-new')!.click();
    fixture.detectChanges();
    el(fixture, 'strategy-create')!.click();
    fixture.detectChanges();
    expect(el(fixture, 'strategy-form-error')?.textContent).toContain('Choose an instrument');
    expect(createStrategy).not.toHaveBeenCalled();

    el(fixture, 'strategy-symbol')!.click();
    fixture.detectChanges();
    el(fixture, 'option-AAPL')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    fixture.detectChanges();
    el(fixture, 'strategy-create')!.click();
    fixture.detectChanges();

    expect(createStrategy).toHaveBeenCalledWith({
      type: 'MA_CROSSOVER', accountId: 1, symbol: 'AAPL', quantity: 10, shortPeriod: 20, longPeriod: 50
    });
    expect(el(fixture, 'strategy-form')).toBeNull();
  });

  it('shows the server reason inside the dialog when it refuses a strategy', async () => {
    const fixture = await build(of(status(true, [])));
    createStrategy.mockReturnValue(throwError(() => ({ status: 422, error: { errorCode: 'AUT-422', message: 'An account can hold up to 10 strategies' } })));

    el(fixture, 'strategy-new')!.click();
    fixture.detectChanges();
    el(fixture, 'strategy-symbol')!.click();
    fixture.detectChanges();
    el(fixture, 'option-AAPL')!.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    fixture.detectChanges();
    el(fixture, 'strategy-create')!.click();
    fixture.detectChanges();

    expect(el(fixture, 'strategy-form-error')).not.toBeNull();
    expect(el(fixture, 'strategy-form')).not.toBeNull();
  });

  it('asks before removing a strategy', async () => {
    const fixture = await build(of(status(true)));

    el(fixture, 'strategy-remove')!.click();
    fixture.detectChanges();
    expect(removeStrategy).not.toHaveBeenCalled();
    expect(el(fixture, 'remove-confirm')?.textContent).toContain('AAPL');

    el(fixture, 'remove-confirm-button')!.click();
    fixture.detectChanges();

    expect(removeStrategy).toHaveBeenCalledWith('5');
  });

  it('keeps a trigger apart from a trade: Order sent and Failed are different outcomes', async () => {
    const fixture = await build(of(status(true)));

    expect(all(fixture, 'result-outcome').map((node) => node.textContent?.trim())).toEqual(['Order sent', 'Failed']);
  });

  it('shows the error with a retry', async () => {
    const fixture = await build(throwError(() => ({ status: 500 })));

    expect(el(fixture, 'automation-error')).not.toBeNull();
  });

  it('draws a chart of the prices and the rule lines for a running strategy, and none for one that is off', async () => {
    const running = strategy({ id: '1', enabled: true, collected: 51, required: 51, chart: { prices: [10, 11, 12], lines: { shortAverage: [null, 10.5, 11.5], longAverage: [null, null, 11] } } });
    const fixture = await build(of(status(true, [running, strategy({ id: '2' })])));

    expect(el(fixture, 'strategy-extras-1')!.querySelector('[data-testid="chart-stub"]')).not.toBeNull();
    expect(el(fixture, 'strategy-extras-2')!.querySelector('[data-testid="chart-stub"]')).toBeNull();
  });

  it('shows how far price collection has got as a bar', async () => {
    const fixture = await build(of(status(true, [strategy({ enabled: true, collected: 25, required: 50 })])));

    expect(fixture.nativeElement.querySelector('.meter__fill').style.width).toBe('50%');
  });

  it('offers the demo only when the platform allows it and the strategy is on', async () => {
    const off = await build(of(status(true, [strategy({ enabled: true })], false)));
    expect(el(off, 'strategy-demo')).toBeNull();
    off.destroy();
    TestBed.resetTestingModule();

    const on = await build(of(status(true, [strategy({ enabled: true }), strategy({ id: '2' })], true)));
    expect(all(on, 'strategy-demo').length).toBe(1);
  });

  it('asks for an acknowledgement before the demo, because the order is real, then runs it', async () => {
    const fixture = await build(of(status(true, [strategy({ enabled: true })], true)));

    el(fixture, 'demo-buy')!.click();
    fixture.detectChanges();
    expect(el(fixture, 'confirm-demo')?.textContent).toContain('real');
    expect(el(fixture, 'confirm-demo')?.textContent).toContain('ACC-0001-7421');
    expect((el(fixture, 'demo-confirm') as HTMLButtonElement).disabled).toBe(true);
    expect(simulateStrategy).not.toHaveBeenCalled();

    const ack = el(fixture, 'demo-ack') as HTMLInputElement;
    ack.checked = true;
    ack.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    el(fixture, 'demo-confirm')!.click();
    fixture.detectChanges();

    expect(simulateStrategy).toHaveBeenCalledWith('5', 'BUY');
    expect(el(fixture, 'confirm-demo')).toBeNull();
  });

  it('tells strategies apart by number and kind', async () => {
    const fixture = await build(of(status(true, [strategy({ id: '5', enabled: true }), strategy({ id: '6', type: 'BOLLINGER_BANDS', parameters: 'period 20, multiplier 2' })])));

    expect(el(fixture, 'strategy-5')?.textContent).toContain('#5');
    expect(el(fixture, 'strategy-5')!.querySelector('[data-testid="strategy-type"]')!.textContent).toContain('MA crossover');
    expect(el(fixture, 'strategy-6')!.querySelector('[data-testid="strategy-type"]')!.textContent).toContain('Bollinger');
  });

  it('shows each strategy its own automated trades under it, newest first, and none under a strategy that has not traded', async () => {
    const fixture = await build(of(status(true, [strategy({ id: '5' }), strategy({ id: '6' })])));

    const five = el(fixture, 'strategy-history-5')!;
    expect(five.querySelectorAll('[data-testid="history-row"]').length).toBe(2);
    expect(five.querySelectorAll('[data-testid="history-row"]')[0].textContent).toContain('100.20');
    expect(five.querySelectorAll('[data-testid="history-row"]')[0].textContent).toContain('BUY');
    expect(el(fixture, 'strategy-history-6')!.querySelectorAll('[data-testid="history-row"]').length).toBe(0);
    expect(el(fixture, 'strategy-history-6')!.querySelector('[data-testid="strategy-no-trades"]')).not.toBeNull();
  });

  it('keeps the history and the chart together under the strategy they belong to', async () => {
    const running = strategy({ id: '5', enabled: true, collected: 5, required: 5, chart: { prices: [10, 11, 12], lines: { shortAverage: [null, 10.5, 11.5], longAverage: [null, null, 11] } } });
    const fixture = await build(of(status(true, [running])));

    const extras = el(fixture, 'strategy-extras-5')!;
    expect(extras.querySelector('[data-testid="chart-stub"]')).not.toBeNull();
    expect(extras.querySelector('[data-testid="strategy-history-5"]')).not.toBeNull();
  });
});
