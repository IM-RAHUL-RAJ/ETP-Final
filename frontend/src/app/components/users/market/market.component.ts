import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MarketInstrumentRow } from '../../../core/models/account.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { ApiErrorMessageService } from '../../../core/services/api-error-message.service';
import { MarketService } from '../../../core/services/market.service';

@Component({
  selector: 'app-market',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './market.component.html',
  styleUrl: './market.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MarketComponent {
  private readonly marketService = inject(MarketService);
  private readonly accountContext = inject(AccountContextService);
  private readonly apiErrorMessageService = inject(ApiErrorMessageService);
  private readonly formBuilder = inject(FormBuilder);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly instruments = signal<MarketInstrumentRow[]>([]);
  readonly conversionResult = signal<number | null>(null);
  readonly conversionError = signal<string | null>(null);

  readonly conversionForm = this.formBuilder.nonNullable.group({
    fromCurrency: ['USD', Validators.required],
    toCurrency: ['EUR', Validators.required],
    amount: ['', [Validators.required, Validators.pattern(/^\d+(\.\d+)?$/)]]
  });

  constructor() {
    this.loadMarketData();
  }

  loadMarketData(): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;

    this.loading.set(true);
    this.error.set(null);

    this.marketService.getMarketOverview(accountId).subscribe({
      next: (rows) => {
        this.instruments.set(rows);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(this.apiErrorMessageService.getMessage(error));
        this.loading.set(false);
      }
    });
  }

  convertCurrency(): void {
    this.conversionError.set(null);
    this.conversionResult.set(null);
    this.conversionForm.markAllAsTouched();

    if (this.conversionForm.invalid) {
      this.conversionError.set('Enter a valid amount greater than zero.');
      return;
    }

    this.marketService
      .convertCurrency({
        fromCurrency: this.conversionForm.controls.fromCurrency.value,
        toCurrency: this.conversionForm.controls.toCurrency.value,
        amount: Number(this.conversionForm.controls.amount.value)
      })
      .subscribe({
        next: (response) => {
          this.conversionResult.set(response.convertedAmount);
        },
        error: (error: unknown) => {
          this.conversionError.set(this.apiErrorMessageService.getMessage(error));
        }
      });
  }
}

