import { test, expect } from '@playwright/test';
import { completeProfile, uploadPhoto, verifiedUser } from '../helpers';

test('chat persists messages only for mutual unblocked connections', async ({ request, playwright }) => {
  const actor = await verifiedUser(request); const a = await completeProfile(request, actor); await uploadPhoto(request, a.auth);
  const other = await playwright.request.newContext({ baseURL: process.env['MATCHA_URL'] || 'http://localhost:8080' });
  try {
    const partner = await verifiedUser(other); const b = await completeProfile(other, partner); await uploadPhoto(other, b.auth);
    const aId = a.profile.id, bId = b.profile.id;
    expect((await request.get(`/api/chat/${bId}`)).status()).toBe(403);
    await request.post(`/api/people/${bId}/like`, a.auth); await other.post(`/api/people/${aId}/like`, b.auth);
    const sent = await request.post(`/api/chat/${bId}`, { ...a.auth, data: { body: '<script>alert(1)</script> Hello!' } });
    expect(sent.status(), await sent.text()).toBe(201);
    const message = await sent.json();
    const received = await (await other.get(`/api/chat/${aId}`)).json();
    expect(received.items[0].body).toBe('<script>alert(1)</script> Hello!');
    expect((await (await other.get('/api/updates')).json()).unread_messages).toBe(1);
    expect((await other.post(`/api/chat/${aId}/read`, { ...b.auth, data: { through: message.id } })).status()).toBe(200);
    expect((await (await other.get('/api/updates')).json()).unread_messages).toBe(0);
    await request.delete(`/api/people/${bId}/like`, a.auth);
    expect((await other.get(`/api/chat/${aId}`)).status()).toBe(403);
    expect((await other.post(`/api/chat/${aId}`, { ...b.auth, data: { body: 'Forbidden after unlike' } })).status()).toBe(403);
    await request.post(`/api/people/${bId}/like`, a.auth);
    await request.post(`/api/people/${bId}/block`, a.auth);
    expect((await other.get(`/api/chat/${aId}`)).status()).toBe(403);
  } finally { await other.dispose(); }
});
