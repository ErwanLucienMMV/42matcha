import { test, expect } from '@playwright/test';
import { signIn, verifiedUser } from '../helpers';

test('denied GPS leaves a working manual city flow in Angular', async ({ page, request, context }) => {
  const user = await verifiedUser(request); await signIn(request, user);
  await context.addCookies((await request.storageState()).cookies);
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'geolocation', { value: { getCurrentPosition: (_success: unknown, error: (value: unknown) => void) => error({ code: 1, message: 'Denied' }) } });
  });
  await page.goto('/profile');
  const gps = page.getByRole('button', { name: 'Use my location', exact: true });
  await expect(gps).toBeDisabled();
  await page.getByRole('checkbox', { name: /I consent/ }).check(); await gps.click();
  await expect(page.getByRole('status')).toContainText('Location access was unavailable');
  await page.getByLabel('City search', { exact: true }).fill('Paris');
  await page.getByRole('button', { name: 'Search places', exact: true }).click();
  await page.getByRole('button', { name: 'Paris, FR', exact: true }).click();
  await page.getByRole('button', { name: 'Save approximate location', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('approximate location is saved');
  await page.reload(); await expect(page.getByLabel('City or neighborhood', { exact: true })).toHaveValue('Paris, FR');
  await expect(page.getByRole('checkbox', { name: /I consent/ })).not.toBeChecked();
});
