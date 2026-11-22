import { test, expect } from '@playwright/test';
import { completeProfile, uploadPhoto, verifiedUser } from '../helpers';

test('messages arrive globally within ten seconds and an open chat loses access after unlike', async ({ page, request, context, playwright }) => {
  const actor = await verifiedUser(request); const a = await completeProfile(request, actor); await uploadPhoto(request, a.auth);
  const other = await playwright.request.newContext({ baseURL: 'http://localhost:8080' });
  try {
    const partner = await verifiedUser(other); const b = await completeProfile(other, partner); await uploadPhoto(other, b.auth);
    await request.post(`/api/people/${b.profile.id}/like`, a.auth); await other.post(`/api/people/${a.profile.id}/like`, b.auth);
    await context.addCookies((await request.storageState()).cookies); await page.goto('/');
    await expect.poll(async () => (await (await other.get(`/api/people/${a.profile.id}`)).json()).online).toBe(true);
    const body = '<img src=x onerror="window.chatInjected=true"> Hello from a connection.';
    const start = Date.now();
    expect((await other.post(`/api/chat/${a.profile.id}`, { ...b.auth, data: { body } })).status()).toBe(201);
    await expect(page.getByTestId('messages-unread')).toHaveText('1', { timeout: 10000 });
    expect(Date.now() - start).toBeLessThan(10000);
    await page.goto(`/messages/${b.profile.id}`);
    await expect(page.locator('.message p').filter({ hasText: body })).toBeVisible();
    expect(await page.evaluate(() => (window as unknown as { chatInjected?: boolean }).chatInjected)).toBeUndefined();
    await expect(page.getByTestId('messages-unread')).toHaveCount(0);
    await page.getByLabel('Your message', { exact: true }).fill('A little hello back.');
    await page.getByRole('button', { name: 'Send message', exact: true }).click();
    await expect(page.getByText('A little hello back.', { exact: true })).toBeVisible();
    await other.delete(`/api/people/${a.profile.id}/like`, b.auth);
    await expect(page.getByRole('alert')).toContainText('no longer available', { timeout: 10000 });
    await expect(page.getByRole('button', { name: 'Send message', exact: true })).toHaveCount(0);
  } finally { await other.dispose(); }
});
