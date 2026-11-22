import { test, expect } from '@playwright/test';
import { completeProfile, verifiedUser } from '../helpers';

test('advanced search combines tags ranges location and stable pagination', async ({ request, playwright }) => {
  const actor = await verifiedUser(request); const city = `Search_${actor.username}`;
  await completeProfile(request, actor, { city });
  const other = await playwright.request.newContext({ baseURL: 'http://localhost:8080' });
  try {
    for (let i = 0; i < 2; i++) {
      const user = await verifiedUser(other);
      await completeProfile(other, user, { city, tags: ['coffee', 'hiking'], birth_date: '1990-01-01' });
    }
    const criteria = `city=${city}&tags=%23Coffee,hiking&min_age=30&max_age=40&min_popularity=0&max_popularity=0&sort=age&direction=asc&size=1`;
    const first = await (await request.get('/api/search?' + criteria)).json();
    const second = await (await request.get('/api/search?' + criteria + '&page=2')).json();
    expect(first.items).toHaveLength(1); expect(first.has_more).toBe(true);
    expect(second.items).toHaveLength(1); expect(second.has_more).toBe(false);
    expect(first.items[0].id).not.toBe(second.items[0].id);
    expect((await (await request.get(`/api/search?city=${city}&tags=coffee,unshared`)).json()).items).toHaveLength(0);
    expect((await request.get('/api/search?tags=coffee,%27%20OR%201=1')).status()).toBe(400);
  } finally { await other.dispose(); }
});
