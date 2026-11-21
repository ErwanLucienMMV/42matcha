import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiClient, errorMessage } from './api';
import { Session } from './session';

@Component({
  selector: 'app-auth-page',
  imports: [FormsModule, RouterLink],
  template: `
    <section class="form-page">
      <p class="eyebrow">A new connection starts here</p>
      <h1>{{ title }}</h1>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @if (message()) { <p class="success" role="status">{{ message() }}</p> }
      @if (mode !== 'verify' && !done()) {
        <form #form="ngForm" (ngSubmit)="submit()">
          @if (mode === 'register') {
            <div class="form-grid">
              <label class="field">First name<input name="first_name" [(ngModel)]="firstName" required maxlength="80" autocomplete="given-name"></label>
              <label class="field">Last name<input name="last_name" [(ngModel)]="lastName" required maxlength="80" autocomplete="family-name"></label>
            </div>
          }
          @if (mode === 'register' || mode === 'forgot' || mode === 'resend') {
            <label class="field">Email<input name="email" [(ngModel)]="email" type="email" email required maxlength="254" autocomplete="email"></label>
          }
          @if (mode === 'register' || mode === 'login') {
            <label class="field">Username<input name="username" [(ngModel)]="username" required minlength="3" maxlength="30" pattern="[A-Za-z0-9_]+" autocomplete="username"></label>
          }
          @if (mode === 'register' || mode === 'login' || mode === 'reset') {
            <label class="field">{{ mode === 'reset' ? 'New password' : 'Password' }}<input name="password" [(ngModel)]="password" type="password" required [minlength]="mode === 'login' ? 1 : 12" maxlength="72" [autocomplete]="mode === 'login' ? 'current-password' : 'new-password'" aria-describedby="password-help"></label>
            @if (mode !== 'login') { <p id="password-help" class="fine-print">At least 12 characters, with uppercase and lowercase letters, a number, and a symbol. Avoid common words and predictable variations.</p> }
          }
          <button class="button full" type="submit" [disabled]="form.invalid || busy()">{{ busy() ? 'One moment…' : action }}</button>
        </form>
      }
      @if (mode === 'login') { <p class="fine-print"><a class="text-link" routerLink="/forgot-password">Forgot your password?</a></p><p class="fine-print">Need a verification email? <a class="text-link" routerLink="/resend-verification">Send a new link</a></p><p>New here? <a class="text-link" routerLink="/register">Create an account</a></p> }
      @else { <p class="fine-print">Already have an account? <a class="text-link" routerLink="/login">Sign in</a></p> }
    </section>
  `,
})
export class AuthPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ApiClient);
  private readonly session = inject(Session);
  mode = this.route.snapshot.data['mode'] as string;
  username = ''; email = ''; firstName = ''; lastName = ''; password = '';
  token = this.route.snapshot.queryParamMap.get('token') || '';
  readonly error = signal(''); readonly message = signal(''); readonly busy = signal(false); readonly done = signal(false);
  get title() { return ({ register: 'Be a little curious.', login: 'Good to see you.', forgot: 'A fresh start.', reset: 'Choose a new password.', verify: 'Verify your email.', resend: 'A new verification link.' } as Record<string, string>)[this.mode]; }
  get action() { return ({ register: 'Create my account', login: 'Sign in', forgot: 'Send reset link', reset: 'Save new password', resend: 'Send verification link' } as Record<string, string>)[this.mode]; }
  ngOnInit() {
    if (this.token) window.history.replaceState(window.history.state, '', window.location.pathname);
    if (this.mode === 'verify') void this.submit();
  }
  async submit() {
    this.error.set(''); this.busy.set(true);
    try {
      const path = ({ register: '/auth/register', login: '/auth/login', forgot: '/auth/forgot-password', reset: '/auth/reset-password', verify: '/auth/verify', resend: '/auth/resend-verification' } as Record<string, string>)[this.mode];
      const response = await this.api.post<{ message?: string }>(path, { username: this.username, email: this.email, first_name: this.firstName, last_name: this.lastName, password: this.password, token: this.token });
      this.password = '';
      if (this.mode === 'login') { await this.session.refresh(); await this.router.navigateByUrl('/account'); }
      else { this.message.set(response.message || 'Done.'); this.done.set(true); }
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.busy.set(false); }
  }
}

@Component({
  selector: 'app-account-page',
  imports: [RouterLink],
  template: `<section class="form-page wide"><p class="eyebrow">Your little corner</p><h1>Welcome, {{ session.user()?.first_name }}.</h1><div class="card"><h2>Your account</h2><p>{{ session.user()?.username }}<br>{{ session.user()?.email }}</p><a class="button" routerLink="/profile">Complete your profile</a></div></section>`,
})
export class AccountPage { readonly session = inject(Session); }
