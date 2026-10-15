import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, switchMap, timer } from 'rxjs';
import { AuthService } from '../../../../core/auth/auth.service';
import { NotificationService } from '../../../../core/services/notification.service';
import { ShellStateService } from '../../../../core/ui/shell-state.service';
import { BrandLogoComponent } from '../brand-logo/brand-logo.component';
import { IconComponent, IconName } from '../icon/icon.component';

interface NavigationItem {
  label: string;
  path: string;
  icon: IconName;
  /** Show the unread-message count beside this item. */
  unread?: boolean;
}

/** How often the unread badge looks for new messages. */
export const UNREAD_REFRESH_MS = 30_000;

/**
 * The left navigation: icons with labels, collapsing to icons only (64px) and opening to 240px.
 * On a phone it is a drawer over the page. The width change is a CSS transition, so it is
 * smooth without any animation code, and it respects reduced-motion.
 */
@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, BrandLogoComponent, IconComponent],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SidebarComponent {
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  protected readonly shell = inject(ShellStateService);

  protected readonly unread = this.notifications.unread;
  protected readonly collapsed = this.shell.collapsed;
  protected readonly drawerOpen = this.shell.drawerOpen;

  private readonly customerItems: NavigationItem[] = [
    { label: 'Dashboard', path: '/dashboard', icon: 'dashboard' },
    { label: 'Order ticket', path: '/order-ticket', icon: 'ticket' },
    { label: 'Orders', path: '/blotter', icon: 'orders' },
    { label: 'Portfolio', path: '/portfolio', icon: 'portfolio' },
    { label: 'Watchlist', path: '/watchlist', icon: 'star' },
    { label: 'Alerts', path: '/alerts', icon: 'bell' },
    { label: 'Advice', path: '/advice', icon: 'candles' },
    { label: 'Automation', path: '/automation', icon: 'clock' }
  ];

  private readonly adminItems: NavigationItem[] = [
    { label: 'Admin', path: '/admin', icon: 'shield' },
    { label: 'Settings', path: '/admin/settings', icon: 'settings' }
  ];

  protected readonly navigationItems = computed(() => (this.auth.isAdmin() ? this.adminItems : this.customerItems));

  private readonly navigated = toSignal(
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)),
    { initialValue: null }
  );

  constructor() {
    // Keep the inbox badge fresh while the customer works. An admin has no inbox.
    if (!this.auth.isAdmin()) {
      timer(0, UNREAD_REFRESH_MS)
        .pipe(
          switchMap(() => this.notifications.refreshUnread()),
          takeUntilDestroyed()
        )
        .subscribe();
    }
    // Choosing a page closes the phone drawer.
    effect(() => {
      if (this.navigated()) {
        this.shell.closeDrawer();
      }
    });
  }
}
