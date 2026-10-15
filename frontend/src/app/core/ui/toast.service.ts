import { Injectable, signal } from '@angular/core';
import { AnimationName } from '../mascot/pixel-mascot';

export type ToastTone = 'success' | 'error' | 'info';

export interface Toast {
  id: number;
  message: string;
  title: string | null;
  tone: ToastTone;
  /** What the companion does while it says this. */
  action: AnimationName;
}

const DEFAULT_ACTION: Record<ToastTone, AnimationName> = { success: 'happy', error: 'sad', info: 'wave' };

/**
 * Everything the app wants to tell the customer is said by their companion: a speech bubble beside the
 * pixel mascot, which acts it out (thumbs up for a save, a sad face for an error, a cheer for a filled order).
 * The dock on screen draws these; this service only holds them.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  private nextId = 1;
  private readonly list = signal<Toast[]>([]);
  readonly toasts = this.list.asReadonly();

  show(message: string, tone: ToastTone = 'success', milliseconds = 4500, action?: AnimationName): void {
    this.push({ message, title: null, tone, action: action ?? DEFAULT_ACTION[tone] }, milliseconds);
  }

  error(message: string): void {
    this.show(message, 'error', 7000);
  }

  /** A message from the platform (an order filled, an alert fired): a title, the words, and the matching move. */
  notify(title: string, message: string, action: AnimationName, tone: ToastTone = 'info', milliseconds = 8000): void {
    this.push({ message, title, tone, action }, milliseconds);
  }

  dismiss(id: number): void {
    this.list.update((toasts) => toasts.filter((toast) => toast.id !== id));
  }

  private push(toast: Omit<Toast, 'id'>, milliseconds: number): void {
    const entry: Toast = { ...toast, id: this.nextId++ };
    // The latest three only: a burst of saves must not bury the page.
    this.list.update((toasts) => [...toasts.slice(-2), entry]);
    setTimeout(() => this.dismiss(entry.id), milliseconds);
  }
}
