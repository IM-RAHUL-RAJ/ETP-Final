import { Injectable, inject, signal } from '@angular/core';
import { PreferencesService } from '../services/preferences.service';
import { MASCOT_TYPES, MascotType } from './pixel-mascot';

const STORAGE_KEY = 'quorum-mascot';

/**
 * Which companion the customer picked. The server holds the truth (preferences); this keeps a copy so
 * empty screens can draw at once, before any request has come back, and so the same one shows after
 * a refresh on this browser.
 */
@Injectable({ providedIn: 'root' })
export class MascotService {
  private readonly preferences = inject(PreferencesService);
  private readonly current = signal<MascotType>(this.read());
  readonly type = this.current.asReadonly();

  /** Called with the value the server returned, and after the customer picks one. */
  set(type: MascotType | string | null | undefined): void {
    const value = MASCOT_TYPES.find((candidate) => candidate === String(type ?? '').toLowerCase());
    if (!value) {
      return;
    }
    this.current.set(value);
    try {
      localStorage.setItem(STORAGE_KEY, value);
    } catch {
      // Private mode or blocked storage: the choice still holds for this visit.
    }
  }

  /** Reads the stored choice from the server once the customer is signed in. A failure keeps what we have. */
  refresh(accountId: number): void {
    if (accountId <= 0) {
      return;
    }
    this.preferences.get(accountId).subscribe({
      next: (preferences) => this.set(preferences.mascot),
      error: () => undefined
    });
  }

  private read(): MascotType {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return MASCOT_TYPES.find((candidate) => candidate === stored) ?? 'owl';
    } catch {
      return 'owl';
    }
  }
}
