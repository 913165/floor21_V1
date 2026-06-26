import { defineConfig, devices } from '@playwright/test';
import path from 'path';

/** Repo root (parent of e2e/). Spring Boot src/ is not modified by this suite. */
const repoRoot = path.resolve(__dirname, '..');

const baseURL =
  (process.env.FLOOR21_BASE_URL?.replace(/\/$/, '') ?? 'http://localhost/floor21') + '/';

export default defineConfig({
  testDir: './tests',
  testMatch: ['floor21-full-flow.spec.ts', 'create-five-clients.spec.ts'],
  fullyParallel: false,
  /** Serial flow shares .flow-state.json — always single worker (CLI + UI). */
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 1,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    actionTimeout: 30_000,
    navigationTimeout: 30_000,
  },
  expect: {
    timeout: 15_000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  /**
   * Start Spring Boot only when FLOOR21_START_SERVER=1 (or in CI).
   * Default: reuse an app you already started (see e2e/README.md).
   */
  webServer:
    process.env.FLOOR21_START_SERVER === '1'
      ? {
          command:
            process.platform === 'win32'
              ? 'mvnw.cmd spring-boot:run'
              : './mvnw spring-boot:run',
          cwd: repoRoot,
          url: `${baseURL}/login`,
          reuseExistingServer: !process.env.CI,
          timeout: 180_000,
        }
      : undefined,
});
