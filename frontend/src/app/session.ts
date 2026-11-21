import { Injectable, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { ApiClient } from './api';

export interface Account { id: number; username: string; first_name: string; last_name: string; email: string; verified: boolean; }

@Injectable({ providedIn: 'root' })
export class Session {
  private readonly api = inject(ApiClient);
  private readonly router = inject(Router);
  readonly user = signal<Account | null>(null);
  readonly ready = this.refresh();
  async refresh() { this.user.set((await this.api.get<{ user: Account | null }>('/auth/session')).user); }
  async logout() {
    await this.api.post('/auth/logout');
    this.user.set(null);
    await this.router.navigateByUrl('/');
  }
}

export const signedIn: CanActivateFn = async () => {
  const session = inject(Session);
  const router = inject(Router);
  await session.ready;
  return session.user() ? true : router.createUrlTree(['/login']);
};
