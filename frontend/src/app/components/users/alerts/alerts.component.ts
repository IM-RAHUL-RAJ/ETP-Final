import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { timer } from 'rxjs';
import { ALERT_CONDITIONS, AlertCondition, MAX_ALERTS_PER_ACCOUNT, PriceAlert, conditionText } from '../../../core/models/alert.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AlertService } from '../../../core/services/alert.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { SelectComponent, SelectOption } from '../shared/select/select.component';
import { IconComponent } from '../shared/icon/icon.component';
import { ModalComponent } from '../shared/modal/modal.component';
import { AlertDialogComponent } from './alert-dialog.component';
import { TriggerHistoryPanelComponent } from './trigger-history-panel.component';

// How often the list looks again, so a fired alert shows up without a manual refresh.
export const ALERT_REFRESH_MS = 15_000;

export type AlertFilter = 'all' | 'enabled' | 'disabled' | 'triggered';

/**
 * Price alerts: "if the price of X is above Y, tell me". An alert can be switched on and off, edited and
 * deleted, and every time it fires is kept as history. This page never sends a message: a fired alert is
 * handed to notifications, and reaches the customer on the channel they chose in Settings.
 */
@Component({
  selector: 'app-alerts',
  standalone: true,
  imports: [RouterLink, SelectComponent, IconComponent, ModalComponent, EmptyStateComponent, AlertDialogComponent, TriggerHistoryPanelComponent],
  templateUrl: './alerts.component.html',
  styleUrl: './alerts.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AlertsComponent {
  private readonly alertService = inject(AlertService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessages = inject(ApiErrorMessageService);
  private readonly route = inject(ActivatedRoute);
  protected readonly home = inject(HomeCurrencyService);

  protected readonly cap = MAX_ALERTS_PER_ACCOUNT;
  protected readonly conditions = ALERT_CONDITIONS;
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly actionError = signal<string | null>(null);
  protected readonly alerts = signal<PriceAlert[]>([]);

  protected readonly filter = signal<AlertFilter>('all');
  /** More ways to narrow the list than the tabs: by symbol, and by what the alert waits for. */
  protected readonly search = signal('');
  protected readonly conditionFilter = signal<string>('');
  protected readonly conditionFilterOptions: SelectOption[] = [
    { value: '', label: 'Any condition' },
    ...ALERT_CONDITIONS.map((option) => ({ value: option.value, label: option.label }))
  ];
  protected readonly hasMoreFilters = computed(() => this.search().trim() !== '' || this.conditionFilter() !== '');

  /** The dialog: open when creating (editing null) or editing (the alert). */
  protected readonly dialogOpen = signal(false);
  protected readonly editing = signal<PriceAlert | null>(null);
  protected readonly initialSymbol = signal('');
  protected readonly deleting = signal<PriceAlert | null>(null);
  protected readonly historyOf = signal<PriceAlert | null>(null);
  protected readonly busyId = signal<number | null>(null);
  protected readonly notice = signal<string | null>(null);

  protected readonly instruments = computed(() => this.home.instrumentList().filter((item) => item.tradable));
  protected readonly remaining = computed(() => Math.max(0, this.cap - this.alerts().length));

  protected readonly enabledCount = computed(() => this.alerts().filter((alert) => alert.enabled).length);
  protected readonly disabledCount = computed(() => this.alerts().length - this.enabledCount());
  protected readonly triggeredCount = computed(() => this.alerts().filter((alert) => this.hasFired(alert)).length);

  protected readonly tabs = computed(() => [
    { id: 'all' as AlertFilter, label: 'All', count: this.alerts().length },
    { id: 'enabled' as AlertFilter, label: 'Enabled', count: this.enabledCount() },
    { id: 'disabled' as AlertFilter, label: 'Disabled', count: this.disabledCount() },
    { id: 'triggered' as AlertFilter, label: 'Triggered', count: this.triggeredCount() }
  ]);

  protected readonly visible = computed(() => {
    const filter = this.filter();
    const rows = this.alerts().filter((alert) => {
      switch (filter) {
        case 'enabled':
          return alert.enabled;
        case 'disabled':
          return !alert.enabled;
        case 'triggered':
          return this.hasFired(alert);
        default:
          return true;
      }
    });
    const term = this.search().trim().toUpperCase();
    const operator = this.conditionFilter() as AlertCondition | '';
    // Newest first, as the server sends them.
    return rows.filter(
      (alert) => (term === '' || alert.symbol.includes(term) || this.instrumentName(alert.symbol).toUpperCase().includes(term)) && (operator === '' || alert.condition === operator)
    );
  });

  constructor() {
    this.home.ensureLoaded();
    // The watchlist's "Set a price alert" link arrives with the symbol already chosen: open the dialog on it.
    const symbol = this.route.snapshot.queryParamMap.get('symbol')?.trim().toUpperCase();
    if (symbol && /^[A-Z0-9.:^=-]{1,20}$/.test(symbol)) {
      this.initialSymbol.set(symbol);
      this.dialogOpen.set(true);
    }
    this.load();
    timer(ALERT_REFRESH_MS, ALERT_REFRESH_MS).pipe(takeUntilDestroyed()).subscribe(() => this.load(true));
  }

  protected load(quiet = false): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    if (!quiet) {
      this.loading.set(true);
    }
    this.error.set(null);

    this.alertService.list(accountId).subscribe({
      next: (alerts) => {
        this.alerts.set(alerts);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        if (!quiet) {
          this.error.set(this.errorMessages.getMessage(error));
        }
        this.loading.set(false);
      }
    });
  }

  protected setFilter(filter: AlertFilter): void {
    this.filter.set(filter);
  }

  protected clearFilters(): void {
    this.search.set('');
    this.conditionFilter.set('');
  }

  protected openCreate(): void {
    this.editing.set(null);
    this.dialogOpen.set(true);
  }

  protected openEdit(alert: PriceAlert): void {
    this.editing.set(alert);
    this.dialogOpen.set(true);
  }

  protected closeDialog(): void {
    this.dialogOpen.set(false);
    this.editing.set(null);
    this.initialSymbol.set('');
  }

  protected onSaved(saved: PriceAlert): void {
    const wasEditing = this.editing() !== null;
    this.alerts.update((alerts) =>
      wasEditing ? alerts.map((alert) => (alert.id === saved.id ? saved : alert)) : [saved, ...alerts]
    );
    this.closeDialog();
    this.flash(wasEditing ? 'Alert updated.' : 'Alert created.');
  }

  /** Flips the switch at once and tells the server; if the server refuses, the switch goes back. */
  protected toggle(alert: PriceAlert): void {
    if (this.busyId() !== null) {
      return;
    }
    const enabled = !alert.enabled;
    this.busyId.set(alert.id);
    this.actionError.set(null);
    this.replace({ ...alert, enabled });

    this.alertService.toggleAlert(this.accountContext.getCurrentAccount().accountNumericId, alert.id, enabled).subscribe({
      next: () => {
        this.busyId.set(null);
        // Switching a fired alert back on arms it again, which changes its status: read the truth.
        this.load(true);
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.replace(alert);
        this.actionError.set(this.errorMessages.getMessage(error));
      }
    });
  }

  protected askDelete(alert: PriceAlert): void {
    this.deleting.set(alert);
  }

  protected confirmDelete(): void {
    const alert = this.deleting();
    if (!alert || this.busyId() !== null) {
      return;
    }
    this.busyId.set(alert.id);
    this.actionError.set(null);

    this.alertService.deleteAlert(this.accountContext.getCurrentAccount().accountNumericId, alert.id).subscribe({
      next: () => {
        this.alerts.update((alerts) => alerts.filter((entry) => entry.id !== alert.id));
        this.busyId.set(null);
        this.deleting.set(null);
        this.flash('Alert deleted.');
      },
      error: (error: unknown) => {
        this.busyId.set(null);
        this.deleting.set(null);
        this.actionError.set(this.errorMessages.getMessage(error));
        this.load(true);
      }
    });
  }

  protected showHistory(alert: PriceAlert): void {
    this.historyOf.set(alert);
  }

  protected conditionLabel(alert: PriceAlert): string {
    return conditionText(alert.condition, alert.price);
  }

  protected currencyOf(symbol: string): string {
    return this.home.currencyOf(symbol) ?? '';
  }

  /** A fired alert whose hand-over is unresolved cannot be changed yet. */
  protected locked(alert: PriceAlert): boolean {
    return alert.status === 'TRIGGERED' && (alert.deliveryState === 'PENDING_HANDOVER' || alert.deliveryState === 'HANDOVER_FAILED');
  }

  protected instrumentName(symbol: string): string {
    return this.home.instrument(symbol)?.name ?? '';
  }

  private hasFired(alert: PriceAlert): boolean {
    return alert.triggerCount > 0 || alert.status === 'TRIGGERED';
  }

  private replace(next: PriceAlert): void {
    this.alerts.update((alerts) => alerts.map((alert) => (alert.id === next.id ? next : alert)));
  }

  private flash(message: string): void {
    this.notice.set(message);
    setTimeout(() => this.notice.set(null), 4000);
  }
}
