import { HttpErrorResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ApiErrorResponse } from '../models/api-error.model';
import {
  CANCEL_MESSAGES,
  ERROR_MESSAGES,
  FALLBACK_MESSAGE,
  SIGN_IN_MESSAGES,
  UNREACHABLE_MESSAGE,
  isCatalogueCode
} from './error-catalogue';

export type ErrorContext = 'default' | 'sign-in' | 'cancel';

@Injectable({ providedIn: 'root' })
export class ApiErrorMessageService {
  getMessage(error: unknown, context: ErrorContext = 'default'): string {
    if (!(error instanceof HttpErrorResponse)) {
      return FALLBACK_MESSAGE;
    }

    if (error.status === 0) {
      return UNREACHABLE_MESSAGE;
    }

    const body = error.error as ApiErrorResponse | null;
    // A gateway or the security filter can answer 401 without the envelope.
    const code = body?.errorCode ?? (error.status === 401 ? 'AUTH-401' : undefined);

    if (!isCatalogueCode(code)) {
      return FALLBACK_MESSAGE;
    }

    if (context === 'sign-in') {
      return SIGN_IN_MESSAGES[code] ?? ERROR_MESSAGES[code];
    }

    if (context === 'cancel') {
      return CANCEL_MESSAGES[code] ?? ERROR_MESSAGES[code];
    }

    return ERROR_MESSAGES[code];
  }
}
