import path from 'path';

export const seleniumConfig = {
  baseUrl: process.env.E2E_BASE_URL || 'http://localhost:3000',
  browser: (process.env.E2E_BROWSER || 'chrome').toLowerCase(),
  headless: process.env.E2E_HEADLESS === 'true' || process.env.CI === 'true',
  defaultTimeoutMs: 15000,
  pollIntervalMs: 250,
  screenshotDir: path.resolve(__dirname, '../reports/screenshots'),
};
