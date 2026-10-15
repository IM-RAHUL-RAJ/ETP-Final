// One place for every sentence a trader can be shown for an API failure.

//Codes in the two contracts (trade-api catalogue, auth-api ErrorResponse) plus the backend's CUR-422. 
export const CATALOGUE_CODES = [
  'ACC-404',
  'ACC-403',
  'INS-404',
  'ORD-400',
  'ORD-409',
  'VAL-422',
  'AUTH-401',
  'AUTH-409',
  'CUR-422',
  'MKT-503',
  'NTF-404',
  'ALR-404',
  'ALR-409'
] as const;

// Raised by this application itself when a feature has no backend endpoint.
export const CLIENT_CODES = ['API-NOT-AVAILABLE'] as const;

export type CatalogueCode = (typeof CATALOGUE_CODES)[number] | (typeof CLIENT_CODES)[number];

export const ERROR_MESSAGES: Record<CatalogueCode, string> = {
  'ACC-404': 'We could not find that account. Sign in again, or contact support if it keeps happening.',
  'ACC-403':
    'This account is not active, or it is not the one this sign-in may trade. Contact support to check its status.',
  'INS-404': 'That symbol is not one we can trade. Check the symbol and try again.',
  'ORD-400': 'There is not enough cash for this order. Reduce the quantity or add funds.',
  'ORD-409':
    'You do not hold enough of this instrument to sell that quantity, or this order has already been placed. Check the blotter before trying again.',
  'VAL-422': 'One of the fields is not acceptable. Check each field and try again.',
  'AUTH-401': 'Your session has expired or the sign-in was refused. Sign in again.',
  'AUTH-409': 'That username is already taken. Choose another one, or sign in instead.',
  'CUR-422': 'That currency pair is not supported. Pick a different pair and try again.',
  'MKT-503':
    'Prices are not available right now, so your holdings cannot be valued. Your orders are not affected. Try again in a moment.',
  'NTF-404': 'We could not find that message. Refresh your inbox.',
  'ALR-404': 'We could not find that alert. It may already have been removed.',
  'ALR-409':
    'You have reached the limit for price alerts, or this alert is still being delivered. Remove a finished alert and try again.',
  'API-NOT-AVAILABLE': 'This feature is not available yet because the platform does not expose it.'
};

// On the sign-in form the same code reads differently: the customer is not "expired", they were refused. 
export const SIGN_IN_MESSAGES: Partial<Record<CatalogueCode, string>> = {
  'AUTH-401': 'Your username or password was not accepted. Check them and try again.'
};

// Cancelling reads differently: ORD-409 there means the order is no longer working, not a holdings problem. 
export const CANCEL_MESSAGES: Partial<Record<CatalogueCode, string>> = {
  'ORD-409':
    'This order can no longer be cancelled. It has already been filled, rejected or cancelled, or it just changed. Refresh the blotter to see where it stands.',
  'ACC-403': 'This sign-in cannot cancel orders on this account.'
};

// The request never got a response: a service that is down, or a CORS rule that blocks this origin. 
export const UNREACHABLE_MESSAGE =
  'The trading service is currently unavailable. Check your connection and try again in a moment.';

// A code nobody has mapped yet. Never a blank panel. 
export const FALLBACK_MESSAGE = "We couldn't complete your request. Please try again.";

export function isCatalogueCode(code: unknown): code is CatalogueCode {
  return typeof code === 'string' && Object.prototype.hasOwnProperty.call(ERROR_MESSAGES, code);
}
