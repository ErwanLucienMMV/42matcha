import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiClient, errorMessage } from './api';

interface Activity { id: number; username: string; first_name: string; last_name: string; visited_at?: string; created_at?: string; }

@Component({
  selector: 'app-activity-page', imports: [RouterLink, DatePipe],
  template: `<section class="page-width section-space"><p class="eyebrow">A little interest goes a long way</p><h1 class="page-title">Who's stopped by.</h1>@if (error()) { <p class="error" role="alert">{{ error() }}</p> }<div class="activity-grid"><section class="card"><h2>Your visitors</h2>@for (person of visitors(); track person.id) { <a class="activity-row" [routerLink]="['/people',person.id]"><strong>{{ person.first_name }} {{ person.last_name }}</strong><span>{{ person.visited_at | date:'short' }}</span></a> } @empty { <p class="muted">Your visitors will appear here.</p> }</section><section class="card"><h2>People who like you</h2>@for (person of likes(); track person.id) { <a class="activity-row" [routerLink]="['/people',person.id]"><strong>{{ person.first_name }} {{ person.last_name }}</strong><span>{{ person.created_at | date:'short' }}</span></a> } @empty { <p class="muted">A little possibility is on its way.</p> }</section><section class="card"><h2>Blocked accounts</h2>@for (person of blocks(); track person.id) { <div class="activity-row"><strong>{{ person.first_name }} {{ person.last_name }}</strong><button class="button small secondary" (click)="unblock(person.id)">Unblock {{ person.username }}</button></div> } @empty { <p class="muted">You have no blocked accounts.</p> }</section></div></section>`,
  styles: `.activity-grid{display:grid;grid-template-columns:1fr 1fr;gap:24px;align-items:start}.activity-row{display:flex;align-items:center;justify-content:space-between;gap:15px;padding:16px 0;border-top:1px solid var(--line)}.activity-row span{color:var(--muted);font-size:.8rem}.activity-row strong{overflow-wrap:anywhere}@media(max-width:750px){.activity-grid{grid-template-columns:1fr}.activity-row{flex-wrap:wrap}}`,
})
export class ActivityPage implements OnInit {
  private readonly api = inject(ApiClient);
  readonly visitors = signal<Activity[]>([]); readonly likes = signal<Activity[]>([]); readonly blocks = signal<Activity[]>([]); readonly error = signal('');
  ngOnInit() { void this.load(); }
  async unblock(id: number) { try { await this.api.delete(`/people/${id}/block`); await this.load(); } catch (error) { this.error.set(errorMessage(error)); } }
  private async load() { try { const [visitors, likes, blocks] = await Promise.all([this.api.get<Activity[]>('/me/visitors'),this.api.get<Activity[]>('/me/likes'),this.api.get<Activity[]>('/me/blocks')]); this.visitors.set(visitors); this.likes.set(likes); this.blocks.set(blocks); } catch (error) { this.error.set(errorMessage(error)); } }
}
