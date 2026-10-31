import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ApiError, toApiError } from '../api/api-error';

/** {@code GET /me} (API.md SRV-000). */
export interface Me {
  id: string;
  email: string;
  displayName: string | null;
  timeZone: string;
  /** "Today" in the user's time zone, from the server */
  today: string;
  roles: string[];
  /** Only enabled services the user may use */
  services: Record<string, { read: boolean; write: boolean }>;
  lastBudgetId: string | null;
  exportReminder: { due: boolean; days: number; never: boolean };
  version: number;
}

/**
 * The signed-in user and their permissions (SCREENS.md section 6). The first {@code GET /me}
 * also creates the user on the server (API.md 2.4).
 */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly http = inject(HttpClient);

  private readonly me = signal<Me | null>(null);
  private readonly failure = signal<string | null>(null);
  private loading: Promise<Me> | null = null;

  readonly user = this.me.asReadonly();
  readonly today = computed(() => this.me()?.today ?? null);
  /** Why the user was sent back to sign-in (such as "not invited"), shown once there. */
  readonly lastFailure = this.failure.asReadonly();

  /** Loads {@code /me} once; later calls get the same answer. Throws an {@link ApiError}. */
  load(): Promise<Me> {
    const current = this.me();
    if (current) return Promise.resolve(current);
    this.loading ??= firstValueFrom(this.http.get<Me>(`${environment.apiUrl}/me`))
      .then((me) => {
        this.me.set(me);
        return me;
      })
      .catch((error: unknown) => {
        this.loading = null;
        throw toApiError(error) satisfies ApiError;
      });
    return this.loading;
  }

  /** Forgets the user (on sign-out), keeping the reason to show on the sign-in screen. */
  clear(reason: string | null = null): void {
    this.me.set(null);
    this.loading = null;
    this.failure.set(reason);
  }

  /** The user may use the service: read, or write when {@code write} is true. */
  can(code: string, write = false): boolean {
    const access = this.me()?.services[code];
    return !!access && (write ? access.write : access.read);
  }
}
