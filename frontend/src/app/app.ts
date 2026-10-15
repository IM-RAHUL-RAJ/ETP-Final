import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, OnInit } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { AppHeaderComponent } from './components/users/shared/app-header/app-header.component';
import { SidebarComponent } from './components/users/shared/sidebar/sidebar.component';
import { MascotDockComponent } from './components/users/shared/mascot-dock/mascot-dock.component';
import { ChatLauncherComponent } from './components/users/shared/chat-launcher/chat-launcher.component';
import { NotificationWatcher } from './core/ui/notification-watcher.service';
import { MascotService } from './core/mascot/mascot.service';
import { AccountContextService } from './core/services/account-context.service';
import { AuthService } from './core/auth/auth.service';
import { ThemeService } from './core/theme/theme.service';
import { SessionSocketService } from './core/auth/session-socket.service';
import { filter, map, startWith } from 'rxjs/operators';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [AppHeaderComponent, SidebarComponent, MascotDockComponent, ChatLauncherComponent, RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class App implements OnInit {
  private readonly themeService = inject(ThemeService);
  protected readonly auth = inject(AuthService);
  private readonly sessionSocket = inject(SessionSocketService);
  private readonly router = inject(Router);
  private readonly mascot = inject(MascotService);
  private readonly watcher = inject(NotificationWatcher);
  private readonly accountContext = inject(AccountContextService);
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event): event is NavigationEnd => event instanceof NavigationEnd),
      map(() => this.router.url),
      startWith(this.router.url)
    ),
    { initialValue: this.router.url }
  );

  /** Shown to a customer who is still on the emailed password, on every page except the one that fixes it. */
  protected readonly showPasswordBanner = computed(() => {
    const path = this.currentUrl().split('?')[0];
    return this.auth.isAuthenticated() && !this.auth.isAdmin() && this.auth.mustChangePassword() && path !== '/profile';
  });

  /** The companion is on every page except the landing page, which has its own big scene. */
  protected readonly showDock = computed(() => this.currentUrl().split("?")[0] !== "/");

  /** The chat bubble: for signed-in customers, on every page except the chat page itself. */
  protected readonly showLauncher = computed(
    () => this.showHeader() && !this.auth.isAdmin() && this.currentUrl().split('?')[0] !== '/assistant'
  );

  protected readonly showHeader = computed(() => {
    const path = this.currentUrl().split('?')[0];
    const isPublic = path === '/' || path === '/sign-in';
    return this.auth.isAuthenticated() && !isPublic;
  });

  constructor() {
    // Once a customer is signed in, learn which companion they chose, so empty screens show it.
    effect(() => {
      if (this.auth.isAuthenticated() && !this.auth.isAdmin()) {
        this.mascot.refresh(this.accountContext.getTokenAccountId());
        this.watcher.start();
      } else {
        this.watcher.stop();
      }
    });
    // Ensure theme preference is applied on every app startup/refresh,
    // regardless of which route loads first.
    this.themeService.getCurrentTheme();
  }

  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.watchTokenIntegrity();

    // Connect to session gateway if user is already authenticated
    // This covers: tab refresh, new tab, browser restart scenarios
    if (this.auth.isAuthenticated()) {
      console.log('[App] User authenticated, connecting to session gateway');
      this.sessionSocket.connect();
    }
  }

  /**
   * sessionStorage edits made in dev tools raise no event in the same tab, so the stored
   * token is re-checked on a short timer and whenever the tab regains focus.
   */
  private watchTokenIntegrity(): void {
    const check = () => this.auth.verifyStorageIntegrity();
    const timer = setInterval(check, 1500);
    window.addEventListener('focus', check);
    document.addEventListener('visibilitychange', check);
    this.destroyRef.onDestroy(() => {
      clearInterval(timer);
      window.removeEventListener('focus', check);
      document.removeEventListener('visibilitychange', check);
    });
  }
}
