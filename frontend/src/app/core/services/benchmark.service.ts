import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BenchmarkComparison, BenchmarkIndex, BenchmarkSummary } from '../models/benchmark.model';
import { HistoryRange } from '../models/portfolio.model';

/**
 * Benchmarking service: fetches market benchmark data and compares portfolio performance
 * against standard indices like S&P 500, Nifty 50, etc.
 */
@Injectable({
  providedIn: 'root'
})
export class BenchmarkService {
  private readonly http = inject(HttpClient);

  private readonly benchmarks = signal<BenchmarkIndex[]>([]);
  private readonly selectedBenchmark = signal<string>('SP500');
  private readonly loading = signal(false);
  private readonly error = signal<string | null>(null);

  readonly benchmarkList = computed(() => this.benchmarks());
  readonly activeBenchmark = computed(() =>
    this.benchmarks().find((b) => b.id === this.selectedBenchmark())
  );
  readonly isLoading = computed(() => this.loading());
  readonly hasError = computed(() => this.error());

  /**
   * Fetch available benchmarks for a given period.
   */
  fetchBenchmarks(period: HistoryRange = '1y'): Observable<BenchmarkIndex[]> {
    this.loading.set(true);
    this.error.set(null);
    // In production, this would hit your API endpoint:
    // return this.http.get<BenchmarkIndex[]>(`/api/benchmarks?period=${period}`)
    //   .pipe(
    //     tap(() => this.loading.set(false)),
    //     catchError((err) => {
    //       this.error.set('Failed to load benchmarks');
    //       return of([]);
    //     })
    //   );

    // For now, return mock benchmarks
    return new Observable((subscriber) => {
      setTimeout(() => {
        const mockBenchmarks: BenchmarkIndex[] = [
          {
            id: 'SP500',
            name: 'S&P 500',
            value: 5891.36,
            startValue: 4766.18,
            currency: 'USD',
            totalReturn: 23.58,
            period,
            asOf: new Date().toISOString()
          },
          {
            id: 'NIFTY50',
            name: 'Nifty 50',
            value: 24543.25,
            startValue: 21789.45,
            currency: 'INR',
            totalReturn: 12.63,
            period,
            asOf: new Date().toISOString()
          },
          {
            id: 'NASDAQ100',
            name: 'Nasdaq 100',
            value: 19812.55,
            startValue: 14562.78,
            currency: 'USD',
            totalReturn: 36.07,
            period,
            asOf: new Date().toISOString()
          },
          {
            id: 'GOLDSPOT',
            name: 'Gold Spot Price',
            value: 2087.50,
            startValue: 1932.25,
            currency: 'USD',
            totalReturn: 8.03,
            period,
            asOf: new Date().toISOString()
          }
        ];
        this.benchmarks.set(mockBenchmarks);
        this.loading.set(false);
        subscriber.next(mockBenchmarks);
        subscriber.complete();
      }, 500);
    });
  }

  /**
   * Select a specific benchmark for comparison.
   */
  selectBenchmark(benchmarkId: string): void {
    if (this.benchmarks().some((b) => b.id === benchmarkId)) {
      this.selectedBenchmark.set(benchmarkId);
    }
  }

  /**
   * Compare portfolio return against a benchmark.
   */
  compare(portfolioReturn: number, benchmark: BenchmarkIndex): BenchmarkComparison {
    const outperformance = portfolioReturn - benchmark.totalReturn;
    const isOutperforming = outperformance > 0.1; // >0.1% threshold
    return {
      portfolioReturn,
      outperformance,
      benchmark,
      isOutperforming
    };
  }

  /**
   * Get summary of portfolio benchmarking across multiple indices.
   */
  getSummary(portfolioReturn: number): BenchmarkSummary | null {
    const benchmark = this.activeBenchmark();
    if (!benchmark) return null;

    const comparisons = [
      this.compare(portfolioReturn, benchmark),
      ...this.benchmarks()
        .filter((b) => b.id !== benchmark.id)
        .slice(0, 2)
        .map((b) => this.compare(portfolioReturn, b))
    ];

    const outperformingCount = comparisons.filter((c) => c.isOutperforming).length;
    const assessment: 'outperforming' | 'underperforming' | 'tracking' =
      outperformingCount > comparisons.length * 0.6
        ? 'outperforming'
        : outperformingCount < comparisons.length * 0.4
          ? 'underperforming'
          : 'tracking';

    return {
      benchmark,
      portfolioReturn,
      comparisons,
      assessment
    };
  }

  /**
   * Clear error state.
   */
  clearError(): void {
    this.error.set(null);
  }
}
