import { Injectable, inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

export interface AccountContext {
  accountNumericId: number;
  accountReference: string;
  displayName: string;
}
@Injectable({ providedIn: 'root' })
export class AccountContextService {
  private readonly auth = inject(AuthService);
  private readonly activeAccountKey = 'active_account_id';

  getCurrentAccount(): AccountContext {
    const user = this.auth.user();
    const claims = this.auth.getTokenClaims();
    const tokenAccountId = this.getTokenAccountId();
    const effectiveAccountId = this.readActiveAccount() ?? (tokenAccountId > 0 ? tokenAccountId : (user?.accountId ?? 0));
    const claimUsername = typeof claims?.username === 'string'
      ? claims.username
      : (typeof claims?.sub?.username === 'string' ? claims.sub.username : null);

    return {
      accountNumericId: effectiveAccountId,
      accountReference: effectiveAccountId > 0 ? String(effectiveAccountId) : 'Unknown',
      displayName: claimUsername ?? user?.username ?? ''
    };
  }

  /** The account the verified token was issued for: always allowed, and the one preferences are read under. */
  getTokenAccountId(): number {
    const claims = this.auth.getTokenClaims();
    const claimAccountId = Number(claims?.accountId ?? claims?.sub?.accountId ?? 0);
    return Number.isFinite(claimAccountId) && claimAccountId > 0 ? claimAccountId : 0;
  }

  /** Every account this token may use. */
  getAllowedAccountIds(): number[] {
    const claims = this.auth.getTokenClaims();
    const listed: unknown = claims?.accountIds;
    const ids = Array.isArray(listed)
      ? listed.map((id) => Number(id)).filter((id) => Number.isInteger(id) && id > 0)
      : [];
    const own = this.getTokenAccountId();
    if (own > 0 && !ids.includes(own)) {
      ids.unshift(own);
    }
    return ids;
  }

  /** Makes `accountId` the account the application works on. Returns false (and changes nothing) if the token does not carry it. */
  setActiveAccount(accountId: number): boolean {
    if (!this.getAllowedAccountIds().includes(accountId)) {
      return false;
    }
    try {
      if (accountId === this.getTokenAccountId()) {
        sessionStorage.removeItem(this.activeAccountKey);
      } else {
        sessionStorage.setItem(this.activeAccountKey, String(accountId));
      }
    } catch {
      // Storage blocked: the session just keeps using the token's own account.
    }
    return true;
  }

  clearActiveAccount(): void {
    try {
      sessionStorage.removeItem(this.activeAccountKey);
    } catch {
      // Nothing to clear.
    }
  }

  private readActiveAccount(): number | null {
    try {
      const raw = sessionStorage.getItem(this.activeAccountKey);
      const id = raw === null ? NaN : Number(raw);
      return Number.isInteger(id) && this.getAllowedAccountIds().includes(id) ? id : null;
    } catch {
      return null;
    }
  }
}