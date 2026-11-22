import { Component, OnInit, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiClient, errorMessage } from './api';

export interface Person {
  id: number; first_name: string; last_name: string; username: string; age: number;
  city: string; distance_km: number; popularity: number; common_tags: number;
  photo_id: number | null; tags: string[]; biography: string;
}
interface Results { items: Person[]; has_more: boolean; page: number; }

@Component({
  selector: 'app-people-page',
  imports: [FormsModule, RouterLink, DecimalPipe],
  template: `
    <section class="page-width section-space">
      <div class="page-intro"><div><p class="eyebrow">A world in common</p><h1 class="page-title">A little closer to your people.</h1><p class="muted">Shared interests. A familiar neighborhood. A little possibility.</p></div><a class="text-link" routerLink="/profile">Edit my profile</a></div>
      <form #form="ngForm" (ngSubmit)="apply()" class="filters card">
        <div class="filter-grid">
          <label class="field">Age from<input type="number" name="min_age" [(ngModel)]="filters.min_age" min="18" max="120" required></label>
          <label class="field">Age to<input type="number" name="max_age" [(ngModel)]="filters.max_age" min="18" max="120" required></label>
          <label class="field">Popularity from<input type="number" name="min_popularity" [(ngModel)]="filters.min_popularity" min="0" max="100" required></label>
          <label class="field">Popularity to<input type="number" name="max_popularity" [(ngModel)]="filters.max_popularity" min="0" max="100" required></label>
          <label class="field">Within (km)<input type="number" name="max_distance" [(ngModel)]="filters.max_distance" min="1" max="20050" required></label>
          <label class="field">Common interests<input type="number" name="min_common" [(ngModel)]="filters.min_common" min="0" max="10" required></label>
        </div>
        <div class="filter-bottom">
          <label class="field">City or neighborhood<input name="city" [(ngModel)]="filters.city" maxlength="120" placeholder="Anywhere"></label>
          <label class="field">Sort by<select name="sort" [(ngModel)]="filters.sort"><option value="recommended">Recommended</option><option value="age">Age</option><option value="location">Distance</option><option value="popularity">Popularity</option><option value="common_tags">Common interests</option></select></label>
          <label class="field">Order<select name="direction" [(ngModel)]="filters.direction" [disabled]="filters.sort === 'recommended'"><option value="">Default</option><option value="asc">Ascending</option><option value="desc">Descending</option></select></label>
          <button class="button" [disabled]="form.invalid || loading()">Apply filters</button>
        </div>
      </form>
      @if (error()) { <div class="error" role="alert">{{ error() }} <a class="text-link" routerLink="/profile">Complete your profile</a></div> }
      @if (loading()) { <p class="empty" role="status">Finding your people…</p> }
      @else if (!error() && !people().length) { <div class="empty"><h2>A little room for possibility.</h2><p>No profiles match these filters yet. Try a wider age or distance range.</p></div> }
      @else {
        <div class="people-grid">
          @for (person of people(); track person.id) {
            <a class="person-card" [routerLink]="['/people', person.id]" [attr.aria-label]="'View ' + person.first_name + ' profile'">
              <div class="portrait">@if (person.photo_id) { <img [src]="'/api/photos/' + person.photo_id" [alt]="person.first_name + ' profile photo'" loading="lazy"> } @else { <span class="initial">{{ person.first_name.slice(0, 1) }}</span> }<span class="popularity-pill">{{ person.popularity }} popularity</span></div>
              <div class="person-copy"><h2>{{ person.first_name }}, {{ person.age }}</h2><p class="place">{{ person.city }} · {{ person.distance_km | number:'1.0-0' }} km away</p><p class="biography">{{ person.biography }}</p><div class="tags">@for (tag of person.tags; track tag) { <span class="tag">#{{ tag }}</span> }</div><p class="shared">{{ person.common_tags }} interests in common</p></div>
            </a>
          }
        </div>
      }
      @if (!loading() && (people().length || page > 1)) { <nav class="pagination" aria-label="Profile result pages"><button class="button secondary" [disabled]="page <= 1" (click)="turn(-1)">Previous</button><span>Page {{ page }}</span><button class="button secondary" [disabled]="!hasMore()" (click)="turn(1)">Next</button></nav> }
    </section>
  `,
  styles: `
    .page-intro{display:flex;align-items:center;justify-content:space-between;gap:20px}.filters{margin:20px 0 34px;padding:22px}.filter-grid{display:grid;grid-template-columns:repeat(6,1fr);gap:14px}.filter-bottom{display:grid;grid-template-columns:2fr 1fr 1fr auto;gap:14px;align-items:end}.filters label{font-size:.8rem;margin-bottom:14px}.filter-bottom label{margin-bottom:0}.filters input,.filters select{padding:10px;font-size:.9rem}.people-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:26px}.person-card{border:1px solid var(--line);border-radius:20px;overflow:hidden;background:white;transition:box-shadow .2s}.person-card:hover{box-shadow:0 10px 30px #254f3d15}.portrait{height:300px;position:relative;background:linear-gradient(135deg,#e7e0f2,#c4d6c7);display:grid;place-items:center}.portrait img{width:100%;height:100%;object-fit:cover}.initial{font-family:Georgia,serif;font-size:7rem;color:#254f3d70}.popularity-pill{position:absolute;bottom:16px;left:16px;background:#fffef5ed;border-radius:40px;padding:7px 12px;font-size:.78rem}.person-copy{padding:22px}.person-copy h2{font-size:1.55rem;margin-bottom:7px}.place{font-size:.85rem;color:var(--muted);margin-bottom:15px}.biography{display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;font-size:.9rem;min-height:3em}.shared{font-size:.8rem;color:var(--green);margin:16px 0 0}.pagination{display:flex;align-items:center;justify-content:center;gap:24px;margin:38px 0}
    @media(max-width:1000px){.filter-grid{grid-template-columns:repeat(3,1fr)}.people-grid{grid-template-columns:repeat(2,1fr)}.filter-bottom{grid-template-columns:1fr 1fr}.portrait{height:290px}}
    @media(max-width:600px){.people-grid{grid-template-columns:1fr}.filter-grid{grid-template-columns:repeat(2,1fr)}.page-intro{display:block}.page-title{font-size:2.2rem}.filter-bottom>label:first-child{grid-column:1/-1}.filter-bottom .button{grid-column:1/-1}.portrait{height:360px}}
  `,
})
export class PeoplePage implements OnInit {
  private readonly api = inject(ApiClient);
  readonly people = signal<Person[]>([]); readonly error = signal(''); readonly loading = signal(false); readonly hasMore = signal(false);
  page = 1;
  filters = { min_age: 18, max_age: 120, min_popularity: 0, max_popularity: 100, max_distance: 20050, min_common: 0, city: '', sort: 'recommended', direction: '' };
  ngOnInit() { void this.load(); }
  apply() { this.page = 1; void this.load(); }
  turn(change: number) { this.page += change; void this.load(); }
  private async load() {
    this.loading.set(true); this.error.set('');
    try {
      const query = new URLSearchParams({ ...Object.fromEntries(Object.entries(this.filters).map(([key, value]) => [key, String(value)])), page: String(this.page) });
      const results = await this.api.get<Results>('/discover?' + query);
      this.people.set(results.items); this.hasMore.set(results.has_more);
    } catch (error) { this.error.set(errorMessage(error)); this.people.set([]); }
    finally { this.loading.set(false); }
  }
}
