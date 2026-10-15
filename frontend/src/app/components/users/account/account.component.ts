import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AccountResponse, BalanceResponse, PositionResponse } from '../../../core/models/account.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';
import { AllocationSlice } from '../../../core/stats/portfolio-stats';
import { ChangePasswordComponent } from '../shared/change-password/change-password.component';
import { AnimatedNumberComponent } from '../shared/animated-number/animated-number.component';
import { DonutChartComponent } from '../shared/donut-chart/donut-chart.component';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { IconComponent } from '../shared/icon/icon.component';
import { MascotComponent } from '../shared/mascot/mascot.component';
import { ModalComponent } from '../shared/modal/modal.component';
import { FundsMovement } from '../../../core/services/funds.service';
import { FundsDialogComponent } from './funds-dialog.component';

function assetClassLabel(assetClass: string): string {
  const labels: Record<string, string> = {
    US_EQUITY: 'US equity',
    INDIAN_EQUITY: 'Indian equity',
    FX: 'FX',
    CRYPTO: 'Crypto',
    UNKNOWN: 'Other'
  };
  return labels[assetClass] ?? assetClass.replaceAll('_', ' ').toLowerCase();
}

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [CommonModule, RouterLink, AnimatedNumberComponent, DonutChartComponent, ChangePasswordComponent, EmptyStateComponent, IconComponent, MascotComponent, ModalComponent, FundsDialogComponent],
  templateUrl: './account.component.html',
  styleUrl: './account.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AccountComponent {
  private readonly accountService = inject(AccountService);
  private readonly accountContext = inject(AccountContextService);
  private readonly apiErrorMessageService = inject(ApiErrorMessageService);
  protected readonly home = inject(HomeCurrencyService);

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly account = signal<AccountResponse | null>(null);
  protected readonly balance = signal<BalanceResponse | null>(null);
  protected readonly positions = signal<PositionResponse[]>([]);
  protected readonly showChangePassword = signal(false);
  /** The funds dialog: which one is open, if any. */
  protected readonly fundsDialog = signal<'deposit' | 'withdraw' | null>(null);

  /** The first letter of the sign-in name, which is what the customer knows themselves by. */
  protected readonly initial = computed(() => {
    const name = this.accountContext.getCurrentAccount().displayName || this.account()?.holderName || '?';
    return name.trim().charAt(0).toUpperCase() || '?';
  });
  protected readonly hhiInfoOpen = signal(false);

  /** Cost of what is held. The API gives average cost, so this is cost, not market value. */
  protected readonly invested = computed(() =>
    this.positions().reduce(
      (sum, position) => sum + this.home.toHome(position.quantity * position.averageCost, position.symbol),
      0
    )
  );

  protected readonly rows = computed(() => {
    const total = this.invested();
    return [...this.positions()]
      .sort((left, right) => left.symbol.localeCompare(right.symbol))
      .map((position) => {
        const averageCostHome = this.home.toHome(position.averageCost, position.symbol);
        const cost = this.home.toHome(position.quantity * position.averageCost, position.symbol);
        return { ...position, averageCostHome, cost, share: total > 0 ? (cost / total) * 100 : 0 };
      });
  });

  protected readonly assetClassSlices = computed<AllocationSlice[]>(() => {
    const totals = new Map<string, number>();
    for (const position of this.positions()) {
      const cost = this.home.toHome(position.quantity * position.averageCost, position.symbol);
      if (cost <= 0) {
        continue;
      }
      const assetClass = this.home.instrument(position.symbol)?.assetClass ?? 'UNKNOWN';
      totals.set(assetClass, (totals.get(assetClass) ?? 0) + cost);
    }

    const costed = [...totals.entries()]
      .map(([assetClass, cost]) => ({ label: assetClassLabel(assetClass), cost }))
      .sort((left, right) => right.cost - left.cost);

    const total = costed.reduce((sum, slice) => sum + slice.cost, 0);
    if (total === 0) {
      return [];
    }

    return costed.map((slice) => ({ ...slice, share: (slice.cost / total) * 100 }));
  });

  /** Herfindahl-Hirschman Index over holdings by cost share (0..10000): lower means more diversified. */
  protected readonly concentrationHhi = computed(() =>
    this.rows().reduce((sum, row) => sum + row.share * row.share, 0)
  );

  protected readonly concentrationBand = computed(() => {
    const hhi = this.concentrationHhi();
    if (hhi < 1500) {
      return 'Diversified';
    }
    if (hhi < 2500) {
      return 'Moderately concentrated';
    }
    return 'Highly concentrated';
  });

  constructor() {
    this.home.ensureLoaded();
    this.loadAccount();
  }

  protected openFunds(kind: 'deposit' | 'withdraw'): void {
    this.fundsDialog.set(kind);
  }

  /** A movement went through: show the balance it left behind at once, then close. */
  protected onFundsDone(movement: FundsMovement): void {
    this.balance.update((balance) =>
      balance ? { ...balance, cashBalance: movement.balanceAfter, asOf: movement.createdAt } : balance
    );
    this.fundsDialog.set(null);
  }

  protected toggleHhiInfo(): void {
    this.hhiInfoOpen.update((open) => !open);
  }

  protected toggleChangePassword(): void {
    this.showChangePassword.update((shown) => !shown);
  }

  protected handlePasswordChanged(): void {
    this.showChangePassword.set(false);
  }

  protected loadAccount(): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;

    this.loading.set(true);
    this.error.set(null);

    forkJoin({
      account: this.accountService.getAccount(accountId),
      balance: this.accountService.getBalance(accountId),
      positions: this.accountService.getPositions(accountId)
    }).subscribe({
      next: (result) => {
        this.account.set(result.account);
        this.balance.set(result.balance);
        this.positions.set(result.positions);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(this.apiErrorMessageService.getMessage(error));
        this.loading.set(false);
      }
    });
  }
}
