import { test, expect } from '@playwright/test';
import { completeProfile, uploadPhoto, verifiedUser } from '../helpers';

test('Angular profile consultation records visits and supports likes reports and blocks', async ({ page, request, context, playwright }) => {
  const actor = await verifiedUser(request); const a = await completeProfile(request, actor); await uploadPhoto(request, a.auth);
  const other = await playwright.request.newContext({ baseURL: process.env['MATCHA_URL'] || 'http://localhost:8080' });
  try {
    const partner = await verifiedUser(other); const b = await completeProfile(other, partner); await uploadPhoto(other, b.auth);
    await context.addCookies((await request.storageState()).cookies);
    await page.goto(`/people/${b.profile.id}`);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(partner.first_name);
    await page.getByRole('button', { name: 'Like profile', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Unlike', exact: true })).toBeVisible();
    expect((await (await other.get('/api/me/visitors')).json()).map((user: { id: number }) => user.id)).toContain(a.profile.id);
    await page.getByRole('button', { name: 'Report fake account', exact: true }).click();
    await page.getByLabel('Why do you think this is a fake account?').fill('This appears to be a fake account.');
    await page.getByRole('button', { name: 'Send report', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('report has been recorded');
    await page.getByRole('button', { name: 'Block user', exact: true }).click();
    await expect(page).toHaveURL(/\/activity$/);
    await expect(page.getByRole('button', { name: `Unblock ${partner.username}`, exact: true })).toBeVisible();
  } finally { await other.dispose(); }
});
