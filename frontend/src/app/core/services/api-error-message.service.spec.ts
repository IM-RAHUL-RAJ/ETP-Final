import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ApiErrorMessageService } from './api-error-message.service';
import { CATALOGUE_CODES, ERROR_MESSAGES, FALLBACK_MESSAGE, UNREACHABLE_MESSAGE } from './error-catalogue';

describe('ApiErrorMessageService', () => {
  let service: ApiErrorMessageService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ApiErrorMessageService);
  });

  it('maps every code in both catalogues to a message', () => {
    // The eight codes the two contracts publish, plus the backend's CUR-422.
    const contractCodes = [
      'ACC-404',
      'ACC-403',
      'INS-404',
      'ORD-400',
      'ORD-409',
      'VAL-422',
      'AUTH-401',
      'AUTH-409'
    ];

    for (const code of [...contractCodes, ...CATALOGUE_CODES]) {
      const message = service.getMessage(
        new HttpErrorResponse({ status: 400, error: { errorCode: code, message: 'developer text' } })
      );

      expect(message, code).toBe(ERROR_MESSAGES[code as keyof typeof ERROR_MESSAGES]);
      expect(message, code).not.toBe(FALLBACK_MESSAGE);
      expect(message, code).not.toContain('developer text');
    }
  });

  it('branches on the code and never shows the developer message', () => {
    const message = service.getMessage(
      new HttpErrorResponse({
        status: 400,
        error: { errorCode: 'ORD-400', message: 'InsufficientFundsException: required=1000' }
      })
    );

    expect(message).toContain('not enough cash');
    expect(message).not.toContain('InsufficientFundsException');
  });

  it('falls back to a readable sentence for an unrecognised code', () => {
    const message = service.getMessage(
      new HttpErrorResponse({ status: 500, error: { errorCode: 'UNKNOWN-CODE' } })
    );

    expect(message).toBe(FALLBACK_MESSAGE);
  });

  it('renders a readable sentence for a request that never reached a service', () => {
    const message = service.getMessage(new HttpErrorResponse({ status: 0 }));

    expect(message).toBe(UNREACHABLE_MESSAGE);
    expect(message).toContain('currently unavailable');
  });

  it('uses sign-in wording for a refused sign-in', () => {
    const message = service.getMessage(
      new HttpErrorResponse({ status: 401, error: { errorCode: 'AUTH-401', message: 'Unauthorised' } }),
      'sign-in'
    );

    expect(message).toBe('Your username or password was not accepted. Check them and try again.');
  });

  it('words the Sprint 10 codes for a customer, never the developer text', () => {
    const say = (status: number, errorCode: string) =>
      service.getMessage(new HttpErrorResponse({ status, error: { errorCode, message: 'developer text' } }));

    expect(say(503, 'MKT-503')).toContain('Prices are not available right now');
    expect(say(503, 'MKT-503')).toContain('orders are not affected');
    expect(say(409, 'ALR-409')).toContain('limit for price alerts');
    expect(say(404, 'ALR-404')).toContain('alert');
    expect(say(404, 'NTF-404')).toContain('message');
    for (const code of ['MKT-503', 'ALR-409', 'ALR-404', 'NTF-404']) {
      expect(say(400, code)).not.toContain('developer text');
    }
  });

  it('treats a 401 without an envelope as an expired session', () => {
    const message = service.getMessage(new HttpErrorResponse({ status: 401 }));

    expect(message).toBe(ERROR_MESSAGES['AUTH-401']);
  });
});
