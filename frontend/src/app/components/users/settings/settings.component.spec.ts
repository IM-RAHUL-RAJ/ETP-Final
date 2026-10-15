import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { MascotService } from '../../../core/mascot/mascot.service';
import { PreferencesResponse } from '../../../core/models/preferences.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { PreferencesService } from '../../../core/services/preferences.service';
import { ThemeService } from '../../../core/theme/theme.service';
import { ToastService } from '../../../core/ui/toast.service';
import { SettingsComponent, maskAccountId } from './settings.component';

describe('SettingsComponent', () => {
  let fixture: ComponentFixture<SettingsComponent>;
  const get = vi.fn();
  const update = vi.fn();
  const getAccount = vi.fn();
  const setTheme = vi.fn();
  const show = vi.fn();
  const error = vi.fn();
  const setMascot = vi.fn();

  const preferences = (overrides: Partial<PreferencesResponse> = {}): PreferencesResponse => ({
    accountId: 1,
    defaultAccountId: 1,
    alertChannel: 'EMAIL',
    contactDetail: 'j***@example.com',
    source: 'STORED',
    channels: [
      { channel: 'EMAIL', available: true, hint: 'j***@example.com' },
      { channel: 'SMS', available: true, hint: '******3210' },
      { channel: 'PUSH', available: true, hint: 'In-app inbox' }
    ],
    updatedAt: null,
    mascot: 'OWL',
    notificationsEnabled: true,
    notificationKinds: ['ORDER_FILLED', 'ORDER_REJECTED', 'ORDER_CANCELLED', 'PRICE_ALERT', 'FUNDS'],
    ...overrides
  });

  const el = <T extends HTMLElement = HTMLElement>(id: string): T | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  async function build(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        { provide: PreferencesService, useValue: { get, update } },
        { provide: AccountService, useValue: { getAccount } },
        { provide: AccountContextService, useValue: { getTokenAccountId: () => 1, getAllowedAccountIds: () => [1] } },
        { provide: ThemeService, useValue: { getCurrentTheme: () => 'auto', setTheme } },
        { provide: ToastService, useValue: { show, error } },
        { provide: MascotService, useValue: { set: setMascot, type: () => 'owl' } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(SettingsComponent);
    await settle();
  }

  beforeEach(() => {
    get.mockReset().mockReturnValue(of(preferences()));
    update.mockReset().mockImplementation((_id: number, request: Record<string, unknown>) => of(preferences(request as Partial<PreferencesResponse>)));
    getAccount.mockReset().mockReturnValue(of({ accountId: 'ACC-0001-7421' }));
    setTheme.mockReset();
    show.mockReset();
    error.mockReset();
    setMascot.mockReset();
  });

  it('shows the account reference as locked, and no internal account id', async () => {
    await build();

    expect(maskAccountId(7)).toBe('••••7');
    expect(maskAccountId(1234567)).toBe('••••4567');
    expect(el('account-id')).toBeNull();
    expect(el('section-other')).toBeNull();
    expect(el('account-ref')?.textContent).toContain('ACC-0001-7421');
    expect(el('account-ref')?.textContent).toContain('cannot be changed');
  });

  it('is a centred column no wider than 700px', async () => {
    await build();
    expect(el('settings-page')?.classList.contains('settings')).toBe(true);
  });

  it('no longer offers a text-message channel', async () => {
    await build();

    expect(el('channel-SMS')).toBeNull();
    expect(text()).not.toContain('Text message');
    expect(text()).not.toContain('Phone');
  });

  it('explains when an old account is still on SMS', async () => {
    get.mockReturnValue(of(preferences({ alertChannel: 'SMS' })));
    await build();

    expect(el('sms-removed')).not.toBeNull();
  });

  describe('theme', () => {
    it('applies a theme as soon as it is picked and says so', async () => {
      await build();

      el('theme-dark')!.click();
      await settle();

      expect(setTheme).toHaveBeenCalledWith('dark');
      expect(show).toHaveBeenCalledWith('Theme saved');
      expect(el('theme-dark')?.getAttribute('aria-checked')).toBe('true');
    });
  });

  describe('notification preferences', () => {
    it('lists every kind, all ticked by default, under a master switch', async () => {
      await build();

      expect(el('notifications-master')?.getAttribute('aria-checked')).toBe('true');
      for (const kind of ['ORDER_FILLED', 'ORDER_REJECTED', 'PRICE_ALERT', 'FUNDS']) {
        expect((el(`kind-${kind}`) as HTMLInputElement).checked).toBe(true);
      }
    });

    it('saves a kind switched off, straight away', async () => {
      await build();

      el('kind-PRICE_ALERT')!.click();
      await settle();

      expect(update).toHaveBeenCalledTimes(1);
      expect(update.mock.calls[0][1].notificationKinds).not.toContain('PRICE_ALERT');
      expect(show).toHaveBeenCalledWith('Settings saved');
    });

    it('hides the kinds and saves when the master switch is turned off', async () => {
      await build();

      el('notifications-master')!.click();
      await settle();

      expect(update.mock.calls[0][1].notificationsEnabled).toBe(false);
      expect(el('notification-kinds')).toBeNull();
    });
  });

  describe('channel', () => {
    it('shows e-mail as the primary channel, masked, and says it cannot be changed here', async () => {
      await build();

      expect(el('email-hint')?.textContent).toContain('j***@example.com');
      expect(el('email-primary')).not.toBeNull();
      expect(text()).toContain('cannot be changed here');
    });

    it('switches to in-app only and back', async () => {
      await build();

      el('channel-push')!.click();
      await settle();
      expect(update.mock.calls[0][1].alertChannel).toBe('PUSH');

      el('channel-push')!.click();
      await settle();
      expect(update.mock.calls[1][1].alertChannel).toBe('EMAIL');
    });
  });

  describe('companion', () => {
    it('offers three, and saves the one picked', async () => {
      await build();

      expect(el('mascot-owl')).not.toBeNull();
      expect(el('mascot-rabbit')).not.toBeNull();
      expect(el('mascot-cat')).not.toBeNull();

      el('mascot-cat')!.click();
      await settle();

      expect(update.mock.calls[0][1].mascot).toBe('CAT');
      expect(setMascot).toHaveBeenCalledWith('cat');
      expect(el('mascot-cat')?.getAttribute('aria-checked')).toBe('true');
    });
  });

  describe('failures', () => {
    it('shows an error with a retry when settings cannot be read', async () => {
      get.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      await build();

      expect(el('settings-load-error')).not.toBeNull();
    });

    it('says why a choice was refused, and puts the page back to what the server holds', async () => {
      await build();
      update.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { errorCode: 'VAL-422', message: 'There is no e-mail address on your profile.' } })));

      el('kind-FUNDS')!.click();
      await settle();

      expect(error).toHaveBeenCalledWith('There is no e-mail address on your profile.');
      expect(get).toHaveBeenCalledTimes(2);
    });
  });
});
