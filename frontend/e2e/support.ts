import { APIRequestContext, Page, expect } from '@playwright/test';
import { env } from './env';

/** Fails fast, with a clear message, when the real stack is not up. */
export async function assertStackIsUp(request: APIRequestContext): Promise<void> {
  const auth = await request.get(`${env.authApi}/health`);
  expect(auth.ok(), `auth service is not reachable at ${env.authApi}`).toBe(true);

  const trade = await request.get(`${env.tradeApi}/actuator/health`);
  expect(trade.ok(), `Trade REST API is not reachable at ${env.tradeApi}`).toBe(true);
}

/** Signs in through the form using stable test identifiers, never label text. */
export async function signIn(page: Page, username = env.username, password = env.password): Promise<void> {
  await page.getByTestId('username-input').fill(username);
  await page.getByTestId('password-input').fill(password);
  await page.getByTestId('submit-button').click();
}
