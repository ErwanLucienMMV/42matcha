import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { Session } from './session';
import { errorMessage } from './api';

@Component({
  imports: [RouterLink, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  readonly session = inject(Session);
  readonly error = signal('');
  async logout() {
    try { await this.session.logout(); }
    catch (error) { this.error.set(errorMessage(error)); }
  }
}
