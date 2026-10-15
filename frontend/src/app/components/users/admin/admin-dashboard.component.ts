import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { forkJoin, of, throwError } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { CURRENCIES, convertAmount } from '../../../core/config/currency-rates';
import {
  AccountTypeName,
  AdminAccountSummary,
  GenderName,
  RegistrationRequest,
  RegistrationResponse,
  RegistrationResult,
  RiskProfileName
} from '../../../core/models/admin.model';
import { BalanceResponse, PositionResponse } from '../../../core/models/account.model';
import { OrderHistoryEntry } from '../../../core/models/order.model';
import { AuthService } from '../../../core/auth/auth.service';
import { AccountService } from '../../../core/services/account.service';
import { AdminService } from '../../../core/services/admin.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { OrderService } from '../../../core/services/order.service';
import { StatusBadgeComponent } from '../shared/status-badge/status-badge.component';
import { ChangePasswordComponent } from '../shared/change-password/change-password.component';

type Tab = 'accounts' | 'register' | 'security';

const USERNAME_PATTERN = /^[a-z0-9._-]{3,64}$/;

/** The currency an account of each type usually holds. A starting suggestion, not a rule. */
const DEFAULT_CURRENCY: Record<AccountTypeName, string> = {
  INDIAN_EQUITY: 'INR',
  US_EQUITY: 'USD',
  FOREX: 'USD',
  CRYPTO: 'USD'
};

function optionalPhone(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  return value === '' || /^[0-9]{10}$/.test(value) ? null : { phone: true };
}

function optionalAmount(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  if (value === '') {
    return null;
  }
  return /^\d+(\.\d{1,2})?$/.test(value) && Number(value) <= 99_999_999.99 ? null : { amount: true };
}

function optionalPastDate(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value;
  return value === '' || new Date(value) < new Date() ? null : { future: true };
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, StatusBadgeComponent, ChangePasswordComponent],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminDashboardComponent {
  private readonly adminService = inject(AdminService);
  private readonly authService = inject(AuthService);
  private readonly accountService = inject(AccountService);
  private readonly orderService = inject(OrderService);
  private readonly errorMessages = inject(ApiErrorMessageService);
  private readonly formBuilder = inject(FormBuilder);

  protected readonly currencies = CURRENCIES;
  protected readonly accountTypes: AccountTypeName[] = ['INDIAN_EQUITY', 'US_EQUITY', 'FOREX', 'CRYPTO'];
  protected readonly riskProfiles: RiskProfileName[] = ['CONSERVATIVE', 'MODERATE', 'AGGRESSIVE'];
  protected readonly genders: GenderName[] = ['MALE', 'FEMALE', 'OTHER'];

  protected readonly tab = signal<Tab>('accounts');

  // ---- All accounts, read only ---------------------------------------------------------
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly accounts = signal<AdminAccountSummary[]>([]);
  protected readonly search = signal('');
  protected readonly typeFilter = signal('');
  protected readonly statusFilter = signal('');

  protected readonly visible = computed(() => {
    const text = this.search().trim().toLowerCase();
    const type = this.typeFilter();
    const status = this.statusFilter();
    return this.accounts().filter(
      (row) =>
        (type === '' || row.accountType === type) &&
        (status === '' || row.status === status) &&
        (text === '' ||
          [row.clientName, row.email, row.accountRef, row.clientRef, row.phone ?? '']
            .some((value) => value.toLowerCase().includes(text)))
    );
  });

  protected readonly stats = computed(() => {
    const rows = this.accounts();
    return {
      total: rows.length,
      active: rows.filter((row) => row.status === 'ACTIVE').length,
      // Cash in different currencies is shown as one number by taking it all to USD at the platform's own rates.
      cashUsd: rows.reduce((sum, row) => sum + (convertAmount(row.currency, 'USD', row.cashBalance) ?? 0), 0)
    };
  });

  // ---- One account's detail, read only -------------------------------------------------
  protected readonly selected = signal<AdminAccountSummary | null>(null);
  protected readonly detailLoading = signal(false);
  protected readonly detailError = signal<string | null>(null);
  protected readonly balance = signal<BalanceResponse | null>(null);
  protected readonly positions = signal<PositionResponse[]>([]);
  protected readonly orders = signal<OrderHistoryEntry[]>([]);
  protected readonly recentOrders = computed(() =>
    [...this.orders()].sort((a, b) => Date.parse(b.createdOn) - Date.parse(a.createdOn)).slice(0, 8)
  );

  // ---- Registration --------------------------------------------------------------------
  protected readonly form = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    phone: ['', [optionalPhone]],
    dateOfBirth: ['', [optionalPastDate]],
    gender: [''],
    accountType: ['INDIAN_EQUITY' as AccountTypeName, [Validators.required]],
    currency: ['INR', [Validators.required]],
    openingBalance: ['', [optionalAmount]],
    riskProfile: ['']
  });

  private readonly formValue = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  private currencyChosen = false;

  /** The login the customer will get: the part of the email before the @. Shown before it is created. */
  protected readonly usernamePreview = computed(() => {
    const email = (this.formValue().email ?? '').trim().toLowerCase();
    if (!email.includes('@')) {
      return null;
    }
    const username = email.split('@')[0];
    return { username, valid: USERNAME_PATTERN.test(username) };
  });

  protected readonly submitting = signal(false);
  protected readonly registerError = signal<string | null>(null);
  protected readonly registered = signal<RegistrationResult | null>(null);
  protected readonly today = new Date().toISOString().slice(0, 10);

  constructor() {
    this.loadAccounts();

    this.form.controls.accountType.valueChanges.subscribe((type) => {
      if (!this.currencyChosen) {
        this.form.controls.currency.setValue(DEFAULT_CURRENCY[type]);
      }
    });
  }

  protected setTab(tab: Tab): void {
    this.tab.set(tab);
  }

  protected loadAccounts(): void {
    this.loading.set(true);
    this.error.set(null);
    this.adminService.listAccounts().subscribe({
      next: (rows) => {
        this.accounts.set(rows);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(this.errorMessages.getMessage(error));
        this.loading.set(false);
      }
    });
  }

  protected clearFilters(): void {
    this.search.set('');
    this.typeFilter.set('');
    this.statusFilter.set('');
  }

  /** Opens an account's balance, holdings and orders. Admin access is read only. */
  protected select(row: AdminAccountSummary): void {
    if (this.selected()?.accountId === row.accountId) {
      this.selected.set(null);
      return;
    }
    this.selected.set(row);
    this.detailLoading.set(true);
    this.detailError.set(null);
    this.balance.set(null);
    this.positions.set([]);
    this.orders.set([]);

    forkJoin({
      balance: this.accountService.getBalance(row.accountId),
      positions: this.accountService.getPositions(row.accountId).pipe(catchError(() => of([] as PositionResponse[]))),
      orders: this.orderService.getOrderHistory(row.accountId).pipe(catchError(() => of([] as OrderHistoryEntry[])))
    }).subscribe({
      next: ({ balance, positions, orders }) => {
        if (this.selected()?.accountId !== row.accountId) {
          return;
        }
        this.balance.set(balance);
        this.positions.set(positions);
        this.orders.set(orders);
        this.detailLoading.set(false);
      },
      error: (error: unknown) => {
        this.detailError.set(this.errorMessages.getMessage(error));
        this.detailLoading.set(false);
      }
    });
  }

  protected chooseCurrency(): void {
    this.currencyChosen = true;
  }

  protected fieldError(name: 'name' | 'email' | 'phone' | 'dateOfBirth' | 'openingBalance'): string | null {
    const control = this.form.controls[name];
    if (!control.touched || control.valid) {
      return null;
    }
    switch (name) {
      case 'name':
        return 'Enter the customer\'s full name.';
      case 'email':
        return 'Enter a valid email address.';
      case 'phone':
        return 'Use exactly 10 digits, or leave it empty.';
      case 'dateOfBirth':
        return 'The date of birth must be in the past.';
      default:
        return 'Use an amount up to 99,999,999.99 with at most two decimals.';
    }
  }

  protected register(): void {
    this.form.markAllAsTouched();
    this.registered.set(null);
    this.registerError.set(null);

    const preview = this.usernamePreview();
    if (this.form.invalid || this.submitting()) {
      return;
    }
    if (preview && !preview.valid) {
      this.registerError.set(
        'The part of the email before the @ becomes the username, so it must be 3 to 64 letters, numbers, dots, underscores or hyphens.'
      );
      return;
    }

    const value = this.form.getRawValue();
    const request: RegistrationRequest = {
      name: value.name.trim(),
      email: value.email.trim(),
      accountType: value.accountType,
      currency: value.currency,
      ...(value.phone.trim() ? { phone: value.phone.trim() } : {}),
      ...(value.dateOfBirth ? { dateOfBirth: value.dateOfBirth } : {}),
      ...(value.gender ? { gender: value.gender as GenderName } : {}),
      ...(value.openingBalance.trim() ? { openingBalance: Number(value.openingBalance) } : {}),
      ...(value.riskProfile ? { riskProfile: value.riskProfile as RiskProfileName } : {})
    };

    this.submitting.set(true);
    // Step 1 at the trade API (client + account), step 2 at the auth service (login + emailed
    // password). If step 2 fails, step 1 is taken back so nothing half-made is left behind.
    this.adminService
      .register(request)
      .pipe(
        switchMap((created) =>
          this.authService.registerLogin(created.username, created.email, created.accountId).pipe(
            map((login): RegistrationResult => ({ ...created, emailSent: login.emailSent })),
            catchError((loginError: unknown) => this.rollBack(created, loginError))
          )
        )
      )
      .subscribe({
      next: (response) => {
        this.registered.set(response);
        this.submitting.set(false);
        this.currencyChosen = false;
        this.form.reset({
          name: '',
          email: '',
          phone: '',
          dateOfBirth: '',
          gender: '',
          accountType: 'INDIAN_EQUITY',
          currency: 'INR',
          openingBalance: '',
          riskProfile: ''
        });
        this.loadAccounts();
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.registerError.set(this.registrationMessage(error));
      }
    });
  }

  /**
   * The login could not be created, so remove the client and account that were just made, then
   * report both what went wrong and what was left. The undo is refused for anything but a
   * brand-new, unused account, so it cannot touch an established one.
   */
  private rollBack(created: RegistrationResponse, loginError: unknown) {
    const reason = this.loginFailureMessage(loginError);
    return this.adminService.undoRegistration(created.accountId).pipe(
      map(() => null),
      catchError(() => of('failed')),
      switchMap((undo) =>
        throwError(
          () =>
            new Error(
              undo === null
                ? `${reason} Nothing was created.`
                : `${reason} The client ${created.clientRef} and account ${created.accountRef} were created but could not be removed automatically.`
            )
        )
      )
    );
  }

  /** What the auth service said, in words for the admin. */
  private loginFailureMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 409) {
        return 'That username or email is already registered.';
      }
      if (error.status === 503) {
        const message: unknown = error.error?.message;
        return typeof message === 'string' ? `${message}.` : 'The welcome email could not be sent.';
      }
      if (error.status === 0) {
        return 'The sign-in service could not be reached.';
      }
      return 'The sign-in service refused the registration.';
    }
    return 'The sign-in service did not answer in time.';
  }

  /** The service explains REG-* refusals itself (duplicate, email trouble); everything else uses the shared wording. */
  private registrationMessage(error: unknown): string {
    // A failure of the login step, already worded and already rolled back.
    if (error instanceof Error && !(error instanceof HttpErrorResponse)) {
      return error.message;
    }
    if (error instanceof HttpErrorResponse) {
      const code: unknown = error.error?.errorCode;
      if (typeof code === 'string' && code.startsWith('REG-') && typeof error.error?.message === 'string') {
        return error.error.message;
      }
      if (error.status === 403) {
        return 'Only an admin can register customers.';
      }
      if (error.status === 422) {
        return 'Some of the details are not valid. Check the form and try again.';
      }
    }
    return this.errorMessages.getMessage(error);
  }
}
