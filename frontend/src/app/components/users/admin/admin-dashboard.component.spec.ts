import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { AdminDashboardComponent } from './admin-dashboard.component';
import { AuthService } from '../../../core/auth/auth.service';
import { AccountService } from '../../../core/services/account.service';
import { AdminService } from '../../../core/services/admin.service';
import { OrderService } from '../../../core/services/order.service';

describe('AdminDashboardComponent', () => {
  const listAccounts = vi.fn();
  const register = vi.fn();
  const undoRegistration = vi.fn();
  const registerLogin = vi.fn();
  const getBalance = vi.fn();
  const getPositions = vi.fn();
  const getOrderHistory = vi.fn();

  const row = (accountId: number, name: string, type = 'INDIAN_EQUITY', currency = 'INR', cash = 1000) => ({
    accountId,
    accountRef: `ACC000000${accountId}`,
    clientRef: `C00000000${accountId}`,
    clientName: name,
    email: `${name.toLowerCase().replace(' ', '.')}@example.com`,
    phone: null,
    accountType: type,
    currency,
    cashBalance: cash,
    status: 'ACTIVE',
    createdAt: '2026-01-01T10:00:00Z'
  });

  beforeEach(async () => {
    listAccounts.mockReset().mockReturnValue(
      of([row(1, 'Priya Menon'), row(2, 'Arjun Rao', 'US_EQUITY', 'USD', 500)])
    );
    register.mockReset();
    undoRegistration.mockReset().mockReturnValue(of(undefined));
    registerLogin.mockReset();
    getBalance.mockReset().mockReturnValue(of({ accountId: 1, cashBalance: 1000, currency: 'INR', asOf: '' }));
    getPositions.mockReset().mockReturnValue(of([{ accountId: 1, symbol: 'AAPL', quantity: 4, averageCost: 100 }]));
    getOrderHistory.mockReset().mockReturnValue(of([]));

    await TestBed.configureTestingModule({
      imports: [AdminDashboardComponent],
      providers: [
        { provide: AdminService, useValue: { listAccounts, register, undoRegistration } },
        { provide: AccountService, useValue: { getBalance, getPositions } },
        { provide: OrderService, useValue: { getOrderHistory } },
        { provide: AuthService, useValue: { changePassword: vi.fn(), registerLogin, mustChangePassword: signal(false) } }
      ]
    }).compileComponents();
  });

  function create() {
    const fixture = TestBed.createComponent(AdminDashboardComponent);
    fixture.detectChanges();
    return fixture;
  }

  const root = (fixture: ReturnType<typeof create>) => fixture.nativeElement as HTMLElement;
  const click = (fixture: ReturnType<typeof create>, testid: string) => {
    root(fixture).querySelector<HTMLElement>(`[data-testid="${testid}"]`)!.click();
    fixture.detectChanges();
  };
  const type = (fixture: ReturnType<typeof create>, testid: string, value: string) => {
    const input = root(fixture).querySelector<HTMLInputElement>(`[data-testid="${testid}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

  it('lists every account with its client, and totals them', () => {
    const fixture = create();

    expect(root(fixture).querySelectorAll('[data-testid="admin-row"]').length).toBe(2);
    expect(root(fixture).querySelector('[data-testid="stat-accounts"]')?.textContent?.trim()).toBe('2');
    // 1,000 INR at 95 per USD plus 500 USD
    expect(root(fixture).querySelector('[data-testid="stat-cash"]')?.textContent).toContain('510.53');
  });

  it('filters by search text and account type', () => {
    const fixture = create();

    type(fixture, 'admin-search', 'arjun');
    expect(root(fixture).querySelectorAll('[data-testid="admin-row"]').length).toBe(1);

    type(fixture, 'admin-search', '');
    type(fixture, 'admin-type', 'INDIAN_EQUITY');
    expect(root(fixture).querySelectorAll('[data-testid="admin-row"]').length).toBe(1);
    expect(root(fixture).textContent).toContain('Priya Menon');
  });

  it('opens an account read only, using the account id', () => {
    const fixture = create();

    click(fixture, 'admin-row');

    expect(getBalance).toHaveBeenCalledWith(1);
    expect(getPositions).toHaveBeenCalledWith(1);
    expect(getOrderHistory).toHaveBeenCalledWith(1);
    expect(root(fixture).querySelector('[data-testid="admin-detail"]')?.textContent).toContain('AAPL');
  });

  describe('registration', () => {
    const fillValid = (fixture: ReturnType<typeof create>) => {
      type(fixture, 'reg-name', 'Priya Menon');
      type(fixture, 'reg-email', 'Priya.Menon@Example.com');
    };

    it('previews the username from the email before anything is sent', () => {
      const fixture = create();
      click(fixture, 'tab-register');

      type(fixture, 'reg-email', 'priya.menon@example.com');
      expect(root(fixture).querySelector('[data-testid="reg-username"]')?.textContent).toContain('priya.menon');

      type(fixture, 'reg-email', 'ab@example.com');
      expect(root(fixture).querySelector('[data-testid="reg-username"]')?.textContent).toContain('cannot be a username');
    });

    it('suggests the currency for the account type until one is chosen', () => {
      const fixture = create();
      click(fixture, 'tab-register');

      type(fixture, 'reg-type', 'US_EQUITY');

      expect((root(fixture).querySelector('[data-testid="reg-currency"]') as HTMLSelectElement).value).toBe('USD');
    });

    const created = {
      clientId: 5,
      clientRef: 'C000000005',
      accountId: 9,
      accountRef: 'ACC0000009',
      username: 'priya.menon',
      email: 'priya.menon@example.com'
    };

    it('opens the client and account, then creates the login, and shows the references without any password', () => {
      register.mockReturnValue(of(created));
      registerLogin.mockReturnValue(
        of({ id: 'u-1', username: 'priya.menon', accountId: 9, roles: ['CUSTOMER'], emailSent: true })
      );
      const fixture = create();
      click(fixture, 'tab-register');
      fillValid(fixture);
      type(fixture, 'reg-balance', '25000.50');

      click(fixture, 'register-submit');

      expect(register).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Priya Menon',
          email: 'Priya.Menon@Example.com',
          accountType: 'INDIAN_EQUITY',
          currency: 'INR',
          openingBalance: 25000.5
        })
      );
      // The login is made for the new account, using the username taken from the email.
      expect(registerLogin).toHaveBeenCalledWith('priya.menon', 'priya.menon@example.com', 9);
      const success = root(fixture).querySelector('[data-testid="register-success"]')?.textContent ?? '';
      expect(success).toContain('ACC0000009');
      expect(success).toContain('priya.menon');
      expect(success).toContain('emailed');
      expect(JSON.stringify(register.mock.calls[0][0])).not.toMatch(/password/i);
      expect(undoRegistration).not.toHaveBeenCalled();
      // The account list was reloaded to include the new account.
      expect(listAccounts).toHaveBeenCalledTimes(2);
    });

    it('takes the new client and account back when the login is refused as a duplicate', () => {
      register.mockReturnValue(of(created));
      registerLogin.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
      const fixture = create();
      click(fixture, 'tab-register');
      fillValid(fixture);

      click(fixture, 'register-submit');

      expect(undoRegistration).toHaveBeenCalledWith(9);
      const message = root(fixture).querySelector('[data-testid="register-error"]')?.textContent ?? '';
      expect(message).toContain('already registered');
      expect(message).toContain('Nothing was created');
      expect(root(fixture).querySelector('[data-testid="register-success"]')).toBeNull();
    });

    it('says so, with the references, when the email cannot be sent and the undo also fails', () => {
      register.mockReturnValue(of(created));
      registerLogin.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 503,
              error: { errorCode: 'AUTH-503', message: 'The welcome email could not be sent' }
            })
        )
      );
      undoRegistration.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      const fixture = create();
      click(fixture, 'tab-register');
      fillValid(fixture);

      click(fixture, 'register-submit');

      const message = root(fixture).querySelector('[data-testid="register-error"]')?.textContent ?? '';
      expect(message).toContain('welcome email could not be sent');
      expect(message).toContain('C000000005');
      expect(message).toContain('ACC0000009');
      expect(message).toContain('could not be removed');
    });

    it('does not call the auth service when the trade API refuses the client', () => {
      register.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 409,
              error: { errorCode: 'REG-409', message: 'That username or email is already registered' }
            })
        )
      );
      const fixture = create();
      click(fixture, 'tab-register');
      fillValid(fixture);

      click(fixture, 'register-submit');

      expect(registerLogin).not.toHaveBeenCalled();
      expect(undoRegistration).not.toHaveBeenCalled();
      expect(root(fixture).querySelector('[data-testid="register-error"]')?.textContent).toContain('already registered');
    });

    it('does not send an incomplete form', () => {
      const fixture = create();
      click(fixture, 'tab-register');

      click(fixture, 'register-submit');

      expect(register).not.toHaveBeenCalled();
    });
  });
});
