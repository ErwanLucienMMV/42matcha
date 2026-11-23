import { test, expect } from '@playwright/test';
import { completeProfile, verifiedUser } from '../helpers';

test('notification badges arrive from any page and read state survives reload', async ({ page, request, context, playwright }) => {
  const actor = await verifiedUser(request); const a = await completeProfile(request, actor);
  const other = await playwright.request.newContext({ baseURL: process.env['MATCHA_URL'] || 'http://localhost:8080' });
  try {
    const partner = await verifiedUser(other); const b = await completeProfile(other, partner);
    await context.addCookies((await request.storageState()).cookies); await page.goto('/profile');
    await expect.poll(async () => (await (await other.get(`/api/people/${a.profile.id}`)).json()).online).toBe(true);
    const start = Date.now(); await other.post(`/api/people/${a.profile.id}/visit`, b.auth);
    await expect(page.getByTestId('notifications-unread')).toHaveText('1', { timeout: 10000 }); expect(Date.now()-start).toBeLessThan(10000);
    await page.goto('/notifications'); await expect(page.getByText('visited your profile.', { exact: false })).toBeVisible();
    await page.getByRole('button', { name: 'Mark all as read', exact: true }).click();
    await expect(page.getByTestId('notifications-unread')).toHaveCount(0);
    await page.reload(); await expect(page.locator('.notification.unread')).toHaveCount(0);
    await page.setViewportSize({ width: 390, height: 844 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth<=document.documentElement.clientWidth)).toBe(true);
  } finally { await other.dispose(); }
});
