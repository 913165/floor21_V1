import { defineConfig } from '@playwright/test';
import baseConfig from './playwright.config';

/** Headed runs with a pause between actions (slowMo is config-only, not a CLI flag). */
export default defineConfig({
  ...baseConfig,
  fullyParallel: false,
  workers: 1,
  use: {
    ...baseConfig.use,
    slowMo: 2500,
  },
});
