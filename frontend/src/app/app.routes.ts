import { Routes } from '@angular/router';
import { adminGuard, authGuard, customerGuard } from './core/auth/auth.guard';

// Public pages stay reachable directly. Protected workspace routes run authGuard.
export const routes: Routes = [
	{
		path: '',
		loadComponent: () =>
			import('./components/users/landing/landing-page').then((m) => m.LandingPage)
	},
	{
		path: 'sign-in',
		loadComponent: () =>
			import('./components/users/login/login.component').then((m) => m.LoginComponent)
	},
	{
		path: 'admin',
		title: 'Admin',
		canActivate: [authGuard, adminGuard],
		loadComponent: () =>
			import('./components/users/admin/admin-dashboard.component').then((m) => m.AdminDashboardComponent)
	},
	{
		path: 'admin/settings',
		title: 'Settings',
		canActivate: [authGuard, adminGuard],
		loadComponent: () =>
			import('./components/users/admin/admin-settings.component').then((m) => m.AdminSettingsComponent)
	},
	{
		path: 'dashboard',
		canActivate: [authGuard, customerGuard],
		loadComponent: () =>
			import('./components/users/dashboard/dashboard.component').then((m) => m.DashboardComponent)
	},
	{
    path: 'order-ticket',
    title: 'Order ticket',
    canActivate: [authGuard, customerGuard],
    loadComponent: () =>
      import('./components/users/order-ticket/order-ticket.component').then(
        (m) => m.OrderTicketComponent
      )
  },
  {
    path: 'watchlist',
    title: 'Watchlist',
    canActivate: [authGuard, customerGuard],
    loadComponent: () =>
      import('./components/users/watchlist/watchlist.component').then(
        (m) => m.WatchlistComponent
      )
  },
  {
    path: 'blotter',
    title: 'Blotter',
    canActivate: [authGuard, customerGuard],
    loadComponent: () =>
      import('./components/users/order-history/order-history.component').then(
        (m) => m.OrderHistoryComponent
      )
  },
  {
    path: 'account',
    pathMatch: 'full',
    redirectTo: 'profile'
  },
  {
    path: 'profile',
    title: 'Profile',
    canActivate: [authGuard, customerGuard],
    loadComponent: () =>
      import('./components/users/account/account.component').then(
        (m) => m.AccountComponent
      )
  },
  {
    path: 'portfolio',
    title: 'Portfolio',
    canActivate: [authGuard, customerGuard],
    loadComponent: () =>
      import('./components/users/portfolio/portfolio.component').then(
        (m) => m.PortfolioComponent
      )
  },
  {
    path: 'alerts',
    title: 'Price alerts',
    canActivate: [authGuard, customerGuard],
    loadComponent: () =>
      import('./components/users/alerts/alerts.component').then(
        (m) => m.AlertsComponent
      )
  },
  {
    path: 'assistant',
    title: 'Ask your companion',
    canActivate: [authGuard, customerGuard],
    loadComponent: () =>
      import('./components/users/assistant/assistant.component').then((m) => m.AssistantComponent)
  },
  {
    path: 'advice',
    title: 'Trade advice',
    canActivate: [authGuard, customerGuard],
    loadComponent: () =>
      import('./components/users/advice/advice.component').then((m) => m.AdviceComponent)
  },
  {
    path: 'automation',
    title: 'Automated trading',
    canActivate: [authGuard, customerGuard],
    loadComponent: () =>
      import('./components/users/automation/automation.component').then((m) => m.AutomationComponent)
  },
  {
    path: 'notifications',
    title: 'Inbox',
    canActivate: [authGuard, customerGuard],
    loadComponent: () =>
      import('./components/users/notifications/notifications.component').then(
        (m) => m.NotificationsComponent
      )
  },
  {
    path: 'settings',
    title: 'Settings',
    canActivate: [authGuard, customerGuard],
    loadComponent: () =>
      import('./components/users/settings/settings.component').then(
        (m) => m.SettingsComponent
      )
  },
	{
		path: '**',
		redirectTo: ''
	}
];

