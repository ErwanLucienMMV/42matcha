import { test, expect } from '@playwright/test';
import { completeProfile, uploadPhoto, verifiedUser } from '../helpers';

test('notification kinds and ownership persist with unlike and block suppression', async ({ request, playwright }) => {
  const actor = await verifiedUser(request); const a = await completeProfile(request, actor); await uploadPhoto(request, a.auth);
  const other = await playwright.request.newContext({ baseURL: 'http://localhost:8080' });
  try {
    const partner = await verifiedUser(other); const b = await completeProfile(other, partner); await uploadPhoto(other, b.auth);
    await request.post(`/api/people/${b.profile.id}/visit`, a.auth);
    await request.post(`/api/people/${b.profile.id}/like`, a.auth);
    let notifications = (await (await other.get('/api/notifications')).json()).items;
    expect(notifications.map((item: { kind: string }) => item.kind)).toEqual(expect.arrayContaining(['visit','like']));
    const id = notifications[0].id;
    expect((await request.post(`/api/notifications/${id}/read`, a.auth)).status()).toBe(404);
    expect((await other.post(`/api/notifications/${id}/read`, b.auth)).status()).toBe(200);
    notifications = (await (await other.get('/api/notifications')).json()).items;
    expect(notifications.find((item: { id: number }) => item.id === id).is_read).toBe(true);
    await other.post(`/api/people/${a.profile.id}/like`, b.auth);
    await request.post(`/api/chat/${b.profile.id}`, { ...a.auth, data: { body: 'Notification message' } });
    await request.delete(`/api/people/${b.profile.id}/like`, a.auth);
    notifications = (await (await other.get('/api/notifications')).json()).items;
    expect(notifications.map((item: { kind: string }) => item.kind)).toEqual(expect.arrayContaining(['connection','message','unlike']));
    const count = (await (await request.get('/api/notifications')).json()).items.length;
    await other.post(`/api/people/${a.profile.id}/visit`, b.auth);
    expect((await (await request.get('/api/notifications')).json()).items.length).toBe(count);
    await other.post(`/api/people/${a.profile.id}/block`, b.auth);
    expect((await (await request.get('/api/notifications')).json()).items).toHaveLength(0);
  } finally { await other.dispose(); }
});
