import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { CANDLE_PRESETS, Candle, CandlePreset, CandlesService } from '../../../core/services/candles.service';

const STORAGE_KEY = 'quorum-candle-preset';

function storedPreset(): CandlePreset {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return CANDLE_PRESETS.find((preset) => preset.value === saved)?.value ?? '1M';
  } catch {
    return '1M';
  }
}

interface Legend {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number | null;
  up: boolean;
}

/**
 * A candlestick chart for one instrument with three presets: 1 Day (15-minute bars), 1 Month and 3 Months (daily bars).
 * Times are shown in the viewer's own time zone for the intraday view, and as calendar dates for daily bars, so the
 * axis and the candles agree. Hover reads a candle; a click pins it. The library loads on first use.
 */
@Component({
  selector: 'app-candle-chart',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './candle-chart.component.html',
  styleUrl: './candle-chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CandleChartComponent {
  private readonly candlesService = inject(CandlesService);
  private readonly errorMessages = inject(ApiErrorMessageService);
  private readonly destroyRef = inject(DestroyRef);

  readonly symbol = input.required<string>();
  readonly currency = input<string | null>(null);

  protected readonly presets = CANDLE_PRESETS;
  protected readonly preset = signal<CandlePreset>(storedPreset());
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly candles = signal<Candle[]>([]);
  protected readonly hover = signal<Legend | null>(null);
  protected readonly pinned = signal<Legend | null>(null);

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private chart: import('lightweight-charts').IChartApi | null = null;
  private candleSeries: import('lightweight-charts').ISeriesApi<'Candlestick'> | null = null;
  private volumeSeries: import('lightweight-charts').ISeriesApi<'Histogram'> | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private themeObserver: MutationObserver | null = null;
  private requestId = 0;

  constructor() {
    // A new symbol or preset asks for new candles; an old answer arriving late is ignored.
    effect(() => {
      const symbol = this.symbol();
      const preset = this.preset();
      untracked(() => this.fetch(symbol, preset));
    });

    this.destroyRef.onDestroy(() => {
      this.resizeObserver?.disconnect();
      this.themeObserver?.disconnect();
      this.chart?.remove();
      this.chart = null;
    });
  }

  /** What the legend shows: the pinned candle if there is one, else what the pointer is over. */
  protected shown(): Legend | null {
    return this.pinned() ?? this.hover();
  }

  protected choose(preset: CandlePreset): void {
    if (preset === this.preset()) {
      return;
    }
    this.preset.set(preset);
    try {
      localStorage.setItem(STORAGE_KEY, preset);
    } catch {
      // Storage blocked: the choice holds for this visit.
    }
  }

  protected retry(): void {
    this.fetch(this.symbol(), this.preset());
  }

  private fetch(symbol: string, presetValue: CandlePreset): void {
    const preset = CANDLE_PRESETS.find((candidate) => candidate.value === presetValue) ?? CANDLE_PRESETS[1];
    const id = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);
    this.hover.set(null);
    this.pinned.set(null);

    this.candlesService.getCandles(symbol, preset.period, preset.bars).subscribe({
      next: (response) => {
        if (id !== this.requestId) {
          return;
        }
        this.candles.set(response.candles.slice(-preset.bars));
        this.loading.set(false);
        void this.draw(preset.intraday);
      },
      error: (error: unknown) => {
        if (id !== this.requestId) {
          return;
        }
        this.candles.set([]);
        this.error.set(this.errorMessages.getMessage(error));
        this.loading.set(false);
      }
    });
  }

  /** Daily bars sit on their calendar date; intraday bars on their moment. The axis formats both in the viewer's own terms. */
  private timeOf(candle: Candle, intraday: boolean): import('lightweight-charts').Time {
    return intraday
      ? (Math.floor(Date.parse(candle.timestamp) / 1000) as import('lightweight-charts').UTCTimestamp)
      : (candle.timestamp.slice(0, 10) as import('lightweight-charts').Time);
  }

  private async draw(intraday: boolean): Promise<void> {
    const candles = this.candles();
    if (candles.length === 0) {
      return;
    }
    const lib = await import('lightweight-charts');
    // The element exists once loading is false and the view has updated.
    await Promise.resolve();
    const element = this.host().nativeElement;

    if (!this.chart) {
      this.chart = lib.createChart(element, {
        autoSize: false,
        width: element.clientWidth || 600,
        height: 320,
        ...this.themeOptions(lib),
        crosshair: { mode: lib.CrosshairMode.Magnet },
        rightPriceScale: { borderVisible: false }
      });
      this.candleSeries = this.chart.addSeries(lib.CandlestickSeries, this.candleColors());
      this.volumeSeries = this.chart.addSeries(lib.HistogramSeries, { priceFormat: { type: 'volume' }, priceScaleId: '' });
      this.volumeSeries.priceScale().applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });

      this.chart.subscribeCrosshairMove((param) => this.onCrosshair(param));
      this.chart.subscribeClick((param) => this.onClick(param));
      // Older browsers and test environments have no ResizeObserver: the chart then keeps its first width.
      if (typeof ResizeObserver !== 'undefined') {
        this.resizeObserver = new ResizeObserver(() => this.chart?.applyOptions({ width: element.clientWidth }));
        this.resizeObserver.observe(element);
      }
      // Light and dark switch while the page is open.
      this.themeObserver = new MutationObserver(() => this.applyTheme(lib));
      this.themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    }

    const local = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit', hour12: false });
    const day = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' });
    this.chart.applyOptions({
      timeScale: {
        timeVisible: intraday,
        secondsVisible: false,
        borderVisible: false,
        // The default axis speaks UTC. Say what the viewer's clock says instead.
        tickMarkFormatter: (time: import('lightweight-charts').Time) => this.axisLabel(time, intraday, local, day)
      },
      localization: { timeFormatter: (time: import('lightweight-charts').Time) => this.fullLabel(time, intraday) }
    });
    this.candleSeries?.setData(
      candles.map((candle) => ({ time: this.timeOf(candle, intraday), open: candle.open, high: candle.high, low: candle.low, close: candle.close }))
    );
    this.volumeSeries?.setData(
      candles.map((candle) => ({
        time: this.timeOf(candle, intraday),
        value: candle.volume ?? 0,
        color: candle.close >= candle.open ? this.cssColor('--ok', '#15803d') + '66' : this.cssColor('--reject', '#dc2626') + '66'
      }))
    );
    this.chart.timeScale().fitContent();
  }

  private moment(time: import('lightweight-charts').Time, intraday: boolean): Date {
    return intraday ? new Date(Number(time) * 1000) : new Date(`${String(time)}T00:00:00Z`);
  }

  private axisLabel(time: import('lightweight-charts').Time, intraday: boolean, local: Intl.DateTimeFormat, day: Intl.DateTimeFormat): string {
    const when = this.moment(time, intraday);
    return intraday ? local.format(when) : day.format(new Date(when.getUTCFullYear(), when.getUTCMonth(), when.getUTCDate()));
  }

  private fullLabel(time: import('lightweight-charts').Time, intraday: boolean): string {
    const when = this.moment(time, intraday);
    return intraday
      ? when.toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false })
      : new Date(when.getUTCFullYear(), when.getUTCMonth(), when.getUTCDate()).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' });
  }

  private legendFor(time: import('lightweight-charts').Time, bar: { open: number; high: number; low: number; close: number }): Legend {
    const intraday = CANDLE_PRESETS.find((candidate) => candidate.value === this.preset())?.intraday ?? false;
    const key = String(time);
    const match = this.candles().find((candle) => (intraday ? String(Math.floor(Date.parse(candle.timestamp) / 1000)) : candle.timestamp.slice(0, 10)) === key);
    return { time: this.fullLabel(time, intraday), ...bar, volume: match?.volume ?? null, up: bar.close >= bar.open };
  }

  private onCrosshair(param: import('lightweight-charts').MouseEventParams): void {
    const bar = param.time && this.candleSeries ? (param.seriesData.get(this.candleSeries) as { open: number; high: number; low: number; close: number } | undefined) : undefined;
    this.hover.set(bar && param.time ? this.legendFor(param.time, bar) : null);
  }

  /** A click pins the candle under the pointer, so its figures stay; another click on empty chart lets go. */
  private onClick(param: import('lightweight-charts').MouseEventParams): void {
    const bar = param.time && this.candleSeries ? (param.seriesData.get(this.candleSeries) as { open: number; high: number; low: number; close: number } | undefined) : undefined;
    this.pinned.set(bar && param.time ? this.legendFor(param.time, bar) : null);
  }

  private applyTheme(lib: typeof import('lightweight-charts')): void {
    this.chart?.applyOptions(this.themeOptions(lib));
    this.candleSeries?.applyOptions(this.candleColors());
  }

  private themeOptions(lib: typeof import('lightweight-charts')) {
    const text = this.cssColor('--muted', '#737373');
    const line = this.cssColor('--line', '#e5e5e5');
    return {
      layout: { background: { color: 'transparent' }, textColor: text, fontSize: 13 },
      grid: { vertLines: { color: line, style: lib.LineStyle.Dotted }, horzLines: { color: line, style: lib.LineStyle.Dotted } }
    } as const;
  }

  private candleColors() {
    const up = this.cssColor('--ok', '#15803d');
    const down = this.cssColor('--reject', '#dc2626');
    return { upColor: up, downColor: down, borderUpColor: up, borderDownColor: down, wickUpColor: up, wickDownColor: down };
  }

  /** Reads a design token, so the chart follows the theme; falls back to a fixed colour where there is no document. */
  private cssColor(token: string, fallback: string): string {
    const value = getComputedStyle(document.documentElement).getPropertyValue(token).trim();
    // The volume bars append an alpha byte, which only works on a six-digit hex.
    return /^#[0-9a-f]{6}$/i.test(value) ? value : fallback;
  }
}
