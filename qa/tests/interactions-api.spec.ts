import { test, expect } from '@playwright/test';
import { completeProfile, uploadPhoto, verifiedUser } from '../helpers';

test('visits likes mutual connections and unlike follow the relationship rules', async ({ request, playwright }) => {
  const actor = await verifiedUser(request); const actorData = await completeProfile(request, actor);
  const other = await playwright.request.newContext({ baseURL: 'http://localhost:8080' });
  try {
    const partner = await verifiedUser(other); const partnerData = await completeProfile(other, partner);
    const a = actorData.profile.id, b = partnerData.profile.id;
    expect((await request.post(`/api/people/${b}/visit`, actorData.auth)).status()).toBe(200);
    expect((await (await other.get('/api/me/visitors')).json()).map((person: { id: number }) => person.id)).toContain(a);
    expect((await request.post(`/api/people/${b}/like`, actorData.auth)).status()).toBe(403);
    await uploadPhoto(request, actorData.auth); await uploadPhoto(other, partnerData.auth);
    expect((await request.post(`/api/people/${b}/like`, actorData.auth)).status()).toBe(200);
    let profile = await (await other.get(`/api/people/${a}`)).json(); expect(profile.likes_me).toBe(true); expect(profile.connected).toBe(false);
    expect(profile.email).toBeUndefined(); expect(profile.password_hash).toBeUndefined(); expect(profile.birth_date).toBeUndefined(); expect(profile.latitude).toBeUndefined();
    expect((await other.post(`/api/people/${a}/like`, partnerData.auth)).status()).toBe(200);
    profile = await (await request.get(`/api/people/${b}`)).json(); expect(profile.connected).toBe(true);
    expect((await request.delete(`/api/people/${b}/like`, actorData.auth)).status()).toBe(200);
    expect((await (await request.get(`/api/people/${b}`)).json()).connected).toBe(false);
  } finally { await other.dispose(); }
});
