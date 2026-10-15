import { expect, test } from '@playwright/test';
import { env } from './env';
import { assertStackIsUp, signIn } from './support';

// Journey 1: sign in. Every test starts from a fresh browser context, signed out, and leaves
// nothing behind for another test to depend on.
test.describe('sign in', () => {
  test.beforeAll(async ({ request }) => {
    await assertStackIsUp(request);
  });

  test('a signed-out visitor sent to a guarded route is redirected to sign-in with the return address', async ({
    page
  }) => {
    await page.goto('/blotter');

    await expect(page).toHaveURL(/\/sign-in\?returnUrl=%2Fblotter$/);
    await expect(page.getByTestId('submit-button')).toBeVisible();
  });

  test('a refused sign-in shows a readable error and stays on sign-in', async ({ page }) => {
    await page.goto('/sign-in');

    await signIn(page, env.username, 'definitely-not-the-password');

    await expect(page.getByTestId('error-message')).toContainText(/not accepted|invalid username or password/i);
    await expect(page).toHaveURL(/\/sign-in/);
  });

  test('a successful sign-in lands where the visitor was going', async ({ page }) => {
    await page.goto('/blotter');
    await expect(page).toHaveURL(/\/sign-in\?returnUrl=%2Fblotter$/);

    await signIn(page);

    await expect(page).toHaveURL(/\/blotter$/);
    await expect(page.getByTestId('order-history')).toBeVisible();
  });

  test('an off-origin return address is not followed after signing in', async ({ page }) => {
    await page.goto('/sign-in?returnUrl=https%3A%2F%2Fevil.example%2Fphish');

    await signIn(page);

    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('signing out ends the session', async ({ page }) => {
    await page.goto('/sign-in');
    await signIn(page);
    await expect(page).toHaveURL(/\/dashboard$/);

    await page.getByTestId('avatar-button').click();
    await page.getByTestId('sign-out').click();
    await expect(page).toHaveURL(/\/$|\/sign-in/);

    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/sign-in\?returnUrl=%2Fdashboard$/);
  });
});
