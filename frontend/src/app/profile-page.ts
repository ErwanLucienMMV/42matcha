import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiClient, errorMessage } from './api';
import { Session } from './session';
import { LocationEditor } from './location-editor';

export interface Photo { id: number; is_primary: boolean; }
export interface OwnProfile {
  id: number; username: string; first_name: string; last_name: string; email: string;
  gender: string | null; orientation: string; biography: string; tags: string[]; photos: Photo[];
  birth_date: string | null; city: string; latitude: number | null; longitude: number | null; gps_consent: boolean;
  popularity: number; age: number | null;
}

@Component({
  selector: 'app-profile-page',
  imports: [FormsModule, LocationEditor],
  template: `
    <section class="page-width section-space">
      <p class="eyebrow">Your little corner</p><h1 class="page-title">More than a first impression.</h1>
      <p class="muted">Share a little of your world. A good connection starts with you.</p>
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @if (message()) { <p class="success" role="status">{{ message() }}</p> }
      @if (profile(); as profile) {
        <div class="profile-layout">
          <form #form="ngForm" (ngSubmit)="save()" class="card">
            <h2>The essentials</h2>
            <div class="form-grid"><label class="field">First name<input name="first_name" [(ngModel)]="profile.first_name" required maxlength="80" autocomplete="given-name"></label><label class="field">Last name<input name="last_name" [(ngModel)]="profile.last_name" required maxlength="80" autocomplete="family-name"></label></div>
            <label class="field">Email<input name="email" [(ngModel)]="profile.email" type="email" email required maxlength="254" autocomplete="email"></label>
            <p class="fine-print">Only you can see your email. Changes require email verification.</p>
            <div class="form-grid">
              <label class="field">Gender<select name="gender" [(ngModel)]="profile.gender" required><option [ngValue]="null" disabled>Choose your gender</option><option value="woman">Woman</option><option value="man">Man</option><option value="nonbinary">Nonbinary</option></select></label>
              <label class="field">Orientation<select name="orientation" [(ngModel)]="profile.orientation"><option value="bisexual">Bisexual</option><option value="heterosexual">Heterosexual</option><option value="homosexual">Homosexual</option></select></label>
            </div>
            <label class="field">A little about you<textarea name="biography" [(ngModel)]="profile.biography" required maxlength="2000" placeholder="The things you love, a perfect Sunday, a story worth sharing…"></textarea></label>
            <label class="field">Your interests<input name="tags" [(ngModel)]="tags" required maxlength="409" placeholder="coffee, hiking, cinema" list="interest-suggestions"></label>
            <datalist id="interest-suggestions">@for (tag of suggestions(); track tag.name) { <option [value]="tag.name"></option> }</datalist>
            <p class="fine-print">One to ten tags, separated by commas. Use letters, numbers, hyphens or underscores.</p>
            <button class="button" type="submit" [disabled]="form.invalid || busy()">{{ busy() ? 'Saving…' : 'Save profile' }}</button>
          </form>
          <aside class="card photos-panel" aria-label="Your photos">
            <h2>A face to say hello to.</h2><p class="fine-print">Up to five JPEG or PNG photos. Maximum 5 MiB and 4096 pixels per side. Add a profile photo before sending likes.</p>
            <div class="photo-grid">
              @for (photo of photos(); track photo.id) {
                <figure><img [src]="'/api/photos/' + photo.id" [alt]="photo.is_primary ? 'Your profile photo' : 'Your gallery photo'">
                  <figcaption>@if (photo.is_primary) { <span class="tag">Profile photo</span> } @else { <button type="button" class="photo-action" [disabled]="busy()" (click)="primary(photo.id)">Make profile photo</button> }<button class="photo-action remove" type="button" [disabled]="busy()" (click)="remove(photo.id)" [attr.aria-label]="'Remove photo ' + photo.id">Remove</button></figcaption>
                </figure>
              }
            </div>
            <label class="upload-label">Add a photo<input type="file" accept="image/jpeg,image/png" [disabled]="busy() || photos().length >= 5" (change)="upload($event)"></label>
            <p class="fine-print">{{ photos().length }} of 5 photos</p>
          </aside>
        </div>
        <app-location-editor [profile]="profile" />
      } @else { <p role="status">Getting your profile…</p> }
    </section>
  `,
  styles: `
    .profile-layout{display:grid;grid-template-columns:1.2fr 1fr;gap:28px;align-items:start}.photos-panel{position:sticky;top:20px}.photo-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}figure{margin:0 0 18px}figure img{display:block;width:100%;aspect-ratio:4/5;object-fit:cover;border-radius:14px;background:var(--soft)}figcaption{display:flex;gap:8px;align-items:center;flex-wrap:wrap;padding-top:9px}.photo-action{border:0;padding:3px 0;background:transparent;color:var(--green);font-size:.82rem;text-decoration:underline}.photo-action.remove{color:var(--red)}.upload-label{display:block;font-size:.9rem;font-weight:600;margin:10px 0}.upload-label input{margin-top:10px;padding:10px;font-size:.85rem}@media(max-width:850px){.profile-layout{grid-template-columns:1fr}.photos-panel{position:static}}
  `,
})
export class ProfilePage implements OnInit {
  private readonly api = inject(ApiClient);
  private readonly session = inject(Session);
  readonly profile = signal<OwnProfile | null>(null);
  tags = '';
  readonly photos = signal<Photo[]>([]); readonly suggestions = signal<{ name: string }[]>([]);
  readonly error = signal(''); readonly message = signal(''); readonly busy = signal(false);
  async ngOnInit() {
    try {
      const profile = await this.api.get<OwnProfile>('/me');
      this.profile.set(profile);
      this.tags = profile.tags.join(', '); this.photos.set(profile.photos);
      this.suggestions.set(await this.api.get<{ name: string }[]>('/tags'));
    } catch (error) { this.error.set(errorMessage(error)); }
  }
  async save() {
    await this.perform(async () => {
      const response = await this.api.put<{ message: string }>('/me', { ...this.profile(), tags: this.tags.split(',').map(tag => tag.trim()).filter(Boolean) });
      this.message.set(response.message); await this.session.refresh();
    });
  }
  async upload(event: Event) {
    const input = event.target as HTMLInputElement; const file = input.files?.[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024 || !['image/jpeg', 'image/png'].includes(file.type)) { this.error.set('Choose a JPEG or PNG image no larger than 5 MiB.'); input.value = ''; return; }
    await this.perform(async () => { await this.api.post('/me/photos', file); await this.refreshPhotos(); this.message.set('Photo added.'); });
    input.value = '';
  }
  async primary(id: number) { await this.perform(async () => { await this.api.post(`/me/photos/${id}/primary`); await this.refreshPhotos(); this.message.set('Profile photo updated.'); }); }
  async remove(id: number) { await this.perform(async () => { await this.api.delete(`/me/photos/${id}`); await this.refreshPhotos(); this.message.set('Photo removed.'); }); }
  private async refreshPhotos() { this.photos.set((await this.api.get<OwnProfile>('/me')).photos); }
  private async perform(work: () => Promise<void>) {
    this.error.set(''); this.message.set(''); this.busy.set(true);
    try { await work(); } catch (error) { this.error.set(errorMessage(error)); } finally { this.busy.set(false); }
  }
}
