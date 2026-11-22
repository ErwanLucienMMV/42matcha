import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiClient, errorMessage } from './api';
import { LiveUpdates } from './live';
import { Session } from './session';

interface Connection { id: number; first_name: string; last_name: string; username: string; photo_id: number | null; unread_count: number; }
interface Message { id: number; sender_id: number; recipient_id: number; body: string; created_at: string; }
interface MessageBatch { items: Message[]; has_more: boolean; }

@Component({
  selector: 'app-messages-page', imports: [FormsModule, RouterLink, DatePipe],
  template: `
    <section class="page-width section-space"><p class="eyebrow">Make it a conversation</p><h1 class="page-title">A little hello.</h1>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      <div class="messenger card">
        <aside class="connection-list" aria-label="Your connections"><h2>Your connections</h2>@for (person of connections(); track person.id) { <a class="connection" [class.active]="person.id === partnerId" [routerLink]="['/messages',person.id]">@if (person.photo_id) { <img [src]="'/api/photos/' + person.photo_id" alt=""> }<span><strong>{{ person.first_name }} {{ person.last_name }}</strong><small>{{ person.username }}</small></span>@if (person.unread_count) { <span class="tag">{{ person.unread_count }}</span> }</a> } @empty { <p class="fine-print">When the feeling is mutual, your connections appear here.</p> }</aside>
        <section class="conversation" aria-label="Conversation">
          @if (peer(); as peer) {
            <header class="conversation-header"><a class="text-link" [routerLink]="['/people',peer.id]">{{ peer.first_name }} {{ peer.last_name }}</a>@if (!live.connected()) { <span class="fine-print">Reconnecting. Your messages are saved.</span> }</header>
            <div class="message-list" aria-label="Messages">
              @if (hasEarlier()) { <button class="button small secondary" [disabled]="loadingEarlier()" (click)="earlier()">Load earlier messages</button> }
              @for (message of messages(); track message.id) { <article class="message" [class.mine]="message.sender_id === session.user()?.id"><p>{{ message.body }}</p><time>{{ message.created_at | date:'shortTime' }}</time></article> } @empty { <p class="empty">You have something in common. Start with hello.</p> }
            </div>
            <form class="compose-message" #form="ngForm" (ngSubmit)="send()"><label class="sr-only" for="message-body">Your message</label><textarea id="message-body" name="body" [(ngModel)]="draft" maxlength="4000" required rows="2" placeholder="A little hello…"></textarea><button class="button" [disabled]="form.invalid || !draft.trim() || sending()">{{ sending() ? 'Sending…' : 'Send message' }}</button></form>
          } @else { <div class="empty"><h2>A conversation worth having.</h2><p>Choose a connection to say hello.</p></div> }
        </section>
      </div>
    </section>
  `,
  styles: `.messenger{padding:0;display:grid;grid-template-columns:290px 1fr;min-height:560px;overflow:hidden}.connection-list{padding:24px;border-right:1px solid var(--line)}.connection-list h2{font-size:1.3rem}.connection{display:flex;align-items:center;gap:12px;padding:15px 8px;border-radius:10px}.connection.active{background:var(--soft)}.connection img{width:42px;height:48px;object-fit:cover;border-radius:9px}.connection small{display:block;color:var(--muted);font-size:.8rem;margin-top:4px}.connection strong{font-size:.9rem}.conversation{display:flex;flex-direction:column;min-width:0}.conversation-header{padding:22px;border-bottom:1px solid var(--line);display:flex;gap:18px;justify-content:space-between;flex-wrap:wrap}.message-list{height:420px;overflow-y:auto;padding:24px;display:flex;flex-direction:column;align-items:flex-start;gap:16px}.message{max-width:85%;padding:12px 16px;border-radius:16px 16px 16px 3px;background:var(--soft)}.message.mine{align-self:flex-end;background:var(--lilac);border-radius:16px 16px 3px 16px}.message p{white-space:pre-wrap;overflow-wrap:anywhere;margin-bottom:4px;font-size:.95rem}.message time{font-size:.75rem;color:var(--muted)}.compose-message{display:flex;align-items:end;gap:14px;border-top:1px solid var(--line);padding:20px;margin-top:auto}.compose-message textarea{min-height:72px;max-height:180px;resize:vertical}.compose-message button{white-space:nowrap}@media(max-width:750px){.messenger{grid-template-columns:1fr}.connection-list{border-right:0;border-bottom:1px solid var(--line);max-height:180px;overflow-y:auto;padding:18px}.message-list{height:50vh;padding:18px}.compose-message{flex-direction:column;align-items:stretch;padding:16px}.conversation-header{padding:18px}}`,
})
export class MessagesPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiClient); private readonly route = inject(ActivatedRoute);
  readonly session = inject(Session); readonly live = inject(LiveUpdates);
  readonly connections = signal<Connection[]>([]); readonly messages = signal<Message[]>([]); readonly error = signal('');
  readonly hasEarlier = signal(false); readonly loadingEarlier = signal(false); readonly sending = signal(false);
  partnerId = 0; draft = '';
  private acknowledged = 0; private running = false; private queued = false;
  private readonly subscriptions = new Subscription();
  peer() { return this.connections().find(person => person.id === this.partnerId); }
  ngOnInit() {
    this.subscriptions.add(this.route.paramMap.subscribe(params => { this.partnerId = Number(params.get('id')) || 0; this.messages.set([]); this.hasEarlier.set(false); this.acknowledged = 0; this.draft = ''; void this.refresh(); }));
    this.subscriptions.add(this.live.changes.subscribe(() => { void this.refresh(); }));
  }
  ngOnDestroy() { this.subscriptions.unsubscribe(); }
  private async refresh() {
    if (this.running) { this.queued = true; return; } this.running = true;
    const partner = this.partnerId;
    try {
      this.connections.set(await this.api.get<Connection[]>('/connections'));
      if (partner !== this.partnerId) return;
      if (!partner) return;
      if (!this.peer()) { this.messages.set([]); this.error.set('This conversation is no longer available. Chat requires a mutual connection.'); return; }
      this.error.set('');
      let after = this.messages().at(-1)?.id || 0;
      let more = true;
      while (more) {
        const batch = await this.api.get<MessageBatch>(`/chat/${partner}` + (after ? `?after=${after}` : ''));
        if (partner !== this.partnerId) return;
        if (!after) this.hasEarlier.set(batch.has_more);
        this.merge(batch.items);
        more = after > 0 && batch.has_more;
        after = this.messages().at(-1)?.id || 0;
      }
      const incoming = this.messages().filter(message => message.recipient_id === this.session.user()?.id).at(-1)?.id || 0;
      if (incoming > this.acknowledged) { this.acknowledged = incoming; await this.api.post(`/chat/${partner}/read`, { through: incoming }); void this.live.refresh(); }
    } catch (error) { this.error.set(errorMessage(error)); }
    finally { this.running = false; if (this.queued) { this.queued = false; void this.refresh(); } }
  }
  async send() {
    if (!this.draft.trim() || this.sending()) return; this.sending.set(true);
    const partner = this.partnerId; const body = this.draft;
    try { await this.api.post(`/chat/${partner}`, { body }); if (this.partnerId === partner) { this.draft = ''; await this.refresh(); } }
    catch (error) { this.error.set(errorMessage(error)); } finally { this.sending.set(false); }
  }
  async earlier() {
    const first = this.messages()[0]?.id; if (!first) return; this.loadingEarlier.set(true);
    const partner = this.partnerId;
    try { const batch = await this.api.get<MessageBatch>(`/chat/${partner}?before=${first}`); if (partner === this.partnerId) { this.merge(batch.items); this.hasEarlier.set(batch.has_more); } }
    catch (error) { this.error.set(errorMessage(error)); } finally { this.loadingEarlier.set(false); }
  }
  private merge(incoming: Message[]) { const merged = new Map(this.messages().map(message => [message.id,message])); for (const message of incoming) merged.set(message.id,message); this.messages.set([...merged.values()].sort((a,b) => a.id-b.id)); }
}
