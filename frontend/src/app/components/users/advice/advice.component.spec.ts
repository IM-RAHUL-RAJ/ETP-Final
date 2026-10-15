import { Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AdviceIndicators, TradeAdvice } from '../../../core/models/advice.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AdviceService } from '../../../core/services/advice.service';
import { EchartComponent } from '../shared/echart/echart.component';
import { AdviceComponent } from './advice.component';

/** The real chart loads a canvas library; these tests care what the page asks it to draw. */
@Component({ selector: 'app-echart', standalone: true, template: '<div data-testid="chart-stub"></div>' })
class EchartStub {
  readonly option = input<unknown>();
  readonly label = input('');
  readonly height = input(0);
}

describe('AdviceComponent', () => {
  const advice = vi.fn();

  const indicators = (overrides: Partial<AdviceIndicators> = {}): AdviceIndicators => ({
    lastPrice: 232.1, shortAverage: 229.4, longAverage: 221.1, shortPeriod: 10, longPeriod: 50, rsi: 58, rsiState: 'NORMAL',
    momentumPercent: 2.3, volatilityPercent: 1.4, trend: 'BULLISH', ...overrides
  });
  const chart = [
    { at: '2026-10-05T00:00:00Z', close: 230, shortAverage: null, longAverage: null },
    { at: '2026-10-06T00:00:00Z', close: 231, shortAverage: 229, longAverage: 221 }
  ];
  const item = (symbol: string, signal: TradeAdvice['signal'], reasoning = ['because'], extra: Partial<TradeAdvice> = {}): TradeAdvice => ({
    symbol, signal, reasoning, timestamp: '2026-10-07T12:00:00Z', indicators: indicators(), chart, ...extra
  });
  const sample = () => of([
    item('AAPL', 'BUY', ['Latest close is above the average']),
    item('MSFT', 'HOLD', ['because'], { indicators: indicators({ trend: 'NEUTRAL', rsi: 75, rsiState: 'OVERBOUGHT' }) }),
    item('NVDA', 'INSUFFICIENT_DATA', ['Required: 50 observations.', 'Available: 17.'], { indicators: null, chart: [] }),
    item('TSLA', 'SELL', ['falling'], { indicators: indicators({ trend: 'BEARISH', momentumPercent: -3.1 }) })
  ]);

  async function build(result: unknown = sample()) {
    advice.mockReset().mockReturnValue(result);
    TestBed.configureTestingModule({
      imports: [AdviceComponent],
      providers: [
        provideRouter([]),
        { provide: AdviceService, useValue: { advice } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 3 }) } }
      ]
    });
    TestBed.overrideComponent(AdviceComponent, { remove: { imports: [EchartComponent] }, add: { imports: [EchartStub] } });
    await TestBed.compileComponents();
    const fixture = TestBed.createComponent(AdviceComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
  const el = (f: { nativeElement: HTMLElement }, id: string) => f.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;

  it('asks for the own account and shows each signal with its reasons', async () => {
    const fixture = await build();

    expect(advice).toHaveBeenCalledWith(3);
    expect(el(fixture, 'advice-AAPL')?.textContent).toContain('Buy');
    expect(el(fixture, 'advice-AAPL')?.textContent).toContain('Latest close is above the average');
    expect(el(fixture, 'advice-NVDA')?.textContent).toContain('No signal');
    expect(el(fixture, 'advice-NVDA')?.textContent).toContain('Available: 17.');
  });

  it('shows the market mood across the instruments: bullish, neutral and bearish counts', async () => {
    const fixture = await build();

    const mood = el(fixture, 'market-mood')!;
    expect(mood.textContent).toContain('1 bullish');
    expect(mood.textContent).toContain('1 neutral');
    expect(mood.textContent).toContain('1 bearish');
    expect(el(fixture, 'mood-label')?.textContent).toBe('Mixed');
  });

  it('calls the overall mood bullish when more instruments are bullish than bearish', async () => {
    const fixture = await build(of([item('AAPL', 'BUY'), item('MSFT', 'BUY'), item('TSLA', 'SELL', ['x'], { indicators: indicators({ trend: 'BEARISH' }) })]));

    expect(el(fixture, 'mood-label')?.textContent).toBe('Bullish');
  });

  it('labels each instrument bullish or bearish and shows its indicators', async () => {
    const fixture = await build();

    expect(el(fixture, 'advice-AAPL')?.querySelector('[data-testid="advice-trend"]')?.textContent).toContain('Bullish');
    expect(el(fixture, 'advice-TSLA')?.querySelector('[data-testid="advice-trend"]')?.textContent).toContain('Bearish');
    const metrics = el(fixture, 'advice-AAPL')!.querySelector('[data-testid="advice-indicators"]')!.textContent!;
    expect(metrics).toContain('232.10');
    expect(metrics).toContain('10-day average');
    expect(metrics).toContain('+2.30%');
    expect(el(fixture, 'advice-TSLA')!.querySelector('[data-testid="advice-indicators"]')!.textContent).toContain('-3.10%');
  });

  it('draws the RSI with its state, and says overbought when it is', async () => {
    const fixture = await build();

    expect(el(fixture, 'advice-MSFT')!.querySelector('[data-testid="advice-rsi"]')!.textContent).toContain('overbought');
    expect(el(fixture, 'advice-AAPL')!.querySelector('[data-testid="advice-rsi"]')!.textContent).toContain('normal');
  });

  it('shows no indicators and no chart for an instrument with no history', async () => {
    const fixture = await build();

    const nvda = el(fixture, 'advice-NVDA')!;
    expect(nvda.querySelector('[data-testid="advice-indicators"]')).toBeNull();
    expect(nvda.querySelector('[data-testid="advice-chart-toggle"]')).toBeNull();
  });

  it('opens the first chart, and opens or hides others on request', async () => {
    const fixture = await build();

    expect(el(fixture, 'advice-AAPL')!.querySelector('[data-testid="chart-stub"]')).not.toBeNull();
    expect(el(fixture, 'advice-MSFT')!.querySelector('[data-testid="chart-stub"]')).toBeNull();

    (el(fixture, 'advice-MSFT')!.querySelector('[data-testid="advice-chart-toggle"]') as HTMLElement).click();
    fixture.detectChanges();
    expect(el(fixture, 'advice-MSFT')!.querySelector('[data-testid="chart-stub"]')).not.toBeNull();

    (el(fixture, 'advice-AAPL')!.querySelector('[data-testid="advice-chart-toggle"]') as HTMLElement).click();
    fixture.detectChanges();
    expect(el(fixture, 'advice-AAPL')!.querySelector('[data-testid="chart-stub"]')).toBeNull();
  });

  it('says it is advice only', async () => {
    const fixture = await build();

    expect(fixture.nativeElement.textContent).toContain('never places an order');
    expect(el(fixture, 'advice-disclaimer')?.textContent).toContain('not a recommendation to trade');
  });

  it('filters by signal', async () => {
    const fixture = await build();

    el(fixture, 'advice-filter-HOLD')!.click();
    fixture.detectChanges();

    expect(el(fixture, 'advice-MSFT')).not.toBeNull();
    expect(el(fixture, 'advice-AAPL')).toBeNull();
  });

  it('shows an empty state with nothing held or watched', async () => {
    const fixture = await build(of([]));

    expect(el(fixture, 'advice-empty')).not.toBeNull();
    expect(el(fixture, 'market-mood')).toBeNull();
  });

  it('shows the error with a retry', async () => {
    const fixture = await build(throwError(() => ({ status: 503 })));

    expect(el(fixture, 'advice-error')).not.toBeNull();
  });
});
