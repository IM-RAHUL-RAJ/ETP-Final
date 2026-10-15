import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PriceAlert } from '../../../core/models/alert.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AlertService } from '../../../core/services/alert.service';
import { HomeCurrencyService } from '../../../core/services/home-currency.service';
import { AlertsComponent } from './alerts.component';

describe('AlertsComponent', () => {
  let fixture: ComponentFixture<AlertsComponent>;
  const list = vi.fn();
  const createAlert = vi.fn();
  const updateAlert = vi.fn();
  const toggleAlert = vi.fn();
  const deleteAlert = vi.fn();
  const getTriggerHistory = vi.fn();

  const alert = (overrides: Partial<PriceAlert> = {}): PriceAlert => ({
    id: 7,
    symbol: 'AAPL',
    condition: '>=',
    price: 235,
    enabled: true,
    status: 'ACTIVE',
    deliveryState: 'NONE',
    triggeredPrice: null,
    triggeredAt: null,
    createdAt: '2026-10-06T09:00:00Z',
    triggerCount: 0,
    ...overrides
  });

  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const el = <T extends HTMLElement = HTMLElement>(testId: string): T | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  const all = (testId: string): HTMLElement[] => Array.from(fixture.nativeElement.querySelectorAll(`[data-testid="${testId}"]`));
  const settle = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  async function build(query: Record<string, string> = {}): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AlertsComponent],
      providers: [
        provideRouter([]),
        { provide: AlertService, useValue: { list, createAlert, updateAlert, toggleAlert, deleteAlert, getTriggerHistory } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
        {
          provide: HomeCurrencyService,
          useValue: {
            ensureLoaded: vi.fn(),
            currencyOf: (symbol: string) => (symbol === 'INFY' ? 'INR' : 'USD'),
            instrument: (symbol: string) => ({ symbol, name: symbol === 'AAPL' ? 'Apple' : symbol, currency: 'USD' }),
            instrumentList: signal([
              { symbol: 'AAPL', name: 'Apple', exchange: 'NASDAQ', assetClass: 'US_EQUITY', currency: 'USD', tradable: true },
              { symbol: 'MSFT', name: 'Microsoft', exchange: 'NASDAQ', assetClass: 'US_EQUITY', currency: 'USD', tradable: true }
            ])
          }
        }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(AlertsComponent);
    await settle();
  }

  beforeEach(() => {
    list.mockReset().mockReturnValue(of([alert()]));
    createAlert.mockReset();
    updateAlert.mockReset();
    toggleAlert.mockReset().mockReturnValue(of(false));
    deleteAlert.mockReset();
    getTriggerHistory.mockReset().mockReturnValue(of({ alertId: 7, triggers: [] }));
  });

  it('lists an alert as symbol, condition with its currency, and a switch', async () => {
    await build();

    expect(text()).toContain('AAPL');
    expect(el('alert-condition-text')?.textContent).toContain('≥ 235.00');
    expect(el('alert-condition-text')?.textContent).toContain('USD');
    expect(el('alert-toggle')?.getAttribute('aria-checked')).toBe('true');
    expect(el('alert-count')?.textContent).toContain('Total: 1 alert');
    expect(el('alert-status')?.textContent?.trim()).toBe('Enabled');
  });

  it('shows the companion and a call to action when there are no alerts', async () => {
    list.mockReturnValue(of([]));
    await build();

    expect(el('alerts-empty')).not.toBeNull();
    expect(el('mascot')).not.toBeNull();
    expect(text()).toContain('Create your first alert');
  });

  it('opens the dialog from the Create Alert button, with every operator on offer', async () => {
    await build();

    el('alert-create-open')!.click();
    await settle();

    expect(el('alert-form')).not.toBeNull();
    el('alert-condition')!.click();
    fixture.detectChanges();
    const options = all('select-panel').length ? Array.from(fixture.nativeElement.querySelectorAll('[data-testid^="option-"]')).map((option) => (option as HTMLElement).dataset['testid']!.slice(7)) : [];
    expect(options).toEqual(['>', '>=', '<', '<=', '==']);
  });

  it('opens straight onto the dialog when arriving from the watchlist with a symbol', async () => {
    await build({ symbol: 'msft' });

    expect(el('alert-form')).not.toBeNull();
    expect(el('alert-symbol')?.textContent).toContain('MSFT');
  });

  it('edit opens the dialog filled in, titled Edit Alert', async () => {
    await build();

    el('alert-edit')!.click();
    await settle();

    expect(text()).toContain('Edit Alert');
    expect(el('alert-symbol')?.textContent).toContain('AAPL');
    expect((el('alert-price') as HTMLInputElement).value).toBe('235');
    expect(el('alert-condition')?.textContent).toContain('Greater or equal');
    expect(el('alert-save')?.textContent).toContain('Update');
  });

  it('turning the switch updates the row at once and tells the server', async () => {
    await build();
    // After the call the page reads the list again; the server now holds the switch off.
    list.mockReturnValue(of([alert({ enabled: false })]));

    el('alert-toggle')!.click();
    fixture.detectChanges();

    expect(toggleAlert).toHaveBeenCalledWith(1, 7, false);
    expect(el('alert-toggle')?.getAttribute('aria-checked')).toBe('false');
  });

  it('puts the switch back and says why when the server refuses', async () => {
    toggleAlert.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { errorCode: 'ALR-409' } })));
    await build();

    el('alert-toggle')!.click();
    await settle();

    expect(el('alert-toggle')?.getAttribute('aria-checked')).toBe('true');
    expect(el('alert-action-error')).not.toBeNull();
  });

  it('asks before deleting, and removes the row on confirm', async () => {
    deleteAlert.mockReturnValue(of(alert()));
    await build();

    el('alert-delete')!.click();
    await settle();
    expect(el('delete-confirm')?.textContent).toContain('AAPL');
    expect(deleteAlert).not.toHaveBeenCalled();

    el('delete-confirm-button')!.click();
    await settle();

    expect(deleteAlert).toHaveBeenCalledWith(1, 7);
    expect(el('alerts-empty')).not.toBeNull();
  });

  it('keeping the alert on the confirmation changes nothing', async () => {
    await build();

    el('alert-delete')!.click();
    await settle();
    el('delete-cancel')!.click();
    await settle();

    expect(deleteAlert).not.toHaveBeenCalled();
    expect(el('delete-confirm')).toBeNull();
    expect(all('alert-toggle').length).toBe(1);
  });

  it('the history button opens the side panel with this alert\'s triggers', async () => {
    getTriggerHistory.mockReturnValue(of({
      alertId: 7,
      triggers: [{ id: 1, triggeredAt: '2026-10-07T14:05:11Z', triggerPrice: 236.5, notificationStatus: 'SENT', notificationId: 4 }]
    }));
    await build();

    el('alert-history')!.click();
    await settle();

    expect(getTriggerHistory).toHaveBeenCalledWith(1, 7);
    expect(el('trigger-panel')?.textContent).toContain('Alert: AAPL');
    expect(all('trigger-card').length).toBe(1);
    expect(el('trigger-panel')?.textContent).toContain('236.50');
    expect(el('trigger-panel')?.textContent).toContain('Sent');

    el('trigger-close')!.click();
    await settle();
    expect(el('trigger-panel')).toBeNull();
  });

  describe('filters', () => {
    const alerts = [
      alert({ id: 1, symbol: 'AAPL', enabled: true }),
      alert({ id: 2, symbol: 'MSFT', enabled: false }),
      alert({ id: 3, symbol: 'NVDA', enabled: true, status: 'TRIGGERED', deliveryState: 'HANDED_OVER', triggerCount: 2 })
    ];

    it('narrows by symbol and by condition too, and says Disabled rather than Paused', async () => {
      list.mockReturnValue(of([alert({ id: 1, symbol: 'AAPL' }), alert({ id: 2, symbol: 'MSFT', enabled: false, condition: '<' })]));
      await build();
      expect(all('alert-status').map((node) => node.textContent?.trim())).toEqual(['Enabled', 'Disabled']);

      const search = el<HTMLInputElement>('alert-search')!;
      search.value = 'msf';
      search.dispatchEvent(new Event('input'));
      await settle();
      expect(all('alert-toggle').length).toBe(1);
      expect(text()).toContain('MSFT');

      el('alert-clear-filters')!.click();
      await settle();
      el('alert-condition-filter')!.click();
      fixture.detectChanges();
      (fixture.nativeElement.querySelector('[data-testid="option-<"]') as HTMLElement).dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
      await settle();
      expect(all('alert-toggle').length).toBe(1);
      expect(text()).toContain('MSFT');
    });

    it('counts each tab and narrows the list', async () => {
      list.mockReturnValue(of(alerts));
      await build();

      expect(el('filter-all')?.textContent).toContain('3');
      expect(el('filter-enabled')?.textContent).toContain('2');
      expect(el('filter-disabled')?.textContent).toContain('1');
      expect(el('filter-triggered')?.textContent).toContain('1');

      el('filter-disabled')!.click();
      await settle();
      expect(all('alert-toggle').length).toBe(1);
      expect(text()).toContain('MSFT');

      el('filter-triggered')!.click();
      await settle();
      expect(text()).toContain('NVDA');
      expect(text()).not.toContain('MSFT');

      el('filter-enabled')!.click();
      await settle();
      expect(all('alert-toggle').length).toBe(2);
    });
  });
});
