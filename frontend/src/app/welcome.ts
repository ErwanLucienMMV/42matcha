import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-welcome',
  imports: [RouterLink],
  template: `
    <section class="welcome page-width">
      <div class="welcome-copy">
        <p class="eyebrow">A little curiosity. A real connection.</p>
        <h1>Your kind of people.<br><em>A little closer.</em></h1>
        <p class="lead">Meet someone who shares your corner of the world, your small obsessions, and your sense of possibility.</p>
        <a class="button" routerLink="/register">Find your connection</a>
        <p class="fine-print">Start with who you are. See where it goes.</p>
      </div>
      <div class="welcome-art" aria-hidden="true">
        <div class="orbit orbit-one"></div><div class="orbit orbit-two"></div>
        <div class="art-flower">✳</div>
        <span class="interest interest-one">Sunday markets</span><span class="interest interest-two">One more coffee</span><span class="interest interest-three">The great outdoors</span>
        <div class="art-note">Something in common.<br>Something to discover.</div>
      </div>
    </section>
    <section class="principles page-width" aria-label="What matters here">
      <article><span class="eyebrow">Be yourself</span><h2>A profile with personality.</h2><p>Your interests, your stories, your everyday joys. There is more to you than a photo.</p></article>
      <article><span class="eyebrow">Find common ground</span><h2>Closer in more ways.</h2><p>Discover people nearby, with interests and intentions that feel familiar.</p></article>
      <article><span class="eyebrow">Make it mutual</span><h2>A conversation worth having.</h2><p>When the feeling goes both ways, say hello. You decide who gets your time.</p></article>
    </section>
  `,
})
export class Welcome {}
