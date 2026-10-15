import { TestBed } from '@angular/core/testing';
import { DOCUMENT } from '@angular/common';
import { ThemeService, ThemeMode } from './theme.service';

describe('ThemeService', () => {
  let service: ThemeService;
  let document: Document;

  const createThemeService = (): ThemeService =>
    TestBed.runInInjectionContext(() => new ThemeService());

  beforeEach(() => {
    // Clear localStorage before each test
    localStorage.clear();

    TestBed.configureTestingModule({
      providers: [ThemeService]
    });

    if (typeof window.matchMedia !== 'function') {
      Object.defineProperty(window, 'matchMedia', {
        writable: true,
        value: (query: string) => ({
          matches: false,
          media: query,
          onchange: null,
          addEventListener: () => undefined,
          removeEventListener: () => undefined,
          addListener: () => undefined,
          removeListener: () => undefined,
          dispatchEvent: () => false
        })
      });
    }

    service = TestBed.inject(ThemeService);
    document = TestBed.inject(DOCUMENT);
  });

  afterEach(() => {
    localStorage.clear();
  });

  describe('Theme Initialization', () => {
    it('should initialize with auto theme if no preference saved', () => {
      expect(service.getCurrentTheme()).toBe('auto');
    });

    it('should load theme preference from localStorage', () => {
      localStorage.setItem('app-theme-preference', 'dark');

      const newService = createThemeService();
      expect(newService.getCurrentTheme()).toBe('dark');
    });

    it('should default to auto if localStorage has invalid value', () => {
      localStorage.setItem('app-theme-preference', 'invalid');

      const newService = createThemeService();
      expect(newService.getCurrentTheme()).toBe('auto');
    });
  });

  describe('Theme Switching', () => {
    it('should set theme to light', () => {
      service.setTheme('light');
      expect(service.getCurrentTheme()).toBe('light');
    });

    it('should set theme to dark', () => {
      service.setTheme('dark');
      expect(service.getCurrentTheme()).toBe('dark');
    });

    it('should set theme to auto', () => {
      service.setTheme('light');
      service.setTheme('auto');
      expect(service.getCurrentTheme()).toBe('auto');
    });

    it('should save theme preference to localStorage', () => {
      service.setTheme('dark');
      expect(localStorage.getItem('app-theme-preference')).toBe('dark');
    });

    it('should apply theme to document root element', async () => {
      service.setTheme('dark');
      
      // Give effect time to run
      await new Promise((resolve) => setTimeout(resolve, 10));
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    });

    it('should remove data-theme attribute for auto mode', async () => {
      service.setTheme('light');
      await new Promise((resolve) => setTimeout(resolve, 10));
      service.setTheme('auto');
      await new Promise((resolve) => setTimeout(resolve, 10));
      expect(document.documentElement.getAttribute('data-theme')).toBeNull();
    });
  });

  describe('Theme Toggle', () => {
    it('should toggle from light to dark', () => {
      service.setTheme('light');
      service.toggleTheme();
      expect(service.getCurrentTheme()).toBe('dark');
    });

    it('should toggle from dark to light', () => {
      service.setTheme('dark');
      service.toggleTheme();
      expect(service.getCurrentTheme()).toBe('light');
    });

    it('should toggle from auto based on system preference', () => {
      service.setTheme('auto');
      
      // Determine what dark mode should be
      const isDarkSystemPreference = window.matchMedia('(prefers-color-scheme: dark)').matches;
      const expectedNewMode = isDarkSystemPreference ? 'light' : 'dark';
      
      service.toggleTheme();
      expect(service.getCurrentTheme()).toBe(expectedNewMode);
    });
  });

  describe('Dark Mode Detection', () => {
    it('should return true when theme is dark', () => {
      service.setTheme('dark');
      expect(service.isDarkMode()).toBe(true);
    });

    it('should return false when theme is light', () => {
      service.setTheme('light');
      expect(service.isDarkMode()).toBe(false);
    });

    it('should check system preference in auto mode', () => {
      service.setTheme('auto');
      
      const isDarkSystemPreference = window.matchMedia('(prefers-color-scheme: dark)').matches;
      expect(service.isDarkMode()).toBe(isDarkSystemPreference);
    });
  });

  describe('Reset to System Preference', () => {
    it('should reset theme to auto', () => {
      service.setTheme('dark');
      service.resetToSystemPreference();
      expect(service.getCurrentTheme()).toBe('auto');
    });

    it('should remove data-theme attribute when resetting', async () => {
      service.setTheme('dark');
      await new Promise((resolve) => setTimeout(resolve, 10));
      service.resetToSystemPreference();
      await new Promise((resolve) => setTimeout(resolve, 10));
      expect(document.documentElement.getAttribute('data-theme')).toBeNull();
    });
  });

  describe('Persistence', () => {
    it('should persist light theme across service instances', () => {
      service.setTheme('light');
      const newService = createThemeService();
      
      expect(newService.getCurrentTheme()).toBe('light');
    });

    it('should persist dark theme across service instances', () => {
      service.setTheme('dark');
      const newService = createThemeService();
      
      expect(newService.getCurrentTheme()).toBe('dark');
    });

    it('should persist auto theme across service instances', () => {
      service.setTheme('auto');
      const newService = createThemeService();
      
      expect(newService.getCurrentTheme()).toBe('auto');
    });
  });

  describe('DOM Manipulation', () => {
    it('should set data-theme attribute on root element', async () => {
      service.setTheme('dark');
      
      await new Promise((resolve) => setTimeout(resolve, 10));
      const root = document.documentElement;
      expect(root.getAttribute('data-theme')).toBe('dark');
    });

    it('should update root element when theme changes', async () => {
      service.setTheme('light');
      
      await new Promise((resolve) => setTimeout(resolve, 10));
      expect(document.documentElement.getAttribute('data-theme')).toBe('light');

      service.setTheme('dark');
      await new Promise((resolve) => setTimeout(resolve, 10));
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    });
  });
});
