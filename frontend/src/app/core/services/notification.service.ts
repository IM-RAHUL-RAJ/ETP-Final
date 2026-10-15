import { HttpClient } from '@angular/common/http';
import { Injectable, effect, inject, signal } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { AuthService } from '../auth/auth.service';
import { API_ENDPOINTS } from '../config/api-endpoints';
import { NotificationCounts, NotificationItem, NotificationType } from '../models/notification.model';
import { AccountContextService } from './account-context.service';

/** The customer's notification history, shown as an inbox. Read-only apart from marking a message read. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly accountContext = inject(AccountContextService);
  private readonly auth = inject(AuthService);

  private readonly unreadSignal = signal(0);
  /** How many messages are unread, for the badge in the header. Updated by the calls below. */
  readonly unread = this.unreadSignal.asReadonly();

  constructor() {
    // Signing out forgets the count, so the next customer never sees the last one's badge.
    effect(() => {
      if (!this.auth.isAuthenticated()) {
        this.unreadSignal.set(0);
      }
    });
  }

  private base(accountId: number): string {
    return `${API_ENDPOINTS.tradeApiBase}/api/v1/accounts/${accountId}/notifications`;
  }

  /** type narrows the inbox to one group; 'all' (or leaving it out) sends no filter. */
  list(accountId: number, unreadOnly = false, limit = 50, type: NotificationType = 'all'): Observable<NotificationItem[]> {
    const params: Record<string, string | number | boolean> = { unreadOnly, limit };
    if (type !== 'all') {
      params['type'] = type;
    }
    return this.http
      .get<NotificationItem[]>(this.base(accountId), { params })
      .pipe(tap((items) => {
        // Only the unfiltered list tells us the whole unread count.
        if (!unreadOnly && type === 'all' && items.length < limit) {
          this.unreadSignal.set(items.filter((item) => !item.read).length);
        }
      }));
  }

  /** How many messages each tab holds, for the badges. */
  counts(accountId: number): Observable<NotificationCounts> {
    return this.http.get<NotificationCounts>(`${this.base(accountId)}/counts`);
  }

  /** Reads everything at once. Answers with how many were unread. */
  markAllRead(accountId: number): Observable<number> {
    return this.http.post<{ updated: number }>(`${this.base(accountId)}/read-all`, null).pipe(
      map((reply) => reply.updated),
      tap(() => this.unreadSignal.set(0))
    );
  }

  markRead(accountId: number, notificationId: number): Observable<NotificationItem> {
    return this.http
      .post<NotificationItem>(`${this.base(accountId)}/${notificationId}/read`, null)
      .pipe(tap(() => this.unreadSignal.update((count) => Math.max(0, count - 1))));
  }

  /** Re-reads the unread count. A failure leaves the badge as it was: a badge must never break a page. */
  refreshUnread(): Observable<number> {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    if (accountId <= 0) {
      return of(0);
    }
    return this.http.get<{ unread: number }>(`${this.base(accountId)}/unread-count`).pipe(
      map((result) => result.unread),
      tap((count) => this.unreadSignal.set(count)),
      catchError(() => of(this.unreadSignal()))
    );
  }

  /** Forgets the count, e.g. on sign-out, so the next customer never sees the last one's badge. */
  clear(): void {
    this.unreadSignal.set(0);
  }
}
