import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ThemeService } from '../../../core/theme/theme.service';
import { BrandLogoComponent } from '../shared/brand-logo/brand-logo.component';
import { IconComponent, IconName } from '../shared/icon/icon.component';
import { MascotComponent } from '../shared/mascot/mascot.component';

interface Feature {
  title: string;
  text: string;
  icon: IconName;
}

interface Tick {
  symbol: string;
  price: string;
  change: string;
  up: boolean;
}

/** The front door: one idea, one button, and the companion dancing to say hello. */
@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [RouterLink, BrandLogoComponent, IconComponent, MascotComponent],
  templateUrl: './landing-page.html',
  styleUrl: './landing-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LandingPage {
  private readonly themeService = inject(ThemeService);
  protected readonly currentTheme = signal<'light' | 'dark'>(this.themeService.isDarkMode() ? 'dark' : 'light');

  protected readonly features: Feature[] = [
    { title: 'Order ticket', text: 'Place market and limit orders with the checks done before you press the button.', icon: 'ticket' },
    { title: 'Watchlist and alerts', text: 'Follow what you care about and be told when a price crosses your line.', icon: 'bell' },
    { title: 'Portfolio', text: 'Cash and holdings together, with the day change worked out for you.', icon: 'portfolio' }
  ];

  /** A static ribbon for show; the real prices live behind the sign-in. */
  protected readonly ticks: Tick[] = [
    { symbol: 'AAPL', price: '193.12', change: '1.2%', up: true },
    { symbol: 'MSFT', price: '330.45', change: '0.6%', up: false },
    { symbol: 'GOOGL', price: '136.80', change: '0.4%', up: true },
    { symbol: 'AMZN', price: '134.22', change: '0.9%', up: false },
    { symbol: 'TSLA', price: '272.50', change: '2.3%', up: true },
    { symbol: 'NVDA', price: '516.30', change: '0.8%', up: true },
    { symbol: 'JPM', price: '116.40', change: '0.3%', up: false },
    { symbol: 'META', price: '279.85', change: '0.2%', up: false }
  ];

  protected toggleTheme(): void {
    this.themeService.toggleTheme();
    this.currentTheme.set(this.themeService.isDarkMode() ? 'dark' : 'light');
  }
}
