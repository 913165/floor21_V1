import { defineConfig } from '@playwright/test';
import baseConfig from './playwright.config';

/** Very slow — ~8s pause between each click/type so you can follow every step. */
export default defineConfig({
  ...baseConfig,
  fullyParallel: false,
  workers: 1,
  timeout: 120_000,
  use: {
    ...baseConfig.use,
    slowMo: 8000,
    actionTimeout: 60_000,
  },
});
