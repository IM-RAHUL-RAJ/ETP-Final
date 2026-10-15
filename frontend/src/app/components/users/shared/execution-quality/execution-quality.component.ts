import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ExecutionRow } from '../../../../core/stats/portfolio-stats';

/**
 * How filled orders compared with the limit price the customer set, per symbol.
 *
 * A limit price is the worst price the customer will accept. The market often fills at a
 * better one; this shows by how much. Right of centre and green is better than the limit,
 * left and red is worse, and the middle line is "exactly at the limit".
 */
@Component({
  selector: 'app-execution-quality',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './execution-quality.component.html',
  styleUrl: './execution-quality.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExecutionQualityComponent {
  readonly rows = input.required<ExecutionRow[]>();

  protected readonly scale = computed(() =>
    Math.max(1, ...this.rows().map((row) => Math.abs(row.improvementPct)))
  );

  protected readonly view = computed(() =>
    this.rows().map((row) => {
      const rounded = Math.round(row.improvementPct * 10) / 10;
      return {
        ...row,
        good: row.improvementPct >= 0,
        verdict: rounded === 0 ? 'At your limit' : rounded > 0 ? 'Better than limit' : 'Worse than limit',
        // Half the track is each direction; the longest bar fills its half.
        width: Math.min(Math.abs(row.improvementPct) / this.scale(), 1) * 50
      };
    })
  );

  /** One sentence that answers "so, am I getting good prices?". Fill-weighted across symbols. */
  protected readonly headline = computed(() => {
    const rows = this.rows();
    const fills = rows.reduce((sum, row) => sum + row.orders, 0);
    if (fills === 0) {
      return null;
    }
    const average = rows.reduce((sum, row) => sum + row.improvementPct * row.orders, 0) / fills;
    const saved = rows.reduce((sum, row) => sum + row.saved, 0);
    const rounded = Math.round(average * 10) / 10;
    return {
      fills,
      good: rounded >= 0,
      percent: Math.abs(average),
      neutral: rounded === 0,
      saved
    };
  });
}
