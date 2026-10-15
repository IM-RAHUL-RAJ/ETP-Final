import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { AllocationSlice } from '../../../../core/stats/portfolio-stats';

const RADIUS = 48;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;
const GAP = 2.5;

/** Allocation donut. Arcs draw themselves in; hovering or focusing one dims the rest and reads it out in the centre. */
@Component({
  selector: 'app-donut-chart',
  standalone: true,
  imports: [DecimalPipe],
  templateUrl: './donut-chart.component.html',
  styleUrl: './donut-chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DonutChartComponent {
  readonly slices = input.required<AllocationSlice[]>();
  readonly unit = input('');
  readonly centreLabel = input('Invested');

  protected readonly circumference = CIRCUMFERENCE;
  protected readonly radius = RADIUS;
  protected readonly active = signal<number | null>(null);

  protected readonly total = computed(() => this.slices().reduce((sum, slice) => sum + slice.cost, 0));

  protected readonly arcs = computed(() => {
    let offset = 0;
    return this.slices().map((slice, index) => {
      const length = (slice.share / 100) * CIRCUMFERENCE;
      const arc = {
        ...slice,
        color: `var(--c${(index % 6) + 1})`,
        dash: Math.max(length - GAP, 0.01),
        offset
      };
      offset += length;
      return arc;
    });
  });

  protected readonly focused = computed(() => {
    const index = this.active();
    return index === null ? null : (this.arcs()[index] ?? null);
  });

  protected readonly summary = computed(() =>
    this.slices()
      .map((slice) => `${slice.label} ${Math.round(slice.share)} percent`)
      .join(', ')
  );

  protected focus(index: number | null): void {
    this.active.set(index);
  }
}
