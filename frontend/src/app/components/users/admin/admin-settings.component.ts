import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AnimationName, MASCOT_LABEL, MASCOT_TYPES, MascotType } from '../../../core/mascot/pixel-mascot';
import { MascotService } from '../../../core/mascot/mascot.service';
import { AccountContextService } from '../../../core/services/account-context.service';
import { ThemeMode, ThemeService } from '../../../core/theme/theme.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ChangePasswordComponent } from '../shared/change-password/change-password.component';
import { IconComponent, IconName } from '../shared/icon/icon.component';
import { MascotComponent } from '../shared/mascot/mascot.component';

const THEMES: ReadonlyArray<{ value: ThemeMode; label: string; icon: IconName }> = [
  { value: 'light', label: 'Light', icon: 'sun' },
  { value: 'dark', label: 'Dark', icon: 'moon' },
  { value: 'auto', label: 'Auto', icon: 'monitor' }
];

/**
 * Settings for an administrator: the same look-and-feel choices clients have (theme, companion), and the
 * password. An administrator has no inbox or alerts, so there are no message preferences here. The theme and
 * companion are this browser's own.
 */
@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [IconComponent, MascotComponent, ChangePasswordComponent],
  template: `
    <section class="page settings" data-testid="admin-settings-page">
      <header class="head">
        <h1>Settings</h1>
        <p class="muted">Everything here saves as you change it.</p>
      </header>

      <article class="card section" data-testid="section-account">
        <h2>Account</h2>
        <dl class="rows">
          <div><dt>Role</dt><dd>Administrator</dd></div>
        </dl>
      </article>

      <article class="card section" data-testid="section-theme">
        <h2>Theme</h2>
        <p class="muted">Auto follows your device.</p>
        <div class="tabs" role="radiogroup" aria-label="Theme">
          @for (option of themes; track option.value) {
            <button type="button" role="radio" [class.active]="themeMode() === option.value"
                    [attr.aria-checked]="themeMode() === option.value" (click)="setTheme(option.value)"
                    [attr.data-testid]="'theme-' + option.value">
              <app-icon [name]="option.icon" [size]="16" /> {{ option.label }}
            </button>
          }
        </div>
      </article>

      <article class="card section" data-testid="section-mascot">
        <h2>Your companion</h2>
        <p class="muted">Keeps you company on every screen. Tap one to choose it.</p>
        <div class="mascots stagger" role="radiogroup" aria-label="Companion">
          @for (type of mascots; track type) {
            <button type="button" role="radio" class="mascot-card" [class.mascot-card--on]="mascot() === type"
                    [attr.aria-checked]="mascot() === type" (click)="pick(type)" (mouseenter)="hovered.set(type)"
                    (mouseleave)="hovered.set(null)" (focus)="hovered.set(type)" (blur)="hovered.set(null)"
                    [attr.data-testid]="'mascot-' + type">
              <app-mascot [type]="type" [action]="actionFor(type)" [size]="96" [interactive]="false" />
              <span>{{ mascotLabel[type] }}</span>
            </button>
          }
        </div>
      </article>

      <article class="card section" data-testid="section-password">
        <h2>Password</h2>
        <app-change-password />
      </article>
    </section>
  `,
  styleUrl: '../settings/settings.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminSettingsComponent {
  private readonly theme = inject(ThemeService);
  private readonly mascotService = inject(MascotService);
  private readonly accountContext = inject(AccountContextService);
  private readonly toast = inject(ToastService);

  protected readonly themes = THEMES;
  protected readonly mascots = MASCOT_TYPES;
  protected readonly mascotLabel = MASCOT_LABEL;
  protected readonly themeMode = signal<ThemeMode>(this.theme.getCurrentTheme());
  protected readonly mascot = this.mascotService.type;
  protected readonly hovered = signal<MascotType | null>(null);
  private readonly picked = signal<MascotType | null>(null);
  protected readonly username = computed(() => this.accountContext.getCurrentAccount().displayName || 'Administrator');

  protected setTheme(mode: ThemeMode): void {
    this.theme.setTheme(mode);
    this.themeMode.set(mode);
    this.toast.show('Theme saved');
  }

  /** Just picked: cheer. Chosen: dance. Pointed at: wave. The rest breathe. */
  protected actionFor(type: MascotType): AnimationName {
    if (this.picked() === type) {
      return 'cheer';
    }
    if (this.mascot() === type) {
      return 'dance';
    }
    return this.hovered() === type ? 'wave' : 'idle';
  }

  protected pick(type: MascotType): void {
    this.mascotService.set(type);
    this.picked.set(type);
    setTimeout(() => this.picked.set(null), 1800);
    this.toast.show(`${MASCOT_LABEL[type]} is with you`);
  }
}
