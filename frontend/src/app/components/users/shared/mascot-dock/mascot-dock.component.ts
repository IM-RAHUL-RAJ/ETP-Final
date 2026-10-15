import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { AnimationName } from '../../../../core/mascot/pixel-mascot';
import { ToastService } from '../../../../core/ui/toast.service';
import { MascotComponent } from '../mascot/mascot.component';

/**
 * The companion appears only when there is something to say: a save, an error, an order filled, an alert.
 * It slides in beside its bubble, acts the message out, and leaves when the last bubble times out.
 * It sits in the corner and never over the page content for long: bubbles are short-lived and can be dismissed.
 */
@Component({
  selector: 'app-mascot-dock',
  standalone: true,
  imports: [MascotComponent],
  templateUrl: './mascot-dock.component.html',
  styleUrl: './mascot-dock.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MascotDockComponent {
  protected readonly toasts = inject(ToastService);

  /** What the companion is doing: acting out the latest message. */
  protected readonly action = computed<AnimationName>(() => {
    const latest = this.toasts.toasts().at(-1);
    return latest?.action ?? 'idle';
  });

}
