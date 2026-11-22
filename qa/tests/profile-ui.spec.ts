import { test, expect } from '@playwright/test';
import { signIn, verifiedUser } from '../helpers';

test('Angular profile editor saves fields and displays a sanitized photo', async ({ page, request, context }) => {
  const user = await verifiedUser(request);
  await signIn(request, user);
  await context.addCookies((await request.storageState()).cookies);
  await page.goto('/profile');
  await page.getByRole('combobox', { name: 'Gender', exact: true }).selectOption('woman');
  await page.getByLabel('A little about you').fill('Coffee, long walks, and a good conversation.');
  await page.getByLabel('Your interests').fill('coffee, hiking');
  await page.getByRole('button', { name: 'Save profile', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Your profile is saved.');
  const encoded = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 40; canvas.height = 50;
    const context = canvas.getContext('2d')!; context.fillStyle = '#8568a6'; context.fillRect(0, 0, 40, 50);
    return canvas.toDataURL('image/png').split(',')[1];
  });
  await page.getByLabel('Add a photo').setInputFiles({ name: 'portrait.png', mimeType: 'image/png', buffer: Buffer.from(encoded, 'base64') });
  await expect(page.getByRole('status')).toHaveText('Photo added.');
  const image = page.getByAltText('Your profile photo');
  await expect(image).toBeVisible();
  await expect.poll(() => image.evaluate((element: HTMLImageElement) => element.naturalWidth)).toBeGreaterThan(0);
  await page.reload();
  await expect(page.getByLabel('Your interests')).toHaveValue('coffee, hiking');
  await expect(page.getByLabel('A little about you')).toHaveValue('Coffee, long walks, and a good conversation.');
});
