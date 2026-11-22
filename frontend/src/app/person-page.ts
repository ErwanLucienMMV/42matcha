import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiClient, errorMessage } from './api';
import type { Photo } from './profile-page';
import { LiveUpdates } from './live';

interface PublicProfile {
  id: number; username: string; first_name: string; last_name: string; age: number | null;
  gender: string | null; orientation: string; biography: string; city: string; tags: string[];
  photos: Photo[]; popularity: number; online: boolean; last_seen: string | null;
  i_like: boolean; likes_me: boolean; connected: boolean; own_photo: boolean;
}

@Component({
  selector: 'app-person-page',
  imports: [RouterLink, FormsModule, DatePipe, TitleCasePipe],
  template: `
    <section class="page-width section-space">
      <a class="text-link" routerLink="/discover">Back to discover</a>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @if (message()) { <p class="success" role="status">{{ message() }}</p> }
      @if (person(); as person) {
        <div class="person-layout">
          <div class="gallery"><div class="hero-photo">@if (selected()) { <img [src]="'/api/photos/' + selected()" [alt]="person.first_name + ' profile photo'"> } @else { <span>{{ person.first_name.slice(0,1) }}</span> }</div><div class="thumbnails">@for (photo of person.photos; track photo.id) { <button (click)="selected.set(photo.id)" [attr.aria-label]="'View photo ' + photo.id"><img [src]="'/api/photos/' + photo.id" alt=""></button> }</div></div>
          <div class="person-detail">
            <p class="eyebrow">{{ person.city || 'Getting to know their world' }}</p><h1 class="page-title">{{ person.first_name }} {{ person.last_name }}@if (person.age) {, {{ person.age }} }</h1><p class="muted">{{ person.username }}</p>
            <p class="presence">@if (person.online) { <span class="online-dot"></span>Online now } @else if (person.last_seen) { Last seen {{ person.last_seen | date:'medium' }} } @else { Offline }</p>
            <div class="facts"><span>{{ person.gender | titlecase }}</span><span>{{ person.orientation | titlecase }}</span><span>{{ person.popularity }} / 100 popularity</span></div>
            <h2>A little about {{ person.first_name }}</h2><p class="biography">{{ person.biography || 'A story still being written.' }}</p><div class="tags">@for (tag of person.tags; track tag) { <span class="tag">#{{ tag }}</span> }</div>
            <div class="relationship">
              @if (person.connected) { <p class="connected">The feeling is mutual. You're connected.</p> }
              @else if (person.likes_me) { <p class="connected">{{ person.first_name }} has liked you.</p> }
              @if (!person.own_photo) { <p class="fine-print">Add a profile photo before sending likes. <a class="text-link" routerLink="/profile">Edit your profile</a></p> }
              <div class="actions"><button class="button" [disabled]="busy() || (!person.i_like && (!person.own_photo || !person.photos.length))" (click)="toggleLike()">{{ person.i_like ? 'Unlike' : 'Like profile' }}</button>@if (person.connected) { <a class="button secondary" [routerLink]="['/messages',person.id]">Say hello</a> }</div>
            </div>
            <div class="moderation"><button class="text-button" (click)="reporting.set(!reporting())">Report fake account</button><button class="text-button danger-text" [disabled]="busy()" (click)="block()">Block user</button><p class="fine-print">Blocking removes this person from discovery, chat and notifications.</p></div>
            @if (reporting()) { <form #reportForm="ngForm" (ngSubmit)="report()"><label class="field">Why do you think this is a fake account?<textarea name="reason" [(ngModel)]="reason" required minlength="3" maxlength="1000"></textarea></label><button class="button secondary" [disabled]="reportForm.invalid || busy()">Send report</button></form> }
          </div>
        </div>
      } @else if (!error()) { <p class="empty" role="status">Getting to know this profile…</p> }
    </section>
  `,
  styles: `.person-layout{display:grid;grid-template-columns:1fr 1fr;gap:60px;margin-top:34px;align-items:start}.hero-photo{aspect-ratio:4/5;border-radius:24px;overflow:hidden;background:linear-gradient(140deg,#e8e1f2,#c2d4c6);display:grid;place-items:center}.hero-photo img{width:100%;height:100%;object-fit:cover}.hero-photo>span{font-size:9rem;font-family:Georgia,serif;color:#254f3d80}.thumbnails{display:flex;gap:12px;margin-top:16px}.thumbnails button{padding:0;border:0;background:transparent;width:72px;height:82px;border-radius:10px;overflow:hidden}.thumbnails img{width:100%;height:100%;object-fit:cover}.person-detail h1{margin-bottom:8px}.presence{font-size:.9rem;color:var(--green)}.online-dot{display:inline-block;width:8px;height:8px;background:#2a8c52;border-radius:50%;margin-right:8px}.facts{display:flex;gap:12px;flex-wrap:wrap;font-size:.85rem;padding:18px 0 28px;border-bottom:1px solid var(--line);margin-bottom:28px}.biography{white-space:pre-wrap;overflow-wrap:anywhere}.relationship{margin-top:28px;padding-top:24px;border-top:1px solid var(--line)}.connected{color:var(--green);font-weight:600}.moderation{margin-top:30px}.text-button{border:0;background:transparent;color:var(--green);padding:0;margin:0 20px 12px 0;font-size:.9rem;text-decoration:underline}.danger-text{color:var(--red)}@media(max-width:800px){.person-layout{grid-template-columns:1fr;gap:28px}.hero-photo{max-height:550px}}`,
})
export class PersonPage implements OnInit, OnDestroy {
  private readonly api = inject(ApiClient); private readonly router = inject(Router);
  private readonly id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));
  private readonly live = inject(LiveUpdates);
  private subscription?: Subscription;
  readonly person = signal<PublicProfile | null>(null); readonly selected = signal<number | null>(null);
  readonly error = signal(''); readonly message = signal(''); readonly busy = signal(false); readonly reporting = signal(false);
  reason = '';
  async ngOnInit() {
    this.subscription = this.live.changes.subscribe(() => { if (this.person() && !this.busy()) void this.reload().catch(error => { this.person.set(null); this.error.set(errorMessage(error)); }); });
    await this.perform(async () => { await this.api.post(`/people/${this.id}/visit`); await this.reload(); });
  }
  ngOnDestroy() { this.subscription?.unsubscribe(); }
  async toggleLike() { await this.perform(async () => { const result = this.person()!.i_like ? await this.api.delete<{ message: string }>(`/people/${this.id}/like`) : await this.api.post<{ message: string }>(`/people/${this.id}/like`); this.message.set(result.message); await this.reload(); }); }
  async block() { await this.perform(async () => { await this.api.post(`/people/${this.id}/block`); await this.router.navigateByUrl('/activity'); }); }
  async report() { await this.perform(async () => { this.message.set((await this.api.post<{ message: string }>(`/people/${this.id}/report`, { reason: this.reason })).message); this.reporting.set(false); }); }
  private async reload() { const person = await this.api.get<PublicProfile>(`/people/${this.id}`); this.person.set(person); if (!this.selected()) this.selected.set(person.photos[0]?.id || null); }
  private async perform(work: () => Promise<void>) { this.busy.set(true); this.error.set(''); this.message.set(''); try { await work(); } catch (error) { this.error.set(errorMessage(error)); } finally { this.busy.set(false); } }
}
