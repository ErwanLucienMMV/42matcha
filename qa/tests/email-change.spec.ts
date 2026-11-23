import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { mailboxLink, signIn, verifiedUser } from '../helpers';

test('email changes keep the old address until a single-use link verifies the new one', async ({ request }) => {
  const user = await verifiedUser(request);
  const auth = await signIn(request, user);
  const email = `changed_${randomUUID().slice(0, 8)}@example.test`;
  const update = await request.put('/api/me', { ...auth, data: { ...user, email, gender: 'woman', orientation: 'bisexual', biography: 'A new address.', tags: ['coffee'] } });
  expect(update.status(), await update.text()).toBe(200);
  expect((await (await request.get('/api/me')).json()).email).toBe(user.email);
  const link = await mailboxLink(request, email, '/verify');
  const token = new URL(link).searchParams.get('token');
  expect((await request.post('/api/auth/verify', { data: { token } })).status()).toBe(200);
  expect((await (await request.get('/api/me')).json()).email).toBe(email);
  expect((await request.post('/api/auth/verify', { data: { token } })).status()).toBe(400);
});
