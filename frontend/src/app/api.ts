import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ApiClient {
  private readonly http = inject(HttpClient);
  get<T>(path: string) { return firstValueFrom(this.http.get<T>('/api' + path)); }
  post<T>(path: string, body: unknown = {}) { return firstValueFrom(this.http.post<T>('/api' + path, body)); }
  put<T>(path: string, body: unknown) { return firstValueFrom(this.http.put<T>('/api' + path, body)); }
  delete<T>(path: string) { return firstValueFrom(this.http.delete<T>('/api' + path)); }
}

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) return error.error?.error || 'We could not complete that request. Please try again.';
  return 'Something went wrong. Please try again.';
}
