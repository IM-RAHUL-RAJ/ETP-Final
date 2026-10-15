import { Injectable, computed, inject, signal } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { convertAmount, isSupportedCurrency } from '../config/currency-rates';
import { InstrumentInfo } from '../models/instrument.model';
import { AccountContextService } from './account-context.service';
import { AccountService } from './account.service';
import { InstrumentService } from './instrument.service';

// Service to manage the account's home currency and each instrument's own currency.
@Injectable({ providedIn: 'root' })
export class HomeCurrencyService {
  private readonly accountService = inject(AccountService);
  private readonly instrumentService = inject(InstrumentService);
  private readonly accountContext = inject(AccountContextService);

  private loadedFor: number | null = null;

  /** The account currency, or null until it is known. */
  readonly homeCurrency = signal<string | null>(null);
  private readonly instruments = signal<ReadonlyMap<string, InstrumentInfo>>(new Map());
  readonly instrumentList = computed(() => [...this.instruments().values()]);
  readonly ready = signal(false);

  /** Safe to call from any screen: loads once per account, and again if the account changes. */
  ensureLoaded(): void {
    const accountId = this.accountContext.getCurrentAccount().accountNumericId;
    if (this.loadedFor === accountId) {
      return;
    }
    this.loadedFor = accountId;
    this.ready.set(false);

    forkJoin({
      balance: this.accountService.getBalance(accountId).pipe(catchError(() => of(null))),
      instruments: this.instrumentService.getInstruments().pipe(catchError(() => of([] as InstrumentInfo[])))
    }).subscribe(({ balance, instruments }) => {
      const code = balance?.currency?.trim().toUpperCase();
      this.homeCurrency.set(isSupportedCurrency(code) ? code : null);
      this.instruments.set(new Map(instruments.map((instrument) => [instrument.symbol, instrument])));
      this.ready.set(true);
    });
  }

  instrument(symbol: string): InstrumentInfo | undefined {
    return this.instruments().get(symbol);
  }

  currencyOf(symbol: string): string | null {
    const code = this.instruments().get(symbol)?.currency?.trim().toUpperCase();
    return isSupportedCurrency(code) ? code : null;
  }

  /** An amount quoted in the instrument's currency, expressed in the home currency. Unchanged if either is unknown. */
  toHome(amount: number, symbol: string): number {
    const home = this.homeCurrency();
    const from = this.currencyOf(symbol);
    if (!home || !from || home === from) {
      return amount;
    }
    return convertAmount(from, home, amount) ?? amount;
  }

  /** Any amount between two supported currencies. */
  convert(amount: number, from: string, to: string): number | null {
    return convertAmount(from, to, amount);
  }
}
