import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { AccountContextService } from './account-context.service';

describe('AccountContextService', () => {
  let claims: Record<string, unknown> | null;

  function create(): AccountContextService {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            user: signal({ username: 'priya.menon', accountId: 1 }).asReadonly(),
            getTokenClaims: () => claims
          }
        }
      ]
    });
    return TestBed.inject(AccountContextService);
  }

  beforeEach(() => {
    sessionStorage.clear();
    claims = { accountId: 1 };
  });

  afterEach(() => sessionStorage.clear());

  it('uses the account the verified token was issued for', () => {
    expect(create().getCurrentAccount().accountNumericId).toBe(1);
  });

  it('honours an active account that the token carries', () => {
    claims = { accountId: 1, accountIds: [1, 2] };
    const context = create();

    expect(context.setActiveAccount(2)).toBe(true);

    expect(context.getCurrentAccount().accountNumericId).toBe(2);
    expect(context.getCurrentAccount().accountReference).toBe('2');
  });

  it('refuses an active account the token does not carry, and keeps the token account', () => {
    claims = { accountId: 1 };
    const context = create();

    expect(context.setActiveAccount(2)).toBe(false);

    expect(context.getCurrentAccount().accountNumericId).toBe(1);
    expect(sessionStorage.getItem('active_account_id')).toBeNull();
  });

  it('ignores a value edited into storage by hand that the token does not allow', () => {
    sessionStorage.setItem('active_account_id', '99');

    expect(create().getCurrentAccount().accountNumericId).toBe(1);
  });

  it('forgets the active account when cleared', () => {
    claims = { accountId: 1, accountIds: [1, 2] };
    const context = create();
    context.setActiveAccount(2);

    context.clearActiveAccount();

    expect(context.getCurrentAccount().accountNumericId).toBe(1);
  });

  it('always lists the token account among the allowed ones', () => {
    claims = { accountId: 1, accountIds: [2, 3] };

    expect(create().getAllowedAccountIds()).toEqual([1, 2, 3]);
  });

  it('reads the account from the nested claim shape too', () => {
    claims = { sub: { accountId: 7, username: 'kim' } };
    const context = create();

    expect(context.getTokenAccountId()).toBe(7);
    expect(context.getCurrentAccount().displayName).toBe('kim');
  });
});