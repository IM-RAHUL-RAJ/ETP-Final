import { defineConfig, devices } from '@playwright/test';
import { env } from './e2e/env';

// The journeys run against the real stack (Trade REST API, auth service and this app), never
// a stub. Every address and credential comes from the environment; see .env.example.
export default defineConfig({
  testDir: './e2e',
  testMatch: ['login.spec.ts', 'place-order.spec.ts'],
  // Each journey stands on its own: no shared state, no ordering. One worker keeps runs
  // against the shared stack predictable.
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: env.baseUrl,
    trace: 'retain-on-failure'
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]
});
