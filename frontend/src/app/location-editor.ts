import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiClient, errorMessage } from './api';
import type { OwnProfile } from './profile-page';

interface Place { id: number; name: string; country: string; latitude: number; longitude: number; }

@Component({
  selector: 'app-location-editor',
  imports: [FormsModule],
  template: `
    <div class="details-layout">
      <section class="card">
        <h2>A little more about you.</h2>
        <p class="fine-print">Your birth date stays private. Other people see your age.</p>
        @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
        @if (message()) { <p class="success" role="status">{{ message() }}</p> }
        <form #ageForm="ngForm" (ngSubmit)="saveAge()">
          <label class="field">Birth date<input type="date" name="birth_date" [(ngModel)]="birthDate" required [max]="maximumBirthDate"></label>
          <button type="submit" class="button secondary" [disabled]="ageForm.invalid || busy()">Save birth date</button>
        </form>
        <div class="popularity"><span class="score">{{ profile().popularity || 0 }}<small>/100</small></span><div><h3>Your popularity</h3><p class="fine-print">Based on received likes and unique profile visits.</p></div></div>
      </section>
      <section class="card">
        <h2>Your corner of the world.</h2><p class="fine-print">Choose an approximate city or neighborhood to discover people nearby. Exact coordinates stay private.</p>
        <label class="consent"><input type="checkbox" [(ngModel)]="consent" name="gps_consent">I consent to using my browser location for nearby matching.</label>
        <button type="button" class="button secondary" [disabled]="!consent || busy()" (click)="useGps()">Use my location</button>
        <p class="fine-print">GPS is optional. You can always choose your place manually below.</p>
        <form #searchForm="ngForm" (ngSubmit)="search()" class="search-row"><label class="field">City search<input name="city_search" [(ngModel)]="query" required minlength="2" maxlength="80" placeholder="For example, Paris"></label><button class="button secondary" type="submit" [disabled]="searchForm.invalid || busy()">Search places</button></form>
        @if (searched() && !results().length) { <p class="fine-print">No city found. Enter your approximate place and coordinates below.</p> }
        <ul class="place-results">@for (place of results(); track place.id) { <li><button type="button" (click)="choose(place)">{{ place.name }}, {{ place.country }}</button></li> }</ul>
        <form #locationForm="ngForm" (ngSubmit)="saveLocation()">
          <label class="field">City or neighborhood<input name="city" [(ngModel)]="city" required maxlength="120" placeholder="Choose a city above or enter a neighborhood"></label>
          <details><summary>Enter an approximate point manually</summary><p class="fine-print">Use a city or neighborhood center, not your home address.</p><div class="form-grid"><label class="field">Approximate latitude<input type="number" name="latitude" [(ngModel)]="latitude" min="-90" max="90" step="0.01" (ngModelChange)="manual()"></label><label class="field">Approximate longitude<input type="number" name="longitude" [(ngModel)]="longitude" min="-180" max="180" step="0.01" (ngModelChange)="manual()"></label></div></details>
          <button class="button" type="submit" [disabled]="locationForm.invalid || latitude === null || longitude === null || busy()">Save approximate location</button>
        </form>
        <p class="fine-print">Offline place data: <a class="text-link" href="https://www.geonames.org/" target="_blank" rel="noreferrer">GeoNames</a>, CC BY 4.0. Coordinates are rounded before storage.</p>
      </section>
    </div>
  `,
  styles: `.details-layout{display:grid;grid-template-columns:1fr 1fr;gap:28px;margin-top:28px;align-items:start}.consent{display:flex;align-items:flex-start;gap:12px;line-height:1.6;font-size:.9rem;margin:20px 0}.consent input{width:18px;min-width:18px;margin-top:4px}.search-row{display:flex;gap:12px;align-items:end}.search-row label{flex:1;margin-bottom:0}.search-row .button{padding:12px 18px;white-space:nowrap}.place-results{list-style:none;padding:0;margin:12px 0}.place-results button{width:100%;text-align:left;background:var(--soft);border:0;padding:12px;border-radius:8px;margin-bottom:5px}details{margin:20px 0}summary{cursor:pointer;font-size:.9rem;color:var(--green);margin-bottom:15px}.popularity{border-top:1px solid var(--line);margin-top:30px;padding-top:25px;display:flex;align-items:center;gap:20px}.popularity h3,.popularity p{margin-bottom:0}.score{font-size:2.5rem;color:var(--green)}.score small{font-size:.9rem}@media(max-width:850px){.details-layout{grid-template-columns:1fr}}@media(max-width:400px){.search-row{display:block}.search-row button{margin-top:10px}}`,
})
export class LocationEditor implements OnInit {
  readonly profile = input.required<OwnProfile>();
  private readonly api = inject(ApiClient);
  birthDate = ''; city = ''; query = ''; latitude: number | null = null; longitude: number | null = null; consent = false;
  private source = 'manual';
  readonly results = signal<Place[]>([]); readonly searched = signal(false); readonly busy = signal(false); readonly error = signal(''); readonly message = signal('');
  readonly maximumBirthDate = new Date(new Date().setFullYear(new Date().getFullYear() - 18)).toISOString().slice(0, 10);
  ngOnInit() { const profile = this.profile(); this.birthDate = profile.birth_date || ''; this.city = profile.city; this.latitude = profile.latitude; this.longitude = profile.longitude; this.consent = profile.gps_consent; this.source = profile.gps_consent ? 'gps' : 'manual'; }
  async search() { await this.perform(async () => { this.results.set(await this.api.get<Place[]>('/locations?q=' + encodeURIComponent(this.query))); this.searched.set(true); }); }
  choose(place: Place) { this.city = `${place.name}, ${place.country}`.slice(0, 120); this.latitude = place.latitude; this.longitude = place.longitude; this.manual(); this.results.set([]); this.searched.set(false); }
  manual() { this.source = 'manual'; this.consent = false; }
  async saveAge() { await this.perform(async () => { this.message.set((await this.api.put<{ message: string }>('/me/details', { birth_date: this.birthDate })).message); }); }
  async saveLocation() { await this.perform(async () => { this.message.set((await this.api.put<{ message: string }>('/me/location', { city: this.city, latitude: this.latitude, longitude: this.longitude, gps_consent: this.source === 'gps' && this.consent, source: this.source })).message); }); }
  async useGps() {
    if (!this.consent) return;
    await this.perform(async () => {
      try {
        if (!navigator.geolocation) throw new Error('Location unavailable');
        const position = await new Promise<GeolocationPosition>((resolve, reject) => navigator.geolocation.getCurrentPosition(resolve, reject, { enableHighAccuracy: false, timeout: 10000, maximumAge: 0 }));
        this.latitude = Math.round(position.coords.latitude * 100) / 100; this.longitude = Math.round(position.coords.longitude * 100) / 100;
        const place = await this.api.get<Place>(`/locations/nearest?latitude=${this.latitude}&longitude=${this.longitude}`);
        this.city = `${place.name}, ${place.country}`.slice(0, 120); this.source = 'gps'; this.message.set('Approximate location found. Review it and save below.');
      } catch { this.manual(); this.message.set('Location access was unavailable. Choose a city or enter an approximate location below.'); }
    });
  }
  private async perform(work: () => Promise<void>) {
    this.error.set(''); this.message.set(''); this.busy.set(true);
    try { await work(); } catch (error) { this.error.set(errorMessage(error)); } finally { this.busy.set(false); }
  }
}
