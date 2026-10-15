import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PriceAlert } from '../../../core/models/alert.model';
import { AccountContextService } from '../../../core/services/account-context.service';
import { AlertService } from '../../../core/services/alert.service';
import { AlertDialogComponent } from './alert-dialog.component';

describe('AlertDialogComponent', () => {
  let fixture: ComponentFixture<AlertDialogComponent>;
  const createAlert = vi.fn();
  const updateAlert = vi.fn();

  const instruments = [
    { symbol: 'AAPL', name: 'Apple', exchange: 'NASDAQ', assetClass: 'US_EQUITY', currency: 'USD', tradable: true },
    { symbol: 'INFY', name: 'Infosys', exchange: 'NSE', assetClass: 'INDIAN_EQUITY', currency: 'INR', tradable: true }
  ];
  const saved: PriceAlert = {
    id: 9, symbol: 'AAPL', condition: '<', price: 200, enabled: true, status: 'ACTIVE', deliveryState: 'NONE',
    triggeredPrice: null, triggeredAt: null, createdAt: '2026-10-06T09:00:00Z', triggerCount: 0
  };

  const el = <T extends HTMLElement = HTMLElement>(id: string): T | null => fixture.nativeElement.querySelector(`[data-testid="${id}"]`);
  const type = (id: string, value: string) => {
    const input = el<HTMLInputElement>(id)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  /** Opens a select, optionally searches it, and picks an option, the way a person would. */
  const pick = (id: string, value: string, search?: string) => {
    el(id)!.click();
    fixture.detectChanges();
    if (search !== undefined) {
      type('select-search', search);
    }
    (fixture.nativeElement.querySelector('[data-testid="option-' + value + '"]') as HTMLElement).dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    fixture.detectChanges();
  };

  async function build(inputs: { alert?: PriceAlert | null; initialSymbol?: string } = {}): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [AlertDialogComponent],
      providers: [
        { provide: AlertService, useValue: { createAlert, updateAlert } },
        { provide: AccountContextService, useValue: { getCurrentAccount: () => ({ accountNumericId: 1 }) } }
      ]
    }).compileComponents();
    fixture = TestBed.createComponent(AlertDialogComponent);
    fixture.componentRef.setInput('instruments', instruments);
    if (inputs.alert !== undefined) {
      fixture.componentRef.setInput('alert', inputs.alert);
    }
    if (inputs.initialSymbol) {
      fixture.componentRef.setInput('initialSymbol', inputs.initialSymbol);
    }
    fixture.detectChanges();
  }

  beforeEach(() => {
    createAlert.mockReset().mockReturnValue(of(saved));
    updateAlert.mockReset().mockReturnValue(of(saved));
  });

  it('is a sentence: If price of [instrument] is [operator] than [price]', async () => {
    await build();

    const sentence = (fixture.nativeElement as HTMLElement).querySelector('.sentence')!.textContent!.replace(/\s+/g, ' ');
    expect(sentence).toContain('If price of');
    expect(sentence).toContain('is');
    expect(sentence).toContain('than');
  });

  it('refuses an empty form and says what is wrong, without calling the server', async () => {
    await build();

    el('alert-save')!.click();
    fixture.detectChanges();

    expect(createAlert).not.toHaveBeenCalled();
    expect(el('alert-price-error')).not.toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Choose an instrument');
  });

  it('refuses a zero, negative or non-numeric price', async () => {
    await build({ initialSymbol: 'AAPL' });

    for (const bad of ['0', '-5', 'abc', '1.23456']) {
      type('alert-price', bad);
      el('alert-save')!.click();
      fixture.detectChanges();
      expect(createAlert).not.toHaveBeenCalled();
    }
  });

  it('shows the currency of the chosen instrument as a badge', async () => {
    await build();

    pick('alert-symbol', 'INFY', 'inf');

    expect(el('alert-currency')?.textContent).toContain('INR');
    expect(el('alert-currency')?.getAttribute('data-ccy')).toBe('INR');
  });

  it('searches by symbol or name', async () => {
    await build();

    el('alert-symbol')!.click();
    fixture.detectChanges();
    type('select-search', 'infos');

    expect(el('option-INFY')).not.toBeNull();
    expect(el('option-AAPL')).toBeNull();
  });

  it('creates with exactly the symbol, the operator and the price', async () => {
    const created = vi.fn();
    await build({ initialSymbol: 'AAPL' });
    fixture.componentInstance.saved.subscribe(created);

    pick('alert-condition', '<');
    type('alert-price', '200');
    el('alert-save')!.click();
    fixture.detectChanges();

    expect(createAlert).toHaveBeenCalledWith(1, { symbol: 'AAPL', condition: '<', price: 200 });
    expect(created).toHaveBeenCalledWith(saved);
  });

  it('edits through the update call, not create', async () => {
    await build({ alert: { ...saved, condition: '>=', price: 235 } });

    type('alert-price', '240');
    el('alert-save')!.click();
    fixture.detectChanges();

    expect(updateAlert).toHaveBeenCalledWith(1, 9, { symbol: 'AAPL', condition: '>=', price: 240 });
    expect(createAlert).not.toHaveBeenCalled();
  });

  it('shows the server\'s reason when a level is refused, and stays open', async () => {
    createAlert.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 422, error: { errorCode: 'VAL-422', message: 'The price of AAPL already satisfies >= 100.' }
    })));
    const closed = vi.fn();
    await build({ initialSymbol: 'AAPL' });
    fixture.componentInstance.closed.subscribe(closed);

    type('alert-price', '100');
    el('alert-save')!.click();
    fixture.detectChanges();

    expect(el('alert-create-error')?.textContent).toContain('already satisfies');
    expect(closed).not.toHaveBeenCalled();
  });

  it('cancel closes without saving', async () => {
    const closed = vi.fn();
    await build();
    fixture.componentInstance.closed.subscribe(closed);

    el('alert-cancel')!.click();

    expect(closed).toHaveBeenCalled();
    expect(createAlert).not.toHaveBeenCalled();
  });
});
