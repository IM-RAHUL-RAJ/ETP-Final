import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { OrderStatus } from '../../../../core/models/order.model';

const DESCRIPTIONS: Record<OrderStatus, string> = {
  NEW: 'Working: waiting for the executor to resolve it',
  FILLED: 'Filled: the order has executed',
  REJECTED: 'Rejected: the order was refused',
  CANCELLED: 'Cancelled: the order was withdrawn'
};

/** Word plus shape, never colour alone: dot (NEW), diamond (FILLED), dash (REJECTED), ring (CANCELLED). */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  templateUrl: './status-badge.component.html',
  styleUrl: './status-badge.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StatusBadgeComponent {
  readonly status = input.required<OrderStatus>();

  protected readonly description = computed(() => DESCRIPTIONS[this.status()] ?? '');
}
