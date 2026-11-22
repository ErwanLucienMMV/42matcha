import { test, expect } from '@playwright/test';
import { completeProfile, verifiedUser } from '../helpers';

test('Angular advanced search applies multiple tags and shows empty results', async ({ page, request, context, playwright }) => {
  const actor = await verifiedUser(request); const city = `Find_${actor.username}`;
  await completeProfile(request, actor, { city });
  const other = await playwright.request.newContext({ baseURL: 'http://localhost:8080' });
  try {
    const partner = await verifiedUser(other); await completeProfile(other, partner, { city });
    await context.addCookies((await request.storageState()).cookies);
    await page.goto('/search');
    await page.getByLabel('Interest tags', { exact: true }).fill('coffee, hiking');
    await page.getByLabel('City or neighborhood', { exact: true }).fill(city);
    await page.getByRole('button', { name: 'Search people', exact: true }).click();
    await expect(page.locator('.person-card')).toHaveCount(1);
    await page.getByLabel('Interest tags', { exact: true }).fill('coffee, unshared');
    await page.getByRole('button', { name: 'Search people', exact: true }).click();
    await expect(page.getByText('No profiles match these filters yet.')).toBeVisible();
  } finally { await other.dispose(); }
});
