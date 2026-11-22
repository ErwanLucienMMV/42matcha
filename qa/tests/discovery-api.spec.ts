import { test, expect } from '@playwright/test';
import { completeProfile, signIn, verifiedUser } from '../helpers';

test('discovery requires a complete profile and validates filtering and ordering', async ({ request, playwright }) => {
  const actor = await verifiedUser(request);
  await signIn(request, actor);
  const incomplete = await request.get('/api/discover');
  expect(incomplete.status()).toBe(403);
  expect((await incomplete.json()).code).toBe('profile_incomplete');
  const city = `Discovery_${actor.username}`;
  await completeProfile(request, actor, { city, gender: 'woman', orientation: 'heterosexual' });
  const other = await playwright.request.newContext({ baseURL: 'http://localhost:8080' });
  try {
    const partner = await verifiedUser(other);
    const completed = await completeProfile(other, partner, { city, gender: 'man', orientation: 'heterosexual', latitude: 48.87 });
    const result = await request.get(`/api/discover?city=${city}&sort=location&direction=asc&size=1`);
    expect(result.status(), await result.text()).toBe(200);
    const data = await result.json();
    expect(data.items.map((person: { id: number }) => person.id)).toContain(completed.profile.id);
    expect(data.items[0].email).toBeUndefined();
    expect(data.items[0].latitude).toBeUndefined();
    expect((await request.get('/api/discover?min_age=80&max_age=20')).status()).toBe(400);
    expect((await request.get('/api/discover?sort=age%3BDROP%20TABLE%20accounts')).status()).toBe(400);
    expect((await request.get('/api/discover?direction=sideways')).status()).toBe(400);
    expect((await request.get('/api/discover?page=0')).status()).toBe(400);
    const empty = await (await request.get(`/api/discover?city=${city}&min_common=10`)).json();
    expect(empty.items).toHaveLength(0);
  } finally { await other.dispose(); }
});
