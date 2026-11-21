import { APIRequestContext, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';

export async function mailboxLink(request: APIRequestContext, email: string, path: string): Promise<string> {
  let link = '';
  await expect.poll(async () => {
    const inbox = await (await request.get('http://localhost:8025/api/v1/messages')).json();
    const mail = inbox.messages?.find((item: { To: { Address: string }[] }) => item.To?.some(to => to.Address === email));
    if (!mail) return false;
    const message = await (await request.get(`http://localhost:8025/api/v1/message/${mail.ID}`)).json();
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
