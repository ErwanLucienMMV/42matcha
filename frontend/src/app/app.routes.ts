import { Routes } from '@angular/router';
import { Welcome } from './welcome';
import { AuthPage, AccountPage } from './auth-page';
import { signedIn } from './session';
import { ProfilePage } from './profile-page';
import { PeoplePage } from './people-page';

export const routes: Routes = [
  { path: '', component: Welcome },
  { path: 'login', component: AuthPage, data: { mode: 'login' } },
  { path: 'register', component: AuthPage, data: { mode: 'register' } },
  { path: 'verify', component: AuthPage, data: { mode: 'verify' } },
  { path: 'forgot-password', component: AuthPage, data: { mode: 'forgot' } },
  { path: 'reset-password', component: AuthPage, data: { mode: 'reset' } },
  { path: 'resend-verification', component: AuthPage, data: { mode: 'resend' } },
  { path: 'account', component: AccountPage, canActivate: [signedIn] },
  { path: 'profile', component: ProfilePage, canActivate: [signedIn] },
  { path: 'discover', component: PeoplePage, canActivate: [signedIn] },
  { path: 'search', component: PeoplePage, canActivate: [signedIn], data: { search: true } },
  { path: '**', redirectTo: '' },
];
