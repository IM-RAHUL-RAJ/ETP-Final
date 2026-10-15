import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ALERT_CONDITIONS, AlertCondition, PriceAlert } from '../../../core/models/alert.model';
import { InstrumentInfo } from '../../../core/models/instrument.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AlertService } from '../../../core/services/alert.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { ModalComponent } from '../shared/modal/modal.component';
import { SelectComponent, SelectOption } from '../shared/select/select.component';

function positiveAmount(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  return /^\d+(\.\d{1,4})?$/.test(value) && Number(value) > 0 ? null : { amount: true };
}

/**
 * Create or edit an alert, written as a sentence down the page: If price of [instrument] is [operator] than [price] USD.
 * It talks to the alert API itself and tells the page what was saved.
 */
@Component({
  selector: 'app-alert-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, ModalComponent, SelectComponent],
  templateUrl: './alert-dialog.component.html',
  styleUrl: './alert-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AlertDialogComponent implements OnInit {
  private readonly alertService = inject(AlertService);
  private readonly accountContext = inject(AccountContextService);
  private readonly errorMessages = inject(ApiErrorMessageService);
  private readonly formBuilder = inject(FormBuilder);

  /** Present when editing. */
  readonly alert = input<PriceAlert | null>(null);
  readonly instruments = input.required<InstrumentInfo[]>();
  /** A symbol to start with, for "Set a price alert" links. */
  readonly initialSymbol = input('');
  readonly saved = output<PriceAlert>();
  readonly closed = output<void>();

  protected readonly conditionOptions: SelectOption[] = ALERT_CONDITIONS.map((option) => ({ value: option.value, label: option.label }));
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = this.formBuilder.nonNullable.group({
    symbol: ['', [Validators.required]],
    condition: ['>=' as AlertCondition, [Validators.required]],
    price: ['', [Validators.required, positiveAmount]]
  });

  private readonly symbolValue = toSignal(this.form.controls.symbol.valueChanges, { initialValue: '' });

  protected readonly editing = computed(() => this.alert() !== null);

  protected readonly instrumentOptions = computed<SelectOption[]>(() =>
    this.instruments().map((item) => ({ value: item.symbol, label: item.symbol, hint: item.name, tag: item.currency }))
  );

  protected readonly currency = computed(() => this.instruments().find((item) => item.symbol === this.symbolValue())?.currency ?? '');

  ngOnInit(): void {
    const existing = this.alert();
    if (existing) {
      this.form.setValue({ symbol: existing.symbol, condition: existing.condition, price: String(existing.price) });
    } else if (this.initialSymbol()) {
      this.form.controls.symbol.setValue(this.initialSymbol());
    }
  }

  protected save(): void {
    this.error.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) {
      return;
    }
    const value = this.form.getRawValue();
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    const body = { symbol: value.symbol.trim().toUpperCase(), condition: value.condition, price: Number(value.price) };
    const existing = this.alert();

    this.saving.set(true);
    const request = existing
      ? this.alertService.updateAlert(accountId, existing.id, body)
      : this.alertService.createAlert(accountId, body);
    request.subscribe({
      next: (alert) => {
        this.saving.set(false);
        this.saved.emit(alert);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.error.set(this.messageFor(error));
      }
    });
  }

  private messageFor(error: unknown): string {
    // The server says why a level is refused (already reached, too many alerts): that is worth showing as it is.
    if (error instanceof HttpErrorResponse && (error.status === 422 || error.status === 409) && typeof error.error?.message === 'string'
      && error.error.message !== 'Invalid input') {
      return error.error.message;
    }
    return this.errorMessages.getMessage(error);
  }
}
