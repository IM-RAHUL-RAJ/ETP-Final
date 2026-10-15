import { Component, input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BenchmarkSummary } from '../../../../core/models/benchmark.model';
import { IconComponent } from '../icon/icon.component';

/**
 * Displays portfolio benchmarking: how the portfolio is performing against market indices.
 * Shows the primary benchmark and outperformance metrics.
 */
@Component({
  selector: 'app-portfolio-benchmark',
  standalone: true,
  imports: [CommonModule, IconComponent],
  template: `
    @if (summary(); as summary) {
      <article class="card benchmark" data-testid="portfolio-benchmark">
        <div class="benchmark__header">
          <h3>Benchmark comparison</h3>
          <span class="badge" [attr.data-assessment]="summary.assessment">
            {{ summary.assessment | titlecase }}
          </span>
        </div>

        <div class="benchmark__primary">
          <div class="benchmark__metric">
            <p class="label muted">vs {{ summary.benchmark.name }}</p>
            <p class="value" [attr.data-dir]="summary.comparisons[0].outperformance > 0 ? 'up' : 'down'">
              <span class="dot" [class.positive]="summary.comparisons[0].outperformance > 0" [class.negative]="summary.comparisons[0].outperformance < 0"></span>
              {{ summary.comparisons[0].outperformance > 0 ? '+' : '' }}{{ summary.comparisons[0].outperformance | number: '1.2-2' }}%
            </p>
          </div>
          <div class="benchmark__metric">
            <p class="label muted">Portfolio return</p>
            <p class="value portfolio">{{ summary.portfolioReturn | number: '1.2-2' }}%</p>
          </div>
          <div class="benchmark__metric">
            <p class="label muted">Benchmark return</p>
            <p class="value">{{ summary.benchmark.totalReturn | number: '1.2-2' }}%</p>
          </div>
        </div>

        @if (summary.comparisons.length > 1) {
          <div class="benchmark__secondary">
            <p class="label muted">Other indices</p>
            <div class="benchmark__list">
              @for (comp of summary.comparisons.slice(1); track comp.benchmark.id) {
                <div class="benchmark__item">
                  <span class="name">{{ comp.benchmark.name }}</span>
                  <span class="return" [attr.data-dir]="comp.outperformance > 0 ? 'up' : 'down'">
                    {{ comp.outperformance > 0 ? '+' : '' }}{{ comp.outperformance | number: '1.2-2' }}%
                  </span>
                </div>
              }
            </div>
          </div>
        }

        <p class="benchmark__note muted">As of {{ summary.benchmark.asOf | date: 'MMM d, HH:mm' }}</p>
      </article>
    }
  `,
  styles: [`
    .benchmark {
      display: flex;
      flex-direction: column;
      gap: 20px;
      padding: 20px;
    }

    .benchmark__header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }

    .benchmark__header h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
    }

    .badge {
      display: inline-flex;
      align-items: center;
      padding: 4px 12px;
      border-radius: 12px;
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .badge[data-assessment="outperforming"] {
      background: var(--ok-light, rgba(76, 175, 80, 0.1));
      color: var(--ok, #4caf50);
    }

    .badge[data-assessment="underperforming"] {
      background: var(--reject-light, rgba(244, 67, 54, 0.1));
      color: var(--reject, #f44336);
    }

    .badge[data-assessment="tracking"] {
      background: var(--muted-light, rgba(107, 112, 120, 0.1));
      color: var(--muted, #6b7078);
    }

    .benchmark__primary {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 16px;
      padding: 16px 0;
      border-top: 1px solid var(--line);
      border-bottom: 1px solid var(--line);
    }

    .benchmark__metric {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .label {
      font-size: 12px;
      font-weight: 500;
    }

    .value {
      font-size: 20px;
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .value[data-dir="up"] {
      color: var(--ok, #4caf50);
    }

    .value[data-dir="down"] {
      color: var(--reject, #f44336);
    }

    .value.portfolio {
      color: var(--accent, #1f5fd6);
    }

    .dot {
      display: inline-block;
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }

    .dot.positive {
      background: var(--ok, #4caf50);
    }

    .dot.negative {
      background: var(--reject, #f44336);
    }

    .benchmark__secondary {
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .benchmark__list {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .benchmark__item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px;
      background: var(--surface, #f5f5f5);
      border-radius: 4px;
      font-size: 14px;
    }

    .benchmark__item .name {
      font-weight: 500;
    }

    .benchmark__item .return {
      font-weight: 600;
    }

    .benchmark__item .return[data-dir="up"] {
      color: var(--ok, #4caf50);
    }

    .benchmark__item .return[data-dir="down"] {
      color: var(--reject, #f44336);
    }

    .benchmark__note {
      font-size: 12px;
    }

    @media (max-width: 600px) {
      .benchmark__primary {
        grid-template-columns: 1fr;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PortfolioBenchmarkComponent {
  /** Summary data to display. */
  readonly summary = input<BenchmarkSummary | null>(null);
}
