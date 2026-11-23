import { APIRequestContext, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { deflateSync } from 'node:zlib';

export async function mailboxLink(request: APIRequestContext, email: string, path: string): Promise<string> {
  const mailUrl = process.env['MATCHA_MAIL_URL'] || 'http://localhost:8025';
  let link = '';
  await expect.poll(async () => {
    const inbox = await (await request.get(`${mailUrl}/api/v1/messages`)).json();
    const mail = inbox.messages?.find((item: { To: { Address: string }[] }) => item.To?.some(to => to.Address === email));
    if (!mail) return false;
    const message = await (await request.get(`${mailUrl}/api/v1/message/${mail.ID}`)).json();
    link = String(message.Text).match(new RegExp(`https?://[^\\s]+${path}\\?token=[A-Za-z0-9_-]+`))?.[0] || '';
    return !!link;
  }).toBe(true);
  return link;
}

export async function register(request: APIRequestContext) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 14);
  const user = { username: `qa_${suffix}`, email: `qa_${suffix}@example.test`, first_name: 'Casey', last_name: 'Tester', password: 'Violet!River42' };
  const response = await request.post('/api/auth/register', { data: user });
  expect(response.status(), await response.text()).toBe(201);
  return user;
}

export async function verifiedUser(request: APIRequestContext) {
  const user = await register(request);
  const link = await mailboxLink(request, user.email, '/verify');
  const response = await request.post('/api/auth/verify', { data: { token: new URL(link).searchParams.get('token') } });
  expect(response.status(), await response.text()).toBe(200);
  return user;
}

export async function signIn(request: APIRequestContext, user: { username: string; password: string }) {
  const response = await request.post('/api/auth/login', { data: user });
  expect(response.status(), await response.text()).toBe(200);
  const csrf = (await request.storageState()).cookies.find(cookie => cookie.name === 'XSRF-TOKEN')!.value;
  return { headers: { 'X-XSRF-TOKEN': csrf } };
}

export async function completeProfile(request: APIRequestContext, user: Awaited<ReturnType<typeof verifiedUser>>, options: { city?: string; gender?: string; orientation?: string; tags?: string[]; latitude?: number; longitude?: number; birth_date?: string } = {}) {
  const auth = await signIn(request, user);
  const profile = { first_name: user.first_name, last_name: user.last_name, email: user.email, gender: options.gender || 'woman', orientation: options.orientation || 'bisexual', biography: 'Long walks, good coffee, and a little curiosity.', tags: options.tags || ['coffee', 'hiking'] };
  expect((await request.put('/api/me', { ...auth, data: profile })).status()).toBe(200);
  expect((await request.put('/api/me/details', { ...auth, data: { birth_date: options.birth_date || '1995-01-01' } })).status()).toBe(200);
  expect((await request.put('/api/me/location', { ...auth, data: { city: options.city || 'Paris, FR', latitude: options.latitude ?? 48.86, longitude: options.longitude ?? 2.35, gps_consent: false, source: 'manual' } })).status()).toBe(200);
  return { auth, profile: await (await request.get('/api/me')).json() };
}

export function testPng(): Buffer {
  const chunk = (type: string, data: Buffer) => {
    const named = Buffer.concat([Buffer.from(type), data]); let crc = 0xffffffff;
    for (const byte of named) { crc ^= byte; for (let bit = 0; bit < 8; bit++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0); }
    const size = Buffer.alloc(4); size.writeUInt32BE(data.length);
    const checksum = Buffer.alloc(4); checksum.writeUInt32BE((crc ^ 0xffffffff) >>> 0);
    return Buffer.concat([size, named, checksum]);
  };
  const header = Buffer.alloc(13); header.writeUInt32BE(4, 0); header.writeUInt32BE(4, 4); header[8] = 8; header[9] = 2;
  const raw = Buffer.alloc(4 * 13, 128); for (let row = 0; row < 4; row++) raw[row * 13] = 0;
  return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]), chunk('IHDR',header), chunk('IDAT',deflateSync(raw)), chunk('IEND',Buffer.alloc(0))]);
}

export async function uploadPhoto(request: APIRequestContext, auth: { headers: { 'X-XSRF-TOKEN': string } }) {
  const response = await request.post('/api/me/photos', { headers: { ...auth.headers, 'Content-Type': 'image/png' }, data: testPng() });
  expect(response.status(), await response.text()).toBe(201);
  return (await response.json()).id as number;
}
