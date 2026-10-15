import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ANIMATIONS, ANIMATION_NAMES, MASCOT_TYPES, frameSvg } from '../../../core/mascot/pixel-mascot';
import { MascotService } from '../../../core/mascot/mascot.service';
import { AuthService } from '../../../core/auth/auth.service';
import { NotificationService } from '../../../core/services/notification.service';
import { PreferencesService } from '../../../core/services/preferences.service';
import { AccountContextService } from '../../../core/services/account-context.service';
import { FundsService } from '../../../core/services/funds.service';
import { ShellStateService } from '../../../core/ui/shell-state.service';
import { ToastService } from '../../../core/ui/toast.service';
import { timeAgo } from '../../../core/ui/time-ago.pipe';
import { FundsDialogComponent } from '../account/funds-dialog.component';
import { AppHeaderComponent, avatarColorOf, initialsOf } from './app-header/app-header.component';
import { EmptyStateComponent } from './empty-state/empty-state.component';
import { MascotComponent } from './mascot/mascot.component';
import { ModalComponent } from './modal/modal.component';
import { SidebarComponent } from './sidebar/sidebar.component';

describe('mascot artwork', () => {
  it('draws every mascot in every animation as pixel art, and every animation has frames to move through', () => {
    for (const type of MASCOT_TYPES) {
      for (const name of ANIMATION_NAMES) {
        expect(ANIMATIONS[name].length).toBeGreaterThan(1);
        const svg = frameSvg(type, name, 0);
        expect(svg.startsWith('<svg')).toBe(true);
        expect(svg).toContain('role="img"');
        expect(svg).toContain('crispEdges');
      }
    }
  });

  it('shows no hand gestures: arms are plain wings, there is no thumb or fingers', () => {
    for (const type of MASCOT_TYPES) {
      for (const name of ANIMATION_NAMES) {
        for (const frame of ANIMATIONS[name]) {
          expect(['down', 'out', 'up']).toContain(frame.armL);
          expect(['down', 'out', 'up']).toContain(frame.armR);
        }
      }
      expect(frameSvg(type, 'happy', 0)).not.toContain('thumb');
    }
  });

  it('moves: the frames of a dance are not all the same picture', () => {
    const frames = new Set(ANIMATIONS['dance'].map((_, index) => frameSvg('owl', 'dance', index)));
    expect(frames.size).toBeGreaterThan(1);
  });
});

describe('MascotComponent', () => {
  let fixture: ComponentFixture<MascotComponent>;

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [MascotComponent],
      providers: [{ provide: PreferencesService, useValue: { get: vi.fn() } }]
    }).compileComponents();
    fixture = TestBed.createComponent(MascotComponent);
  });

  it('draws the customer\'s own companion when none is given', () => {
    TestBed.inject(MascotService).set('cat');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="mascot"]').dataset.type).toBe('cat');
    expect(fixture.nativeElement.querySelector('svg')).not.toBeNull();
  });

  it('draws the one it is told to, whatever the customer chose', () => {
    TestBed.inject(MascotService).set('cat');
    fixture.componentRef.setInput('type', 'rabbit');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="mascot"]').dataset.type).toBe('rabbit');
  });

  it('plays the animation it is asked for, else the one its mood implies', () => {
    fixture.componentRef.setInput('action', 'cheer');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="mascot"]').dataset.action).toBe('cheer');

    fixture.componentRef.setInput('action', null);
    fixture.componentRef.setInput('state', 'happy');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="mascot"]').dataset.action).toBe('happy');
  });

  it('dances and says something when tapped, and has no button when it is only decoration', () => {
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.mascot__btn').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="mascot"]').dataset.action).toBe('dance');
    expect(fixture.nativeElement.querySelector('.mascot__thought')).not.toBeNull();

    const plain = TestBed.createComponent(MascotComponent);
    plain.componentRef.setInput('interactive', false);
    plain.detectChanges();
    expect(plain.nativeElement.querySelector('.mascot__btn')).toBeNull();
  });

  it('is labelled for screen readers', () => {
    fixture.componentRef.setInput('type', 'owl');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('svg').getAttribute('aria-label')).toContain('owl');
  });
});

describe('MascotService', () => {
  it('ignores a value it does not know and remembers one it does', () => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [{ provide: PreferencesService, useValue: { get: vi.fn() } }] });
    const service = TestBed.inject(MascotService);

    service.set('dragon');
    expect(service.type()).toBe('owl');

    service.set('RABBIT');
    expect(service.type()).toBe('rabbit');
    expect(localStorage.getItem('quorum-mascot')).toBe('rabbit');
  });

  it('takes the server\'s choice after sign-in and keeps its own when the server cannot be reached', () => {
    localStorage.clear();
    const get = vi.fn().mockReturnValue(of({ mascot: 'CAT' }));
    TestBed.configureTestingModule({ providers: [{ provide: PreferencesService, useValue: { get } }] });
    const service = TestBed.inject(MascotService);

    service.refresh(1);
    expect(service.type()).toBe('cat');

    get.mockReturnValue(throwError(() => new Error('down')));
    service.refresh(1);
    expect(service.type()).toBe('cat');
  });
});

describe('EmptyStateComponent', () => {
  it('shows the companion, the words and whatever is projected as the next step', () => {
    @Component({
      standalone: true,
      imports: [EmptyStateComponent],
      template: '<app-empty-state title="No orders yet" message="Start trading."><button id="go">Place order</button></app-empty-state>'
    })
    class Host {}
    TestBed.configureTestingModule({ providers: [{ provide: PreferencesService, useValue: { get: vi.fn() } }] });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('h2')?.textContent).toBe('No orders yet');
    expect(root.textContent).toContain('Start trading.');
    expect(root.querySelector('#go')).not.toBeNull();
    expect(root.querySelector('[data-testid="mascot"]')?.getAttribute('data-state')).toBe('empty');
  });
});

describe('ModalComponent', () => {
  @Component({
    standalone: true,
    imports: [ModalComponent],
    template: '<app-modal title="Hello" (closed)="closed.set(closed() + 1)"><button id="inside">OK</button></app-modal>'
  })
  class Host {
    readonly closed = signal(0);
  }

  it('is a labelled dialog, closes on Escape, on the close button and on the backdrop, but not on a click inside', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    const dialog = root.querySelector('[role="dialog"]')!;
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(root.querySelector(`#${dialog.getAttribute('aria-labelledby')}`)?.textContent).toBe('Hello');

    dialog.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    expect(fixture.componentInstance.closed()).toBe(0);

    root.querySelector('[data-testid="modal-backdrop"]')!.dispatchEvent(new MouseEvent('mousedown'));
    expect(fixture.componentInstance.closed()).toBe(1);

    (root.querySelector('[data-testid="modal-close"]') as HTMLElement).click();
    expect(fixture.componentInstance.closed()).toBe(2);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(fixture.componentInstance.closed()).toBe(3);
  });

  it('moves focus in on open', () => {
    const fixture = TestBed.createComponent(Host);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();

    expect(fixture.nativeElement.contains(document.activeElement)).toBe(true);
    fixture.nativeElement.remove();
  });
});

describe('time ago', () => {
  const now = Date.parse('2026-10-07T12:00:00Z');

  it('speaks in minutes, hours and days, and falls back to the date after a month', () => {
    expect(timeAgo('2026-10-07T11:59:50Z', now)).toBe('just now');
    expect(timeAgo('2026-10-07T11:55:00Z', now)).toBe('5 minutes ago');
    expect(timeAgo('2026-10-07T11:00:00Z', now)).toBe('1 hour ago');
    expect(timeAgo('2026-10-05T12:00:00Z', now)).toBe('2 days ago');
    expect(timeAgo('2026-06-01T12:00:00Z', now)).toMatch(/2026/);
    expect(timeAgo(null, now)).toBe('');
    expect(timeAgo('not a date', now)).toBe('');
  });
});

describe('ToastService', () => {
  it('shows a message, then lets it go, and never piles up more than three', () => {
    vi.useFakeTimers();
    const toasts = TestBed.inject(ToastService);

    toasts.show('one');
    toasts.show('two');
    toasts.show('three');
    toasts.show('four');
    expect(toasts.toasts().map((toast) => toast.message)).toEqual(['two', 'three', 'four']);

    vi.advanceTimersByTime(4600);
    expect(toasts.toasts()).toEqual([]);
    vi.useRealTimers();
  });
});

describe('ShellStateService', () => {
  it('remembers whether the sidebar is collapsed', () => {
    localStorage.clear();
    const shell = TestBed.inject(ShellStateService);
    expect(shell.collapsed()).toBe(false);

    shell.toggleCollapsed();
    expect(shell.collapsed()).toBe(true);
    expect(localStorage.getItem('quorum-sidebar-collapsed')).toBe('true');
  });

  it('opens and closes the phone drawer', () => {
    const shell = TestBed.inject(ShellStateService);
    shell.openDrawer();
    expect(shell.drawerOpen()).toBe(true);
    shell.closeDrawer();
    expect(shell.drawerOpen()).toBe(false);
  });
});

describe('avatar', () => {
  it('is the first letter of the name', () => {
    expect(initialsOf('jane.doe')).toBe('J');
    expect(initialsOf('Jane Q Doe')).toBe('J');
    expect(initialsOf('trader01')).toBe('T');
    expect(initialsOf('')).toBe('?');
  });

  it('gives the same person the same colour', () => {
    expect(avatarColorOf('jane')).toBe(avatarColorOf('jane'));
    expect(avatarColorOf('jane')).toMatch(/^hsl\(\d+ 42% 38%\)$/);
  });
});

describe('navigation shell', () => {
  const isAdmin = signal(false);
  const logout = vi.fn();

  beforeEach(() => {
    localStorage.clear();
    isAdmin.set(false);
    logout.mockReset();
    TestBed.configureTestingModule({
      imports: [SidebarComponent, AppHeaderComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAdmin, mustChangePassword: signal(false), user: signal({ username: 'jane.doe' }), isAuthenticated: signal(true), logout } },
        { provide: NotificationService, useValue: { unread: signal(3), refreshUnread: () => of(3) } },
        { provide: PreferencesService, useValue: { get: vi.fn() } }
      ]
    });
  });

  it('the sidebar has an icon and a label for each page, with the unread count on the inbox', () => {
    const fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    const labels = Array.from(root.querySelectorAll('.nav-item__label')).map((node) => node.textContent!.trim());
    expect(labels).toEqual(expect.arrayContaining(['Dashboard', 'Orders', 'Portfolio', 'Watchlist', 'Alerts']));
    // the inbox lives in the top bar now, not in the side menu
    expect(labels).not.toContain('Inbox');
    expect(root.querySelectorAll('.nav-item app-icon').length).toBeGreaterThanOrEqual(6);
  });

  it('the top bar has the inbox with its unread count', () => {
    const fixture = TestBed.createComponent(AppHeaderComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('[data-testid="inbox-link"]')?.getAttribute('href')).toBe('/notifications');
    expect(root.querySelector('[data-testid="unread-badge"]')?.textContent).toBe('3');
  });

  it('collapses to icons, hands the label to the hover tip, and expands again', () => {
    const fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const sidebar = root.querySelector('[data-testid="sidebar"]')!;
    expect(sidebar.classList.contains('sidebar--collapsed')).toBe(false);
    expect(root.querySelector('[data-testid="nav-dashboard"]')?.getAttribute('data-tip')).toBeNull();

    (root.querySelector('[data-testid="sidebar-toggle"]') as HTMLElement).click();
    fixture.detectChanges();

    expect(sidebar.classList.contains('sidebar--collapsed')).toBe(true);
    expect(root.querySelector('[data-testid="nav-dashboard"]')?.getAttribute('data-tip')).toBe('Dashboard');
    expect(root.querySelector('[data-testid="sidebar-toggle"]')?.getAttribute('aria-expanded')).toBe('false');
  });

  it('an admin sees the admin page only', () => {
    isAdmin.set(true);
    const fixture = TestBed.createComponent(SidebarComponent);
    fixture.detectChanges();

    const labels = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.nav-item__label')).map((node) => node.textContent!.trim());
    expect(labels).toEqual(['Admin', 'Settings']);
  });

  it('the top bar has a settings gear and an avatar, and no theme control', () => {
    const fixture = TestBed.createComponent(AppHeaderComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('[data-testid="settings-link"]')?.getAttribute('aria-label')).toBe('Settings');
    expect(root.querySelector('[data-testid="settings-link"]')?.getAttribute('href')).toBe('/settings');
    expect(root.querySelector('[data-testid="avatar-button"]')?.textContent?.trim()).toBe('J');
    expect(root.querySelector('[data-testid="avatar-button"]')?.getAttribute('data-tip')).toBe('jane.doe');
    expect(root.querySelector('app-theme-toggle')).toBeNull();
  });

  it('the avatar menu offers Profile and Sign out, and signing out calls the auth service', () => {
    const fixture = TestBed.createComponent(AppHeaderComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('[data-testid="avatar-menu"]')).toBeNull();

    (root.querySelector('[data-testid="avatar-button"]') as HTMLElement).click();
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="menu-profile"]')).not.toBeNull();

    (root.querySelector('[data-testid="sign-out"]') as HTMLElement).click();
    expect(logout).toHaveBeenCalled();
  });
});

describe('FundsDialogComponent', () => {
  let fixture: ComponentFixture<FundsDialogComponent>;
  const deposit = vi.fn();
  const withdraw = vi.fn();
  const show = vi.fn();

  const movement = { id: 'm1', accountId: 1, kind: 'DEPOSIT', amount: 250, currency: 'USD', method: 'UPI', balanceAfter: 1250, createdAt: '2026-10-07T10:00:00Z' };
  const el = <T extends HTMLElement = HTMLElement>(id: string): T | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  const type = (id: string, value: string) => {
    const input = el<HTMLInputElement>(id)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  function build(kind: 'deposit' | 'withdraw', balance = 1000) {
    TestBed.configureTestingModule({
      imports: [FundsDialogComponent],
      providers: [
        { provide: FundsService, useValue: { deposit, withdraw } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } },
        { provide: ToastService, useValue: { show } }
      ]
    });
    fixture = TestBed.createComponent(FundsDialogComponent);
    fixture.componentRef.setInput('kind', kind);
    fixture.componentRef.setInput('balance', balance);
    fixture.componentRef.setInput('currency', 'USD');
    fixture.detectChanges();
  }

  beforeEach(() => {
    deposit.mockReset().mockReturnValue(of(movement));
    withdraw.mockReset().mockReturnValue(of({ ...movement, kind: 'WITHDRAWAL', balanceAfter: 900 }));
    show.mockReset();
  });

  it('asks for an amount and nothing else: no payment method, no simulation note', () => {
    build('deposit');

    expect(el('funds-method')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('simulation');
  });

  it('adds funds with just the amount, then says so and reports the new balance', () => {
    build('deposit');
    const done = vi.fn();
    fixture.componentInstance.done.subscribe(done);

    type('funds-amount', '250');
    el('funds-submit')!.click();
    fixture.detectChanges();

    expect(deposit).toHaveBeenCalledWith(1, { amount: 250 });
    expect(show).toHaveBeenCalledWith('Funds added successfully');
    expect(done).toHaveBeenCalledWith(movement);
  });

  it('refuses zero, negative and non-numeric amounts', () => {
    build('deposit');

    for (const bad of ['0', '-5', 'abc', '10.999', '']) {
      type('funds-amount', bad);
      el('funds-submit')!.click();
      fixture.detectChanges();
    }

    expect(deposit).not.toHaveBeenCalled();
    expect(el('funds-amount-error')).not.toBeNull();
  });

  it('shows the available cash on a withdrawal, fills it in on Max, and says "request initiated"', () => {
    build('withdraw', 1000);
    expect(fixture.nativeElement.textContent).toContain('1,000.00 USD');

    el('funds-max')!.click();
    fixture.detectChanges();
    expect((el('funds-amount') as HTMLInputElement).value).toBe('1000.00');

    el('funds-submit')!.click();
    expect(withdraw).toHaveBeenCalledWith(1, { amount: 1000 });
    expect(show).toHaveBeenCalledWith('Withdrawal request initiated');
  });

  it('will not withdraw more than the balance', () => {
    build('withdraw', 100);

    type('funds-amount', '100.01');
    el('funds-submit')!.click();
    fixture.detectChanges();

    expect(withdraw).not.toHaveBeenCalled();
    expect(el('funds-amount-error')?.textContent).toContain('up to 100.00 USD');
  });

  it('shows the server\'s reason and stays open when it refuses', () => {
    withdraw.mockReturnValue(throwError(() => ({ status: 422, error: { message: 'You can withdraw up to your available cash balance' } })));
    build('withdraw', 1000);

    type('funds-amount', '50');
    el('funds-submit')!.click();
    fixture.detectChanges();

    expect(show).not.toHaveBeenCalled();
    expect(el('funds-error')).not.toBeNull();
  });
});
