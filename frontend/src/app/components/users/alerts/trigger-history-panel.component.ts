import { ChangeDetectionStrategy, Component, HostListener, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { AlertTrigger, NotificationDeliveryStatus, PriceAlert, conditionText } from '../../../core/models/alert.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AlertService } from '../../../core/services/alert.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { TimeAgoPipe } from '../../../core/ui/time-ago.pipe';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { IconComponent } from '../shared/icon/icon.component';

const STATUS_TEXT: Record<NotificationDeliveryStatus, { label: string; tone: 'live' | 'delayed' | 'stale' | 'idle' }> = {
  SENT: { label: 'Sent', tone: 'live' },
  QUEUED: { label: 'Queued', tone: 'delayed' },
  HELD: { label: 'Queued', tone: 'delayed' },
  FAILED: { label: 'Failed', tone: 'stale' },
  SKIPPED: { label: 'Not sent (notifications off)', tone: 'idle' },
  UNKNOWN: { label: 'Not recorded', tone: 'idle' }
};

/**
 * Each time one alert fired: when, at what price, and whether the message got out.
 * Creating the alert is not an event here, only triggers are.
 */
@Component({
  selector: 'app-trigger-history-panel',
  standalone: true,
  imports: [DatePipe, DecimalPipe, TimeAgoPipe, IconComponent, EmptyStateComponent],
  templateUrl: './trigger-history-panel.component.html',
  styleUrl: './trigger-history-panel.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TriggerHistoryPanelComponent implements OnInit {
  private readonly alertService = inject(AlertService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessages = inject(ApiErrorMessageService);

  readonly alert = input.required<PriceAlert>();
  readonly currency = input('');
  readonly closed = output<void>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly triggers = signal<AlertTrigger[]>([]);
  protected readonly heading = computed(() => `Alert: ${this.alert().symbol} ${conditionText(this.alert().condition, this.alert().price)}`);

  ngOnInit(): void {
    this.alertService.getTriggerHistory(this.accountContext.getCurrentAccount().accountNumericId, this.alert().id).subscribe({
      next: (history) => {
        this.triggers.set(history.triggers);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(this.errorMessages.getMessage(error));
        this.loading.set(false);
      }
    });
  }

  protected statusOf(trigger: AlertTrigger): { label: string; tone: string } {
    return STATUS_TEXT[trigger.notificationStatus] ?? STATUS_TEXT.UNKNOWN;
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.closed.emit();
  }
}
