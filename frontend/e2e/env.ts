function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is not set. Copy .env.example, export the values and run again.`);
  }
  return value;
}

/** Every address and credential the journeys use, read from the environment and nowhere else. */
export const env = {
  get baseUrl(): string {
    return required('E2E_BASE_URL');
  },
  get tradeApi(): string {
    return required('E2E_TRADE_API');
  },
  get authApi(): string {
    return required('E2E_AUTH_API');
  },
  get username(): string {
    return required('E2E_USERNAME');
  },
  get password(): string {
    return required('E2E_PASSWORD');
  },
  get accountId(): string {
    return required('E2E_ACCOUNT_ID');
  },
  get symbol(): string {
    return required('E2E_SYMBOL');
  }
};
