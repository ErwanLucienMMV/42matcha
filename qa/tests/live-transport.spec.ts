import { test, expect } from '@playwright/test';
import { completeProfile, uploadPhoto, verifiedUser } from '../helpers';

test('authenticated WebSocket delivers committed message events within ten seconds', async ({ page, context, request, playwright }) => {
  const actor = await verifiedUser(request); const a = await completeProfile(request, actor); await uploadPhoto(request, a.auth);
  const other = await playwright.request.newContext({ baseURL: 'http://localhost:8080' });
  try {
    const partner = await verifiedUser(other); const b = await completeProfile(other, partner); await uploadPhoto(other, b.auth);
    await request.post(`/api/people/${b.profile.id}/like`, a.auth); await other.post(`/api/people/${a.profile.id}/like`, b.auth);
    await context.addCookies((await request.storageState()).cookies);
    await page.goto('/');
    await page.evaluate(() => {
      const state = window as unknown as { liveEvents: string[]; testSocket: WebSocket };
      state.liveEvents = []; state.testSocket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/api/live`);
      state.testSocket.onmessage = event => state.liveEvents.push(event.data);
    });
    await expect.poll(() => page.evaluate(() => (window as unknown as { liveEvents: string[] }).liveEvents.includes('ready'))).toBe(true);
    const start = Date.now();
    expect((await other.post(`/api/chat/${a.profile.id}`, { ...b.auth, data: { body: 'Live delivery check' } })).status()).toBe(201);
    await expect.poll(() => page.evaluate(() => (window as unknown as { liveEvents: string[] }).liveEvents.includes('refresh')), { timeout: 10000 }).toBe(true);
    expect(Date.now() - start).toBeLessThan(10000);
    expect((await (await other.get(`/api/people/${a.profile.id}`)).json()).online).toBe(true);
  } finally { await other.dispose(); }
});
