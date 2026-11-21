import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  timeout: 30000,
  expect: { timeout: 10000 },
  outputDir: '../.local-validation/browser-results',
  reporter: [['list'], ['json', { outputFile: '../.local-validation/browser-results.json' }]],
  use: { baseURL: process.env['MATCHA_URL'] || 'http://localhost:8080', trace: 'retain-on-failure' },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
  ],
});
