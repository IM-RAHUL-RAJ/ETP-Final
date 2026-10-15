// Mirrors backend/trade-api util/CurrencyConverter.java exactly
export const USD_RATES: Readonly<Record<string, number>> = {
  USD: 1,
  EUR: 0.85,
  GBP: 0.75,
  INR: 95,
  JPY: 150,
  CNY: 7.20,
  KRW: 1400,
  AUD: 1.55,
  CAD: 1.38,
  CHF: 0.80,
  HKD: 7.80,
  NZD: 1.70,
  SGD: 1.30,
  SEK: 9.50,
  NOK: 10.00,
  DKK: 6.35,
  PLN: 3.65,
  CZK: 21.50,
  HUF: 340,
  RON: 4.35,
  BGN: 1.66,
  ISK: 125,
  RSD: 100,
  TRY: 41.00,
  RUB: 80.00,
  UAH: 41.50,
  AED: 3.6725,
  SAR: 3.75,
  QAR: 3.64,
  KWD: 0.307,
  BHD: 0.376,
  OMR: 0.385,
  JOD: 0.709,
  ILS: 3.40,
  EGP: 48.00,
  ZAR: 17.50,
  NGN: 1500,
  KES: 129,
  GHS: 15.00,
  MAD: 9.80,
  TND: 2.90,
  DZD: 132,
  ETB: 140,
  TZS: 2700,
  UGX: 3500,
  RWF: 1400,
  MUR: 45.00,
  SCR: 14.00,
  BWP: 13.50,
  NAD: 17.50,
  ZMW: 23.00,
  BRL: 5.50,
  MXN: 19.00,
  ARS: 1000,
  CLP: 950,
  COP: 4200,
  PEN: 3.55,
  UYU: 40.00,
  BOB: 6.90,
  PYG: 7800,
  DOP: 60.00,
  CRC: 510,
  GTQ: 7.70,
  JMD: 160,
  BSD: 1.00,
  BBD: 2.00,
  TTD: 6.80,
  XCD: 2.70
};

const NAMES: Readonly<Record<string, string>> = {
  USD: 'US dollar',
  EUR: 'Euro',
  GBP: 'British pound',
  INR: 'Indian rupee',
  JPY: 'Japanese yen',
  CNY: 'Chinese yuan',
  KRW: 'South Korean won',
  AUD: 'Australian dollar',
  CAD: 'Canadian dollar',
  CHF: 'Swiss franc',
  HKD: 'Hong Kong dollar',
  NZD: 'New Zealand dollar',
  SGD: 'Singapore dollar',
  SEK: 'Swedish krona',
  NOK: 'Norwegian krone',
  DKK: 'Danish krone',
  AED: 'UAE dirham',
  SAR: 'Saudi riyal',
  ZAR: 'South African rand',
  BRL: 'Brazilian real',
  MXN: 'Mexican peso',
  TRY: 'Turkish lira'
};

export interface CurrencyOption {
  code: string;
  name: string;
}

/** Common currencies first, then the rest alphabetically. */
const PINNED = ['USD', 'EUR', 'GBP', 'INR', 'JPY', 'CNY', 'AUD', 'CAD', 'CHF', 'SGD', 'HKD'];

export const CURRENCIES: readonly CurrencyOption[] = [
  ...PINNED,
  ...Object.keys(USD_RATES).filter((code) => !PINNED.includes(code)).sort()
].map((code) => ({ code, name: NAMES[code] ?? code }));

export function isSupportedCurrency(code: unknown): code is string {
  return typeof code === 'string' && Object.prototype.hasOwnProperty.call(USD_RATES, code);
}

/** How many `to` you get for one `from`, as the backend computes it (via USD). */
export function rateBetween(from: string, to: string): number | null {
  const fromRate = USD_RATES[from];
  const toRate = USD_RATES[to];
  return fromRate && toRate ? toRate / fromRate : null;
}

/** Same steps as the backend: to USD at 10 decimals, then to the target, rounded to 2. */
export function convertAmount(from: string, to: string, amount: number): number | null {
  const fromRate = USD_RATES[from];
  const toRate = USD_RATES[to];
  if (!fromRate || !toRate || !Number.isFinite(amount) || amount < 0) {
    return null;
  }
  const inUsd = Math.round((amount / fromRate) * 1e10) / 1e10;
  return Math.round(inUsd * toRate * 100) / 100;
}
