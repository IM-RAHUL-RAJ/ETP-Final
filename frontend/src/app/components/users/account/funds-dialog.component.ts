import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { AccountContextService } from '../../../core/services/account-context.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { FundsMovement, FundsService } from '../../../core/services/funds.service';
import { ToastService } from '../../../core/ui/toast.service';
import { ModalComponent } from '../shared/modal/modal.component';

function amount(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  return /^\d{1,12}(\.\d{1,2})?$/.test(value) && Number(value) > 0 ? null : { amount: true };
}

/** Add or withdraw cash: just an amount. */
@Component({
  selector: 'app-funds-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, DecimalPipe, ModalComponent],
  template: `
    <app-modal [title]="kind() === 'deposit' ? 'Add funds' : 'Withdraw funds'" [mascot]="kind() === 'deposit' ? 'cheer' : 'think'" (closed)="closed.emit()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate data-testid="funds-form">
        @if (error()) {
          <div class="banner error" role="alert" data-testid="funds-error">{{ error() }}</div>
        }

        <div class="field">
          <label for="funds-amount">Amount ({{ currency() }})</label>
          <input id="funds-amount" type="text" inputmode="decimal" formControlName="amount" autocomplete="off"
                 [class.invalid]="showAmountError()" data-testid="funds-amount" />
          @if (kind() === 'withdraw') {
            <span class="field-hint">
              Available: <strong>{{ balance() | number: '1.2-2' }} {{ currency() }}</strong>
              <button type="button" class="ghost max" (click)="useMax()" data-testid="funds-max">Max</button>
            </span>
          }
          @if (showAmountError()) {
            <span class="field-error" data-testid="funds-amount-error">{{ amountMessage() }}</span>
          }
        </div>

        <div class="modal__actions">
          <button type="button" class="secondary" (click)="closed.emit()" data-testid="funds-cancel">Cancel</button>
          <button type="submit" class="primary" [disabled]="saving()" data-testid="funds-submit">
            @if (saving()) { Working... } @else if (kind() === 'deposit') { Add funds } @else { Withdraw }
          </button>
        </div>
      </form>
    </app-modal>
  `,
  styles: [
    `
      .field {
        margin-top: 16px;
      }
      .max {
        margin-left: 8px;
        padding: 2px 8px;
      }
      .fine {
        margin-top: 16px;
        font-size: 12px;
      }
    `
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FundsDialogComponent {
  private readonly funds = inject(FundsService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessages = inject(ApiErrorMessageService);
  private readonly toast = inject(ToastService);
  private readonly formBuilder = inject(FormBuilder);

  readonly kind = input.required<'deposit' | 'withdraw'>();
  readonly balance = input.required<number>();
  readonly currency = input('');
  readonly done = output<FundsMovement>();
  readonly closed = output<void>();

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.formBuilder.nonNullable.group({
    amount: ['', [Validators.required, amount]]
  });

  protected readonly tooMuch = computed(() => {
    const value = Number(this.form.controls.amount.value);
    return this.kind() === 'withdraw' && Number.isFinite(value) && value > this.balance();
  });

  protected showAmountError(): boolean {
    const control = this.form.controls.amount;
    return (control.touched && control.invalid) || (control.dirty && this.overBalance());
  }

  protected amountMessage(): string {
    return this.overBalance()
      ? `You can withdraw up to ${this.balance().toFixed(2)} ${this.currency()}.`
      : 'Enter an amount above zero, with at most two decimals.';
  }

  protected useMax(): void {
    this.form.controls.amount.setValue(this.balance().toFixed(2));
    this.form.controls.amount.markAsDirty();
  }

  protected submit(): void {
    this.error.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.overBalance() || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    const request = { amount: Number(value.amount) };

    this.saving.set(true);
    (this.kind() === 'deposit' ? this.funds.deposit(accountId, request) : this.funds.withdraw(accountId, request)).subscribe({
      next: (movement) => {
        this.saving.set(false);
        this.toast.show(this.kind() === 'deposit' ? 'Funds added successfully' : 'Withdrawal request initiated');
        this.done.emit(movement);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.error.set(this.messageFor(error));
      }
    });
  }

  private overBalance(): boolean {
    return this.tooMuch();
  }

  /** The server explains a refused amount itself ("up to your available balance"); everything else uses the shared wording. */
  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse && (error.status === 422 || error.status === 403) && typeof error.error?.message === 'string'
      && error.error.message !== 'Invalid input') {
      return error.error.message;
    }
    return this.errorMessages.getMessage(error);
  }
}
