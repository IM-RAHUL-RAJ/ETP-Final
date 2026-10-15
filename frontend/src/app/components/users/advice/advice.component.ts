import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AdviceSignal, MarketTrend, TradeAdvice } from '../../../core/models/advice.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AdviceService } from '../../../core/services/advice.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { ThemeService } from '../../../core/theme/theme.service';
import { ChartOption, EchartComponent, chartText, chartTooltip, token } from '../shared/echart/echart.component';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { IconComponent } from '../shared/icon/icon.component';
import { MascotComponent } from '../shared/mascot/mascot.component';

type Filter = 'ALL' | AdviceSignal;

const LABEL: Record<AdviceSignal, string> = { BUY: 'Buy', SELL: 'Sell', HOLD: 'Hold', INSUFFICIENT_DATA: 'No signal' };
const TREND_LABEL: Record<MarketTrend, string> = { BULLISH: 'Bullish', BEARISH: 'Bearish', NEUTRAL: 'Neutral' };

/**
 * Research signals for what the customer holds and watches, with the market behind each: the trend (bullish or bearish),
 * RSI, momentum and a price chart with the two moving averages. Advice only: nothing on this page places an order.
 */
@Component({
  selector: 'app-advice',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink, IconComponent, MascotComponent, EmptyStateComponent, EchartComponent],
  templateUrl: './advice.component.html',
  styleUrl: './advice.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdviceComponent {
  private readonly service = inject(AdviceService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errors = inject(ApiErrorMessageService);
  private readonly theme = inject(ThemeService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly items = signal<TradeAdvice[]>([]);
  protected readonly filter = signal<Filter>('ALL');
  /** Symbols whose chart is open. The first one starts open. */
  protected readonly open = signal<ReadonlySet<string>>(new Set());

  protected readonly tabs = computed(() => {
    const items = this.items();
    const count = (signal: AdviceSignal) => items.filter((item) => item.signal === signal).length;
    return [
      { id: 'ALL' as Filter, label: 'All', count: items.length },
      { id: 'BUY' as Filter, label: 'Buy', count: count('BUY') },
      { id: 'SELL' as Filter, label: 'Sell', count: count('SELL') },
      { id: 'HOLD' as Filter, label: 'Hold', count: count('HOLD') },
      { id: 'INSUFFICIENT_DATA' as Filter, label: 'No signal', count: count('INSUFFICIENT_DATA') }
    ];
  });

  protected readonly visible = computed(() => {
    const filter = this.filter();
    return this.items().filter((item) => filter === 'ALL' || item.signal === filter);
  });

  protected readonly asOf = computed(() => this.items()[0]?.timestamp ?? null);

  /** How the instruments in view are leaning, overall. */
  protected readonly mood = computed(() => {
    const trends = this.items().map((item) => item.indicators?.trend).filter((trend): trend is MarketTrend => !!trend);
    const bullish = trends.filter((trend) => trend === 'BULLISH').length;
    const bearish = trends.filter((trend) => trend === 'BEARISH').length;
    const neutral = trends.length - bullish - bearish;
    const total = trends.length;
    const label = total === 0 ? 'Not enough data' : bullish > bearish ? 'Bullish' : bearish > bullish ? 'Bearish' : 'Mixed';
    const percent = (count: number) => (total === 0 ? 0 : Math.round((count / total) * 100));
    return {
      label, total, bullish, bearish, neutral,
      bullishPercent: percent(bullish), neutralPercent: percent(neutral), bearishPercent: percent(bearish),
      tone: label === 'Bullish' ? 'up' : label === 'Bearish' ? 'down' : 'flat'
    };
  });

  /** One chart per instrument, rebuilt with the theme. */
  protected readonly charts = computed<Record<string, ChartOption>>(() => {
    this.theme.changes();
    const options: Record<string, ChartOption> = {};
    for (const item of this.items()) {
      if (item.chart.length > 1) {
        options[item.symbol] = this.chartOption(item);
      }
    }
    return options;
  });

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.advice(this.accountContext.getCurrentAccount().accountNumericId).subscribe({
      next: (items) => {
        this.items.set(items);
        this.open.set(new Set(items.slice(0, 1).map((item) => item.symbol)));
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(this.errors.getMessage(error));
        this.loading.set(false);
      }
    });
  }

  protected setFilter(filter: Filter): void {
    this.filter.set(filter);
  }

  protected toggleChart(symbol: string): void {
    this.open.update((current) => {
      const next = new Set(current);
      if (!next.delete(symbol)) {
        next.add(symbol);
      }
      return next;
    });
  }

  protected label(signal: AdviceSignal): string {
    return LABEL[signal];
  }

  protected trendLabel(trend: MarketTrend): string {
    return TREND_LABEL[trend];
  }

  protected mascotMood(item: TradeAdvice): 'happy' | 'think' | 'wave' | 'sad' {
    return item.signal === 'BUY' ? 'happy' : item.signal === 'SELL' ? 'sad' : item.signal === 'HOLD' ? 'think' : 'wave';
  }

  /** Where the RSI marker sits on its 0 to 100 bar. */
  protected rsiPosition(rsi: number | null): number {
    return rsi === null ? 0 : Math.max(0, Math.min(100, rsi));
  }

  private chartOption(item: TradeAdvice): ChartOption {
    const text = token('--text', '#0a0a0a');
    const muted = token('--muted', '#525252');
    const grid = token('--line', '#e5e5e5');
    const price = token('--accent', '#171717');
    const short = token('--blue', '#2563eb');
    const long = token('--amber', '#d97706');
    const labels = item.chart.map((point, index) => (point.at ? new Date(point.at).toLocaleDateString(undefined, { day: 'numeric', month: 'short' }) : String(index + 1)));
    const series = (name: string, color: string, values: Array<number | null>, width: number, dashed = false) => ({
      name, type: 'line', data: values, showSymbol: false, smooth: false, connectNulls: false,
      lineStyle: { color, width, type: dashed ? 'dashed' : 'solid' }, itemStyle: { color }
    });
    return {
      grid: { left: 8, right: 12, top: 28, bottom: 4, containLabel: true },
      legend: { top: 0, right: 0, icon: 'roundRect', itemWidth: 14, itemHeight: 3, textStyle: chartText(muted, 12) },
      tooltip: chartTooltip({ trigger: 'axis', axisPointer: { type: 'line', lineStyle: { color: muted, type: 'dashed' } } }),
      xAxis: { type: 'category', data: labels, boundaryGap: false, axisLine: { lineStyle: { color: grid } }, axisTick: { show: false }, axisLabel: { ...chartText(muted, 11), hideOverlap: true } },
      yAxis: { type: 'value', scale: true, splitLine: { lineStyle: { color: grid, type: 'dashed' } }, axisLabel: { ...chartText(muted, 11) } },
      series: [
        series('Price', price, item.chart.map((point) => point.close), 2),
        series(`${item.indicators?.shortPeriod ?? ''}-day average`, short, item.chart.map((point) => point.shortAverage), 1.5),
        series(`${item.indicators?.longPeriod ?? ''}-day average`, long, item.chart.map((point) => point.longAverage), 1.5, true)
      ],
      color: [price, short, long],
      textStyle: { color: text }
    };
  }
}
