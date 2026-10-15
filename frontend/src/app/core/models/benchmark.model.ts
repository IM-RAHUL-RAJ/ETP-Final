/** Benchmark data for comparing portfolio performance against market indices. */
export interface BenchmarkIndex {
  /** Unique identifier (e.g., 'SP500', 'NIFTY50', 'NASDAQ100'). */
  id: string;
  /** Display name (e.g., 'S&P 500'). */
  name: string;
  /** Current value of the index. */
  value: number;
  /** Value at start of comparison period. */
  startValue: number;
  /** Currency of the index. */
  currency: string;
  /** Total return percentage. */
  totalReturn: number;
  /** Time period (e.g., '1y', '3m', '30d'). */
  period: string;
  /** Timestamp when data was last updated. */
  asOf: string;
}

/** Comparison of portfolio performance against benchmarks. */
export interface BenchmarkComparison {
  /** Portfolio total return percentage. */
  portfolioReturn: number;
  /** Outperformance relative to benchmark (positive = beating benchmark). */
  outperformance: number;
  /** Benchmark data. */
  benchmark: BenchmarkIndex;
  /** Whether portfolio is outperforming (beating) the benchmark. */
  isOutperforming: boolean;
}

/** Global benchmarking summary. */
export interface BenchmarkSummary {
  /** Selected benchmark index. */
  benchmark: BenchmarkIndex;
  /** Portfolio return for the period. */
  portfolioReturn: number;
  /** Comparisons against primary benchmarks. */
  comparisons: BenchmarkComparison[];
  /** Overall assessment: 'outperforming', 'underperforming', or 'tracking'. */
  assessment: 'outperforming' | 'underperforming' | 'tracking';
}
