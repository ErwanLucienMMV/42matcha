import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { signIn } from '../helpers';

function demoPassword() {
  const env = readFileSync(resolve(__dirname, '../../.env'), 'utf8');
  const password = env.match(/^SEED_PASSWORD=([^\r\n]+)$/m)?.[1];
  if (!password || password.startsWith('REPLACE_')) throw new Error('Run the evaluation seed before acceptance checks.');
  return password;
}

test('evaluation profiles support discovery photos and a seeded conversation', async ({ page, request, context }) => {
  await signIn(request, { username: 'demo_001', password: demoPassword() });
  const profile = await (await request.get('/api/me')).json();
  expect(profile.photos.length).toBeGreaterThan(0); expect(profile.tags.length).toBeGreaterThan(0);
  const connections = await (await request.get('/api/connections')).json();
  expect(connections.some((person: { username: string }) => person.username === 'demo_002')).toBe(true);
  await context.addCookies((await request.storageState()).cookies); await page.goto('/discover');
  await expect(page.locator('.person-card')).toHaveCount(20);
  const image = page.locator('.portrait img').first(); await expect(image).toBeVisible();
  await expect.poll(() => image.evaluate((element: HTMLImageElement) => element.naturalWidth)).toBeGreaterThan(0);
  await page.screenshot({ path: `../.local-validation/evaluation-discovery-${test.info().project.name}.png`, fullPage: false });
});
