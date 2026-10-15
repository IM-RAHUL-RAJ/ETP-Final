import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import { NotificationCounts, NotificationItem, NotificationKind, NotificationType, typeOf } from '../../../core/models/notification.model';
import { AlertChannel } from '../../../core/models/preferences.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ToastService } from '../../../core/ui/toast.service';
import { TimeAgoPipe } from '../../../core/ui/time-ago.pipe';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { IconComponent, IconName } from '../shared/icon/icon.component';

/** How often the inbox looks for new messages. */
export const INBOX_REFRESH_MS = 30_000;

/** Plain words for each kind: never the internal code. */
const KIND_LABELS: Record<NotificationKind, string> = {
  ORDER_FILLED: 'Order placed',
  ORDER_REJECTED: 'Order rejected',
  ORDER_CANCELLED: 'Order cancelled',
  PRICE_ALERT: 'Alert triggered',
  FUNDS: 'Funds'
};

const TYPE_ICONS: Record<Exclude<NotificationType, 'all'>, IconName> = {
  price_alert: 'bell',
  order: 'orders',
  funds: 'wallet'
};

const CHANNEL_LABELS: Record<AlertChannel, string> = {
  EMAIL: 'e-mail',
  SMS: 'text message',
  PUSH: 'in-app notification'
};

/**
 * The customer's notification history, filtered by what it is about: price alerts, orders or funds. A message
 * that was missed on its channel is still here, with what happened to it: waiting, sent, on hold, or failed.
 */
@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [DatePipe, TimeAgoPipe, IconComponent, EmptyStateComponent],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NotificationsComponent {
  private readonly notificationService = inject(NotificationService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessages = inject(ApiErrorMessageService);
  private readonly toast = inject(ToastService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly items = signal<NotificationItem[]>([]);
  protected readonly type = signal<NotificationType>('all');
  protected readonly unreadOnly = signal(false);
  protected readonly busyId = signal<number | null>(null);
  protected readonly counts = signal<NotificationCounts>({ all: 0, priceAlert: 0, order: 0, funds: 0 });

  protected readonly tabs = computed(() => [
    { id: 'all' as NotificationType, label: 'All', count: this.counts().all },
    { id: 'price_alert' as NotificationType, label: 'Price Alerts', count: this.counts().priceAlert },
    { id: 'order' as NotificationType, label: 'Orders', count: this.counts().order },
    { id: 'funds' as NotificationType, label: 'Funds', count: this.counts().funds }
  ]);

  protected readonly visible = computed(() => (this.unreadOnly() ? this.items().filter((item) => !item.read) : this.items()));
  protected readonly unreadCount = computed(() => this.items().filter((item) => !item.read).length);

  constructor() {
    this.load();
    // New messages arrive while the page is open: look again from time to time, quietly.
    timer(INBOX_REFRESH_MS, INBOX_REFRESH_MS).pipe(takeUntilDestroyed()).subscribe(() => this.load(true));
  }

  protected setType(type: NotificationType): void {
    this.type.set(type);
    this.load();
  }

  protected toggleUnread(): void {
    this.unreadOnly.update((value) => !value);
  }

  protected kindLabel(kind: NotificationKind): string {
    return KIND_LABELS[kind];
  }

  protected iconOf(kind: NotificationKind): IconName {
    return TYPE_ICONS[typeOf(kind)];
  }

  /** What happened to the message, in words. Says which channel it used, and whether that was the customer's choice or the default. */
  protected delivery(item: NotificationItem): string {
    const channel = item.channel ? CHANNEL_LABELS[item.channel] : null;
    const viaDefault = item.channelSource === 'DEFAULT' ? ' (your default: you have not chosen a channel)' : '';
    switch (item.status) {
      case 'SENT':
        return item.statusReason === 'STUB_DELIVERY'
          ? `Recorded for ${channel} delivery. No ${channel} provider is connected in this environment.${viaDefault}`
          : `Sent by ${channel}.${viaDefault}`;
      case 'QUEUED':
        return item.statusReason ? 'Waiting to be sent. A first attempt did not go through, so we are trying again.' : 'Waiting to be sent.';
      case 'HELD':
        return 'On hold: your profile has no contact detail for the channel you chose. Change your channel in Settings.';
      case 'SKIPPED':
        return 'Not sent: you switched this kind of message off in Settings. It is kept here.';
      case 'FAILED':
        return `We could not send this by ${channel ?? 'your channel'}.`;
    }
  }

  protected load(quiet = false): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    if (!quiet) {
      this.loading.set(true);
    }
    this.error.set(null);

    this.notificationService.list(accountId, false, 50, this.type()).subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        // A failed quiet refresh must not replace messages the customer is reading.
        if (!quiet) {
          this.error.set(this.errorMessages.getMessage(error));
        }
        this.loading.set(false);
      }
    });

    // The badges on the tabs. A failure leaves the numbers as they were; a badge must not break the page.
    this.notificationService.counts(accountId).subscribe({
      next: (counts) => this.counts.set(counts),
      error: () => undefined
    });
  }

  /** Marks the whole inbox read, then shows it that way. */
  protected markAllRead(): void {
    if (this.busyId() !== null || this.unreadCount() === 0 && this.counts().all === 0) {
      return;
    }
    this.busyId.set(-1);
    this.actionError.set(null);

    this.notificationService.markAllRead(this.accountContext.getCurrentAccount().accountNumericId).subscribe({
      next: (updated) => {
        this.items.update((items) => items.map((item) => ({ ...item, read: true })));
        this.busyId.set(null);
        this.toast.show(updated === 0 ? 'Nothing was unread' : `Marked ${updated} as read`);
      },
      error: (error: unknown) => {
        this.actionError.set(this.errorMessages.getMessage(error));
        this.busyId.set(null);
      }
    });
  }

  protected markRead(item: NotificationItem): void {
    if (item.read || this.busyId() !== null) {
      return;
    }
    this.busyId.set(item.id);
    this.actionError.set(null);

    this.notificationService.markRead(this.accountContext.getCurrentAccount().accountNumericId, item.id).subscribe({
      next: (updated) => {
        this.items.update((items) => items.map((entry) => (entry.id === updated.id ? updated : entry)));
        this.busyId.set(null);
      },
      error: (error: unknown) => {
        this.actionError.set(this.errorMessages.getMessage(error));
        this.busyId.set(null);
      }
    });
  }
}
