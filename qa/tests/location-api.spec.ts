import { test, expect } from '@playwright/test';
import { signIn, verifiedUser } from '../helpers';

test('manual location requires no GPS consent and stores only approximate coordinates', async ({ request }) => {
  const user = await verifiedUser(request); const auth = await signIn(request, user);
  expect((await (await request.get('/api/me')).json()).latitude).toBeNull();
  const places = await (await request.get('/api/locations?q=Paris')).json();
  expect(places.some((place: { name: string; country: string }) => place.name === 'Paris' && place.country === 'FR')).toBe(true);
  const location = { city: 'Paris — Montmartre', latitude: 48.8566, longitude: 2.3522, gps_consent: false, source: 'manual' };
  expect((await request.put('/api/me/location', { ...auth, data: location })).status()).toBe(200);
  const profile = await (await request.get('/api/me')).json();
  expect(profile.latitude).toBe(48.86); expect(profile.longitude).toBe(2.35); expect(profile.gps_consent).toBe(false);
  expect((await request.put('/api/me/location', { ...auth, data: { ...location, source: 'gps' } })).status()).toBe(403);
  expect((await request.put('/api/me/location', { ...auth, data: { ...location, latitude: 91 } })).status()).toBe(400);
  expect((await request.put('/api/me/details', { ...auth, data: { birth_date: '2000-01-01' } })).status()).toBe(200);
  expect((await request.put('/api/me/details', { ...auth, data: { birth_date: '2100-01-01' } })).status()).toBe(400);
});
