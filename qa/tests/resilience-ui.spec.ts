import { test, expect } from '@playwright/test';
import { signIn, verifiedUser, completeProfile } from '../helpers';

test('initial session outage offers recovery without an unhandled error', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.route('**/api/auth/session', route => route.fulfill({ status: 503, contentType: 'application/json', body: '{"error":"Temporarily unavailable"}' }));
  await page.goto('/');
  await expect(page.getByRole('alert')).toContainText('Temporarily unavailable');
  await page.unroute('**/api/auth/session');
  await page.getByRole('button', { name: 'Retry connection' }).click();
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('authenticated pages fit a narrow mobile screen and home links to discovery', async ({ page, request, context }) => {
  const user = await verifiedUser(request);
  await completeProfile(request, user);
  await signIn(request, user);
  await context.addCookies((await request.storageState()).cookies);
  await page.setViewportSize({ width: 320, height: 740 });
  await page.goto('/');
  await expect(page.getByRole('link', { name: 'Discover your people' })).toHaveAttribute('href', '/discover');
  for (const path of ['/discover', '/search', '/profile', '/activity', '/messages', '/notifications']) {
    await page.goto(path);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow, path).toBeLessThanOrEqual(0);
  }
  await page.screenshot({ path: `../.local-validation/narrow-notifications-${test.info().project.name}.png`, fullPage: true });
});
