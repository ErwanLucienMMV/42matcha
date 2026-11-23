import { test, expect } from '@playwright/test';
import { completeProfile, verifiedUser } from '../helpers';

test('Angular discovery filters real data and fits mobile navigation', async ({ page, request, context, playwright }) => {
  const actor = await verifiedUser(request); const city = `Browse_${actor.username}`;
  await completeProfile(request, actor, { city });
  const other = await playwright.request.newContext({ baseURL: process.env['MATCHA_URL'] || 'http://localhost:8080' });
  try {
    const partner = await verifiedUser(other); await completeProfile(other, partner, { city, gender: 'man' });
    await context.addCookies((await request.storageState()).cookies);
    await page.goto('/discover');
    await page.getByLabel('City or neighborhood', { exact: true }).fill(city);
    await page.getByRole('button', { name: 'Apply filters' }).click();
    await expect(page.getByRole('link', { name: `View ${partner.first_name} profile`, exact: true })).toBeVisible();
    await page.getByRole('combobox', { name: 'Sort by', exact: true }).selectOption('common_tags');
    await page.getByRole('combobox', { name: 'Order', exact: true }).selectOption('desc');
    await page.getByRole('button', { name: 'Apply filters' }).click();
    await expect(page.locator('.person-card')).toHaveCount(1);
    await page.setViewportSize({ width: 390, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
    await page.screenshot({ path: `../.local-validation/discovery-mobile-${test.info().project.name}.png`, fullPage: true });
  } finally { await other.dispose(); }
});
