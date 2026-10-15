import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MascotComponent } from '../mascot/mascot.component';

/**
 * Shown wherever a list can be empty: the companion looking thoughtful, a headline, one sentence that
 * says what to do next, and a slot for the button that does it.
 * Use: <app-empty-state title="No orders yet" message="..."><a class="button" ...>Place order</a></app-empty-state>
 */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [MascotComponent],
  template: `
    <div class="empty-state" data-testid="empty-state" role="status">
      <app-mascot state="empty" [size]="size()" />
      <h2>{{ title() }}</h2>
      <p class="muted">{{ message() }}</p>
      <div class="empty-state__action"><ng-content /></div>
    </div>
  `,
  styles: [
    `
      .empty-state {
        display: grid;
        justify-items: center;
        gap: 8px;
        padding: 40px 16px;
        text-align: center;
        animation: emptyIn 300ms ease-out both;
      }
      .empty-state h2 {
        margin-top: 8px;
      }
      .empty-state p {
        max-width: 360px;
      }
      .empty-state__action {
        margin-top: 8px;
      }
      @keyframes emptyIn {
        from {
          opacity: 0;
          transform: translateY(16px);
        }
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly size = input(140);
}
