import { test, expect } from '@playwright/test';
import { mailboxLink, register } from '../helpers';

test('registration sends a working unique email verification link', async ({ request }) => {
  const user = await register(request);
  const link = await mailboxLink(request, user.email, '/verify');
  const token = new URL(link).searchParams.get('token');
  expect((await request.post('/api/auth/verify', { data: { token } })).status()).toBe(200);
  expect((await request.post('/api/auth/verify', { data: { token } })).status()).toBe(400);
  const duplicate = await request.post('/api/auth/register', { data: user });
  expect(duplicate.status()).toBe(409);
});

test('common-word passwords and foreign-origin registrations are rejected', async ({ request }) => {
  const invalid = await request.post('/api/auth/register', { data: { username: 'invalid_test', email: 'invalid@example.test', first_name: 'Test', last_name: 'Person', password: 'Football12345!' } });
  expect(invalid.status()).toBe(400);
  const foreign = await request.post('/api/auth/register', { headers: { Origin: 'https://unrelated.example' }, data: {} });
  expect(foreign.status()).toBe(403);
});
