import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AnimationName, MASCOT_LABEL, MASCOT_TYPES, MascotType } from '../../../core/mascot/pixel-mascot';
import { MascotService } from '../../../core/mascot/mascot.service';
import {
  AlertChannel,
  MascotChoice,
  NOTIFICATION_KIND_OPTIONS,
  NotificationKindKey,
  PreferencesResponse
} from '../../../core/models/preferences.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { PreferencesService } from '../../../core/services/preferences.service';
import { ThemeMode, ThemeService } from '../../../core/theme/theme.service';
import { ToastService } from '../../../core/ui/toast.service';
import { IconComponent, IconName } from '../shared/icon/icon.component';
import { MascotComponent } from '../shared/mascot/mascot.component';

const THEMES: ReadonlyArray<{ value: ThemeMode; label: string; icon: IconName }> = [
  { value: 'light', label: 'Light', icon: 'sun' },
  { value: 'dark', label: 'Dark', icon: 'moon' },
  { value: 'auto', label: 'Auto', icon: 'monitor' }
];

/** "7" becomes "••••7"; "12345" becomes "••••2345". Only the last four digits ever show. */
export function maskAccountId(id: number): string {
  const digits = String(id);
  return `••••${digits.slice(-4)}`;
}

/**
 * Settings: account details (read-only), theme, which messages to receive, where they go, the companion,
 * and the default account. Every control saves as soon as it changes and says so; there is no Save button
 * to forget. The server holds the choices; the theme is this browser's own.
 */
@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [IconComponent, MascotComponent],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SettingsComponent {
  private readonly preferencesService = inject(PreferencesService);
  private readonly accountContext = inject(AccountContextService);
  private readonly accountService = inject(AccountService);
  private readonly errorMessages = inject(ApiErrorMessageService);
  private readonly theme = inject(ThemeService);
  private readonly mascotService = inject(MascotService);
  private readonly toast = inject(ToastService);

  protected readonly themes = THEMES;
  protected readonly kindOptions = NOTIFICATION_KIND_OPTIONS;
  protected readonly mascots = MASCOT_TYPES;
  protected readonly mascotLabel = MASCOT_LABEL;

  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly preferences = signal<PreferencesResponse | null>(null);
  protected readonly accountRef = signal<string | null>(null);

  // What the controls show. Each change updates these and saves.
  protected readonly themeMode = signal<ThemeMode>(this.theme.getCurrentTheme());
  protected readonly notificationsEnabled = signal(true);
  protected readonly kinds = signal<NotificationKindKey[]>(NOTIFICATION_KIND_OPTIONS.map((option) => option.key));
  protected readonly channel = signal<AlertChannel>('EMAIL');
  protected readonly mascot = signal<MascotChoice>('OWL');
  protected readonly defaultAccountId = signal(0);
  protected readonly happyPick = signal<MascotType | null>(null);
  protected readonly hovered = signal<MascotType | null>(null);

  protected readonly accountId = computed(() => this.accountContext.getTokenAccountId());
  protected readonly accounts = computed(() => this.accountContext.getAllowedAccountIds());
  protected readonly email = computed(() => this.preferences()?.channels.find((option) => option.channel === 'EMAIL') ?? null);
  protected readonly emailAvailable = computed(() => this.email()?.available ?? false);
  protected readonly inAppOnly = computed(() => this.channel() === 'PUSH');
  /** An account that saved SMS before it was removed keeps working, but the page no longer offers it. */
  protected readonly legacySms = computed(() => this.preferences()?.alertChannel === 'SMS');

  private dirty = false;

  constructor() {
    this.load();
  }

  protected load(): void {
    const accountId = this.accountContext.getTokenAccountId();
    this.loading.set(true);
    this.loadError.set(null);

    this.preferencesService.get(accountId).subscribe({
      next: (preferences) => {
        this.apply(preferences);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loadError.set(this.errorMessages.getMessage(error));
        this.loading.set(false);
      }
    });

    // Account reference: read-only, from the account itself.
    this.accountService.getAccount(accountId).subscribe({
      next: (account) => this.accountRef.set(account.accountId),
      error: () => this.accountRef.set(null)
    });
  }

  protected setTheme(mode: ThemeMode): void {
    this.theme.setTheme(mode);
    this.themeMode.set(mode);
    this.toast.show('Theme saved');
  }

  protected toggleNotifications(): void {
    this.notificationsEnabled.update((value) => !value);
    this.persist();
  }

  protected toggleKind(key: NotificationKindKey): void {
    this.kinds.update((kinds) => (kinds.includes(key) ? kinds.filter((kind) => kind !== key) : [...kinds, key]));
    this.persist();
  }

  protected setInAppOnly(inApp: boolean): void {
    this.channel.set(inApp || !this.emailAvailable() ? 'PUSH' : 'EMAIL');
    this.persist();
  }

  /** Just picked: cheer. Chosen: dance. Pointed at: wave. The rest breathe. */
  protected actionFor(type: MascotType): AnimationName {
    if (this.happyPick() === type) {
      return 'cheer';
    }
    if (this.mascot().toLowerCase() === type) {
      return 'dance';
    }
    return this.hovered() === type ? 'wave' : 'idle';
  }

  protected pickMascot(type: MascotType): void {
    this.mascot.set(type.toUpperCase() as MascotChoice);
    this.mascotService.set(type);
    this.happyPick.set(type);
    setTimeout(() => this.happyPick.set(null), 1800);
    this.persist();
  }

  protected setDefaultAccount(value: string): void {
    this.defaultAccountId.set(Number(value));
    this.persist();
  }

  protected retry(): void {
    this.load();
  }

  /** Saves everything as it now stands. A change made while a save is running is saved right after it. */
  private persist(): void {
    if (this.saving()) {
      this.dirty = true;
      return;
    }
    this.saving.set(true);
    this.dirty = false;

    this.preferencesService
      .update(this.accountContext.getTokenAccountId(), {
        defaultAccountId: this.defaultAccountId(),
        alertChannel: this.channel(),
        mascot: this.mascot(),
        notificationsEnabled: this.notificationsEnabled(),
        notificationKinds: this.kinds()
      })
      .subscribe({
        next: (preferences) => {
          this.preferences.set(preferences);
          this.saving.set(false);
          this.toast.show('Settings saved');
          if (this.dirty) {
            this.persist();
          }
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.toast.error(this.messageFor(error));
          // Put the controls back to what the server holds, so the page never claims a choice that was not saved.
          this.load();
        }
      });
  }

  private apply(preferences: PreferencesResponse): void {
    this.preferences.set(preferences);
    this.notificationsEnabled.set(preferences.notificationsEnabled);
    this.kinds.set(preferences.notificationKinds);
    // SMS is not offered any more: show e-mail, which the next save stores.
    this.channel.set(preferences.alertChannel === 'SMS' ? 'EMAIL' : preferences.alertChannel);
    this.mascot.set(preferences.mascot);
    this.mascotService.set(preferences.mascot);
    this.defaultAccountId.set(preferences.defaultAccountId);
  }

  /** The server explains a refused choice itself; everything else uses the shared wording. */
  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 422 && typeof error.error?.message === 'string'
      && error.error.message !== 'Invalid input') {
      return error.error.message;
    }
    return this.errorMessages.getMessage(error);
  }
}
