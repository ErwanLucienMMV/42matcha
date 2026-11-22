import { test, expect } from '@playwright/test';
import { completeProfile, verifiedUser } from '../helpers';

test('reports are recorded and blocks exclude both directions from browsing and views', async ({ request, playwright }) => {
  const actor = await verifiedUser(request); const a = await completeProfile(request, actor);
  const other = await playwright.request.newContext({ baseURL: 'http://localhost:8080' });
  try {
    const partner = await verifiedUser(other); const b = await completeProfile(other, partner);
    expect((await request.post(`/api/people/${b.profile.id}/report`, { ...a.auth, data: { reason: 'This appears to be a fake account.' } })).status()).toBe(200);
    expect((await request.post(`/api/people/${b.profile.id}/block`, a.auth)).status()).toBe(200);
    expect((await request.get(`/api/people/${b.profile.id}`)).status()).toBe(404);
    expect((await other.get(`/api/people/${a.profile.id}`)).status()).toBe(404);
    const blocks = await (await request.get('/api/me/blocks')).json(); expect(blocks.map((person: { id: number }) => person.id)).toContain(b.profile.id);
    const discovered = await (await request.get('/api/discover?size=50')).json(); expect(discovered.items.map((person: { id: number }) => person.id)).not.toContain(b.profile.id);
    expect((await request.delete(`/api/people/${b.profile.id}/block`, a.auth)).status()).toBe(200);
    expect((await request.get(`/api/people/${b.profile.id}`)).status()).toBe(200);
  } finally { await other.dispose(); }
});
