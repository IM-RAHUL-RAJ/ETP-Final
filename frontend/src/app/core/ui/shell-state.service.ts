import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'quorum-sidebar-collapsed';

/** Whether the sidebar is collapsed to icons (remembered across visits) and whether the mobile drawer is open. */
@Injectable({ providedIn: 'root' })
export class ShellStateService {
  private readonly collapsedSignal = signal(this.read());
  private readonly drawerSignal = signal(false);

  readonly collapsed = this.collapsedSignal.asReadonly();
  readonly drawerOpen = this.drawerSignal.asReadonly();

  toggleCollapsed(): void {
    this.collapsedSignal.update((value) => !value);
    try {
      localStorage.setItem(STORAGE_KEY, String(this.collapsedSignal()));
    } catch {
      // Storage blocked: the sidebar still works for this visit.
    }
  }

  openDrawer(): void {
    this.drawerSignal.set(true);
  }

  closeDrawer(): void {
    this.drawerSignal.set(false);
  }

  private read(): boolean {
    try {
      return localStorage.getItem(STORAGE_KEY) === 'true';
    } catch {
      return false;
    }
  }
}
