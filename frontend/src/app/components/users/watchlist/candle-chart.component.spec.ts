import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { CandlesService } from '../../../core/services/candles.service';
import { CandleChartComponent } from './candle-chart.component';

// The chart library needs a real canvas. These tests care what the component asks for and shows, not the drawing.
const setData = vi.fn();
const createChart = vi.fn(() => ({
  addSeries: vi.fn(() => ({ setData, applyOptions: vi.fn(), priceScale: () => ({ applyOptions: vi.fn() }) })),
  subscribeCrosshairMove: vi.fn(),
  subscribeClick: vi.fn(),
  applyOptions: vi.fn(),
  timeScale: () => ({ fitContent: vi.fn() }),
  remove: vi.fn()
}));
vi.mock('lightweight-charts', () => ({
  createChart: (...args: unknown[]) => (createChart as (...a: unknown[]) => unknown)(...args),
  CandlestickSeries: 'candles',
  HistogramSeries: 'volume',
  CrosshairMode: { Magnet: 1 },
  LineStyle: { Dotted: 1 }
}));

describe('CandleChartComponent', () => {
  let fixture: ComponentFixture<CandleChartComponent>;
  const getCandles = vi.fn();

  const response = (symbol = 'AAPL') => ({
    symbol,
    currency: 'USD',
    period: '1d',
    asOf: '2026-10-07T14:05:11Z',
    candles: [
      { timestamp: '2026-10-05T00:00:00Z', open: 229, high: 232, low: 228, close: 231.2, volume: 1_000_000 },
      { timestamp: '2026-10-06T00:00:00Z', open: 231, high: 233, low: 230, close: 230.5, volume: 900_000 }
    ]
  });

  const el = <T extends HTMLElement = HTMLElement>(id: string): T | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
  };

  async function build(symbol = 'AAPL'): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [CandleChartComponent],
      providers: [{ provide: CandlesService, useValue: { getCandles } }]
    }).compileComponents();
    fixture = TestBed.createComponent(CandleChartComponent);
    fixture.componentRef.setInput('symbol', symbol);
    fixture.componentRef.setInput('currency', 'USD');
    await settle();
  }

  beforeEach(() => {
    localStorage.clear();
    getCandles.mockReset().mockReturnValue(of(response()));
    setData.mockReset();
    createChart.mockClear();
  });

  it('asks for a month of daily candles first', async () => {
    await build();

    expect(getCandles).toHaveBeenCalledWith('AAPL', '1d', 31);
  });

  it('draws the candles and the volume once they arrive', async () => {
    await build();

    expect(createChart).toHaveBeenCalledTimes(1);
    // one call for candles, one for volume
    expect(setData).toHaveBeenCalledTimes(2);
    expect(setData.mock.calls[0][0]).toEqual([
      { time: '2026-10-05', open: 229, high: 232, low: 228, close: 231.2 },
      { time: '2026-10-06', open: 231, high: 233, low: 230, close: 230.5 }
    ]);
  });

  it('offers 1 Week, 1 Month, 3 Months and 6 Months, and re-fetches when one is chosen', async () => {
    await build();
    expect(Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('[role="radio"]')).map((b) => b.textContent!.trim())).toEqual(['1 Week', '1 Month', '3 Months', '6 Months']);

    el('preset-1W')!.click();
    await settle();

    // a week is read in hourly bars
    expect(getCandles).toHaveBeenLastCalledWith('AAPL', '1h', 168);
    expect(el('preset-1W')?.getAttribute('aria-checked')).toBe('true');

    el('preset-6M')!.click();
    await settle();
    expect(getCandles).toHaveBeenLastCalledWith('AAPL', '1d', 183);
    expect(el('preset-1M')?.getAttribute('aria-checked')).toBe('false');
  });

  it('remembers the chosen period for next time', async () => {
    await build();
    el('preset-3M')!.click();
    await settle();

    expect(localStorage.getItem('quorum-candle-preset')).toBe('3M');

    TestBed.resetTestingModule();
    getCandles.mockClear();
    await build();
    expect(getCandles).toHaveBeenCalledWith('AAPL', '1d', 92);
  });

  it('asks again for a different instrument', async () => {
    await build();

    fixture.componentRef.setInput('symbol', 'MSFT');
    await settle();

    expect(getCandles).toHaveBeenLastCalledWith('MSFT', '1d', 31);
  });

  it('says so when there are no candles, and draws nothing', async () => {
    getCandles.mockReturnValue(of({ ...response(), candles: [] }));
    await build();

    expect(el('candle-empty')).not.toBeNull();
    expect(createChart).not.toHaveBeenCalled();
  });

  it('shows the reason with a retry when the data cannot be had, rather than an invented chart', async () => {
    getCandles.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: { errorCode: 'MKT-503', message: 'Chart data is not available right now.' } })));
    await build();

    expect(el('candle-error')).not.toBeNull();
    expect(createChart).not.toHaveBeenCalled();

    getCandles.mockReturnValue(of(response()));
    (el('candle-error')!.querySelector('button') as HTMLButtonElement).click();
    await settle();

    expect(el('candle-error')).toBeNull();
    expect(createChart).toHaveBeenCalledTimes(1);
  });
});
