import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../../core/auth/auth.service';
import { MascotService } from '../../../../core/mascot/mascot.service';
import { ShellStateService } from '../../../../core/ui/shell-state.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { IconComponent } from '../icon/icon.component';
import { MascotComponent } from '../mascot/mascot.component';

/** "jane.doe" or "Jane Doe" becomes "JD"; one word gives one letter. */
export function initialsOf(name: string): string {
  return name.trim().charAt(0).toUpperCase() || '?';
}

/** A calm colour from the name, so the same person always gets the same avatar. White text stays readable on all of them. */
export function avatarColorOf(name: string): string {
  let hash = 0;
  for (const char of name) {
    hash = (hash * 31 + char.charCodeAt(0)) % 360;
  }
  return `hsl(${hash} 42% 38%)`;
}

/**
 * The bar above the page: the menu button on a phone, a small companion, the settings gear and the
 * avatar with its menu (Profile, Sign out). The theme choice is not here; it lives in Settings.
 */
@Component({
  selector: 'app-app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, IconComponent, MascotComponent],
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AppHeaderComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly shell = inject(ShellStateService);
  protected readonly mascot = inject(MascotService);

  protected readonly mustChangePassword = this.auth.mustChangePassword;
  protected readonly isAdmin = this.auth.isAdmin;
  /** The unread count, kept fresh by the side menu's poll. */
  protected readonly unread = inject(NotificationService).unread;
  protected readonly menuOpen = signal(false);

  protected readonly displayName = computed(() => this.auth.user()?.username ?? 'Account');
  protected readonly initials = computed(() => initialsOf(this.displayName()));
  protected readonly avatarColor = computed(() => avatarColorOf(this.displayName()));

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected openProfile(): void {
    this.menuOpen.set(false);
    void this.router.navigate(['/profile']);
  }

  protected signOut(): void {
    this.menuOpen.set(false);
    this.auth.logout();
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    if (this.menuOpen() && !this.host.nativeElement.querySelector('.avatar')?.contains(event.target as Node)) {
      this.menuOpen.set(false);
    }
  }

  @HostListener('keydown.escape')
  protected onEscape(): void {
    this.menuOpen.set(false);
  }
}
