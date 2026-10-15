import { Injectable } from '@angular/core';

export interface OrderDraft {
  symbol: string;
  side: 'BUY' | 'SELL';
  quantity: string;
  price: string;
}

/**
 * Keeps a half-filled order ticket while the user visits other tabs. It lives in sessionStorage
 * only: it survives navigation and refresh, ends with the browser tab, and AuthService.logout
 * removes it. Nothing sensitive is in it, and nothing here is ever sent anywhere.
 */
@Injectable({ providedIn: 'root' })
export class OrderDraftService {
  private readonly key = 'order_ticket_draft';

  load(): Partial<OrderDraft> | null {
    try {
      const raw = sessionStorage.getItem(this.key);
      const parsed = raw ? (JSON.parse(raw) as Partial<OrderDraft>) : null;
      return parsed && typeof parsed === 'object' ? parsed : null;
    } catch {
      return null;
    }
  }

  save(draft: OrderDraft): void {
    try {
      sessionStorage.setItem(this.key, JSON.stringify(draft));
    } catch {
      // Storage full or blocked: the ticket still works, it just will not be remembered.
    }
  }

  clear(): void {
    try {
      sessionStorage.removeItem(this.key);
    } catch {
      // Nothing to clear.
    }
  }
}
