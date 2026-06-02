import { defineConfig } from '@playwright/test';
import baseConfig from './playwright.config';

export default defineConfig({
  ...baseConfig,
  fullyParallel: false,
  workers: 1,
  use: {
    ...baseConfig.use,
    slowMo: 5000,
  },
});
