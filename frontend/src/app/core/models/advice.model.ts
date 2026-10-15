export type AdviceSignal = 'BUY' | 'SELL' | 'HOLD' | 'INSUFFICIENT_DATA';

/** One recommendation. Advice only: it never places an order. */
export interface TradeAdvice {
  symbol: string;
  signal: AdviceSignal;
  reasoning: string[];
  timestamp: string;
  /** What the rules looked at; null when there is no price history at all. */
  indicators: AdviceIndicators | null;
  /** Closes with the two moving averages, oldest first, for the chart. */
  chart: ChartPoint[];
}

export type MarketTrend = 'BULLISH' | 'BEARISH' | 'NEUTRAL';

export interface AdviceIndicators {
  lastPrice: number;
  shortAverage: number | null;
  longAverage: number | null;
  shortPeriod: number;
  longPeriod: number;
  rsi: number | null;
  rsiState: 'OVERBOUGHT' | 'OVERSOLD' | 'NORMAL' | null;
  momentumPercent: number | null;
  volatilityPercent: number | null;
  trend: MarketTrend | null;
}

export interface ChartPoint {
  at: string | null;
  close: number;
  shortAverage: number | null;
  longAverage: number | null;
}

export type OrderOutcome = 'STRATEGY_TRIGGERED' | 'ORDER_SUBMITTED' | 'ORDER_REJECTED' | 'ORDER_SUBMISSION_FAILED';

export interface AutomationStrategy {
  id: string;
  type: 'MA_CROSSOVER' | 'BOLLINGER_BANDS' | null;
  accountId: number;
  symbol: string | null;
  quantity: number | null;
  enabled: boolean;
  parameters: string;
  problems: string[];
  /** Prices collected so far and prices needed before the rule can act (only while it is switched on). */
  collected: number | null;
  required: number | null;
  /** The prices it has collected and the lines its rule compares them with, while it is switched on. */
  chart: StrategyChart | null;
}

export interface StrategyChart {
  prices: number[];
  lines: Record<string, Array<number | null>>;
}

export interface SimulateResponse {
  ticks: number;
  startPrice: number;
  finalPrice: number;
  trigger: 'BUY' | 'SELL';
}

export interface CreateStrategyRequest {
  type: 'MA_CROSSOVER' | 'BOLLINGER_BANDS';
  accountId: number;
  symbol: string;
  quantity: number;
  shortPeriod?: number;
  longPeriod?: number;
  period?: number;
  multiplier?: number;
}

export interface AutomationResult {
  strategyId: string | null;
  /** MA_CROSSOVER or BOLLINGER_BANDS; null for activity recorded before the history table existed. */
  strategyType: string | null;
  symbol: string;
  limitPrice: number | null;
  quantity: number | null;
  accountId: number;
  trigger: 'BUY' | 'SELL' | 'NONE';
  outcome: OrderOutcome;
  orderId: string | null;
  detail: string | null;
  at: string;
}

export interface AutomationStatus {
  running: boolean;
  simulationEnabled: boolean;
  strategies: AutomationStrategy[];
  results: AutomationResult[];
}
