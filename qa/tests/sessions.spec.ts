import { test, expect } from '@playwright/test';
import { mailboxLink, register, verifiedUser } from '../helpers';

test('unverified accounts cannot log in and authenticated writes require CSRF', async ({ request }) => {
  const user = await register(request);
  expect((await request.post('/api/auth/login', { data: user })).status()).toBe(403);
  const link = await mailboxLink(request, user.email, '/verify');
  expect((await request.post('/api/auth/verify', { data: { token: new URL(link).searchParams.get('token') } })).status()).toBe(200);
  const login = await request.post('/api/auth/login', { data: user });
  expect(login.status()).toBe(200);
  const cookies = (await request.storageState()).cookies;
  expect(cookies.find(cookie => cookie.name === 'matcha_session')?.httpOnly).toBe(true);
  const session = await (await request.get('/api/auth/session')).json();
  expect(session.user.username).toBe(user.username);
  expect(session.user.csrf_hash).toBeUndefined();
  expect(session.user.password_hash).toBeUndefined();
  expect((await request.post('/api/auth/logout')).status()).toBe(403);
  const csrf = cookies.find(cookie => cookie.name === 'XSRF-TOKEN')!.value;
  expect((await request.post('/api/auth/logout', { headers: { 'X-XSRF-TOKEN': csrf } })).status()).toBe(200);
  expect((await (await request.get('/api/auth/session')).json()).user).toBeNull();
});

test('password reset is single-use and revokes old sessions', async ({ request }) => {
  const user = await verifiedUser(request);
  expect((await request.post('/api/auth/login', { data: user })).status()).toBe(200);
  const reset = await request.post('/api/auth/forgot-password', { data: { email: user.email } });
  expect(reset.status()).toBe(200);
  const link = await mailboxLink(request, user.email, '/reset-password');
  const body = { token: new URL(link).searchParams.get('token'), password: 'Orchid!Cloud83' };
  expect((await request.post('/api/auth/reset-password', { data: body })).status()).toBe(200);
  expect((await (await request.get('/api/auth/session')).json()).user).toBeNull();
  expect((await request.post('/api/auth/reset-password', { data: body })).status()).toBe(400);
  expect((await request.post('/api/auth/login', { data: user })).status()).toBe(401);
  expect((await request.post('/api/auth/login', { data: { ...user, password: body.password } })).status()).toBe(200);
});
