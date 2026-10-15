import { Injectable, inject } from '@angular/core';
import { AnimationName } from '../mascot/pixel-mascot';
import { NotificationItem, NotificationKind } from '../models/notification.model';
import { AccountContextService } from '../services/account-context.service';
import { NotificationService } from '../services/notification.service';
import { ToastService } from './toast.service';

/** How often to look for something new to announce. */
export const WATCH_INTERVAL_MS = 20_000;

/** What the companion does for each kind of news. */
export const ACTION_FOR: Record<NotificationKind, AnimationName> = {
  ORDER_FILLED: 'cheer',
  ORDER_REJECTED: 'sad',
  ORDER_CANCELLED: 'think',
  PRICE_ALERT: 'wave',
  FUNDS: 'happy'
};

/**
 * Tells the customer about new inbox messages as they arrive: the companion says it and acts it out.
 * The first look only learns what is already there, so signing in never replays old news.
 */
@Injectable({ providedIn: 'root' })
export class NotificationWatcher {
  private readonly notifications = inject(NotificationService);
  private readonly accountContext = inject(AccountContextService);
  private readonly toast = inject(ToastService);

  private timer: ReturnType<typeof setInterval> | null = null;
  private lastSeenId: number | null = null;

  start(): void {
    if (this.timer !== null) {
      return;
    }
    this.lastSeenId = null;
    this.look();
    this.timer = setInterval(() => this.look(), WATCH_INTERVAL_MS);
  }

  stop(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
    this.lastSeenId = null;
  }

  /** One look at the latest few messages. Public so a page can ask for it right after it does something. */
  look(): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    if (accountId <= 0) {
      return;
    }
    this.notifications.list(accountId, false, 5).subscribe({
      next: (items) => this.announce(items),
      // A failed look is not worth a message: the next one will try again.
      error: () => undefined
    });
  }

  private announce(items: NotificationItem[]): void {
    const newest = items.reduce((highest, item) => Math.max(highest, item.id), 0);
    if (this.lastSeenId === null) {
      this.lastSeenId = newest;
      return;
    }
    const fresh = items.filter((item) => item.id > (this.lastSeenId ?? 0)).sort((left, right) => left.id - right.id);
    this.lastSeenId = Math.max(this.lastSeenId, newest);
    for (const item of fresh) {
      this.toast.notify(item.subject, item.body, ACTION_FOR[item.kind], item.kind === 'ORDER_REJECTED' ? 'error' : 'info');
    }
  }
}
