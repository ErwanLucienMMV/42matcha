import { test, expect } from '@playwright/test';
import { completeProfile, verifiedUser } from '../helpers';

test('mark-all uses a seen boundary and preserves a concurrent new notification', async ({ request, playwright }) => {
  const actor = await verifiedUser(request); const a = await completeProfile(request, actor);
  const other = await playwright.request.newContext({ baseURL: process.env['MATCHA_URL'] || 'http://localhost:8080' });
  try {
    const partner = await verifiedUser(other); const b = await completeProfile(other, partner);
    await other.post(`/api/people/${a.profile.id}/visit`, b.auth);
    const before = (await (await request.get('/api/notifications')).json()).items[0].id;
    await other.post(`/api/people/${a.profile.id}/visit`, b.auth);
    expect((await request.post('/api/notifications/read-all', { ...a.auth, data: { through: before } })).status()).toBe(200);
    const items = (await (await request.get('/api/notifications')).json()).items;
    expect(items[0].id).toBeGreaterThan(before); expect(items[0].is_read).toBe(false);
    expect(items.find((item: { id: number }) => item.id === before).is_read).toBe(true);
    expect((await (await request.get('/api/updates')).json()).unread_notifications).toBe(1);
    expect((await request.post('/api/notifications/read-all', { ...a.auth, data: { through: -1 } })).status()).toBe(400);
  } finally { await other.dispose(); }
});
