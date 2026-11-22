import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiClient, errorMessage } from './api';
import { LiveUpdates } from './live';

interface Notice { id: number; actor_id: number; kind: string; is_read: boolean; created_at: string; first_name: string; last_name: string; username: string; }
interface NoticeBatch { items: Notice[]; has_more: boolean; }

@Component({
  selector: 'app-notifications-page', imports: [DatePipe, RouterLink],
  template: `<section class="page-width section-space"><p class="eyebrow">A little something for you</p><div class="notification-heading"><h1 class="page-title">Your connections, unfolding.</h1><button class="button secondary" [disabled]="!hasUnread() || busy()" (click)="readAll()">Mark all as read</button></div>@if (error()) { <p class="error" role="alert">{{ error() }}</p> }<div class="notification-list card">@for (notice of notices(); track notice.id) { <article class="notification" [class.unread]="!notice.is_read"><div><a [routerLink]="notice.kind === 'message' ? ['/messages',notice.actor_id] : ['/people',notice.actor_id]" (click)="read(notice.id)"><strong>{{ notice.first_name }} {{ notice.last_name }}</strong> {{ description(notice.kind) }}</a><time>{{ notice.created_at | date:'medium' }}</time></div>@if (!notice.is_read) { <button class="button small secondary" [disabled]="busy()" (click)="read(notice.id)" [attr.aria-label]="'Mark notification ' + notice.id + ' as read'">Mark read</button> }</article> } @empty { <div class="empty"><h2>A little anticipation.</h2><p>Your likes, visitors, connections and messages will appear here.</p></div> }</div>@if (more()) { <div class="load-more"><button class="button secondary" [disabled]="loading()" (click)="next()">Show earlier notifications</button></div> }</section>`,
  styles: `.notification-heading{display:flex;align-items:center;justify-content:space-between;gap:24px;margin-bottom:20px}.notification-heading h1{margin-bottom:0}.notification-list{padding:8px 24px}.notification{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:22px 12px;border-bottom:1px solid var(--line);border-left:3px solid transparent}.notification:last-child{border-bottom:0}.notification.unread{border-left-color:#87619f;background:#f7f3fa}.notification a{font-size:.98rem;line-height:1.6}.notification time{display:block;font-size:.8rem;color:var(--muted);margin-top:6px}.notification button{white-space:nowrap}.load-more{text-align:center;margin-top:28px}@media(max-width:700px){.notification-heading{align-items:flex-start;flex-direction:column}.notification-list{padding:6px 12px}.notification{flex-wrap:wrap;gap:12px}.notification-heading h1{font-size:2.2rem}}`,
})
export class NotificationsPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiClient); private readonly live = inject(LiveUpdates);
  readonly notices = signal<Notice[]>([]); readonly more = signal(false); readonly error = signal(''); readonly loading = signal(false); readonly busy = signal(false);
  private pages = 1; private queued = false; private destroyed = false; private subscription?: Subscription;
  ngOnInit() { this.subscription = this.live.changes.subscribe(() => { void this.load(); }); void this.load(); }
  ngOnDestroy() { this.destroyed = true; this.subscription?.unsubscribe(); }
  hasUnread() { return this.notices().some(notice => !notice.is_read); }
  description(kind: string) { return ({ like: 'liked your profile.', visit: 'visited your profile.', connection: "liked you back. You're connected.", message: 'sent you a message.', unlike: 'removed their like. This conversation is closed.' } as Record<string,string>)[kind] || 'updated your connection.'; }
  async read(id: number) { await this.perform(async () => { await this.api.post(`/notifications/${id}/read`); }); }
  async readAll() { const through = this.notices()[0]?.id; if (through) await this.perform(async () => { await this.api.post('/notifications/read-all', { through }); }); }
  next() { this.pages++; void this.load(); }
  private async perform(work: () => Promise<void>) { this.busy.set(true); this.error.set(''); try { await work(); await this.load(); void this.live.refresh(); } catch (error) { this.error.set(errorMessage(error)); } finally { this.busy.set(false); } }
  private async load() {
    if (this.destroyed) return;
    if (this.loading()) { this.queued = true; return; } this.loading.set(true);
    try {
      const notices: Notice[] = []; let more = true; let before = '';
      for (let page = 0; page < this.pages && more; page++) { const batch = await this.api.get<NoticeBatch>('/notifications' + (before ? `?before=${before}` : '')); notices.push(...batch.items); more = batch.has_more; before = String(batch.items.at(-1)?.id || ''); }
      if (!this.destroyed) { this.notices.set(notices); this.more.set(more); }
    } catch (error) { if (!this.destroyed) this.error.set(errorMessage(error)); }
    finally { this.loading.set(false); if (this.queued) { this.queued = false; void this.load(); } }
  }
}
