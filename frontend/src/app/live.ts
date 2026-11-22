import { Injectable, effect, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { ApiClient } from './api';
import { Session } from './session';

@Injectable({ providedIn: 'root' })
export class LiveUpdates {
  private readonly api = inject(ApiClient); private readonly session = inject(Session); private readonly router = inject(Router);
  readonly unreadMessages = signal(0); readonly unreadNotifications = signal(0); readonly connected = signal(false);
  readonly changes = new Subject<void>();
  private socket: WebSocket | null = null;
  private retry: ReturnType<typeof setTimeout> | undefined;
  private poll: ReturnType<typeof setInterval> | undefined;
  private refreshing = false;

  constructor() {
    effect(cleanup => {
      const user = this.session.user();
      if (user) { this.connect(); void this.refresh(); this.poll = setInterval(() => { void this.refresh(); }, 5000); }
      else { this.unreadMessages.set(0); this.unreadNotifications.set(0); }
      cleanup(() => this.stop());
    });
  }

  async refresh() {
    const user = this.session.user();
    if (!user || this.refreshing) return;
    this.refreshing = true;
    try {
      const result = await this.api.get<{ unread_messages: number; unread_notifications: number }>('/updates');
      if (this.session.user()?.id !== user.id) return;
      this.unreadMessages.set(result.unread_messages); this.unreadNotifications.set(result.unread_notifications); this.changes.next();
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 401) await this.expired();
    } finally { this.refreshing = false; }
  }

  private connect() {
    if (!this.session.user() || this.socket) return;
    const socket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/api/live`);
    this.socket = socket;
    socket.onopen = () => { if (this.socket === socket) { this.connected.set(true); void this.refresh(); } };
    socket.onmessage = event => {
      if (this.socket !== socket) return;
      if (event.data === 'heartbeat' && socket.readyState === WebSocket.OPEN) socket.send('ping');
      else if (event.data === 'refresh' || event.data === 'ready') void this.refresh();
    };
    socket.onerror = () => { this.connected.set(false); };
    socket.onclose = event => {
      if (this.socket !== socket) return;
      this.socket = null; this.connected.set(false);
      if (event.code === 1008) { void this.expired(); return; }
      if (this.session.user()) this.retry = setTimeout(() => this.connect(), 2000);
    };
  }

  private async expired() {
    try { await this.session.refresh(); if (!this.session.user()) await this.router.navigateByUrl('/login'); }
    catch { /* The fallback timer retries after a temporary connection failure. */ }
  }

  private stop() {
    clearTimeout(this.retry); clearInterval(this.poll);
    const socket = this.socket; this.socket = null; this.connected.set(false);
    if (socket?.readyState === WebSocket.OPEN) socket.close(1000, 'Page session changed');
    else if (socket?.readyState === WebSocket.CONNECTING) socket.addEventListener('open', () => socket.close(1000, 'Page session changed'), { once: true });
  }
}
