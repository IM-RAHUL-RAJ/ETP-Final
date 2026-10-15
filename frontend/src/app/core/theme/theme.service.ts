import { Injectable, effect, inject, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';

export type ThemeMode = 'light' | 'dark' | 'auto';

/**
 * ThemeService: Manages light/dark mode switching
 * 
 * Features:
 * - Respects system preference via prefers-color-scheme
 * - Allows user override
 * - Persists preference to localStorage
 * - Uses CSS custom properties for theming
 */
@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private document = inject(DOCUMENT);
  private transitionCleanupHandle: number | null = null;

  /**
   * Current theme mode: 'light', 'dark', or 'auto' (follows system)
   */
  private themeMode = signal<ThemeMode>(this.loadThemePreference());

  /**
   * Computed theme for UI display
   */
  private prefersDark = signal(this.checkSystemPreference());

  /** Bumps whenever the theme is applied, so a chart can rebuild its colours from the tokens. */
  readonly changes = signal(0);

  constructor() {
    // Listen to system preference changes
    this.setupSystemPreferenceListener();

    // Effect to update DOM when theme changes
    effect(() => {
      const mode = this.themeMode();
      this.applyTheme(mode);
    });
  }

  /**
   * Get current theme mode
   */
  getCurrentTheme(): ThemeMode {
    return this.themeMode();
  }

  /**
   * Set theme mode
   * @param mode - 'light', 'dark', or 'auto'
   */
  setTheme(mode: ThemeMode): void {
    this.themeMode.set(mode);
    localStorage.setItem('app-theme-preference', mode);
  }

  /**
   * Toggle between light and dark mode
   * If currently 'auto', switches to opposite of system preference
   */
  toggleTheme(): void {
    const current = this.themeMode();
    
    if (current === 'auto') {
      // If auto, switch to explicit opposite of system
      const newMode = this.prefersDark() ? 'light' : 'dark';
      this.setTheme(newMode);
    } else {
      // Toggle between light/dark
      const newMode = current === 'light' ? 'dark' : 'light';
      this.setTheme(newMode);
    }
  }

  /**
   * Reset to system preference
   */
  resetToSystemPreference(): void {
    this.setTheme('auto');
  }

  /**
   * Check if dark mode is currently active
   */
  isDarkMode(): boolean {
    const mode = this.themeMode();
    
    if (mode === 'dark') return true;
    if (mode === 'light') return false;
    
    // auto mode - check system preference
    return this.prefersDark();
  }

  /**
   * Load theme preference from localStorage
   */
  private loadThemePreference(): ThemeMode {
    const saved = localStorage.getItem('app-theme-preference');

    if (saved === 'light' || saved === 'dark' || saved === 'auto') {
      return saved;
    }

    // Backward compatibility: migrate legacy key used by older UI code.
    const legacy = localStorage.getItem('theme');
    if (legacy === 'light' || legacy === 'dark') {
      localStorage.setItem('app-theme-preference', legacy);
      return legacy;
    }

    return 'auto';
  }

  /**
   * Check system color scheme preference
   */
  private checkSystemPreference(): boolean {
    const view = this.document.defaultView;
    const supportsMatchMedia = typeof view?.matchMedia === 'function';

    if (!supportsMatchMedia) {
      return false;
    }

    return view.matchMedia('(prefers-color-scheme: dark)').matches;
  }

  /**
   * Setup listener for system preference changes
   */
  private setupSystemPreferenceListener(): void {
    const view = this.document.defaultView;
    if (typeof view?.matchMedia !== 'function') {
      return;
    }

    const mediaQuery = view.matchMedia('(prefers-color-scheme: dark)');

    if (typeof mediaQuery.addEventListener === 'function') {
      mediaQuery.addEventListener('change', (e) => {
        this.prefersDark.set(e.matches);
        // Reapply theme if in auto mode
        if (this.themeMode() === 'auto') {
          this.applyTheme('auto');
        }
      });
    }
  }

  /**
   * Apply theme to document root
   */
  private applyTheme(mode: ThemeMode): void {
    const root = this.document.documentElement;

    // Enable a short transition window so theme token changes animate smoothly.
    root.classList.add('theme-transitioning');
    if (this.transitionCleanupHandle !== null) {
      this.document.defaultView?.clearTimeout(this.transitionCleanupHandle);
    }
    
    if (mode === 'auto') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', mode);
    }
    this.changes.update((count) => count + 1);

    this.transitionCleanupHandle = this.document.defaultView?.setTimeout(() => {
      root.classList.remove('theme-transitioning');
      this.transitionCleanupHandle = null;
    }, 420) ?? null;
  }
}
