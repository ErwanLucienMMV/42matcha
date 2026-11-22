import { test, expect } from '@playwright/test';
import { signIn, verifiedUser } from '../helpers';

test('profile fields and reusable tags can be edited without exposing credentials', async ({ request }) => {
  const user = await verifiedUser(request);
  const auth = await signIn(request, user);
  const body = { ...user, first_name: 'Robin', gender: 'nonbinary', biography: '<script>alert("test")</script> I enjoy slow mornings.', tags: ['#Coffee', 'hiking', 'coffee'] };
  const update = await request.put('/api/me', { ...auth, data: body });
  expect(update.status(), await update.text()).toBe(200);
  const profile = await (await request.get('/api/me')).json();
  expect(profile.first_name).toBe('Robin');
  expect(profile.orientation).toBe('bisexual');
  expect(profile.tags).toEqual(['coffee', 'hiking']);
  expect(profile.biography).toBe(body.biography);
  expect(profile.password_hash).toBeUndefined();
  const tags = await (await request.get('/api/tags?q=cof')).json();
  expect(tags).toContainEqual({ name: 'coffee' });
});
