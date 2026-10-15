import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { DayActivity } from '../../../../core/stats/portfolio-stats';

const WIDTH = 560;
const HEIGHT = 190;
const PAD_X = 8;
const PAD_TOP = 14;
const PAD_BOTTOM = 26;

const STACK: { key: 'filled' | 'working' | 'rejected' | 'cancelled'; label: string }[] = [
  { key: 'filled', label: 'Filled' },
  { key: 'working', label: 'Working' },
  { key: 'rejected', label: 'Rejected' },
  { key: 'cancelled', label: 'Cancelled' }
];

/** Orders per day, stacked by status. Hover or focus a day to read it out. */
@Component({
  selector: 'app-activity-chart',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './activity-chart.component.html',
  styleUrl: './activity-chart.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ActivityChartComponent {
  readonly days = input.required<DayActivity[]>();

  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;
  protected readonly stack = STACK;
  protected readonly hovered = signal<number | null>(null);

  protected readonly totalOrders = computed(() => this.days().reduce((sum, day) => sum + day.total, 0));

  protected readonly baseline = HEIGHT - PAD_BOTTOM;

  /** The scale never drops below three so a single order does not fill the chart. */
  protected readonly peak = computed(() => Math.max(3, ...this.days().map((day) => day.total)));

  protected readonly bars = computed(() => {
    const days = this.days();
    const slot = (WIDTH - PAD_X * 2) / Math.max(days.length, 1);
    const barWidth = Math.min(slot * 0.62, 26);
    const unit = (HEIGHT - PAD_TOP - PAD_BOTTOM) / this.peak();

    return days.map((day, index) => {
      let cursor = HEIGHT - PAD_BOTTOM;
      const segments = STACK.filter((part) => day[part.key] > 0).map((part) => {
        const height = day[part.key] * unit;
        cursor -= height;
        return { key: part.key, y: cursor, height };
      });

      return {
        day,
        index,
        slotX: PAD_X + index * slot,
        slot,
        x: PAD_X + index * slot + (slot - barWidth) / 2,
        width: barWidth,
        segments,
        showLabel: index % 2 === (days.length - 1) % 2
      };
    });
  });

  protected readonly readout = computed(() => {
    const index = this.hovered();
    return index === null ? null : (this.days()[index] ?? null);
  });

  protected point(index: number | null): void {
    this.hovered.set(index);
  }
}
