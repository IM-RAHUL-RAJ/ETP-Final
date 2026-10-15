import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AccountService } from '../../../core/services/account.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';
import { AccountComponent } from './account.component';

describe('AccountComponent (profile)', () => {
  async function build() {
    await TestBed.configureTestingModule({
      imports: [AccountComponent],
      providers: [
        provideRouter([]),
        {
          provide: AccountService,
          useValue: {
            getAccount: () => of({ id: 1, accountId: 'ACC-1', holderName: 'Jane Doe', status: 'ACTIVE', lastUpdated: '2026-10-07T10:00:00Z' }),
            getBalance: () => of({ accountId: 1, cashBalance: 100, currency: 'USD', asOf: '2026-10-07T10:00:00Z' }),
            getPositions: () => of([{ accountId: 1, symbol: 'AAPL', quantity: 10, averageCost: 100 }])
          }
        },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1, displayName: 'trader.joe' }) } },
        { provide: HomeCurrencyService, useValue: { ensureLoaded: vi.fn(), toHome: (amount: number) => amount, instrument: () => ({ assetClass: 'US_EQUITY' }), homeCurrency: () => 'USD' } }
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(AccountComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }
  const el = (fixture: { nativeElement: HTMLElement }, id: string) => fixture.nativeElement.querySelector(`[data-testid="${id}"]`) as HTMLElement | null;

  it('shows the first letter of the username in the circle', async () => {
    const fixture = await build();

    expect(el(fixture, 'account-avatar')?.textContent?.trim()).toBe('T');
  });

  it('has prominent Add funds and Withdraw buttons', async () => {
    const fixture = await build();

    expect(el(fixture, 'add-funds')?.classList.contains('lg')).toBe(true);
    expect(el(fixture, 'withdraw-funds')?.classList.contains('lg')).toBe(true);
  });

  it('hides the HHI ranges until the i is clicked, then shows them with the link', async () => {
    const fixture = await build();
    expect(el(fixture, 'hhi-popover')).toBeNull();

    el(fixture, 'hhi-info')!.click();
    fixture.detectChanges();

    const popover = el(fixture, 'hhi-popover')!;
    expect(popover.textContent).toContain('1,500');
    expect(popover.textContent).toContain('2,500');
    expect(popover.querySelector('a')?.getAttribute('href')).toContain('investopedia.com');
  });

  it('opens the funds dialog with just an amount', async () => {
    const fixture = await build();

    el(fixture, 'add-funds')!.click();
    fixture.detectChanges();

    expect(el(fixture, 'funds-amount')).not.toBeNull();
    expect(el(fixture, 'funds-method')).toBeNull();
  });
});
