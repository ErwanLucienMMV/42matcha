import { test, expect } from '@playwright/test';
import { signIn, verifiedUser } from '../helpers';

test('concurrent uploads respect the five-photo limit and ownership', async ({ request, page, playwright }) => {
  const user = await verifiedUser(request);
  const auth = await signIn(request, user);
  await page.goto('/');
  const encoded = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 20; canvas.height = 20;
    canvas.getContext('2d')!.fillRect(0, 0, 20, 20);
    return canvas.toDataURL('image/png').split(',')[1];
  });
  const data = Buffer.from(encoded, 'base64');
  const results = await Promise.all(Array.from({ length: 6 }, () => request.post('/api/me/photos', { headers: { ...auth.headers, 'Content-Type': 'image/png' }, data })));
  expect(results.filter(response => response.status() === 201)).toHaveLength(5);
  expect(results.filter(response => response.status() === 400)).toHaveLength(1);
  let profile = await (await request.get('/api/me')).json();
  expect(profile.photos.filter((photo: { is_primary: boolean }) => photo.is_primary)).toHaveLength(1);
  const id = profile.photos[0].id;
  const image = await request.get(`/api/photos/${id}`);
  expect(image.status()).toBe(200);
  expect(image.headers()['content-type']).toContain('image/png');
  expect((await image.body()).subarray(0, 8)).toEqual(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]));
  const other = await playwright.request.newContext({ baseURL: process.env['MATCHA_URL'] || 'http://localhost:8080' });
  try {
    const anotherUser = await verifiedUser(other);
    const otherAuth = await signIn(other, anotherUser);
    expect((await other.delete(`/api/me/photos/${id}`, otherAuth)).status()).toBe(404);
  } finally { await other.dispose(); }
  expect((await request.delete(`/api/me/photos/${id}`, auth)).status()).toBe(200);
  profile = await (await request.get('/api/me')).json();
  expect(profile.photos).toHaveLength(4);
  expect(profile.photos.filter((photo: { is_primary: boolean }) => photo.is_primary)).toHaveLength(1);
  const malicious = await request.post('/api/me/photos', { headers: { ...auth.headers, 'Content-Type': 'image/png' }, data: Buffer.from('<svg onload="alert(1)"/>') });
  expect(malicious.status()).toBe(400);
});
