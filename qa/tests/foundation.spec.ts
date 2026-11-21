import { test, expect } from '@playwright/test';

test('application shell and proxied database readiness work without browser errors', async ({ page }) => {
  const failures: string[] = [];
  page.on('pageerror', error => failures.push(error.message));
  page.on('console', message => { if (['error', 'warning'].includes(message.type())) failures.push(message.text()); });
  await page.goto('/');
  await expect(page).toHaveTitle(/Matcha/);
  await expect(page.getByRole('banner')).toBeVisible();
  await expect(page.getByRole('main')).toBeVisible();
  await expect(page.getByRole('contentinfo')).toBeVisible();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Your kind of people');
  const ready = await page.evaluate(async () => (await fetch('/api/ready')).json());
  expect(ready.status).toBe('ready');
  expect(failures).toEqual([]);
});

test('landing page fits a mobile screen', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await expect(page.getByRole('link', { name: 'Find your connection' })).toBeVisible();
  const sizes = await page.evaluate(() => ({ viewport: document.documentElement.clientWidth, content: document.documentElement.scrollWidth }));
  expect(sizes.content).toBeLessThanOrEqual(sizes.viewport);
  await page.screenshot({ path: `../.local-validation/landing-mobile-${test.info().project.name}.png`, fullPage: true });
});
