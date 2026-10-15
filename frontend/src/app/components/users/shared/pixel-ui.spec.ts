import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PreferencesService } from '../../../core/services/preferences.service';
import { AccountContextService } from '../../../core/services/account-context.service';
import { NotificationService } from '../../../core/services/notification.service';
import { ThemeService } from '../../../core/theme/theme.service';
import { MascotService } from '../../../core/mascot/mascot.service';
import { ACTION_FOR, NotificationWatcher, WATCH_INTERVAL_MS } from '../../../core/ui/notification-watcher.service';
import { ToastService } from '../../../core/ui/toast.service';
import { AdminSettingsComponent } from '../admin/admin-settings.component';
import { MascotDockComponent } from './mascot-dock/mascot-dock.component';
import { SelectComponent, SelectOption } from './select/select.component';

const OPTIONS: SelectOption[] = [
  { value: 'AAPL', label: 'AAPL', hint: 'Apple Inc.', tag: 'US equity' },
  { value: 'INFY', label: 'INFY', hint: 'Infosys', tag: 'Indian equity' },
  { value: 'BTC', label: 'BTC', hint: 'Bitcoin', tag: 'Crypto' }
];

describe('SelectComponent', () => {
  @Component({
    standalone: true,
    imports: [SelectComponent, ReactiveFormsModule],
    template: '<app-select [formControl]="control" [options]="options" [allowCustom]="custom()" testId="pick" />'
  })
  class Host {
    readonly control = new FormControl('', { nonNullable: true });
    readonly options = OPTIONS;
    readonly custom = signal(false);
  }

  const build = () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const open = () => {
      (root.querySelector('[data-testid="pick"]') as HTMLElement).click();
      fixture.detectChanges();
    };
    const search = (value: string) => {
      const input = root.querySelector('[data-testid="select-search"]') as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    return { fixture, root, open, search };
  };

  it('opens a searchable list showing each instrument with its name and class', () => {
    const { root, open } = build();
    expect(root.querySelector('[data-testid="select-panel"]')).toBeNull();

    open();

    expect(root.querySelectorAll('[role="option"]').length).toBe(3);
    expect(root.querySelector('[data-testid="option-INFY"]')?.textContent).toContain('Infosys');
    expect(root.querySelector('[data-testid="option-INFY"]')?.textContent).toContain('Indian equity');
  });

  it('narrows by symbol or by name, and says so when nothing matches', () => {
    const { root, open, search } = build();
    open();

    search('bitc');
    expect(root.querySelector('[data-testid="option-BTC"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="option-AAPL"]')).toBeNull();

    search('zzz');
    expect(root.querySelector('.select__empty')).not.toBeNull();
  });

  it('writes the chosen value to the form and closes', () => {
    const { fixture, root, open } = build();
    open();

    (root.querySelector('[data-testid="option-INFY"]') as HTMLElement).dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    fixture.detectChanges();

    expect(fixture.componentInstance.control.value).toBe('INFY');
    expect(root.querySelector('[data-testid="select-panel"]')).toBeNull();
    expect(root.querySelector('[data-testid="pick"]')?.textContent).toContain('INFY');
  });

  it('works from the keyboard: arrows move, Enter picks, Escape closes', () => {
    const { fixture, root, open } = build();
    open();
    const input = root.querySelector('[data-testid="select-search"]') as HTMLInputElement;

    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    fixture.detectChanges();
    expect(fixture.componentInstance.control.value).toBe('INFY');

    open();
    (root.querySelector('[data-testid="select-search"]') as HTMLInputElement).dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="select-panel"]')).toBeNull();
  });

  it('keeps what was typed when custom values are allowed', () => {
    const { fixture, root, open, search } = build();
    fixture.componentInstance.custom.set(true);
    fixture.detectChanges();
    open();

    search('zzz');
    (root.querySelector('[data-testid="select-search"]') as HTMLInputElement).dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    fixture.detectChanges();

    expect(fixture.componentInstance.control.value).toBe('ZZZ');
  });
});

describe('ToastService actions', () => {
  it('says what the companion should do: thumbs up for good news, sad for a refusal, its own move for platform news', () => {
    const toasts = TestBed.inject(ToastService);
    toasts.show('saved');
    toasts.error('nope');
    toasts.notify('Order filled', 'BUY 10 AAPL', 'cheer');

    expect(toasts.toasts().map((toast) => toast.action)).toEqual(['happy', 'sad', 'cheer']);
    expect(toasts.toasts()[2].title).toBe('Order filled');
  });
});

describe('MascotDockComponent', () => {
  it('is not on screen until there is something to say, then acts it out, then leaves', () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(MascotDockComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="mascot-dock"]')).toBeNull();

    TestBed.inject(ToastService).notify('Order filled', 'BUY 10 AAPL', 'cheer', 'info', 3000);
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="mascot"]')?.getAttribute('data-action')).toBe('cheer');
    expect(root.querySelector('[data-testid="toast"]')?.textContent).toContain('BUY 10 AAPL');

    vi.advanceTimersByTime(3100);
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="mascot-dock"]')).toBeNull();
    vi.useRealTimers();
  });
});

describe('NotificationWatcher', () => {
  const list = vi.fn();
  const item = (id: number, kind: string) => ({ id, kind, subject: `subject ${id}`, body: `body ${id}` });

  beforeEach(() => {
    vi.useFakeTimers();
    list.mockReset();
    TestBed.configureTestingModule({
      providers: [
        { provide: NotificationService, useValue: { list } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } }
      ]
    });
  });
  afterEach(() => vi.useRealTimers());

  it('learns what is already there without announcing it, then announces only what is new, acting each out', () => {
    list.mockReturnValueOnce(of([item(5, 'ORDER_FILLED')])).mockReturnValueOnce(of([item(6, 'ORDER_REJECTED'), item(5, 'ORDER_FILLED')]));
    const watcher = TestBed.inject(NotificationWatcher);
    const toasts = TestBed.inject(ToastService);

    watcher.start();
    expect(toasts.toasts()).toEqual([]);

    vi.advanceTimersByTime(WATCH_INTERVAL_MS);

    expect(toasts.toasts().length).toBe(1);
    expect(toasts.toasts()[0].action).toBe(ACTION_FOR['ORDER_REJECTED']);
    expect(toasts.toasts()[0].tone).toBe('error');
    watcher.stop();
  });

  it('shrugs off a failed look and tries again next time', () => {
    list.mockReturnValueOnce(throwError(() => new Error('down'))).mockReturnValue(of([item(1, 'FUNDS')]));
    const watcher = TestBed.inject(NotificationWatcher);

    watcher.start();
    vi.advanceTimersByTime(WATCH_INTERVAL_MS);

    expect(TestBed.inject(ToastService).toasts()).toEqual([]);
    watcher.stop();
  });
});

describe('AdminSettingsComponent', () => {
  it('has the look-and-feel choices clients have, an account line and the password, and no message preferences', () => {
    localStorage.clear();
    const setTheme = vi.fn();
    TestBed.configureTestingModule({
      imports: [AdminSettingsComponent],
      providers: [
        { provide: ThemeService, useValue: { getCurrentTheme: () => 'light', setTheme } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ displayName: 'root.admin' }) } },
        { provide: PreferencesService, useValue: { get: vi.fn() } }
      ]
    });
    const fixture = TestBed.createComponent(AdminSettingsComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('[data-testid="admin-username"]')?.textContent).toBe('root.admin');
    expect(root.querySelector('[data-testid="section-notifications"]')).toBeNull();

    (root.querySelector('[data-testid="theme-dark"]') as HTMLElement).click();
    expect(setTheme).toHaveBeenCalledWith('dark');

    (root.querySelector('[data-testid="mascot-rabbit"]') as HTMLElement).click();
    fixture.detectChanges();
    expect(TestBed.inject(MascotService).type()).toBe('rabbit');
    expect(root.querySelector('[data-testid="mascot-rabbit"] [data-testid="mascot"]')?.getAttribute('data-action')).toBe('cheer');
  });
});
